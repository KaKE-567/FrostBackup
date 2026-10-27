package io.github.project.vault.data.repository

import android.content.Context
import io.github.project.vault.core.cloud.GoogleDriveManager
import io.github.project.vault.core.root.RootShell
import io.github.project.vault.core.telephony.TelephonyEngine
import io.github.project.vault.core.wifi.WifiEngine
import io.github.project.vault.data.model.TelephonyArchiveMetadata
import io.github.project.vault.data.model.TelephonyProgress
import io.github.project.vault.data.model.WifiNetwork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale

class TelephonyRepository(
    private val context: Context,
    private val backupRepository: BackupRepository
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    private val _telephonyBackupsFlow = MutableStateFlow<List<TelephonyArchiveMetadata>>(emptyList())
    val telephonyBackups: StateFlow<List<TelephonyArchiveMetadata>> = _telephonyBackupsFlow.asStateFlow()

    fun getTelephonyDirectory(): File {
        val baseBackupDir = backupRepository.getBackupDirectory()
        val telephonyDir = File(baseBackupDir.parentFile ?: baseBackupDir, "Telephony")
        if (!telephonyDir.exists()) {
            telephonyDir.mkdirs()
        }
        return telephonyDir
    }

    suspend fun ensureTelephonyDirectoryExists(): File = withContext(Dispatchers.IO) {
        val dir = getTelephonyDirectory()
        RootShell.execute("mkdir -p \"${dir.absolutePath}\" && chmod 777 \"${dir.absolutePath}\" 2>/dev/null")
        dir
    }

    suspend fun getDeviceStats(): Triple<Int, Int, Int> = withContext(Dispatchers.IO) {
        val smsCount = TelephonyEngine.getDeviceSmsCount(context)
        val callCount = TelephonyEngine.getDeviceCallCount(context)
        val wifiCount = try {
            WifiEngine.readSavedNetworks().size
        } catch (_: Exception) { 0 }
        Triple(smsCount, callCount, wifiCount)
    }

    suspend fun getSavedWifiNetworks(): List<WifiNetwork> = withContext(Dispatchers.IO) {
        try {
            WifiEngine.readSavedNetworks()
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun backupWifi(
        encrypt: Boolean = false,
        password: String? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        val res = WifiEngine.createBackup(
            destinationDir = ensureTelephonyDirectoryExists(),
            encrypt = encrypt,
            password = password
        )
        if (res.isSuccess) {
            refreshBackups()
        }
        res
    }

    suspend fun restoreWifi(
        archiveFile: File,
        password: String? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        WifiEngine.restoreBackup(archiveFile, password)
    }

    suspend fun refreshBackups(): List<TelephonyArchiveMetadata> = withContext(Dispatchers.IO) {
        val dir = ensureTelephonyDirectoryExists()
        val directFiles = dir.listFiles { file -> file.extension == "vault" }

        val filePaths: List<String> = if (!directFiles.isNullOrEmpty()) {
            directFiles.map { it.absolutePath }
        } else {
            val findCmd = "find \"${dir.absolutePath}\" -maxdepth 1 -name '*.vault' 2>/dev/null"
            val res = RootShell.execute(findCmd)
            if (res.isSuccess && res.stdout.isNotBlank()) {
                res.stdout.lines().map { it.trim() }.filter { it.endsWith(".vault") }
            } else {
                emptyList()
            }
        }

        val parsedList = filePaths.mapNotNull { path ->
            val file = File(path)
            val size = if (file.length() > 0L) file.length() else {
                val sizeRes = RootShell.execute("stat -c %s \"$path\" 2>/dev/null || wc -c < \"$path\"")
                sizeRes.stdout.trim().toLongOrNull() ?: 0L
            }

            val metaRes = RootShell.execute(
                "tar -xf \"$path\" -O ./metadata.json 2>/dev/null || tar -xf \"$path\" -O metadata.json 2>/dev/null || tar -xOf \"$path\" ./metadata.json 2>/dev/null"
            )

            if (metaRes.isSuccess && metaRes.stdout.isNotBlank()) {
                try {
                    json.decodeFromString<TelephonyArchiveMetadata>(metaRes.stdout).copy(
                        archiveSizeBytes = size,
                        archiveFileName = file.name
                    )
                } catch (_: Exception) {
                    fallbackMeta(file, size)
                }
            } else {
                fallbackMeta(file, size)
            }
        }.sortedByDescending { it.backupTimestamp }

        _telephonyBackupsFlow.value = parsedList
        parsedList
    }

    private fun fallbackMeta(file: File, sizeBytes: Long): TelephonyArchiveMetadata {
        val name = file.nameWithoutExtension
        val timestamp = try {
            val timeStr = name.takeLast(19) // "yyyy-MM-dd_HH-mm-ss"
            SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).parse(timeStr)?.time
        } catch (_: Exception) { null } ?: (if (file.lastModified() > 0L) file.lastModified() else System.currentTimeMillis())

        return TelephonyArchiveMetadata(
            backupTimestamp = timestamp,
            smsCount = 0,
            callCount = 0,
            archiveFileName = file.name,
            archiveSizeBytes = sizeBytes
        )
    }

    suspend fun backup(
        includeSms: Boolean = true,
        includeCalls: Boolean = true,
        includeWifi: Boolean = true,
        onProgress: (TelephonyProgress) -> Unit = {}
    ): Result<TelephonyArchiveMetadata> = withContext(Dispatchers.IO) {
        val result = TelephonyEngine.backup(
            context = context,
            destinationDir = ensureTelephonyDirectoryExists(),
            includeSms = includeSms,
            includeCalls = includeCalls,
            includeWifi = includeWifi,
            onProgress = onProgress
        )
        if (result.isSuccess) {
            refreshBackups()
        }
        result
    }

    suspend fun restore(
        archiveFileName: String,
        onProgress: (TelephonyProgress) -> Unit = {}
    ): Result<String> = withContext(Dispatchers.IO) {
        val archiveFile = File(getTelephonyDirectory(), archiveFileName)
        TelephonyEngine.restore(
            context = context,
            archiveFile = archiveFile,
            onProgress = onProgress
        )
    }

    suspend fun deleteBackup(archiveFileName: String): Boolean = withContext(Dispatchers.IO) {
        val archiveFile = File(getTelephonyDirectory(), archiveFileName)
        val res = RootShell.execute("rm -f \"${archiveFile.absolutePath}\"")
        if (res.isSuccess) {
            refreshBackups()
        }
        res.isSuccess
    }

    suspend fun uploadToCloud(
        archiveFileName: String,
        onProgress: (uploadedMb: Float, totalMb: Float, percent: Int, fraction: Float) -> Unit = { _, _, _, _ -> }
    ): Boolean = withContext(Dispatchers.IO) {
        val dir = getTelephonyDirectory()
        val archiveFile = File(dir, archiveFileName)
        val metaResult = RootShell.execute("tar -xf \"${archiveFile.absolutePath}\" -O ./metadata.json 2>/dev/null || tar -xf \"${archiveFile.absolutePath}\" -O metadata.json 2>/dev/null")
        val metaString = if (metaResult.isSuccess && metaResult.stdout.isNotBlank()) metaResult.stdout else "{}"

        val stagingDir = File(context.cacheDir, "cloud_telephony_staging")
        stagingDir.mkdirs()
        val cacheStaging = File(stagingDir, archiveFileName)
        try {
            RootShell.execute("cp -p \"${archiveFile.absolutePath}\" \"${cacheStaging.absolutePath}\"")
            RootShell.execute("chmod 666 \"${cacheStaging.absolutePath}\"")

            val drive = GoogleDriveManager(context)
            if (!drive.isConnected()) return@withContext false
            val totalBytes = cacheStaging.length().coerceAtLeast(1L)
            val totalMb = totalBytes / (1024f * 1024f)

            drive.uploadBackup(cacheStaging, metaString) { fraction ->
                val currentMb = (totalBytes * fraction) / (1024f * 1024f)
                val percent = (fraction * 100).toInt()
                onProgress(currentMb, totalMb, percent, fraction)
            }
        } catch (_: Exception) {
            false
        } finally {
            RootShell.execute("rm -rf \"${stagingDir.absolutePath}\"")
        }
    }
}
