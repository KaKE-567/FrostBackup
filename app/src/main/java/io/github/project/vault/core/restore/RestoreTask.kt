package io.github.project.vault.core.restore

data class RestoreProgress(
    val currentStep: String,
    val progressFraction: Float,
    val currentApp: String
)

sealed class RestoreResult {
    data class Success(val needsReboot: Boolean) : RestoreResult()
    data class Error(val message: String, val cause: Throwable? = null) : RestoreResult()
}
