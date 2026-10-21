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

    suspend fun getInstalledApps(includeSystem: Boolean = false): List<AppInfo> = withContext(Dispatchers.IO) {
        val flags = PackageManager.GET_META_DATA
        val installedPackages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getInstalledPackages(flags)
        }

        val backupMap = backupRepository.getLocalBackups().associateBy { it.packageName }

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
            val localBackup = backupMap[packageInfo.packageName]

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
                hasBackup = localBackup != null,
                lastBackupTimestamp = localBackup?.backupTimestamp
            )
        }.sortedBy { it.appName.lowercase() }
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
