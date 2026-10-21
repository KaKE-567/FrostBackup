package io.github.project.vault.data.repository

import android.content.Context
import io.github.project.vault.core.cloud.GoogleDriveManager
import io.github.project.vault.core.root.RootShell
import io.github.project.vault.data.model.BackupMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class BackupRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }

    companion object {
        private val metadataCache = ConcurrentHashMap<String, BackupMetadata>()
        @Volatile
        private var cachedBackupList: List<BackupMetadata>? = null

        fun invalidateCache() {
            metadataCache.clear()
            cachedBackupList = null
        }
    }

    fun getBackupDirectory(): File {
        val dir = File("/sdcard/FrostBackup/Backups")
        return dir
    }

    fun getCachedBackups(): List<BackupMetadata>? = cachedBackupList

    suspend fun getLocalBackups(forceRefresh: Boolean = false): List<BackupMetadata> = withContext(Dispatchers.IO) {
        if (!forceRefresh && cachedBackupList != null) {
            return@withContext cachedBackupList!!
        }

        val backupDir = getBackupDirectory()

        // Fast native file listing first (0ms, no su process spawn)
        val directFiles = backupDir.listFiles { file -> file.extension == "vault" }
        val filePaths = if (directFiles != null) {
            directFiles.map { it.absolutePath }
        } else {
            // Fallback to root shell if standard file access fails
            RootShell.execute("mkdir -p ${backupDir.absolutePath}")
            val listResult = RootShell.execute("ls -1 ${backupDir.absolutePath}/*.vault 2>/dev/null")
            if (!listResult.isSuccess || listResult.stdout.isBlank()) {
                emptyList()
            } else {
                listResult.stdout.lines().map { it.trim() }.filter { it.endsWith(".vault") }
            }
        }

        val backups = filePaths.mapNotNull { path ->
            val file = File(path)
            val cacheKey = "${file.absolutePath}_${file.length()}"
            metadataCache[cacheKey] ?: parseMetadataFromArchive(file)?.also {
                metadataCache[cacheKey] = it
            }
        }.sortedByDescending { it.backupTimestamp }

        cachedBackupList = backups
        backups
    }

    private suspend fun parseMetadataFromArchive(file: File): BackupMetadata? {
        val path = if (file.absolutePath.startsWith("/storage/emulated/0")) {
            file.absolutePath.replaceFirst("/storage/emulated/0", "/sdcard")
        } else {
            file.absolutePath
        }

        val result = RootShell.execute(
            "tar -xOf $path ./metadata.json 2>/dev/null || tar -xOf $path metadata.json 2>/dev/null"
        )
        return if (result.isSuccess && result.stdout.isNotBlank()) {
            try {
                val sizeResult = RootShell.execute("stat -c %s $path 2>/dev/null || wc -c < $path")
                val sizeBytes = sizeResult.stdout.trim().toLongOrNull() ?: file.length()
                json.decodeFromString<BackupMetadata>(result.stdout).copy(
                    archiveSizeBytes = sizeBytes,
                    archiveFileName = file.name
                )
            } catch (e: Exception) {
                null
            }
        } else {
            // Check if file is actually an encrypted archive or if it's an unencrypted tar
            val isTar = RootShell.execute("tar -tf $path 2>/dev/null | head -n1").stdout.isNotBlank()
            val isEncrypted = !isTar

            val sizeResult = RootShell.execute("stat -c %s $path 2>/dev/null || wc -c < $path")
            val sizeBytes = sizeResult.stdout.trim().toLongOrNull() ?: file.length()
            val name = file.nameWithoutExtension
            val appTitle = name.substringBeforeLast("_").replace("_", " ").replaceFirstChar { it.uppercase() }
            BackupMetadata(
                packageName = name.substringBeforeLast("_").ifBlank { name },
                appName = appTitle,
                versionName = "Unknown",
                versionCode = 0,
                targetSdk = 34,
                originalUid = 0,
                backupTimestamp = file.lastModified(),
                isEncrypted = isEncrypted,
                archiveFileName = file.name,
                archiveSizeBytes = sizeBytes
            )
        }
    }

    suspend fun deleteBackup(archiveFileName: String): Boolean = withContext(Dispatchers.IO) {
        val filePath = "${getBackupDirectory().absolutePath}/$archiveFileName"
        val result = RootShell.execute("rm -f $filePath")
        if (result.isSuccess) {
            invalidateCache()
        }
        result.isSuccess
    }

    suspend fun uploadToCloud(archiveFileName: String): Boolean = withContext(Dispatchers.IO) {
        val backupDir = getBackupDirectory()
        val archiveFile = File(backupDir, archiveFileName)
        val metaResult = RootShell.execute("tar -xOf ${archiveFile.absolutePath} ./metadata.json 2>/dev/null")
        val metaString = if (metaResult.isSuccess && metaResult.stdout.isNotBlank()) metaResult.stdout else "{}"

        val stagingDir = File(context.cacheDir, "cloud_staging")
        stagingDir.mkdirs()
        val cacheStaging = File(stagingDir, archiveFileName)
        try {
            RootShell.execute("cp -p ${archiveFile.absolutePath} ${cacheStaging.absolutePath}")
            RootShell.execute("chmod 666 ${cacheStaging.absolutePath}")

            val drive = GoogleDriveManager(context)
            if (!drive.isConnected()) return@withContext false
            drive.uploadBackup(cacheStaging, metaString) {}
        } catch (e: Exception) {
            false
        } finally {
            RootShell.execute("rm -rf ${stagingDir.absolutePath}")
        }
    }
}
