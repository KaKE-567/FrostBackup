package io.github.project.vault.data.model

import kotlinx.serialization.Serializable

@Serializable
data class BackupConfig(
    val includeApk: Boolean = true,
    val includeInternalData: Boolean = true,
    val includeExternalData: Boolean = false,
    val includeSSAID: Boolean = true,
    val includeKeystore: Boolean = true,
    val includePermissions: Boolean = true,
    val encrypt: Boolean = false,
    val encryptionPassword: String? = null,
    val uploadToCloud: Boolean = false
)
