package io.github.project.vault.data.model

import kotlinx.serialization.Serializable

@Serializable
data class BackupMetadata(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val versionCode: Long,
    val targetSdk: Int,
    val originalUid: Int,
    val backupTimestamp: Long,
    val isEncrypted: Boolean,
    val ssaid: String? = null,
    val permissions: List<String> = emptyList(),
    val hasApk: Boolean = true,
    val hasData: Boolean = true,
    val hasExternalData: Boolean = false,
    val hasKeystore: Boolean = false,
    val archiveFileName: String,
    val archiveSizeBytes: Long = 0L
)
