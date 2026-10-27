package io.github.project.vault.core.backup

enum class BackupSubStep(val label: String) {
    PREPARING("Necessary preparations"),
    APK("Backup APK"),
    USER("Backup USER"),
    USER_DE("Backup USER_DE"),
    DATA("Backup DATA"),
    SSAID("Backup SSAID"),
    KEYSTORE("Backup Keystore"),
    COMPRESSING("Compressing archive"),
    ENCRYPTING("Encrypting archive"),
    UPLOAD("GDrive Backup"),
    DONE("Completed")
}

data class BackupProgress(
    val currentStep: String,
    val progressFraction: Float,
    val currentApp: String,
    val currentPackageName: String = "",
    val subStep: BackupSubStep = BackupSubStep.PREPARING,
    val appIndex: Int = 1,
    val totalApps: Int = 1,
    val isPreprocessing: Boolean = false,
    val isFinished: Boolean = false,
    val isFailed: Boolean = false,
    val errorMessage: String? = null
)

sealed class BackupResult {
    data class Success(val archivePath: String, val sizeBytes: Long) : BackupResult()
    data class Error(val message: String, val cause: Throwable? = null) : BackupResult()
}
