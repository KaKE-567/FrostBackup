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
                    val mountPath = "/storage/$uuid"

                    var total = 0L
                    var free = 0L
                    var mounted = false
                    try {
                        val stat = StatFs(mountPath)
                        total = stat.totalBytes
                        free = stat.availableBytes
                        mounted = total > 0
                    } catch (_: Exception) {}

                    val backupDirPath = "$mountPath/$DEFAULT_BACKUP_SUBDIR"
                    targets.add(
                        StorageTarget(
                            id = uuid,
                            name = desc,
                            type = type,
                            backupDirectoryPath = backupDirPath,
                            totalBytes = total,
                            freeBytes = free,
                            isMounted = mounted || File(mountPath).exists()
                        )
                    )
                }
            }
        } catch (_: Exception) {}

        // 3. Root Fallback: Inspect /storage and /mnt/media_rw for external volumes
        try {
            val storageDir = File("/storage")
            val subdirs = storageDir.listFiles()
            if (subdirs != null) {
                for (dir in subdirs) {
                    val name = dir.name
                    if (name == "emulated" || name == "self" || name == "knox") continue
                    if (targets.any { it.id == name || it.backupDirectoryPath.contains(name) }) continue

                    var total = 0L
                    var free = 0L
                    try {
                        val stat = StatFs(dir.absolutePath)
                        total = stat.totalBytes
                        free = stat.availableBytes
                    } catch (_: Exception) {
                        // Fallback root stat
                        val dfRes = RootShell.execute("df ${dir.absolutePath} | tail -n 1")
                        if (dfRes.isSuccess && dfRes.stdout.isNotBlank()) {
                            val parts = dfRes.stdout.split("\\s+".toRegex())
                            if (parts.size >= 4) {
                                total = (parts[1].toLongOrNull() ?: 0L) * 1024L
                                free = (parts[3].toLongOrNull() ?: 0L) * 1024L
                            }
                        }
                    }

                    if (total > 0 || dir.exists()) {
                        val isUsb = name.startsWith("usb", ignoreCase = true)
                        val type = if (isUsb) StorageType.USB_DRIVE else StorageType.SD_CARD
                        val label = if (type == StorageType.USB_DRIVE) "USB Pendrive ($name)" else "SD Card ($name)"
                        targets.add(
                            StorageTarget(
                                id = name,
                                name = label,
                                type = type,
                                backupDirectoryPath = "${dir.absolutePath}/$DEFAULT_BACKUP_SUBDIR",
                                totalBytes = total,
                                freeBytes = free,
                                isMounted = true
                            )
                        )
                    }
                }
            }
        } catch (_: Exception) {}

        targets
    }
}
