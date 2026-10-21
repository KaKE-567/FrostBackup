package io.github.project.vault.core.backup

data class BackupProgress(
    val currentStep: String,
    val progressFraction: Float,
    val currentApp: String
)

sealed class BackupResult {
    data class Success(val archivePath: String, val sizeBytes: Long) : BackupResult()
    data class Error(val message: String, val cause: Throwable? = null) : BackupResult()
}
