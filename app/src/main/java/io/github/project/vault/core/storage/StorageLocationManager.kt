package io.github.project.vault.core.storage

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import io.github.project.vault.core.root.RootShell
import io.github.project.vault.data.model.StorageTarget
import io.github.project.vault.data.model.StorageType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

object StorageLocationManager {
    private const val PREFS_NAME = "vault_storage_prefs"
    private const val KEY_SELECTED_PATH = "selected_backup_path"
    private const val DEFAULT_BACKUP_SUBDIR = "FrostBackup/Backups"

    fun getDefaultBackupDirectory(): File {
        return File(Environment.getExternalStorageDirectory(), DEFAULT_BACKUP_SUBDIR)
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getBackupDirectory(context: Context): File {
        val prefs = getPrefs(context)
        val savedPath = prefs.getString(KEY_SELECTED_PATH, null)
        val defaultDir = getDefaultBackupDirectory()

        if (savedPath.isNullOrBlank()) {
            return defaultDir
        }

        val targetDir = File(savedPath)
        // If the saved path points to a removable drive that is currently unmounted or inaccessible, fallback to internal
        val isAccessible = targetDir.exists() || targetDir.parentFile?.exists() == true || isPathAccessibleViaRoot(targetDir.absolutePath)
        return if (isAccessible) targetDir else defaultDir
    }

    private fun isPathAccessibleViaRoot(path: String): Boolean {
        val rootDir = File(path)
        if (rootDir.exists()) return true
        val parent = rootDir.parentFile
        return parent != null && parent.exists()
    }

    fun setSelectedBackupDirectory(context: Context, path: String) {
        getPrefs(context).edit().putString(KEY_SELECTED_PATH, path).apply()
        // Ensure destination directory is created with accessible permissions
        runCatching { File(path).mkdirs() }
        CoroutineScope(Dispatchers.IO).launch {
            RootShell.execute("mkdir -p $path && chmod -R 777 $path 2>/dev/null")
        }
    }

    suspend fun getAvailableStorageTargets(context: Context): List<StorageTarget> = withContext(Dispatchers.IO) {
        val targets = mutableListOf<StorageTarget>()

        // 1. Primary Internal Storage
        try {
            val internalPath = Environment.getExternalStorageDirectory().absolutePath
            val stat = StatFs(internalPath)
            val total = stat.totalBytes
            val free = stat.availableBytes
            val defaultBackupDir = File(internalPath, DEFAULT_BACKUP_SUBDIR).absolutePath

            targets.add(
                StorageTarget(
                    id = "internal_primary",
                    name = "Internal Device Storage",
                    type = StorageType.INTERNAL,
                    backupDirectoryPath = defaultBackupDir,
                    totalBytes = total,
                    freeBytes = free,
                    isMounted = true
                )
            )
        } catch (_: Exception) {}

        // 2. Removable Storage via Android StorageManager API
        try {
            val sm = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
            if (sm != null) {
                for (volume in sm.storageVolumes) {
                    if (volume.isPrimary) continue
                    val uuid = volume.uuid ?: continue
                    val desc = volume.getDescription(context) ?: "External Drive"
                    val isUsb = desc.contains("USB", ignoreCase = true) || desc.contains("OTG", ignoreCase = true) || desc.contains("Pendrive", ignoreCase = true)
                    val type = if (isUsb) StorageType.USB_DRIVE else StorageType.SD_CARD

                    // Removable drives on Android may be mounted under /mnt/media_rw/$uuid or /storage/$uuid
                    val storagePath = "/storage/$uuid"
                    val mediaRwPath = "/mnt/media_rw/$uuid"
                    val validMountPath = when {
                        File(mediaRwPath).exists() -> mediaRwPath
                        File(storagePath).exists() -> storagePath
                        else -> mediaRwPath
                    }

                    var total = 0L
                    var free = 0L
                    var mounted = false

                    // Use root df first to get actual hardware filesystem block stats
                    val dfRes = RootShell.execute("df -k $validMountPath 2>/dev/null | tail -n 1")
                    if (dfRes.isSuccess && dfRes.stdout.isNotBlank()) {
                        val parts = dfRes.stdout.trim().split("\\s+".toRegex())
                        if (parts.size >= 4) {
                            total = (parts[1].toLongOrNull() ?: 0L) * 1024L
                            free = (parts[3].toLongOrNull() ?: 0L) * 1024L
                            mounted = total > 0
                        }
                    }

                    // Fallback to StatFs if df didn't produce total
                    if (total <= 0L && File(validMountPath).exists()) {
                        try {
                            val stat = StatFs(validMountPath)
                            total = stat.totalBytes
                            free = stat.availableBytes
                            mounted = total > 0
                        } catch (_: Exception) {}
                    }

                    val backupDirPath = "$validMountPath/$DEFAULT_BACKUP_SUBDIR"
                    targets.add(
                        StorageTarget(
                            id = uuid,
                            name = desc,
                            type = type,
                            backupDirectoryPath = backupDirPath,
                            totalBytes = total,
                            freeBytes = free,
                            isMounted = mounted || File(validMountPath).exists()
                        )
                    )
                }
            }
        } catch (_: Exception) {}

        // 3. Root Fallback: Inspect /mnt/media_rw and /storage for removable volumes
        try {
            val searchDirs = listOf(File("/mnt/media_rw"), File("/storage"))
            for (parentDir in searchDirs) {
                val subdirs = parentDir.listFiles() ?: continue
                for (dir in subdirs) {
                    val name = dir.name
                    if (name == "emulated" || name == "self" || name == "knox") continue
                    if (targets.any { it.id == name || it.backupDirectoryPath.contains(name) }) continue

                    var total = 0L
                    var free = 0L
                    var mounted = false

                    val dfRes = RootShell.execute("df -k ${dir.absolutePath} 2>/dev/null | tail -n 1")
                    if (dfRes.isSuccess && dfRes.stdout.isNotBlank()) {
                        val parts = dfRes.stdout.trim().split("\\s+".toRegex())
                        if (parts.size >= 4) {
                            total = (parts[1].toLongOrNull() ?: 0L) * 1024L
                            free = (parts[3].toLongOrNull() ?: 0L) * 1024L
                            mounted = total > 0
                        }
                    }

                    if (total <= 0L) {
                        try {
                            val stat = StatFs(dir.absolutePath)
                            total = stat.totalBytes
                            free = stat.availableBytes
                            mounted = total > 0
                        } catch (_: Exception) {}
                    }

                    if (total > 0 || dir.exists()) {
                        val isUsb = name.startsWith("usb", ignoreCase = true) || parentDir.name == "media_rw"
                        val type = if (isUsb) StorageType.USB_DRIVE else StorageType.SD_CARD
                        val label = if (type == StorageType.USB_DRIVE) "USB Drive ($name)" else "MicroSD Card ($name)"
                        targets.add(
                            StorageTarget(
                                id = name,
                                name = label,
                                type = type,
                                backupDirectoryPath = "${dir.absolutePath}/$DEFAULT_BACKUP_SUBDIR",
                                totalBytes = total,
                                freeBytes = free,
                                isMounted = mounted || dir.exists()
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {}

        targets
    }
}
