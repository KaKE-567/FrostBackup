package io.github.project.vault.core.root

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.File
import java.io.InputStreamReader

object RootShell {
    private var isRootedCache: Boolean? = null

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

    suspend fun execute(command: String, timeoutMs: Long = 60_000L): ShellResult = withContext(Dispatchers.IO) {
        val timedResult = withTimeoutOrNull(timeoutMs) {
            try {
                val suBinary = getSuExecutable()
                val process = ProcessBuilder(suBinary).start()
                val os = DataOutputStream(process.outputStream)
                val stdoutReader = BufferedReader(InputStreamReader(process.inputStream))
                val stderrReader = BufferedReader(InputStreamReader(process.errorStream))

                // Write command and exit
                os.writeBytes("$command\n")
                os.writeBytes("exit\n")
                os.flush()

                val stdout = stdoutReader.readText().trim()
                val stderr = stderrReader.readText().trim()
                val exitCode = process.waitFor()

                os.close()
                stdoutReader.close()
                stderrReader.close()

                ShellResult(exitCode, stdout, stderr)
            } catch (e: Exception) {
                ShellResult(-1, "", e.message ?: "Execution error")
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
    }
}
