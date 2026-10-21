package io.github.project.vault.core.cloud

import java.io.File

data class CloudBackupItem(
    val fileId: String,
    val name: String,
    val sizeBytes: Long,
    val modifiedTime: Long,
    val appPackage: String
)

interface CloudProvider {
    val providerName: String
    suspend fun isConnected(): Boolean
    suspend fun disconnect()
    suspend fun uploadBackup(file: File, metadataJson: String, onProgress: (Float) -> Unit): Boolean
    suspend fun downloadBackup(fileId: String, destinationFile: File, onProgress: (Float) -> Unit): Boolean
    suspend fun listBackups(): List<CloudBackupItem>
    suspend fun deleteBackup(fileId: String): Boolean
}
