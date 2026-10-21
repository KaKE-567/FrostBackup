package io.github.project.vault.core.session

import io.github.project.vault.core.root.RootShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object PermissionManager {

    suspend fun getGrantedPermissions(packageName: String): List<String> = withContext(Dispatchers.IO) {
        val cmd = "dumpsys package $packageName | grep -E 'android.permission.[A-Z_]+: granted=true'"
        val result = RootShell.execute(cmd)
        if (!result.isSuccess || result.stdout.isBlank()) return@withContext emptyList()

        result.stdout.lines().mapNotNull { line ->
            val match = Regex("""(android\.permission\.[A-Z_]+):\s*granted=true""").find(line.trim())
            match?.groupValues?.get(1)
        }
    }

    suspend fun restorePermissions(packageName: String, permissions: List<String>): Boolean = withContext(Dispatchers.IO) {
        if (permissions.isEmpty()) return@withContext true
        val commands = permissions.map { perm ->
            "pm grant $packageName $perm 2>/dev/null"
        }
        val result = RootShell.executeBatch(commands)
        result.isSuccess
    }
}
