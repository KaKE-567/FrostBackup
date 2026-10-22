package io.github.project.vault.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
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
) {
    companion object {
        fun extractAppSlugFromArchive(fileName: String): String {
            return fileName.removeSuffix(".vault")
                .replace("_[0-9]{4}-[0-9]{2}-[0-9]{2}_[0-9]{2}-[0-9]{2}-[0-9]{2}$".toRegex(), "")
                .replace("-[0-9]{4}-[0-9]{2}-[0-9]{2}_[0-9]{2}-[0-9]{2}-[0-9]{2}$".toRegex(), "")
                .replace("_[0-9]{10,15}$".toRegex(), "")
                .replace("-[0-9]{10,15}$".toRegex(), "")
        }
    }
}
