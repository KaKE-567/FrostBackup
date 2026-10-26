package io.github.project.vault.core.restore

import android.content.Context
import io.github.project.vault.core.crypto.CryptoManager
import io.github.project.vault.core.root.RootShell
import io.github.project.vault.core.session.KeystoreManager
import io.github.project.vault.core.session.PermissionManager
import io.github.project.vault.core.session.SSAIDManager
import io.github.project.vault.data.model.BackupMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File

class RestoreEngine(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun performRestore(
        archiveFile: File,
        password: String? = null,
        onProgress: (RestoreProgress) -> Unit
    ): RestoreResult = withContext(Dispatchers.IO) {
        if (!RootShell.isRootAvailable()) {
            return@withContext RestoreResult.Error("Root privileges required to restore application state")
        }

        val timestamp = System.currentTimeMillis()
        val stagingPath = "/data/local/tmp/vault_restore_$timestamp"
        var decryptedTempFile: File? = null

        val safeArchiveFile = if (archiveFile.absolutePath.startsWith("/storage/emulated/0")) {
            File(archiveFile.absolutePath.replaceFirst("/storage/emulated/0", "/sdcard"))
        } else {
            archiveFile
        }

        try {
            onProgress(RestoreProgress("Preparing restore environment…", 0.05f, ""))
            RootShell.execute("mkdir -p $stagingPath")

            // 1. Decrypt if password provided or file is encrypted
            val effectiveArchiveFile = if (!password.isNullOrBlank()) {
                onProgress(RestoreProgress("Decrypting backup archive…", 0.15f, ""))
                val tempDecrypted = File(context.cacheDir, "decrypted_$timestamp.tar")
                CryptoManager.decryptFile(safeArchiveFile, tempDecrypted, password)
                decryptedTempFile = tempDecrypted
                tempDecrypted
            } else {
                safeArchiveFile
            }

            // 2. Unpack archive to staging directory
            onProgress(RestoreProgress("Unpacking backup archive…", 0.25f, ""))
            val unpackResult = RootShell.execute(
                "tar -xpf ${effectiveArchiveFile.absolutePath} -C $stagingPath"
            )
            if (!unpackResult.isSuccess) {
                return@withContext RestoreResult.Error("Failed to extract backup package: ${unpackResult.stderr}")
            }
            // Ensure system / installd / package manager has full read permissions into the staging APKs
            RootShell.execute("chmod -R 777 $stagingPath")

            // 3. Read and parse metadata
            val metaFileResult = RootShell.execute("cat $stagingPath/metadata.json")
            if (!metaFileResult.isSuccess || metaFileResult.stdout.isBlank()) {
                return@withContext RestoreResult.Error("Backup metadata is missing or corrupted")
            }
            val metadata = json.decodeFromString<BackupMetadata>(metaFileResult.stdout)
            val pkg = metadata.packageName
            onProgress(RestoreProgress("Restoring ${metadata.appName}…", 0.35f, metadata.appName))

            // 4. Reinstall APK if present
            if (metadata.hasApk) {
                onProgress(RestoreProgress("Reinstalling APK packages…", 0.45f, metadata.appName))
                val apkFilesResult = RootShell.execute("ls -1 $stagingPath/apk/*.apk 2>/dev/null")
                if (apkFilesResult.isSuccess && apkFilesResult.stdout.isNotBlank()) {
                    val apks = apkFilesResult.stdout.lines().map { it.trim() }.filter { it.endsWith(".apk") }
                    if (apks.size == 1) {
                        val installCmd = "pm install -r -d -g --bypass-low-target-sdk-block ${apks.first()} 2>&1 || pm install -r -d -g ${apks.first()} 2>&1"
                        val apkInstallResult = RootShell.execute(installCmd)
                        val isFailed = !apkInstallResult.isSuccess || apkInstallResult.stdout.contains("Failure [")
                        if (isFailed) {
                            val failureReason = apkInstallResult.stdout.lines().firstOrNull { it.contains("Failure [") || it.startsWith("Error:") }
                                ?: apkInstallResult.output
                            return@withContext RestoreResult.Error("APK installation failed: $failureReason")
                        }
                    } else if (apks.size > 1) {
                        val sessionRes = RootShell.execute("pm install-create -r -d -g --bypass-low-target-sdk-block 2>&1 || pm install-create -r -d -g 2>&1")
                        val sessionId = Regex("""\[(\d+)\]""").find(sessionRes.stdout)?.groupValues?.get(1)
                            ?: Regex("""(\d+)""").find(sessionRes.stdout)?.groupValues?.get(1)
                        if (sessionId != null) {
                            for (apkPath in apks) {
                                val apkName = File(apkPath).name
                                val size = RootShell.execute("stat -c %s $apkPath 2>/dev/null || wc -c < $apkPath").stdout.trim()
                                RootShell.execute("pm install-write -S $size $sessionId $apkName $apkPath")
                            }
                            val commitRes = RootShell.execute("pm install-commit $sessionId 2>&1")
                            val commitFailed = !commitRes.isSuccess || commitRes.stdout.contains("Failure [")
                            if (commitFailed) {
                                val failureReason = commitRes.stdout.lines().firstOrNull { it.contains("Failure [") || it.startsWith("Error:") }
                                    ?: commitRes.output
                                return@withContext RestoreResult.Error("Split APK install failed: $failureReason")
                            }
                        } else {
                            val baseApk = apks.firstOrNull { it.endsWith("base.apk") } ?: apks.first()
                            RootShell.execute("pm install -r -d -g $baseApk")
                        }
                    }
                }
            }

            // 5. Look up newly assigned UID with multi-tier fallback
            onProgress(RestoreProgress("Detecting security identifiers…", 0.55f, metadata.appName))

            var resolvedUid: Int? = null

            // Tier 1: Fast standard pm list packages -U
            val pmRes = RootShell.execute("pm list packages -U $pkg 2>/dev/null")
            val pmMatch = Regex("""uid:(\d+)""").find(pmRes.stdout)
            resolvedUid = pmMatch?.groupValues?.get(1)?.toIntOrNull()

            // Tier 2: Direct lookup in /data/system/packages.list
            if (resolvedUid == null) {
                val pkgListRes = RootShell.execute("awk '\$1 == \"$pkg\" {print \$2}' /data/system/packages.list 2>/dev/null")
                resolvedUid = pkgListRes.stdout.trim().toIntOrNull()
            }

            // Tier 3: stat ownership on /data/data/$pkg or /data/user/0/$pkg
            if (resolvedUid == null) {
                val statRes = RootShell.execute("stat -c %u /data/data/$pkg 2>/dev/null || stat -c %u /data/user/0/$pkg 2>/dev/null")
                val statUid = statRes.stdout.trim().toIntOrNull()
                if (statUid != null && statUid >= 10000) {
                    resolvedUid = statUid
                }
            }

            // Tier 4: dumpsys package (handles userId=, appId=, and uid=)
            if (resolvedUid == null) {
                val uidResult = RootShell.execute("dumpsys package $pkg 2>/dev/null | grep -E -m 1 '(userId=|appId=|uid=)'")
                val uidMatch = Regex("""(?:userId|appId|uid)=(\d+)""").find(uidResult.stdout)
                resolvedUid = uidMatch?.groupValues?.get(1)?.toIntOrNull()
            }

            if (resolvedUid == null) {
                val pathCheck = RootShell.execute("pm path $pkg 2>/dev/null")
                if (pathCheck.stdout.isBlank()) {
                    return@withContext RestoreResult.Error(
                        "Package $pkg is not installed. If this was a Data-Only backup, please install $pkg from Play Store/Aurora first, then restore."
                    )
                }
                return@withContext RestoreResult.Error("Failed to obtain newly assigned user ID for $pkg")
            }

            val newUid = resolvedUid

            // 6. Stop app before restoring data
            RootShell.execute("am force-stop $pkg")

            // 7. Extract Internal Data
            if (metadata.hasData) {
                onProgress(RestoreProgress("Restoring internal databases & sessions…", 0.65f, metadata.appName))
                RootShell.execute("tar -xpf $stagingPath/data.tar -C /data/data/")
                RootShell.execute("tar -xpf $stagingPath/de_data.tar -C /data/user_de/0/ 2>/dev/null")
            }

            // 8. Extract External Data
            if (metadata.hasExternalData) {
                onProgress(RestoreProgress("Restoring external storage files…", 0.75f, metadata.appName))
                RootShell.execute("tar -xpf $stagingPath/ext_data.tar -C /sdcard/Android/data/ 2>/dev/null")
            }

            // 9. Re-map Linux UID / GID ownership & SELinux contexts
            onProgress(RestoreProgress("Applying security & SELinux contexts…", 0.85f, metadata.appName))
            RootShell.execute("chown -R $newUid:$newUid /data/data/$pkg")
            RootShell.execute("chmod 771 /data/data/$pkg")
            RootShell.execute("restorecon -R /data/data/$pkg")
            RootShell.execute("restorecon -R /data/user_de/0/$pkg 2>/dev/null")

            // 10. Restore Keystore Encryption Tokens
            if (metadata.hasKeystore) {
                onProgress(RestoreProgress("Restoring Keystore cryptographic keys…", 0.90f, metadata.appName))
                val keystoreStaging = File(stagingPath, "keystore")
                KeystoreManager.restoreKeystoreForUid(metadata.originalUid, newUid, keystoreStaging)
            }

            // 11. Restore Permissions
            PermissionManager.restorePermissions(pkg, metadata.permissions)

            // 12. Restore SSAID (Preserve Login State)
            var needsReboot = false
            if (!metadata.ssaid.isNullOrBlank()) {
                onProgress(RestoreProgress("Restoring SSAID device signature…", 0.95f, metadata.appName))
                val ssaidRestored = SSAIDManager.restoreSSAID(pkg, metadata.ssaid)
                if (ssaidRestored) {
                    needsReboot = true
                }
            }

            onProgress(RestoreProgress("Restore completed successfully!", 1.0f, metadata.appName))
            RestoreResult.Success(needsReboot)
        } catch (e: Exception) {
            e.printStackTrace()
            RestoreResult.Error("Restore failed: ${e.message}", e)
        } finally {
            decryptedTempFile?.delete()
            RootShell.execute("rm -rf $stagingPath")
        }
    }
}
