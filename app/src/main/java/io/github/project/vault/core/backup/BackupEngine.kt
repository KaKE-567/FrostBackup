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
        RootShell.execute("chmod 775 ${destinationDir.absolutePath}")

        try {
            onProgress(BackupProgress("Freezing app process…", 0.1f, app.appName))
            RootShell.execute("am force-stop ${app.packageName}")
            RootShell.execute("mkdir -p $payloadPath/apk $payloadPath/keystore")
            RootShell.execute("chmod -R 777 $stagingPath")

            // 1. Back up APKs
            if (config.includeApk) {
                onProgress(BackupProgress("Backing up APK binaries…", 0.25f, app.appName))
                RootShell.execute("cp -p ${app.sourceDir} $payloadPath/apk/base.apk")
                app.splitSourceDirs.forEachIndexed { index, splitPath ->
                    RootShell.execute("cp -p $splitPath $payloadPath/apk/split_$index.apk")
                }
            }

            // 2. Back up Internal Data (/data/data)
            var hasData = false
            if (config.includeInternalData) {
                onProgress(BackupProgress("Archiving internal app data…", 0.45f, app.appName))
                val dataTarResult = RootShell.execute(
                    "tar -cpf $payloadPath/data.tar -C /data/data ${app.packageName} 2>/dev/null"
                )
                hasData = dataTarResult.isSuccess

                // Device-encrypted data
                RootShell.execute(
                    "tar -cpf $payloadPath/de_data.tar -C /data/user_de/0 ${app.packageName} 2>/dev/null"
                )
            }

            // 3. Back up External Data (/sdcard/Android/data)
            var hasExtData = false
            if (config.includeExternalData) {
                onProgress(BackupProgress("Archiving external storage data…", 0.60f, app.appName))
                val extTarResult = RootShell.execute(
                    "tar -cpf $payloadPath/ext_data.tar -C /sdcard/Android/data ${app.packageName} 2>/dev/null"
                )
                hasExtData = extTarResult.isSuccess
            }

            // 4. Capture SSAID Session Token
            var ssaid: String? = null
            if (config.includeSSAID) {
                onProgress(BackupProgress("Extracting SSAID login identifier…", 0.70f, app.appName))
                ssaid = SSAIDManager.getSSAID(app.packageName)
            }

            // 5. Capture Keystore
            var hasKeystore = false
            if (config.includeKeystore) {
                onProgress(BackupProgress("Extracting Keystore encryption tokens…", 0.75f, app.appName))
                val keystoreStaging = File(payloadPath, "keystore")
                hasKeystore = KeystoreManager.backupKeystoreForUid(app.uid, keystoreStaging)
            }

            // 6. Permissions
            val permissions = if (config.includePermissions) {
                PermissionManager.getGrantedPermissions(app.packageName)
            } else emptyList()

            // 7. Write Metadata JSON
            val baseFileName = "${app.packageName}_$timestamp.vault"
            val finalArchiveFile = File(destinationDir, baseFileName)

            val metadata = BackupMetadata(
                packageName = app.packageName,
                appName = app.appName,
                versionName = app.versionName,
                versionCode = app.versionCode,
                targetSdk = 34,
                originalUid = app.uid,
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
            onProgress(BackupProgress("Compressing backup package…", 0.85f, app.appName))
            val rawBundlePath = "$stagingPath/bundle.tar"
            val finalBundlePath = "$stagingPath/final.vault"

            val tarResult = RootShell.execute("tar -cpf $rawBundlePath -C $payloadPath .")
            if (!tarResult.isSuccess) {
                return@withContext BackupResult.Error("Failed to create backup archive: ${tarResult.stderr}")
            }
            RootShell.execute("chmod 666 $rawBundlePath")

            // 9. Client-Side Encryption
            if (config.encrypt && !config.encryptionPassword.isNullOrBlank()) {
                onProgress(BackupProgress("Encrypting archive with AES-256…", 0.90f, app.appName))
                CryptoManager.encryptFile(File(rawBundlePath), File(finalBundlePath), config.encryptionPassword)
                RootShell.execute("rm -f $rawBundlePath")
            } else {
                RootShell.execute("mv $rawBundlePath $finalBundlePath")
            }
            RootShell.execute("chmod 666 $finalBundlePath")

            // 10. Copy final archive to storage destination via root
            onProgress(BackupProgress("Saving archive to storage…", 0.93f, app.appName))
            RootShell.execute("cp -p $finalBundlePath ${finalArchiveFile.absolutePath}")
            RootShell.execute("chmod 664 ${finalArchiveFile.absolutePath}")

            // 11. Google Drive Cloud Upload
            var cloudUploaded = false
            if (config.uploadToCloud) {
                onProgress(BackupProgress("Uploading to Google Drive…", 0.95f, app.appName))
                val drive = GoogleDriveManager(context)
                if (drive.isConnected()) {
                    val uploadSuccess = drive.uploadBackup(File(finalBundlePath), metadataString) {}
                    if (uploadSuccess) {
                        cloudUploaded = true
                    } else {
                        onProgress(BackupProgress("Google Drive upload failed (saved locally)", 0.98f, app.appName))
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

            onProgress(BackupProgress(completionMessage, 1.0f, app.appName))
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
