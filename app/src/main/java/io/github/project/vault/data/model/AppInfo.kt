package io.github.project.vault.data.model

import kotlinx.serialization.Serializable

@Serializable
data class AppInfo(
    val packageName: String,
    val appName: String,
    val versionName: String,
    val versionCode: Long,
    val isSystemApp: Boolean,
    val uid: Int,
    val sourceDir: String,
    val splitSourceDirs: List<String> = emptyList(),
    val dataDir: String = "/data/data/$packageName",
    val externalDataDir: String? = null,
    val appSizeFormatted: String = "",
    val hasBackup: Boolean = false,
    val lastBackupTimestamp: Long? = null
)
