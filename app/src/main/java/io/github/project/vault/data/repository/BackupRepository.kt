package io.github.project.vault.data.repository

import android.content.Context
import io.github.project.vault.core.cloud.GoogleDriveManager
import io.github.project.vault.core.root.RootShell
import io.github.project.vault.core.storage.StorageLocationManager
import io.github.project.vault.data.model.BackupMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class BackupRepository(private val context: Context) {
    private val json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
    }

    companion object {
        private val metadataCache = ConcurrentHashMap<String, BackupMetadata>()
        @Volatile
        private var cachedBackupList: List<BackupMetadata>? = null

        private val _backupsFlow = MutableStateFlow<List<BackupMetadata>>(emptyList())
        val backupsFlow: StateFlow<List<BackupMetadata>> = _backupsFlow.asStateFlow()

        fun invalidateCache() {
            metadataCache.clear()
            cachedBackupList = null
        }
    }

    val backups: StateFlow<List<BackupMetadata>> = backupsFlow

    fun getBackupDirectory(): File {
        return StorageLocationManager.getBackupDirectory(context)
    }

    fun setBackupDirectory(path: String) {
        StorageLocationManager.setSelectedBackupDirectory(context, path)
        invalidateCache()
    }

    fun getCachedBackups(): List<BackupMetadata>? = cachedBackupList ?: _backupsFlow.value.ifEmpty { null }

    suspend fun refreshBackups(forceRefresh: Boolean = true): List<BackupMetadata> = withContext(Dispatchers.IO) {
        if (forceRefresh) {
            invalidateCache()
        }
        val result = getLocalBackups(forceRefresh = forceRefresh)
        _backupsFlow.value = result
        result
    }

    suspend fun getLocalBackups(forceRefresh: Boolean = false): List<BackupMetadata> = withContext(Dispatchers.IO) {
        if (!forceRefresh && cachedBackupList != null) {
            _backupsFlow.value = cachedBackupList!!
            return@withContext cachedBackupList!!
        }

        val backupDir = getBackupDirectory()

        // 1. Fast native file listing first (0ms, no su process spawn)
        val directFiles = backupDir.listFiles { file -> file.extension == "vault" }

        // 2. If native listing returned null or empty, ensure folder exists and query via root shell
        val filePaths: List<String> = if (!directFiles.isNullOrEmpty()) {
            directFiles.map { it.absolutePath }
        } else {
            RootShell.execute("mkdir -p \"${backupDir.absolutePath}\" && chmod 777 \"${backupDir.absolutePath}\" 2>/dev/null")

            val findCmd = "find \"${backupDir.absolutePath}\" -maxdepth 1 -name '*.vault' 2>/dev/null"
            var listResult = RootShell.execute(findCmd)

            if (!listResult.isSuccess || listResult.stdout.isBlank()) {
                if (backupDir.absolutePath.startsWith("/storage/emulated/0")) {
                    val altPath = backupDir.absolutePath.replaceFirst("/storage/emulated/0", "/sdcard")
                    listResult = RootShell.execute("find \"$altPath\" -maxdepth 1 -name '*.vault' 2>/dev/null")
                }
            }

            if (!listResult.isSuccess || listResult.stdout.isBlank()) {
                emptyList()
            } else {
                listResult.stdout.lines().map { it.trim() }.filter { it.endsWith(".vault") }
            }
        }

        val parsedList = filePaths.mapNotNull { path ->
            val file = File(path)
            val size = if (file.length() > 0L) file.length() else {
                val sizeRes = RootShell.execute("stat -c %s \"$path\" 2>/dev/null || wc -c < \"$path\"")
                sizeRes.stdout.trim().toLongOrNull() ?: 0L
            }
            val cacheKey = "${file.name}_$size"
            metadataCache[cacheKey] ?: parseMetadataFromArchive(file, size)?.also {
                metadataCache[cacheKey] = it
            }
        }.sortedByDescending { it.backupTimestamp }

        cachedBackupList = parsedList
        _backupsFlow.value = parsedList
        parsedList
    }

    private suspend fun parseMetadataFromArchive(file: File, knownSize: Long = 0L): BackupMetadata? {
        val path = if (file.absolutePath.startsWith("/storage/emulated/0")) {
            file.absolutePath.replaceFirst("/storage/emulated/0", "/sdcard")
        } else {
            file.absolutePath
        }

        val result = RootShell.execute(
            "tar -xf \"$path\" -O ./metadata.json 2>/dev/null || tar -xf \"$path\" -O metadata.json 2>/dev/null || tar -xOf \"$path\" ./metadata.json 2>/dev/null"
        )
        val sizeBytes = if (knownSize > 0L) knownSize else {
            val sizeResult = RootShell.execute("stat -c %s \"$path\" 2>/dev/null || wc -c < \"$path\"")
            sizeResult.stdout.trim().toLongOrNull() ?: file.length()
        }

        val parsedMeta: BackupMetadata? = if (result.isSuccess && result.stdout.isNotBlank()) {
            try {
                json.decodeFromString<BackupMetadata>(result.stdout).copy(
                    archiveSizeBytes = sizeBytes,
                    archiveFileName = file.name
                )
            } catch (_: Exception) {
                // Fallback: extract packageName and appName using regex from metadata JSON
                val pkgRegex = Regex("\"packageName\"\\s*:\\s*\"([^\"]+)\"")
                val appRegex = Regex("\"appName\"\\s*:\\s*\"([^\"]+)\"")
                val foundPkg = pkgRegex.find(result.stdout)?.groupValues?.getOrNull(1)
                val foundName = appRegex.find(result.stdout)?.groupValues?.getOrNull(1)
                if (!foundPkg.isNullOrBlank()) {
                    BackupMetadata(
                        packageName = foundPkg,
                        appName = foundName ?: file.nameWithoutExtension,
                        versionName = "Unknown",
                        versionCode = 0,
                        targetSdk = 34,
                        originalUid = 0,
                        backupTimestamp = file.lastModified().takeIf { it > 0 } ?: System.currentTimeMillis(),
                        isEncrypted = false,
                        archiveFileName = file.name,
                        archiveSizeBytes = sizeBytes
                    )
                } else null
            }
        } else null

        return parsedMeta ?: run {
            // Check if file is actually an encrypted archive or if it's an unencrypted tar
            val isTar = RootShell.execute("tar -tf \"$path\" 2>/dev/null | head -n1").stdout.isNotBlank()
            val isEncrypted = !isTar

            val name = file.nameWithoutExtension
            val appSlug = BackupMetadata.extractAppSlugFromArchive(file.name).ifBlank { name }
            val appTitle = appSlug.replace("_", " ").replaceFirstChar { it.uppercase() }

            val parsedTimestamp = try {
                val timeStr = name.takeLast(19) // "yyyy-MM-dd_HH-mm-ss"
                SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).parse(timeStr)?.time
            } catch (_: Exception) { null } ?: (if (file.lastModified() > 0L) file.lastModified() else System.currentTimeMillis())

            val resolvedPackage = resolvePackageNameFromSlug(appSlug) ?: appSlug

            BackupMetadata(
                packageName = resolvedPackage,
                appName = appTitle,
                versionName = "Unknown",
                versionCode = 0,
                targetSdk = 34,
                originalUid = 0,
                backupTimestamp = parsedTimestamp,
                isEncrypted = isEncrypted,
                archiveFileName = file.name,
                archiveSizeBytes = sizeBytes
            )
        }
    }

    private fun resolvePackageNameFromSlug(slug: String): String? {
        val cleanSlug = slug.trim().lowercase().replace("-", "_").replace("[^a-z0-9_]".toRegex(), "")
        if (cleanSlug.isBlank()) return null

        return runCatching {
            val pm = context.packageManager
            val packages = pm.getInstalledPackages(0)
            for (pkg in packages) {
                val appName = pkg.applicationInfo?.loadLabel(pm)?.toString() ?: ""
                val clean = appName.trim().lowercase()
                    .replace("\\s+".toRegex(), "_")
                    .replace("[^a-z0-9_]".toRegex(), "")
                val cleanPkg = pkg.packageName.trim().lowercase().replace("[^a-z0-9_]".toRegex(), "")
                val cleanPkgTail = pkg.packageName.substringAfterLast(".").trim().lowercase().replace("[^a-z0-9_]".toRegex(), "")

                if (clean == cleanSlug ||
                    pkg.packageName.equals(cleanSlug, ignoreCase = true) ||
                    cleanPkg == cleanSlug ||
                    cleanPkgTail == cleanSlug
                ) {
                    return@runCatching pkg.packageName
                }
            }
            null
        }.getOrNull()
    }

    suspend fun deleteBackup(archiveFileName: String): Boolean = withContext(Dispatchers.IO) {
        val filePath = "${getBackupDirectory().absolutePath}/$archiveFileName"
        val result = RootShell.execute("rm -f \"$filePath\"")
        if (result.isSuccess) {
            invalidateCache()
            getLocalBackups(forceRefresh = true)
        }
        result.isSuccess
    }

    suspend fun uploadToCloud(archiveFileName: String): Boolean = withContext(Dispatchers.IO) {
        val backupDir = getBackupDirectory()
        val archiveFile = File(backupDir, archiveFileName)
        val metaResult = RootShell.execute("tar -xf \"${archiveFile.absolutePath}\" -O ./metadata.json 2>/dev/null || tar -xf \"${archiveFile.absolutePath}\" -O metadata.json 2>/dev/null")
        val metaString = if (metaResult.isSuccess && metaResult.stdout.isNotBlank()) metaResult.stdout else "{}"

        val stagingDir = File(context.cacheDir, "cloud_staging")
        stagingDir.mkdirs()
        val cacheStaging = File(stagingDir, archiveFileName)
        try {
            RootShell.execute("cp -p \"${archiveFile.absolutePath}\" \"${cacheStaging.absolutePath}\"")
            RootShell.execute("chmod 666 \"${cacheStaging.absolutePath}\"")

            val drive = GoogleDriveManager(context)
            if (!drive.isConnected()) return@withContext false
            drive.uploadBackup(cacheStaging, metaString) {}
        } catch (e: Exception) {
            false
        } finally {
            RootShell.execute("rm -rf \"${stagingDir.absolutePath}\"")
        }
    }
}
