package io.github.project.vault.core.backup

import android.content.Context
import io.github.project.vault.core.crypto.CryptoManager
import io.github.project.vault.core.cloud.GoogleDriveManager
import io.github.project.vault.core.root.RootShell
import io.github.project.vault.core.session.KeystoreManager
import io.github.project.vault.core.session.PermissionManager
import io.github.project.vault.core.session.SSAIDManager
import io.github.project.vault.data.model.AppInfo
import io.github.project.vault.data.model.BackupConfig
import io.github.project.vault.data.model.BackupMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BackupEngine(private val context: Context) {
    private val json = Json { prettyPrint = true }

    suspend fun performBackup(
        app: AppInfo,
        config: BackupConfig,
        destinationDir: File,
        onProgress: (BackupProgress) -> Unit
    ): BackupResult = withContext(Dispatchers.IO) {
        if (!RootShell.isRootAvailable()) {
            return@withContext BackupResult.Error("Superuser/Root access is required but not granted")
        }

        val timestamp = System.currentTimeMillis()
        val stagingPath = File(context.cacheDir, "stage_${app.packageName}_$timestamp").absolutePath
        val payloadPath = "$stagingPath/payload"

        // Ensure destination folder is created via root so Android Scoped Storage does not block it
        RootShell.execute("mkdir -p ${destinationDir.absolutePath}")
        RootShell.execute("chmod 777 ${destinationDir.absolutePath} 2>/dev/null")
        destinationDir.parentFile?.let { parent ->
            RootShell.execute("chmod 777 ${parent.absolutePath} 2>/dev/null")
        }

        try {
            val appUid = context.applicationInfo.uid
            onProgress(BackupProgress("Freezing app process…", 0.1f, app.appName, app.packageName, BackupSubStep.PREPARING))
            RootShell.execute("am force-stop ${app.packageName}")
            RootShell.execute("mkdir -p $payloadPath/apk $payloadPath/keystore")
            RootShell.execute("chown -R $appUid:$appUid $stagingPath 2>/dev/null && chmod -R 777 $stagingPath && restorecon -R -F $stagingPath 2>/dev/null")

            // 1. Back up APKs
            if (config.includeApk) {
                onProgress(BackupProgress("Backing up APK binaries…", 0.25f, app.appName, app.packageName, BackupSubStep.APK))
                RootShell.execute("cp -p ${app.sourceDir} $payloadPath/apk/base.apk")
                app.splitSourceDirs.forEachIndexed { index, splitPath ->
                    RootShell.execute("cp -p $splitPath $payloadPath/apk/split_$index.apk")
                }
            }

            // 2. Back up Internal Data (/data/user/0 or /data/data)
            var hasData = false
            if (config.includeInternalData) {
                onProgress(BackupProgress("Archiving internal app data…", 0.40f, app.appName, app.packageName, BackupSubStep.USER))
                val srcDir = if (RootShell.execute("[ -d /data/user/0 ] && echo 1 || echo 0").stdout.trim() == "1") "/data/user/0" else "/data/data"
                val dataCmd = "tar -cpf \"$payloadPath/data.tar\" -C $srcDir --exclude=\"${app.packageName}/cache\" --exclude=\"${app.packageName}/code_cache\" --exclude=\"${app.packageName}/lib\" \"${app.packageName}\""
                RootShell.execute(dataCmd, timeoutMs = 300_000L)
                val dataCheck = RootShell.execute("[ \$(stat -c %s \"$payloadPath/data.tar\" 2>/dev/null || wc -c < \"$payloadPath/data.tar\" || echo 0) -gt 2048 ] && echo 1 || echo 0")
                hasData = dataCheck.stdout.trim() == "1"

                // Device-encrypted data
                onProgress(BackupProgress("Archiving device encrypted data…", 0.50f, app.appName, app.packageName, BackupSubStep.USER_DE))
                RootShell.execute(
                    "if [ -d \"/data/user_de/0/${app.packageName}\" ]; then tar -cpf \"$payloadPath/de_data.tar\" -C /data/user_de/0 --exclude=\"${app.packageName}/cache\" --exclude=\"${app.packageName}/code_cache\" \"${app.packageName}\"; fi",
                    timeoutMs = 180_000L
                )
            }

            // 3. Back up External Data (/sdcard/Android/data)
            var hasExtData = false
            if (config.includeExternalData) {
                onProgress(BackupProgress("Archiving external storage data…", 0.60f, app.appName, app.packageName, BackupSubStep.DATA))
                RootShell.execute(
                    "if [ -d \"/sdcard/Android/data/${app.packageName}\" ]; then tar -cpf \"$payloadPath/ext_data.tar\" -C /sdcard/Android/data --exclude=\"${app.packageName}/cache\" \"${app.packageName}\"; fi",
                    timeoutMs = 300_000L
                )
                val extCheck = RootShell.execute("[ \$(stat -c %s \"$payloadPath/ext_data.tar\" 2>/dev/null || wc -c < \"$payloadPath/ext_data.tar\" || echo 0) -gt 2048 ] && echo 1 || echo 0")
                hasExtData = extCheck.stdout.trim() == "1"
            }

            // 4. Capture SSAID Session Token
            var ssaid: String? = null
            if (config.includeSSAID) {
                onProgress(BackupProgress("Extracting SSAID login identifier…", 0.70f, app.appName, app.packageName, BackupSubStep.SSAID))
                ssaid = SSAIDManager.getSSAID(app.packageName)
            }

            val liveUid = try {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
                    context.packageManager.getPackageInfo(app.packageName, android.content.pm.PackageManager.PackageInfoFlags.of(0)).applicationInfo?.uid
                } else {
                    @Suppress("DEPRECATION")
                    context.packageManager.getPackageInfo(app.packageName, 0).applicationInfo?.uid
                }
            } catch (_: Exception) { null } ?: app.uid

            // 5. Capture Keystore
            var hasKeystore = false
            if (config.includeKeystore) {
                onProgress(BackupProgress("Extracting Keystore encryption tokens…", 0.75f, app.appName, app.packageName, BackupSubStep.KEYSTORE))
                val keystoreStaging = File(payloadPath, "keystore")
                hasKeystore = KeystoreManager.backupKeystoreForUid(context, liveUid, keystoreStaging)
            }

            // 6. Permissions
            val permissions = if (config.includePermissions) {
                PermissionManager.getGrantedPermissions(app.packageName)
            } else emptyList()

            // 7. Write Metadata JSON
            val cleanAppName = app.appName.trim().lowercase()
                .replace("\\s+".toRegex(), "_")
                .replace("[^a-z0-9_-]".toRegex(), "")
                .ifBlank { app.packageName.substringAfterLast(".").lowercase() }

            val dateFormatted = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date(timestamp))
            val baseFileName = "${cleanAppName}_$dateFormatted.vault"
            val finalArchiveFile = File(destinationDir, baseFileName)

            val metadata = BackupMetadata(
                packageName = app.packageName,
                appName = app.appName,
                versionName = app.versionName,
                versionCode = app.versionCode,
                targetSdk = 37,
                originalUid = liveUid,
                backupTimestamp = timestamp,
                isEncrypted = config.encrypt,
                ssaid = ssaid,
                permissions = permissions,
                hasApk = config.includeApk,
                hasData = hasData,
                hasExternalData = hasExtData,
                hasKeystore = hasKeystore,
                archiveFileName = baseFileName,
                archiveSizeBytes = 0L
            )

            val metadataString = json.encodeToString(metadata)
            val metaFile = File(context.cacheDir, "metadata.json")
            metaFile.writeText(metadataString)
            RootShell.execute("cp ${metaFile.absolutePath} $payloadPath/metadata.json")

            // 8. Package staging folder into bundle inside app cache staging area
            onProgress(BackupProgress("Compressing backup package…", 0.85f, app.appName, app.packageName, BackupSubStep.COMPRESSING))
            val rawBundlePath = "$stagingPath/bundle.tar"
            val finalBundlePath = "$stagingPath/$baseFileName"

            val tarResult = RootShell.execute("tar -cf $rawBundlePath -C $payloadPath .", timeoutMs = 300_000L)
            if (!tarResult.isSuccess) {
                return@withContext BackupResult.Error("Failed to create backup archive: ${tarResult.stderr}")
            }
            RootShell.execute("chmod 666 $rawBundlePath")

            // 9. Client-Side Encryption
            if (config.encrypt && !config.encryptionPassword.isNullOrBlank()) {
                onProgress(BackupProgress("Encrypting archive with AES-256…", 0.90f, app.appName, app.packageName, BackupSubStep.ENCRYPTING))
                CryptoManager.encryptFile(File(rawBundlePath), File(finalBundlePath), config.encryptionPassword)
                RootShell.execute("rm -f $rawBundlePath")
            } else {
                RootShell.execute("mv $rawBundlePath $finalBundlePath")
            }
            RootShell.execute("chmod 666 $finalBundlePath")

            // 10. Copy final archive to storage destination via root
            onProgress(BackupProgress("Saving archive to storage…", 0.93f, app.appName, app.packageName, BackupSubStep.COMPRESSING))
            RootShell.execute("cp -f $finalBundlePath ${finalArchiveFile.absolutePath}")
            RootShell.execute("chmod 666 ${finalArchiveFile.absolutePath} 2>/dev/null")
            RootShell.execute("am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d \"file://${finalArchiveFile.absolutePath}\" 2>/dev/null")

            // 11. Google Drive Cloud Upload
            var cloudUploaded = false
            if (config.uploadToCloud) {
                val uploadFile = File(finalBundlePath)
                val totalBytes = uploadFile.length().coerceAtLeast(1L)
                val totalMb = totalBytes / (1024f * 1024f)
                onProgress(BackupProgress("Starting GDrive backup…", 0.90f, app.appName, app.packageName, BackupSubStep.UPLOAD))
                val drive = GoogleDriveManager(context)
                if (drive.isConnected()) {
                    val uploadSuccess = drive.uploadBackup(
                        file = uploadFile,
                        metadataJson = metadataString,
                        onProgress = { uploadFrac ->
                            val currentMb = (totalBytes * uploadFrac) / (1024f * 1024f)
                            val percent = (uploadFrac * 100).toInt()
                            val progressText = String.format(
                                java.util.Locale.US,
                                "GDrive Backup: %.1f / %.1f MB (%d%%)",
                                currentMb, totalMb, percent
                            )
                            val totalFraction = 0.90f + (uploadFrac * 0.09f)
                            onProgress(BackupProgress(progressText, totalFraction, app.appName, app.packageName, BackupSubStep.UPLOAD))
                        }
                    )
                    if (uploadSuccess) {
                        cloudUploaded = true
                    } else {
                        onProgress(BackupProgress("GDrive backup failed (saved locally)", 0.98f, app.appName, app.packageName, BackupSubStep.UPLOAD))
                    }
                }
            }

            val sizeResult = RootShell.execute("stat -c %s ${finalArchiveFile.absolutePath} 2>/dev/null || wc -c < ${finalArchiveFile.absolutePath}")
            val finalSize = sizeResult.stdout.trim().toLongOrNull() ?: 0L

            val completionMessage = if (config.uploadToCloud) {
                if (cloudUploaded) "Backup & Google Drive sync completed!" else "Local backup saved (cloud upload failed)"
            } else {
                "Backup completed successfully!"
            }

            onProgress(BackupProgress(completionMessage, 1.0f, app.appName, app.packageName, BackupSubStep.DONE, isFinished = true))
            BackupResult.Success(finalArchiveFile.absolutePath, finalSize)
        } catch (e: Exception) {
            e.printStackTrace()
            BackupResult.Error("Backup failed: ${e.message}", e)
        } finally {
            // Clean up temporary files
            RootShell.execute("rm -rf $stagingPath")
        }
    }
}
