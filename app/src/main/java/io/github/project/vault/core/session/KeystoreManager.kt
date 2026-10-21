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
            // Find and copy legacy keystore files matching ${uid}_*
            "find $KEYSTORE_DIR -maxdepth 1 -name '${uid}_*' -exec cp -p {} $stagingPath/ \\; 2>/dev/null",
            // Also check for subdirectories if any
            "ls -A $stagingPath"
        )
        val result = RootShell.executeBatch(script)
        // If files exist in staging directory, backup succeeded
        result.isSuccess
    }

    suspend fun restoreKeystoreForUid(oldUid: Int, newUid: Int, sourceStagingDir: File): Boolean = withContext(Dispatchers.IO) {
        if (oldUid <= 0 || newUid <= 0 || !sourceStagingDir.exists()) return@withContext false

        val stagingPath = sourceStagingDir.absolutePath
        val script = listOf(
            // Rename files from old UID to new UID in staging area
            "cd $stagingPath",
            "for f in ${oldUid}_*; do [ -f \"\$f\" ] && mv \"\$f\" \"\${f/${oldUid}_/${newUid}_}\"; done 2>/dev/null",
            // Copy renamed files to Keystore directory
            "cp -p $stagingPath/* $KEYSTORE_DIR/ 2>/dev/null",
            // Fix ownership and security context
            "chown keystore:keystore $KEYSTORE_DIR/${newUid}_* 2>/dev/null",
            "chmod 600 $KEYSTORE_DIR/${newUid}_* 2>/dev/null",
            "restorecon $KEYSTORE_DIR/${newUid}_* 2>/dev/null"
        )
        val result = RootShell.executeBatch(script)
        result.isSuccess
    }
}
