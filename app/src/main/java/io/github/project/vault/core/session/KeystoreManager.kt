package io.github.project.vault.core.session

import io.github.project.vault.core.root.RootShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object KeystoreManager {
    private const val KEYSTORE_DIR = "/data/misc/keystore/user_0"

    suspend fun backupKeystoreForUid(uid: Int, targetStagingDir: File): Boolean = withContext(Dispatchers.IO) {
        if (uid <= 0) return@withContext false

        val stagingPath = targetStagingDir.absolutePath
        val script = listOf(
            "mkdir -p $stagingPath",
            // Find and copy ALL keystore files matching .${uid}_* AND ${uid}_*
            "find $KEYSTORE_DIR -maxdepth 1 \\( -name '.${uid}_*' -o -name '${uid}_*' \\) -exec cp -p {} $stagingPath/ \\; 2>/dev/null",
            // Check if any files were copied
            "ls -A $stagingPath"
        )
        val result = RootShell.executeBatch(script)
        result.isSuccess && result.stdout.isNotBlank()
    }

    suspend fun restoreKeystoreForUid(oldUid: Int, newUid: Int, sourceStagingDir: File): Boolean = withContext(Dispatchers.IO) {
        if (oldUid <= 0 || newUid <= 0 || !sourceStagingDir.exists()) return@withContext false

        val stagingPath = sourceStagingDir.absolutePath
        val script = listOf(
            "cd $stagingPath",
            // Rename any .${oldUid}_* files to .${newUid}_*
            "for f in .${oldUid}_*; do [ -f \"\$f\" ] && mv \"\$f\" \".${newUid}_\${f#.${oldUid}_}\"; done 2>/dev/null",
            // Rename any ${oldUid}_* files to ${newUid}_*
            "for f in ${oldUid}_*; do [ -f \"\$f\" ] && mv \"\$f\" \"${newUid}_\${f#${oldUid}_}\"; done 2>/dev/null",
            // Copy renamed files to Keystore directory (including hidden files, skipping . and ..)
            "find \"$stagingPath\" -maxdepth 1 -type f -exec cp -p {} $KEYSTORE_DIR/ \\; 2>/dev/null",
            // Fix ownership, permissions, and SELinux contexts
            "chown keystore:keystore $KEYSTORE_DIR/.${newUid}_* $KEYSTORE_DIR/${newUid}_* 2>/dev/null",
            "chmod 600 $KEYSTORE_DIR/.${newUid}_* $KEYSTORE_DIR/${newUid}_* 2>/dev/null",
            "restorecon $KEYSTORE_DIR/.${newUid}_* $KEYSTORE_DIR/${newUid}_* 2>/dev/null",
            // Reload keystore / keystore2 daemon so in-memory cache picks up restored keys
            "pkill -9 -f android.system.keystore2 2>/dev/null || pkill -9 -f keystore 2>/dev/null || true"
        )
        val result = RootShell.executeBatch(script)
        result.isSuccess
    }
}
