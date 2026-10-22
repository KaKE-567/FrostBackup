package io.github.project.vault.core.storage

import android.content.Context
import android.content.SharedPreferences
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

    data class MountEntry(
        val device: String,
        val mountPoint: String,
        val fsType: String
    )

    fun getDefaultBackupDirectory(): File {
        return File(Environment.getExternalStorageDirectory(), DEFAULT_BACKUP_SUBDIR)
    }

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private fun getActiveMounts(): List<MountEntry> {
        val mounts = mutableListOf<MountEntry>()
        val mountsFile = File("/proc/mounts")
        val lines = if (mountsFile.exists() && mountsFile.canRead()) {
            try { mountsFile.readLines() } catch (_: Exception) { emptyList() }
        } else {
            emptyList()
        }

        val sourceLines = if (lines.isNotEmpty()) lines else {
            val res = RootShell.execute("cat /proc/mounts 2>/dev/null")
            if (res.isSuccess) res.stdout.lines() else emptyList()
        }

        for (line in sourceLines) {
            val parts = line.trim().split("\\s+".toRegex())
            if (parts.size >= 3) {
                val device = parts[0]
                val mountPoint = parts[1]
                val fsType = parts[2]

                // Ignore virtual / RAM pseudo filesystems
                if (fsType in listOf(
                        "tmpfs", "rootfs", "devpts", "proc", "sysfs", "selinuxfs",
                        "cgroup", "cgroup2", "configfs", "bpf", "pstore", "tracefs",
                        "debugfs", "functionfs", "overlay"
                    )
                ) {
                    continue
                }

                // Ignore root container mount points
                if (mountPoint == "/" || mountPoint == "/storage" || mountPoint == "/storage/self" || mountPoint == "/mnt") {
                    continue
                }

                mounts.add(MountEntry(device, mountPoint, fsType))
            }
        }
        return mounts
    }

    private fun tryAutoMountUnmountedDrive(uuid: String) {
        val blkidRes = RootShell.execute("blkid 2>/dev/null | grep -i \"$uuid\" | head -n 1")
        var dev = ""
        if (blkidRes.isSuccess && blkidRes.stdout.isNotBlank()) {
            dev = blkidRes.stdout.substringBefore(":").trim()
        }
        if (dev.isBlank()) {
            val mmcRes = RootShell.execute("blkid /dev/block/mmcblk* 2>/dev/null | grep -i \"$uuid\" | head -n 1")
            if (mmcRes.isSuccess && mmcRes.stdout.isNotBlank()) {
                dev = mmcRes.stdout.substringBefore(":").trim()
            }
        }
        if (dev.isNotBlank()) {
            RootShell.execute(
                """
                mkdir -p /mnt/media_rw/$uuid /storage/$uuid 2>/dev/null
                mount $dev /mnt/media_rw/$uuid 2>/dev/null
                mount -o bind /mnt/media_rw/$uuid /storage/$uuid 2>/dev/null
                mount -o bind /mnt/media_rw/$uuid /mnt/runtime/default/$uuid 2>/dev/null
                """.trimIndent()
            )
        }
    }

    private fun cleanGhostDirectories(activeMountPoints: Set<String>) {
        val searchParents = listOf(File("/storage"), File("/mnt/media_rw"), File("/mnt/runtime/default"))
        val uuidRegex = Regex("^[A-Za-z0-9]{4}-[A-Za-z0-9]{4}$|^[A-Za-z0-9]{8}-[A-Za-z0-9]{4}-[A-Za-z0-9]{4}-[A-Za-z0-9]{4}-[A-Za-z0-9]{12}$")
        for (parent in searchParents) {
            val children = parent.listFiles() ?: continue
            for (child in children) {
                val name = child.name
                if (name == "emulated" || name == "self" || name == "knox") continue
                if (uuidRegex.matches(name)) {
                    val isMounted = activeMountPoints.any { it == child.absolutePath || it.startsWith("${child.absolutePath}/") }
                    if (!isMounted) {
                        RootShell.execute("rm -rf \"${child.absolutePath}\" 2>/dev/null")
                    }
                }
            }
        }
    }

    private fun getMountStats(mountPath: String): Pair<Long, Long> {
        var total = 0L
        var free = 0L

        val dfRes = RootShell.execute("df -k \"$mountPath\" 2>/dev/null | tail -n 1")
        if (dfRes.isSuccess && dfRes.stdout.isNotBlank()) {
            val parts = dfRes.stdout.trim().split("\\s+".toRegex())
            val fsDevice = parts.getOrNull(0) ?: ""
            if (parts.size >= 4 && !fsDevice.contains("tmpfs", ignoreCase = true)) {
                total = (parts[1].toLongOrNull() ?: 0L) * 1024L
                free = (parts[3].toLongOrNull() ?: 0L) * 1024L
            }
        }

        if (total <= 0L && File(mountPath).exists()) {
            try {
                val stat = StatFs(mountPath)
                total = stat.totalBytes
                free = stat.availableBytes
            } catch (_: Exception) {}
        }

        return Pair(total, free)
    }

    private fun extractUuidFromPath(path: String): String? {
        val match = Regex(".*/(?:storage|mnt/media_rw|mnt/runtime/[^/]+)/([A-Za-z0-9]{4}-[A-Za-z0-9]{4}|[A-Za-z0-9]{8}-[A-Za-z0-9]{4}-[A-Za-z0-9]{4}-[A-Za-z0-9]{4}-[A-Za-z0-9]{12})").find(path)
        return match?.groupValues?.get(1)
    }

    fun getBackupDirectory(context: Context): File {
        val prefs = getPrefs(context)
        val savedPath = prefs.getString(KEY_SELECTED_PATH, null)
        val defaultDir = getDefaultBackupDirectory()

        if (savedPath.isNullOrBlank()) {
            return defaultDir
        }

        val internalPrefix = Environment.getExternalStorageDirectory().absolutePath
        if (savedPath.startsWith(internalPrefix) || savedPath.startsWith("/storage/emulated/0")) {
            return File(savedPath)
        }

        // Removable storage path: Verify it is currently mounted
        val activeMounts = getActiveMounts()
        val isMounted = activeMounts.any { mount ->
            savedPath == mount.mountPoint || savedPath.startsWith("${mount.mountPoint}/")
        }

        if (!isMounted) {
            val uuid = extractUuidFromPath(savedPath)
            if (uuid != null) {
                tryAutoMountUnmountedDrive(uuid)
                val updatedMounts = getActiveMounts()
                val nowMounted = updatedMounts.any { mount ->
                    savedPath == mount.mountPoint || savedPath.startsWith("${mount.mountPoint}/")
                }
                if (nowMounted) {
                    return File(savedPath)
                }
            }
            // Drive unplugged or unmounted: safely fall back to internal storage
            return defaultDir
        }

        return File(savedPath)
    }

    fun setSelectedBackupDirectory(context: Context, path: String) {
        getPrefs(context).edit().putString(KEY_SELECTED_PATH, path).apply()
        val internalPrefix = Environment.getExternalStorageDirectory().absolutePath
        val isInternal = path.startsWith(internalPrefix) || path.startsWith("/storage/emulated/0")

        if (isInternal || getActiveMounts().any { path == it.mountPoint || path.startsWith("${it.mountPoint}/") }) {
            runCatching { File(path).mkdirs() }
            CoroutineScope(Dispatchers.IO).launch {
                RootShell.execute("mkdir -p \"$path\" && chmod -R 777 \"$path\" 2>/dev/null")
            }
        }
    }

    suspend fun getAvailableStorageTargets(context: Context): List<StorageTarget> = withContext(Dispatchers.IO) {
        val targets = mutableListOf<StorageTarget>()

        // 1. Primary Internal Storage
        try {
            val internalPath = Environment.getExternalStorageDirectory().absolutePath
            val stat = StatFs(internalPath)
            val defaultBackupDir = File(internalPath, DEFAULT_BACKUP_SUBDIR).absolutePath

            targets.add(
                StorageTarget(
                    id = "internal_primary",
                    name = "Internal Device Storage",
                    type = StorageType.INTERNAL,
                    backupDirectoryPath = defaultBackupDir,
                    totalBytes = stat.totalBytes,
                    freeBytes = stat.availableBytes,
                    isMounted = true
                )
            )
        } catch (_: Exception) {}

        // 2. Discover Volumes from Android StorageManager
        val sm = context.getSystemService(Context.STORAGE_SERVICE) as? StorageManager
        val volumeList = sm?.storageVolumes?.filter { !it.isPrimary && it.uuid != null } ?: emptyList()

        // For any unmounted volume, try auto-mounting via root if physical block device is present
        for (vol in volumeList) {
            val uuid = vol.uuid ?: continue
            if (vol.state != Environment.MEDIA_MOUNTED && vol.state != Environment.MEDIA_MOUNTED_READ_ONLY) {
                tryAutoMountUnmountedDrive(uuid)
            }
        }

        // 3. Inspect active mounts from /proc/mounts
        val activeMounts = getActiveMounts()
        val activeMountPaths = activeMounts.map { it.mountPoint }.toSet()

        // Clean any ghost directories left behind by unplugged USB drives
        cleanGhostDirectories(activeMountPaths)

        val processedUuids = mutableSetOf<String>()

        for (mount in activeMounts) {
            val mountPoint = mount.mountPoint
            val uuid = when {
                mountPoint.startsWith("/storage/") -> mountPoint.removePrefix("/storage/").substringBefore("/")
                mountPoint.startsWith("/mnt/media_rw/") -> mountPoint.removePrefix("/mnt/media_rw/").substringBefore("/")
                mountPoint.startsWith("/mnt/runtime/") -> {
                    val sub = mountPoint.removePrefix("/mnt/runtime/")
                    val slashIdx = sub.indexOf('/')
                    if (slashIdx != -1) sub.substring(slashIdx + 1).substringBefore("/") else sub
                }
                else -> null
            }

            if (uuid.isNullOrBlank() || uuid == "emulated" || uuid == "self" || uuid == "knox") continue
            if (processedUuids.contains(uuid)) continue
            processedUuids.add(uuid)

            val matchedVolume = volumeList.find { it.uuid.equals(uuid, ignoreCase = true) }
            val volDesc = matchedVolume?.getDescription(context)?.trim()

            val isMmc = mount.device.contains("mmcblk", ignoreCase = true)
            val isUsbDevice = mount.device.contains("/sd", ignoreCase = true) || mount.device.contains("public:8", ignoreCase = true)
            val descIsUsb = volDesc?.contains("USB", ignoreCase = true) == true ||
                    volDesc?.contains("OTG", ignoreCase = true) ||
                    volDesc?.contains("Pendrive", ignoreCase = true)

            val type = when {
                descIsUsb || isUsbDevice -> StorageType.USB_DRIVE
                isMmc -> StorageType.SD_CARD
                volDesc?.contains("SD", ignoreCase = true) == true -> StorageType.SD_CARD
                else -> StorageType.SD_CARD
            }

            val displayName = when {
                !volDesc.isNullOrBlank() -> volDesc
                type == StorageType.USB_DRIVE -> "USB Drive ($uuid)"
                else -> "MicroSD Card ($uuid)"
            }

            val preferredMount = when {
                activeMountPaths.contains("/storage/$uuid") -> "/storage/$uuid"
                activeMountPaths.contains("/mnt/media_rw/$uuid") -> "/mnt/media_rw/$uuid"
                else -> mountPoint
            }

            val (total, free) = getMountStats(preferredMount)
            if (total > 0L) {
                targets.add(
                    StorageTarget(
                        id = uuid,
                        name = displayName,
                        type = type,
                        backupDirectoryPath = "$preferredMount/$DEFAULT_BACKUP_SUBDIR",
                        totalBytes = total,
                        freeBytes = free,
                        isMounted = true
                    )
                )
            }
        }

        targets
    }
}
