package io.github.project.vault.data.repository

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import io.github.project.vault.data.model.AppInfo
import io.github.project.vault.data.model.BackupMetadata
import io.github.project.vault.ui.components.AppIconCache
import androidx.compose.runtime.Stable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Stable
class AppRepository(
    private val context: Context,
    private val backupRepository: BackupRepository
) {
    private val packageManager: PackageManager = context.packageManager

    companion object {
        @Volatile
        private var cachedInstalledApps: List<AppInfo>? = null
        @Volatile
        private var cachedEnrichedApps: List<AppInfo>? = null

        fun getCachedApps(): List<AppInfo>? = cachedEnrichedApps ?: cachedInstalledApps

        fun invalidateCache() {
            cachedInstalledApps = null
            cachedEnrichedApps = null
        }
    }

    suspend fun getInstalledAppsFast(includeSystem: Boolean = false, forceRefresh: Boolean = false): List<AppInfo> = withContext(Dispatchers.IO) {
        if (!forceRefresh && cachedInstalledApps != null) {
            val cached = cachedInstalledApps!!
            return@withContext if (includeSystem) cached else cached.filter { !it.isSystemApp }
        }

        val flags = PackageManager.GET_META_DATA
        val installedPackages = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getInstalledPackages(PackageManager.PackageInfoFlags.of(flags.toLong()))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getInstalledPackages(flags)
        }

        val apps = installedPackages.mapNotNull { packageInfo ->
            val appInfo = packageInfo.applicationInfo ?: return@mapNotNull null
            val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0

            val appName = appInfo.loadLabel(packageManager).toString()
            val versionName = packageInfo.versionName ?: "1.0"
            val versionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                packageInfo.longVersionCode
            } else {
                @Suppress("DEPRECATION")
                packageInfo.versionCode.toLong()
            }

            val splitDirs = appInfo.splitSourceDirs?.toList() ?: emptyList()
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
                appSizeBytes = apkSize,
                firstInstallTime = packageInfo.firstInstallTime,
                lastUpdateTime = packageInfo.lastUpdateTime,
                hasBackup = false,
                lastBackupTimestamp = null
            )
        }.sortedBy { it.appName.lowercase() }

        cachedInstalledApps = apps

        // Pre-warm icon cache for top 30 non-system apps after UI stabilizes
        CoroutineScope(Dispatchers.IO).launch {
            kotlinx.coroutines.delay(1200)
            val topApps = apps.filter { !it.isSystemApp }.take(30)
            for (app in topApps) {
                if (AppIconCache.get(app.packageName) == null) {
                    runCatching {
                        val drawable = packageManager.getApplicationIcon(app.packageName)
                        val bmp = drawable.toBitmap(72, 72, Bitmap.Config.ARGB_8888).asImageBitmap()
                        AppIconCache.put(app.packageName, bmp)
                    }
                }
            }
        }

        if (includeSystem) apps else apps.filter { !it.isSystemApp }
    }

    suspend fun enrichWithBackupStatus(apps: List<AppInfo>, forceRefresh: Boolean = false): List<AppInfo> = withContext(Dispatchers.IO) {
        if (!forceRefresh && cachedEnrichedApps != null && cachedEnrichedApps?.size == apps.size) {
            return@withContext cachedEnrichedApps!!
        }

        val backups = backupRepository.getLocalBackups(forceRefresh)
        val backupMap = mutableMapOf<String, BackupMetadata>()
        for (b in backups) {
            val pkg = b.packageName.trim().lowercase()
            if (pkg.isNotBlank()) backupMap.putIfAbsent(pkg, b)
            val slug = BackupMetadata.extractAppSlugFromArchive(b.archiveFileName).lowercase().replace("-", "_")
            if (slug.isNotBlank()) backupMap.putIfAbsent(slug, b)
        }

        val enriched = apps.map { app ->
            val pkgKey = app.packageName.trim().lowercase()
            val cleanAppSlug = app.appName.trim().lowercase().replace("\\s+".toRegex(), "_").replace("[^a-z0-9_]".toRegex(), "")
            val cleanPkgSlug = pkgKey.substringAfterLast(".").replace("[^a-z0-9_]".toRegex(), "")

            val backup = backupMap[pkgKey]
                ?: backupMap[cleanAppSlug]
                ?: (if (cleanPkgSlug.isNotBlank()) backupMap[cleanPkgSlug] else null)
                ?: backups.find { b ->
                    val cleanBackupSlug = BackupMetadata.extractAppSlugFromArchive(b.archiveFileName).lowercase().replace("-", "_")
                    (cleanBackupSlug.isNotEmpty() && (cleanBackupSlug == cleanAppSlug || cleanBackupSlug == cleanPkgSlug)) ||
                    b.packageName.trim().equals(app.packageName.trim(), ignoreCase = true)
                }

            if (backup != null) {
                app.copy(
                    hasBackup = true,
                    lastBackupTimestamp = backup.backupTimestamp
                )
            } else {
                app
            }
        }
        cachedEnrichedApps = enriched
        enriched
    }

    suspend fun getInstalledApps(includeSystem: Boolean = false, forceRefresh: Boolean = false): List<AppInfo> = withContext(Dispatchers.IO) {
        val fastList = getInstalledAppsFast(includeSystem, forceRefresh)
        enrichWithBackupStatus(fastList, forceRefresh)
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
