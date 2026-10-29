package io.github.project.vault.core.session

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.util.Log
import io.github.project.vault.core.root.RootShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

object KeystoreManager {
    private const val TAG = "KeystoreManager"
    private const val KEYSTORE2_DB = "/data/misc/keystore/persistent.sqlite"
    private const val KEYSTORE_LEGACY_DIR = "/data/misc/keystore/user_0"
    private const val SQLITE_BACKUP_NAME = "keystore2.sqlite"

    suspend fun backupKeystoreForUid(context: Context, uid: Int, targetStagingDir: File): Boolean = withContext(Dispatchers.IO) {
        if (uid <= 0) return@withContext false
        val stagingPath = targetStagingDir.absolutePath
        RootShell.execute("mkdir -p $stagingPath")

        var backedUpAny = false

        // 1. Keystore 2.0 (Android 12+ through Android 17)
        val hasKeystore2 = RootShell.execute("[ -f $KEYSTORE2_DB ] && echo 1 || echo 0").stdout.trim() == "1"
        if (hasKeystore2) {
            val appUid = context.applicationInfo.uid
            val timestamp = System.currentTimeMillis()
            val tempWorkingDb = File(context.cacheDir, "keystore2_temp_$timestamp.sqlite")
            val localExportDb = File(context.cacheDir, "keystore2_export_$timestamp.sqlite")
            val targetDb = File(targetStagingDir, SQLITE_BACKUP_NAME)

            try {
                // Copy persistent.sqlite to app cache with app ownership and SELinux label
                val copyCmd = "cp -p $KEYSTORE2_DB ${tempWorkingDb.absolutePath} && " +
                        "chown $appUid:$appUid ${tempWorkingDb.absolutePath} && " +
                        "chmod 600 ${tempWorkingDb.absolutePath} && " +
                        "restorecon -F ${tempWorkingDb.absolutePath}"
                RootShell.execute(copyCmd)

                if (tempWorkingDb.exists() && tempWorkingDb.length() > 0) {
                    var db: SQLiteDatabase? = null
                    try {
                        db = SQLiteDatabase.openDatabase(tempWorkingDb.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)

                        // Check if this UID has any keys
                        var count = 0
                        db.rawQuery("SELECT count(*) FROM keyentry WHERE namespace = ?", arrayOf(uid.toString())).use { cursor ->
                            if (cursor.moveToFirst()) {
                                count = cursor.getInt(0)
                            }
                        }
                        Log.d(TAG, "Keystore 2.0: namespace $uid has $count key(s)")

                        if (count > 0) {
                            if (localExportDb.exists()) localExportDb.delete()
                            db.execSQL("ATTACH DATABASE '${localExportDb.absolutePath}' AS backup_db")
                            db.execSQL("CREATE TABLE backup_db.keyentry AS SELECT * FROM keyentry WHERE namespace = $uid")
                            db.execSQL("CREATE TABLE backup_db.blobentry AS SELECT * FROM blobentry WHERE keyentryid IN (SELECT id FROM keyentry WHERE namespace = $uid)")
                            db.execSQL("CREATE TABLE backup_db.keyparameter AS SELECT * FROM keyparameter WHERE keyentryid IN (SELECT id FROM keyentry WHERE namespace = $uid)")
                            db.execSQL("CREATE TABLE backup_db.keymetadata AS SELECT * FROM keymetadata WHERE keyentryid IN (SELECT id FROM keyentry WHERE namespace = $uid)")
                            db.execSQL("CREATE TABLE backup_db.grant AS SELECT * FROM grant WHERE keyentryid IN (SELECT id FROM keyentry WHERE namespace = $uid)")
                            db.execSQL("DETACH DATABASE backup_db")

                            // Move exported database to target staging directory via root
                            RootShell.execute(
                                "mkdir -p ${targetStagingDir.absolutePath} && " +
                                "cp ${localExportDb.absolutePath} ${targetDb.absolutePath} && " +
                                "chmod 644 ${targetDb.absolutePath}"
                            )
                            backedUpAny = true
                            Log.d(TAG, "Successfully backed up Keystore 2.0 keys for UID $uid")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error querying/backing up Keystore 2.0: ${e.message}", e)
                    } finally {
                        db?.close()
                    }
                }
            } finally {
                tempWorkingDb.delete()
                localExportDb.delete()
                RootShell.execute("rm -f ${tempWorkingDb.absolutePath}* ${localExportDb.absolutePath}*")
            }
        }

        // 2. Legacy Keystore (Android 10/11)
        val hasLegacy = RootShell.execute("[ -d $KEYSTORE_LEGACY_DIR ] && echo 1 || echo 0").stdout.trim() == "1"
        if (hasLegacy) {
            val script = listOf(
                "find $KEYSTORE_LEGACY_DIR -maxdepth 1 \\( -name '.${uid}_*' -o -name '${uid}_*' \\) -exec cp -p {} $stagingPath/ \\; 2>/dev/null",
                "ls -A $stagingPath"
            )
            val result = RootShell.executeBatch(script)
            if (result.isSuccess && result.stdout.isNotBlank()) {
                backedUpAny = true
                Log.d(TAG, "Successfully backed up legacy Keystore files for UID $uid")
            }
        }

        backedUpAny
    }

    suspend fun restoreKeystoreForUid(context: Context, oldUid: Int, newUid: Int, sourceStagingDir: File): Boolean = withContext(Dispatchers.IO) {
        if (oldUid <= 0 || newUid <= 0 || !sourceStagingDir.exists()) return@withContext false
        val stagingPath = sourceStagingDir.absolutePath
        var restoredAny = false

        // 1. Keystore 2.0 Restore
        val backupDb = File(sourceStagingDir, SQLITE_BACKUP_NAME)
        val hasKeystore2 = RootShell.execute("[ -f $KEYSTORE2_DB ] && echo 1 || echo 0").stdout.trim() == "1"
        if (hasKeystore2 && backupDb.exists() && backupDb.length() > 0) {
            val appUid = context.applicationInfo.uid
            val timestamp = System.currentTimeMillis()
            val tempWorkingDb = File(context.cacheDir, "keystore2_restore_work_$timestamp.sqlite")
            val localImportDb = File(context.cacheDir, "keystore2_restore_import_$timestamp.sqlite")

            try {
                // Copy current persistent.sqlite AND backupDb to app cache with app ownership and SELinux label
                RootShell.execute(
                    "cp -p $KEYSTORE2_DB ${tempWorkingDb.absolutePath} && " +
                    "chown $appUid:$appUid ${tempWorkingDb.absolutePath} && " +
                    "chmod 600 ${tempWorkingDb.absolutePath} && " +
                    "restorecon -F ${tempWorkingDb.absolutePath} && " +
                    "cp ${backupDb.absolutePath} ${localImportDb.absolutePath} && " +
                    "chown $appUid:$appUid ${localImportDb.absolutePath} && " +
                    "chmod 600 ${localImportDb.absolutePath} && " +
                    "restorecon -F ${localImportDb.absolutePath}"
                )

                if (tempWorkingDb.exists() && tempWorkingDb.length() > 0 && localImportDb.exists() && localImportDb.length() > 0) {
                    var db: SQLiteDatabase? = null
                    try {
                        db = SQLiteDatabase.openDatabase(tempWorkingDb.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
                        db.beginTransaction()
                        try {
                            db.execSQL("ATTACH DATABASE '${localImportDb.absolutePath}' AS backup_db")

                            // Delete any old keyentries for this newUid to avoid collisions
                            db.execSQL("DELETE FROM blobentry WHERE keyentryid IN (SELECT id FROM keyentry WHERE namespace = $newUid)")
                            db.execSQL("DELETE FROM keyparameter WHERE keyentryid IN (SELECT id FROM keyentry WHERE namespace = $newUid)")
                            db.execSQL("DELETE FROM keymetadata WHERE keyentryid IN (SELECT id FROM keyentry WHERE namespace = $newUid)")
                            db.execSQL("DELETE FROM grant WHERE keyentryid IN (SELECT id FROM keyentry WHERE namespace = $newUid)")
                            db.execSQL("DELETE FROM keyentry WHERE namespace = $newUid")

                            // Insert restored keys with remap to newUid
                            db.execSQL(
                                "INSERT OR REPLACE INTO keyentry (id, key_type, domain, namespace, alias, state, km_uuid) " +
                                "SELECT id, key_type, domain, $newUid, alias, state, km_uuid FROM backup_db.keyentry"
                            )
                            db.execSQL("INSERT OR REPLACE INTO blobentry SELECT * FROM backup_db.blobentry")
                            db.execSQL("INSERT OR REPLACE INTO keyparameter SELECT * FROM backup_db.keyparameter")
                            db.execSQL("INSERT OR REPLACE INTO keymetadata SELECT * FROM backup_db.keymetadata")
                            db.execSQL("INSERT OR REPLACE INTO grant SELECT * FROM backup_db.grant")

                            db.execSQL("DETACH DATABASE backup_db")
                            db.setTransactionSuccessful()
                            restoredAny = true
                            Log.d(TAG, "Successfully remapped and restored Keystore 2.0 keys from $oldUid to $newUid")
                        } finally {
                            db.endTransaction()
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error restoring Keystore 2.0 database: ${e.message}", e)
                    } finally {
                        db?.close()
                    }

                    if (restoredAny) {
                        // Copy back into /data/misc/keystore/persistent.sqlite with correct permissions & reload daemon
                        RootShell.execute(
                            "cp ${tempWorkingDb.absolutePath} $KEYSTORE2_DB && " +
                            "chown keystore:keystore $KEYSTORE2_DB && " +
                            "chmod 600 $KEYSTORE2_DB && " +
                            "restorecon $KEYSTORE2_DB && " +
                            "rm -f /data/misc/keystore/persistent.sqlite-wal /data/misc/keystore/persistent.sqlite-shm && " +
                            "pkill -9 -f android.system.keystore2 2>/dev/null || true"
                        )
                    }
                }
            } finally {
                tempWorkingDb.delete()
                localImportDb.delete()
                RootShell.execute("rm -f ${tempWorkingDb.absolutePath}* ${localImportDb.absolutePath}*")
            }
        }

        // 2. Legacy Keystore Restore (Android 10/11)
        val hasLegacy = RootShell.execute("[ -d $KEYSTORE_LEGACY_DIR ] && echo 1 || echo 0").stdout.trim() == "1"
        if (hasLegacy) {
            val script = listOf(
                "cd $stagingPath",
                "for f in .${oldUid}_*; do [ -f \"\$f\" ] && mv \"\$f\" \".${newUid}_\${f#.${oldUid}_}\"; done 2>/dev/null",
                "for f in ${oldUid}_*; do [ -f \"\$f\" ] && mv \"\$f\" \"${newUid}_\${f#${oldUid}_}\"; done 2>/dev/null",
                "find \"$stagingPath\" -maxdepth 1 -type f ! -name \"$SQLITE_BACKUP_NAME\" -exec cp -p {} $KEYSTORE_LEGACY_DIR/ \\; 2>/dev/null",
                "chown keystore:keystore $KEYSTORE_LEGACY_DIR/.${newUid}_* $KEYSTORE_LEGACY_DIR/${newUid}_* 2>/dev/null",
                "chmod 600 $KEYSTORE_LEGACY_DIR/.${newUid}_* $KEYSTORE_LEGACY_DIR/${newUid}_* 2>/dev/null",
                "restorecon $KEYSTORE_LEGACY_DIR/.${newUid}_* $KEYSTORE_LEGACY_DIR/${newUid}_* 2>/dev/null",
                "pkill -9 -f keystore 2>/dev/null || true"
            )
            val result = RootShell.executeBatch(script)
            if (result.isSuccess) restoredAny = true
        }

        restoredAny
    }
}
