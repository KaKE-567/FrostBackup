package io.github.project.vault.data.model

import androidx.compose.runtime.Immutable
import kotlinx.serialization.Serializable

@Immutable
@Serializable
data class WifiNetwork(
    val ssid: String,
    val preSharedKey: String? = null,
    val wepKey: String? = null,
    val keyMgmt: String = "WPA_PSK",
    val hiddenSSID: Boolean = false,
    val rawConfigXml: String? = null
) {
    val cleanSsid: String
        get() = ssid.removeSurrounding("\"").removeSurrounding("&quot;")

    val cleanPassword: String?
        get() = preSharedKey?.removeSurrounding("\"")?.removeSurrounding("&quot;")

    val isSecure: Boolean
        get() = !cleanPassword.isNullOrBlank() || !wepKey.isNullOrBlank()

    val securityBadge: String
        get() = when {
            keyMgmt.contains("SAE", ignoreCase = true) -> "WPA3"
            keyMgmt.contains("WPA", ignoreCase = true) -> "WPA2"
            keyMgmt.contains("WEP", ignoreCase = true) || !wepKey.isNullOrBlank() -> "WEP"
            cleanPassword.isNullOrBlank() -> "Open"
            else -> "WPA2"
        }
}

@Immutable
@Serializable
data class WifiArchiveMetadata(
    val backupTimestamp: Long,
    val networkCount: Int,
    val archiveFileName: String,
    val archiveSizeBytes: Long = 0L,
    val deviceModel: String = "",
    val isEncrypted: Boolean = false
)
