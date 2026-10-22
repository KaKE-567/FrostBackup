package io.github.project.vault.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
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
    val appSizeBytes: Long = 0L,
    val firstInstallTime: Long = 0L,
    val lastUpdateTime: Long = 0L,
    val hasBackup: Boolean = false,
    val lastBackupTimestamp: Long? = null
)
