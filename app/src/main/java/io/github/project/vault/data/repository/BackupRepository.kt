package io.github.project.vault.data.repository

import android.content.Context
import android.os.Environment
import io.github.project.vault.core.root.RootShell
import io.github.project.vault.data.model.BackupMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

class BackupRepository(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }

    fun getBackupDirectory(): File {
        val rootSdcard = File(Environment.getExternalStorageDirectory(), "AppVault/Backups")
        if (!rootSdcard.exists()) {
            rootSdcard.mkdirs()
        }
        return rootSdcard
    }

    suspend fun getLocalBackups(): List<BackupMetadata> = withContext(Dispatchers.IO) {
        val backupDir = getBackupDirectory()
        val vaultFiles = backupDir.listFiles { _, name -> name.endsWith(".vault") } ?: return@withContext emptyList()

        vaultFiles.mapNotNull { file ->
            parseMetadataFromArchive(file)
        }.sortedByDescending { it.backupTimestamp }
    }

    private suspend fun parseMetadataFromArchive(file: File): BackupMetadata? {
        val result = RootShell.execute(
            "tar -xOf ${file.absolutePath} ./metadata.json 2>/dev/null"
        )
        return if (result.isSuccess && result.stdout.isNotBlank()) {
            try {
                json.decodeFromString<BackupMetadata>(result.stdout).copy(
                    archiveSizeBytes = file.length(),
                    archiveFileName = file.name
                )
            } catch (e: Exception) {
                null
            }
        } else {
            // If encrypted or missing internal metadata, synthesize from filename
            val parts = file.nameWithoutExtension.split("_")
            val pkg = parts.getOrNull(0) ?: file.nameWithoutExtension
            val timestamp = parts.getOrNull(1)?.toLongOrNull() ?: file.lastModified()
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
                archiveSizeBytes = file.length()
            )
        }
    }

    suspend fun deleteBackup(archiveFileName: String): Boolean = withContext(Dispatchers.IO) {
        val file = File(getBackupDirectory(), archiveFileName)
        if (file.exists()) {
            file.delete()
        } else {
            false
        }
    }
}
