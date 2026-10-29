package io.github.project.vault.core.root

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.DataOutputStream
import java.io.File

object RootShell {
    private const val TAG = "RootShell"
    private var isRootedCache: Boolean? = null
    @Volatile
    private var mountMasterOption: String? = null

    fun isRootAvailableSync(): Boolean? = isRootedCache

    suspend fun isRootAvailable(forceCheck: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        if (!forceCheck && isRootedCache == true) return@withContext true
        clearCache()
        val result = execute("id", timeoutMs = 25_000L)
        val isRoot = result.isSuccess && (result.stdout.contains("uid=0(root)") || result.stdout.contains("uid=0"))
        if (isRoot) {
            isRootedCache = true
        }
        isRoot
    }

    private fun getSuExecutable(): String {
        val candidates = listOf(
            "/system/bin/su",
            "/system/xbin/su",
            "/sbin/su",
            "su"
        )
        return candidates.firstOrNull { File(it).canExecute() } ?: "su"
    }

    private fun getMountMasterFlag(suBinary: String): String {
        mountMasterOption?.let { return it }
        // Test mount-master flags: --mount-master (KernelSU / Magisk), -M (KernelSU), -mm (Magisk)
        val candidates = listOf("--mount-master", "-mm", "-M")
        for (flag in candidates) {
            try {
                val p = ProcessBuilder(suBinary, flag, "-c", "id").start()
                val text = p.inputStream.bufferedReader().use { it.readText() }
                if (p.waitFor() == 0 && (text.contains("uid=0") || text.contains("root"))) {
                    Log.d(TAG, "Root shell using global mount namespace flag: $flag")
                    mountMasterOption = flag
                    return flag
                }
            } catch (_: Exception) {}
        }
        mountMasterOption = ""
        return ""
    }

    suspend fun execute(command: String, timeoutMs: Long = 60_000L): ShellResult = withContext(Dispatchers.IO) {
        val timedResult = withTimeoutOrNull(timeoutMs) {
            try {
                val suBinary = getSuExecutable()
                val mmFlag = getMountMasterFlag(suBinary)
                val cmdList = mutableListOf(suBinary)
                if (mmFlag.isNotBlank()) {
                    cmdList.add(mmFlag)
                }
                cmdList.add("-c")
                cmdList.add(command)

                val process = ProcessBuilder(cmdList).start()
                val stdoutDeferred = async(Dispatchers.IO) {
                    process.inputStream.bufferedReader().use { it.readText().trim() }
                }
                val stderrDeferred = async(Dispatchers.IO) {
                    process.errorStream.bufferedReader().use { it.readText().trim() }
                }
                val stdout = stdoutDeferred.await()
                val stderr = stderrDeferred.await()
                val exitCode = process.waitFor()
                Log.d(TAG, "[$exitCode] $command ${if (stderr.isNotBlank()) " | ERR: $stderr" else ""}")
                ShellResult(exitCode, stdout, stderr)
            } catch (e: Exception) {
                // Fallback to piping if direct su -c failed
                try {
                    val suBinary = getSuExecutable()
                    val mmFlag = getMountMasterFlag(suBinary)
                    val cmdList = mutableListOf(suBinary)
                    if (mmFlag.isNotBlank()) {
                        cmdList.add(mmFlag)
                    }
                    val process = ProcessBuilder(cmdList).start()
                    val os = DataOutputStream(process.outputStream)
                    val stdoutDeferred = async(Dispatchers.IO) {
                        process.inputStream.bufferedReader().use { it.readText().trim() }
                    }
                    val stderrDeferred = async(Dispatchers.IO) {
                        process.errorStream.bufferedReader().use { it.readText().trim() }
                    }
                    os.writeBytes("$command\n")
                    os.writeBytes("exit\n")
                    os.flush()
                    os.close()

                    val stdout = stdoutDeferred.await()
                    val stderr = stderrDeferred.await()
                    val exitCode = process.waitFor()
                    Log.d(TAG, "Fallback [$exitCode] $command")
                    ShellResult(exitCode, stdout, stderr)
                } catch (fallbackEx: Exception) {
                    Log.e(TAG, "Execution error on: $command", fallbackEx)
                    ShellResult(-1, "", fallbackEx.message ?: e.message ?: "Execution error")
                }
            }
        }
        timedResult ?: ShellResult(-2, "", "Command timed out after ${timeoutMs}ms")
    }

    suspend fun executeBatch(commands: List<String>, timeoutMs: Long = 180_000L): ShellResult = withContext(Dispatchers.IO) {
        val script = commands.joinToString("\n")
        execute(script, timeoutMs)
    }

    fun clearCache() {
        isRootedCache = null
        mountMasterOption = null
    }
}
