package io.github.project.vault.data.repository

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import io.github.project.vault.data.model.AppInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class AppRepository(
    private val context: Context,
    private val backupRepository: BackupRepository
) {
    private val packageManager: PackageManager = context.packageManager

    suspend fun getInstalledAppsFast(includeSystem: Boolean = false): List<AppInfo> = withContext(Dispatchers.IO) {
        val flags = PackageManager.GET_META_DATA
        val installedPackages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getInstalledPackages(flags)
        }

        installedPackages.mapNotNull { packageInfo ->
            val appInfo = packageInfo.applicationInfo ?: return@mapNotNull null
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

            if (!includeSystem && isSystem) {
                return@mapNotNull null
            }

            val appName = appInfo.loadLabel(packageManager).toString()
            val versionName = packageInfo.versionName ?: "1.0"
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }

            val splitDirs = appInfo.splitSourceDirs?.toList() ?: emptyList()

            // Approximate app size
            val apkSize = File(appInfo.sourceDir).length()
            val sizeFormatted = formatFileSize(apkSize)

            AppInfo(
                packageName = packageInfo.packageName,
                appName = appName,
                versionName = versionName,
                versionCode = versionCode,
                isSystemApp = isSystem,
                uid = appInfo.uid,
                sourceDir = appInfo.sourceDir,
                splitSourceDirs = splitDirs,
                dataDir = appInfo.dataDir,
                appSizeFormatted = sizeFormatted,
                hasBackup = false,
                lastBackupTimestamp = null
            )
        }.sortedBy { it.appName.lowercase() }
    }

    suspend fun enrichWithBackupStatus(apps: List<AppInfo>): List<AppInfo> = withContext(Dispatchers.IO) {
        val backupMap = backupRepository.getLocalBackups().associateBy { it.packageName }
        apps.map { app ->
            val backup = backupMap[app.packageName]
            if (backup != null) {
                app.copy(
                    hasBackup = true,
                    lastBackupTimestamp = backup.backupTimestamp
                )
            } else {
                app
            }
        }
    }

    suspend fun getInstalledApps(includeSystem: Boolean = false): List<AppInfo> = withContext(Dispatchers.IO) {
        val fastList = getInstalledAppsFast(includeSystem)
        enrichWithBackupStatus(fastList)
    }

    private fun formatFileSize(bytes: Long): String {
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        val gb = mb / 1024.0
        return when {
            gb >= 1.0 -> String.format("%.2f GB", gb)
            mb >= 1.0 -> String.format("%.1f MB", mb)
            kb >= 1.0 -> String.format("%.0f KB", kb)
            else -> "$bytes B"
        }
    }
}
