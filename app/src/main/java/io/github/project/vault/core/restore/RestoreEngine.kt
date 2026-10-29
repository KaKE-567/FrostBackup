package io.github.project.vault.core.restore

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
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
            onProgress(RestoreProgress("Preparing restore environment…", 0.05f, "", "", RestoreSubStep.PREPARING))
            RootShell.execute("mkdir -p $stagingPath")

            // 1. Decrypt if password provided or file is encrypted
            val effectiveArchiveFile = if (!password.isNullOrBlank()) {
                onProgress(RestoreProgress("Decrypting backup archive…", 0.15f, "", "", RestoreSubStep.DECRYPTING))
                val tempDecrypted = File(context.cacheDir, "decrypted_$timestamp.tar")
                CryptoManager.decryptFile(safeArchiveFile, tempDecrypted, password)
                decryptedTempFile = tempDecrypted
                tempDecrypted
            } else {
                safeArchiveFile
            }

            // 2. Unpack archive to staging directory
            onProgress(RestoreProgress("Unpacking backup archive…", 0.25f, "", "", RestoreSubStep.UNPACKING))
            val unpackResult = RootShell.execute(
                "tar -xf ${effectiveArchiveFile.absolutePath} -C $stagingPath",
                timeoutMs = 300_000L
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
            onProgress(RestoreProgress("Restoring ${metadata.appName}…", 0.35f, metadata.appName, pkg, RestoreSubStep.PREPARING))

            // 4. Reinstall APK if present
            if (metadata.hasApk) {
                onProgress(RestoreProgress("Reinstalling APK packages…", 0.45f, metadata.appName, pkg, RestoreSubStep.APK))
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

            // Tier 0: Direct Android PackageManager query from this app
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    resolvedUid = context.packageManager.getPackageInfo(pkg, PackageManager.PackageInfoFlags.of(0)).applicationInfo?.uid
                } else {
                    @Suppress("DEPRECATION")
                    resolvedUid = context.packageManager.getPackageInfo(pkg, 0).applicationInfo?.uid
                }
            } catch (_: Exception) { }

            // Tier 1: Fast direct parse of /data/system/packages.list in Kotlin (no awk needed)
            if (resolvedUid == null || resolvedUid < 10000) {
                val pkgListRes = RootShell.execute("cat /data/system/packages.list 2>/dev/null")
                if (pkgListRes.isSuccess && pkgListRes.stdout.isNotBlank()) {
                    for (line in pkgListRes.stdout.lineSequence()) {
                        val parts = line.trim().split(Regex("\\s+"))
                        if (parts.size >= 2 && parts[0] == pkg) {
                            val candidate = parts[1].toIntOrNull()
                            if (candidate != null && candidate >= 10000) {
                                resolvedUid = candidate
                                break
                            }
                        }
                    }
                }
            }

            // Tier 2: Standard pm list packages -U
            if (resolvedUid == null || resolvedUid < 10000) {
                val pmRes = RootShell.execute("pm list packages -U $pkg 2>/dev/null")
                val pmMatch = Regex("""uid:(\d+)""").find(pmRes.stdout)
                val candidate = pmMatch?.groupValues?.get(1)?.toIntOrNull()
                if (candidate != null && candidate >= 10000) {
                    resolvedUid = candidate
                }
            }

            // Tier 3: dumpsys package $pkg
            if (resolvedUid == null || resolvedUid < 10000) {
                val uidResult = RootShell.execute("dumpsys package $pkg 2>/dev/null")
                val uidMatch = Regex("""(?:userId|appId|uid)=(\d+)""").find(uidResult.stdout)
                val candidate = uidMatch?.groupValues?.get(1)?.toIntOrNull()
                if (candidate != null && candidate >= 10000) {
                    resolvedUid = candidate
                }
            }

            // Tier 4: stat ownership on /data/data/$pkg or /data/user/0/$pkg
            if (resolvedUid == null || resolvedUid < 10000) {
                val statRes = RootShell.execute("stat -c %u /data/data/$pkg 2>/dev/null || stat -c %u /data/user/0/$pkg 2>/dev/null")
                val statUid = statRes.stdout.trim().toIntOrNull()
                if (statUid != null && statUid >= 10000) {
                    resolvedUid = statUid
                }
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
            RootShell.execute("am force-stop $pkg 2>/dev/null || true")
            RootShell.execute("pkill -9 -f $pkg 2>/dev/null || true")

            // Capture the dynamic SELinux context assigned to this package by installd before altering data
            var appCtx = RootShell.execute("ls -Zd /data/user/0/$pkg 2>/dev/null | awk '{print \$1}'").stdout.trim()
            if (appCtx.isBlank() || !appCtx.startsWith("u:object_r:")) {
                appCtx = RootShell.execute("ls -Zd /data/user_de/0/$pkg 2>/dev/null | awk '{print \$1}'").stdout.trim()
            }
            if (appCtx.isBlank() || !appCtx.startsWith("u:object_r:")) {
                val parentCtx = RootShell.execute("ls -Zd /data/user/0 2>/dev/null | awk '{print \$1}'").stdout.trim()
                if (parentCtx.isNotBlank()) {
                    appCtx = parentCtx.replace("system_data_file", "app_data_file")
                }
            }

            // 7. Extract Internal Data
            val hasDataTar = RootShell.execute("[ -f $stagingPath/data.tar ] && [ \$(stat -c %s $stagingPath/data.tar 2>/dev/null || echo 0) -gt 2048 ] && echo 1 || echo 0").stdout.trim() == "1"
            if (metadata.hasData || hasDataTar) {
                onProgress(RestoreProgress("Restoring internal databases & sessions…", 0.60f, metadata.appName, pkg, RestoreSubStep.USER))
                RootShell.execute("mkdir -p /data/user/0/$pkg /data/data/$pkg 2>/dev/null")
                RootShell.execute("find /data/user/0/$pkg -mindepth 1 -delete 2>/dev/null || rm -rf /data/user/0/$pkg/* /data/data/$pkg/* 2>/dev/null")
                val dstDir = if (RootShell.execute("[ -d /data/user/0 ] && echo 1 || echo 0").stdout.trim() == "1") "/data/user/0" else "/data/data"
                RootShell.execute(
                    "tar -xpf $stagingPath/data.tar -C $dstDir/",
                    timeoutMs = 300_000L
                )

                val hasDeDataTar = RootShell.execute("[ -f $stagingPath/de_data.tar ] && [ \$(stat -c %s $stagingPath/de_data.tar 2>/dev/null || echo 0) -gt 2048 ] && echo 1 || echo 0").stdout.trim() == "1"
                if (hasDeDataTar) {
                    onProgress(RestoreProgress("Restoring device encrypted data…", 0.70f, metadata.appName, pkg, RestoreSubStep.USER_DE))
                    RootShell.execute("mkdir -p /data/user_de/0/$pkg 2>/dev/null")
                    RootShell.execute("find /data/user_de/0/$pkg -mindepth 1 -delete 2>/dev/null || rm -rf /data/user_de/0/$pkg/* 2>/dev/null")
                    RootShell.execute("tar -xpf $stagingPath/de_data.tar -C /data/user_de/0/", timeoutMs = 180_000L)
                }
            }

            // 8. Extract External Data
            val hasExtTar = RootShell.execute("[ -f $stagingPath/ext_data.tar ] && [ \$(stat -c %s $stagingPath/ext_data.tar 2>/dev/null || echo 0) -gt 2048 ] && echo 1 || echo 0").stdout.trim() == "1"
            if (metadata.hasExternalData || hasExtTar) {
                onProgress(RestoreProgress("Restoring external storage files…", 0.75f, metadata.appName, pkg, RestoreSubStep.DATA))
                RootShell.execute("mkdir -p /storage/emulated/0/Android/data/$pkg 2>/dev/null")
                RootShell.execute("find /storage/emulated/0/Android/data/$pkg -mindepth 1 -delete 2>/dev/null || true")
                RootShell.execute("tar -xpf $stagingPath/ext_data.tar -C /storage/emulated/0/Android/data/", timeoutMs = 300_000L)
            }

            // 9. Re-map Linux UID / GID ownership & SELinux contexts
            onProgress(RestoreProgress("Applying security & SELinux contexts…", 0.85f, metadata.appName, pkg, RestoreSubStep.PERMISSIONS))
            // Fix /data/user/0, /data/user_de/0 & /data/data ownership and permissions
            RootShell.execute("chown -hR $newUid:$newUid /data/user/0/$pkg /data/user_de/0/$pkg /data/data/$pkg 2>/dev/null")
            RootShell.execute("chmod 700 /data/user/0/$pkg /data/user_de/0/$pkg 2>/dev/null")

            // CRITICAL: Apply preserved dynamic SELinux context recursively instead of restorecon!
            // restorecon strips dynamic MCS categories (e.g. c159,c257,c512,c768), which blocks untrusted_app domain from reading data on Android 12-17!
            if (appCtx.isNotBlank() && appCtx.startsWith("u:object_r:")) {
                RootShell.execute("chcon -hR $appCtx /data/user/0/$pkg /data/user_de/0/$pkg /data/data/$pkg 2>/dev/null")
            }

            // Fix /storage/emulated/0/Android/data ownership and SELinux
            RootShell.execute("chown -hR $newUid:ext_data_rw /storage/emulated/0/Android/data/$pkg /sdcard/Android/data/$pkg 2>/dev/null || chown -hR $newUid:9997 /storage/emulated/0/Android/data/$pkg /sdcard/Android/data/$pkg 2>/dev/null")
            RootShell.execute("chcon -hR u:object_r:media_rw_data_file:s0 /storage/emulated/0/Android/data/$pkg /sdcard/Android/data/$pkg 2>/dev/null")

            // 10. Restore Keystore Encryption Tokens
            val keystoreStaging = File(stagingPath, "keystore")
            val hasKeystoreFiles = RootShell.execute("[ -d $stagingPath/keystore ] && [ \"$(ls -A $stagingPath/keystore 2>/dev/null)\" ] && echo 1 || echo 0").stdout.trim() == "1"
            if (metadata.hasKeystore || hasKeystoreFiles) {
                onProgress(RestoreProgress("Restoring Keystore cryptographic keys…", 0.90f, metadata.appName, pkg, RestoreSubStep.KEYSTORE))
                KeystoreManager.restoreKeystoreForUid(context, metadata.originalUid, newUid, keystoreStaging)
            }

            // 11. Restore Permissions
            PermissionManager.restorePermissions(pkg, metadata.permissions)

            // 12. Restore SSAID (Preserve Login State)
            var needsReboot = false
            if (!metadata.ssaid.isNullOrBlank()) {
                onProgress(RestoreProgress("Restoring SSAID device signature…", 0.95f, metadata.appName, pkg, RestoreSubStep.SSAID))
                val ssaidRestored = SSAIDManager.restoreSSAID(pkg, metadata.ssaid, newUid)
                if (ssaidRestored) {
                    needsReboot = true
                }
            }

            // 13. Stop app completely so it launches fresh with restored data & keys
            RootShell.execute("am force-stop $pkg 2>/dev/null || true")
            RootShell.execute("pkill -9 -f $pkg 2>/dev/null || true")

            onProgress(RestoreProgress("Restore completed successfully!", 1.0f, metadata.appName, pkg, RestoreSubStep.DONE, isFinished = true))
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
