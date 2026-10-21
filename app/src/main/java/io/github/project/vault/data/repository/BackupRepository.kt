package io.github.project.vault.data.repository

import android.content.Context
import android.os.Environment
import io.github.project.vault.core.cloud.GoogleDriveManager
import io.github.project.vault.core.root.RootShell
import io.github.project.vault.data.model.BackupMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

class BackupRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }

    fun getBackupDirectory(): File {
        val dir = File(Environment.getExternalStorageDirectory(), "FrostBackup/Backups")
        return dir
    }

    suspend fun getLocalBackups(): List<BackupMetadata> = withContext(Dispatchers.IO) {
        val backupDir = getBackupDirectory()
        RootShell.execute("mkdir -p ${backupDir.absolutePath}")

        // List files via root to bypass any Scoped Storage blocks
        val listResult = RootShell.execute("ls -1 ${backupDir.absolutePath}/*.vault 2>/dev/null")
        if (!listResult.isSuccess || listResult.stdout.isBlank()) {
            return@withContext emptyList()
        }

        val filePaths = listResult.stdout.lines().map { it.trim() }.filter { it.endsWith(".vault") }
        filePaths.mapNotNull { path ->
            val file = File(path)
            parseMetadataFromArchive(file)
        }.sortedByDescending { it.backupTimestamp }
    }

    private suspend fun parseMetadataFromArchive(file: File): BackupMetadata? {
        val result = RootShell.execute(
            "tar -xOf ${file.absolutePath} ./metadata.json 2>/dev/null"
        )
        return if (result.isSuccess && result.stdout.isNotBlank()) {
            try {
                val sizeResult = RootShell.execute("stat -c %s ${file.absolutePath} 2>/dev/null || wc -c < ${file.absolutePath}")
                val sizeBytes = sizeResult.stdout.trim().toLongOrNull() ?: file.length()
                json.decodeFromString<BackupMetadata>(result.stdout).copy(
                    archiveSizeBytes = sizeBytes,
                    archiveFileName = file.name
                )
            } catch (e: Exception) {
                null
            }
        } else {
            val sizeResult = RootShell.execute("stat -c %s ${file.absolutePath} 2>/dev/null || wc -c < ${file.absolutePath}")
            val sizeBytes = sizeResult.stdout.trim().toLongOrNull() ?: file.length()
            val parts = file.nameWithoutExtension.split("_")
            val pkg = parts.getOrNull(0) ?: file.nameWithoutExtension
            val timestamp = parts.getOrNull(1)?.toLongOrNull() ?: System.currentTimeMillis()
            BackupMetadata(
                packageName = pkg,
                appName = pkg,
                versionName = "Unknown",
                versionCode = 0,
                targetSdk = 34,
                originalUid = 0,
                backupTimestamp = timestamp,
                isEncrypted = true,
                archiveFileName = file.name,
                archiveSizeBytes = sizeBytes
            )
        }
    }

    suspend fun deleteBackup(archiveFileName: String): Boolean = withContext(Dispatchers.IO) {
        val filePath = "${getBackupDirectory().absolutePath}/$archiveFileName"
        val result = RootShell.execute("rm -f $filePath")
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
