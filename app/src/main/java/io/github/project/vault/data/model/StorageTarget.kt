package io.github.project.vault.data.model

enum class StorageType {
    INTERNAL,
    SD_CARD,
    USB_DRIVE,
    CUSTOM
}

data class StorageTarget(
    val id: String,
    val name: String,
    val type: StorageType,
    val backupDirectoryPath: String,
    val totalBytes: Long,
    val freeBytes: Long,
    val isMounted: Boolean
) {
    val usedFraction: Float
        get() = if (totalBytes > 0) ((totalBytes - freeBytes).toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f

    val formattedFreeSpace: String
        get() = formatBytes(freeBytes)

    val formattedTotalSpace: String
        get() = formatBytes(totalBytes)

    companion object {
        fun formatBytes(bytes: Long): String {
            val gb = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
            val mb = bytes.toDouble() / (1024.0 * 1024.0)
            return when {
                gb >= 1.0 -> String.format(java.util.Locale.US, "%.1f GB", gb)
                mb >= 1.0 -> String.format(java.util.Locale.US, "%.0f MB", mb)
                else -> "$bytes B"
            }
        }
    }
}
