package io.github.project.vault.core.restore

enum class RestoreSubStep(val label: String) {
    PREPARING("Necessary preparations"),
    DECRYPTING("Decrypting archive"),
    UNPACKING("Unpacking archive"),
    APK("Restore APK"),
    USER("Restore USER"),
    USER_DE("Restore USER_DE"),
    DATA("Restore DATA"),
    SSAID("Restore SSAID"),
    KEYSTORE("Restore Keystore"),
    PERMISSIONS("Restore Permissions & Contexts"),
    DONE("Completed")
}

data class RestoreProgress(
    val currentStep: String,
    val progressFraction: Float,
    val currentApp: String,
    val currentPackageName: String = "",
    val subStep: RestoreSubStep = RestoreSubStep.PREPARING,
    val appIndex: Int = 1,
    val totalApps: Int = 1,
    val isPreprocessing: Boolean = false,
    val isFinished: Boolean = false,
    val isFailed: Boolean = false,
    val errorMessage: String? = null
)

sealed class RestoreResult {
    data class Success(val needsReboot: Boolean) : RestoreResult()
    data class Error(val message: String, val cause: Throwable? = null) : RestoreResult()
}
