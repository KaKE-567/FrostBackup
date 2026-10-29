package io.github.project.vault.core.session

import android.content.Context
import android.database.Cursor
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

    private fun tableExists(db: SQLiteDatabase, tableName: String): Boolean {
        return try {
            db.rawQuery("SELECT name FROM sqlite_master WHERE type='table' AND name=?", arrayOf(tableName)).use { cursor ->
                cursor.count > 0
            }
        } catch (_: Exception) {
            false
        }
    }

    private fun copyTable(
        sourceDb: SQLiteDatabase,
        targetDb: SQLiteDatabase,
        tableName: String,
        createTableSql: String,
        selectQuery: String,
        selectionArgs: Array<String>? = null
    ) {
        if (createTableSql.isNotBlank()) {
            targetDb.execSQL(createTableSql)
        }
        if (!tableExists(sourceDb, tableName)) return
        sourceDb.rawQuery(selectQuery, selectionArgs).use { cursor ->
            val columnNames = cursor.columnNames
            val placeholders = columnNames.map { "?" }.joinToString(",")
            val insertSql = "INSERT OR REPLACE INTO $tableName (${columnNames.joinToString(",")}) VALUES ($placeholders)"
            val stmt = targetDb.compileStatement(insertSql)
            while (cursor.moveToNext()) {
                stmt.clearBindings()
                for (i in columnNames.indices) {
                    when (cursor.getType(i)) {
                        Cursor.FIELD_TYPE_NULL -> stmt.bindNull(i + 1)
                        Cursor.FIELD_TYPE_INTEGER -> stmt.bindLong(i + 1, cursor.getLong(i))
                        Cursor.FIELD_TYPE_FLOAT -> stmt.bindDouble(i + 1, cursor.getDouble(i))
                        Cursor.FIELD_TYPE_STRING -> stmt.bindString(i + 1, cursor.getString(i))
                        Cursor.FIELD_TYPE_BLOB -> stmt.bindBlob(i + 1, cursor.getBlob(i))
                    }
                }
                stmt.executeInsert()
            }
        }
    }

    private fun copyTableWithRemap(
        sourceDb: SQLiteDatabase,
        targetDb: SQLiteDatabase,
        tableName: String,
        selectQuery: String,
        remapColumn: String,
        remapValue: Long
    ) {
        if (!tableExists(sourceDb, tableName)) return
        sourceDb.rawQuery(selectQuery, null).use { cursor ->
            val columnNames = cursor.columnNames
            val placeholders = columnNames.map { "?" }.joinToString(",")
            val insertSql = "INSERT OR REPLACE INTO $tableName (${columnNames.joinToString(",")}) VALUES ($placeholders)"
            val stmt = targetDb.compileStatement(insertSql)
            val remapIndex = columnNames.indexOf(remapColumn)

            while (cursor.moveToNext()) {
                stmt.clearBindings()
                for (i in columnNames.indices) {
                    if (i == remapIndex) {
                        stmt.bindLong(i + 1, remapValue)
                    } else {
                        when (cursor.getType(i)) {
                            Cursor.FIELD_TYPE_NULL -> stmt.bindNull(i + 1)
                            Cursor.FIELD_TYPE_INTEGER -> stmt.bindLong(i + 1, cursor.getLong(i))
                            Cursor.FIELD_TYPE_FLOAT -> stmt.bindDouble(i + 1, cursor.getDouble(i))
                            Cursor.FIELD_TYPE_STRING -> stmt.bindString(i + 1, cursor.getString(i))
                            Cursor.FIELD_TYPE_BLOB -> stmt.bindBlob(i + 1, cursor.getBlob(i))
                        }
                    }
                }
                stmt.executeInsert()
            }
        }
    }

    suspend fun backupKeystoreForUid(context: Context, uid: Int, targetStagingDir: File): Boolean = withContext(Dispatchers.IO) {
        if (uid <= 0) return@withContext false
        val stagingPath = targetStagingDir.absolutePath
        RootShell.execute("mkdir -p $stagingPath")

        var backedUpAny = false

        // 1. Keystore 2.0 (Android 12+ through Android 17)
        val hasKeystore2 = RootShell.execute("[ -f $KEYSTORE2_DB ] && echo 1 || echo 0").stdout.trim() == "1"
        if (hasKeystore2) {
            val appUid = context.applicationInfo.uid
            val appCtx = RootShell.execute("ls -Zd ${context.cacheDir.absolutePath} 2>/dev/null | awk '{print \$1}'").stdout.trim()
            val timestamp = System.currentTimeMillis()
            val tempWorkingDb = File(context.cacheDir, "keystore2_temp_$timestamp.sqlite")
            val localExportDb = File(context.cacheDir, "keystore2_export_$timestamp.sqlite")
            val targetDb = File(targetStagingDir, SQLITE_BACKUP_NAME)

            try {
                val chconPart = if (appCtx.isNotBlank() && appCtx.startsWith("u:object_r:")) " && chcon $appCtx ${tempWorkingDb.absolutePath}" else ""
                val copyCmd = "cp -p $KEYSTORE2_DB ${tempWorkingDb.absolutePath} && " +
                        "chown $appUid:$appUid ${tempWorkingDb.absolutePath} && " +
                        "chmod 600 ${tempWorkingDb.absolutePath}$chconPart"
                RootShell.execute(copyCmd)

                if (tempWorkingDb.exists() && tempWorkingDb.length() > 0) {
                    var sourceDb: SQLiteDatabase? = null
                    var exportDb: SQLiteDatabase? = null
                    try {
                        sourceDb = SQLiteDatabase.openDatabase(tempWorkingDb.absolutePath, null, SQLiteDatabase.OPEN_READONLY)

                        // Check if this UID has any keys
                        var count = 0
                        sourceDb.rawQuery("SELECT count(*) FROM keyentry WHERE namespace = ?", arrayOf(uid.toString())).use { cursor ->
                            if (cursor.moveToFirst()) {
                                count = cursor.getInt(0)
                            }
                        }
                        Log.d(TAG, "Keystore 2.0: namespace $uid has $count key(s)")

                        if (count > 0) {
                            if (localExportDb.exists()) localExportDb.delete()
                            exportDb = SQLiteDatabase.openOrCreateDatabase(localExportDb, null)
                            exportDb.beginTransaction()
                            try {
                                copyTable(
                                    sourceDb, exportDb, "keyentry",
                                    "CREATE TABLE IF NOT EXISTS keyentry (id INTEGER UNIQUE, key_type INTEGER, domain INTEGER, namespace INTEGER, alias BLOB, state INTEGER, km_uuid BLOB)",
                                    "SELECT * FROM keyentry WHERE namespace = ?",
                                    arrayOf(uid.toString())
                                )
                                copyTable(
                                    sourceDb, exportDb, "blobentry",
                                    "CREATE TABLE IF NOT EXISTS blobentry (id INTEGER PRIMARY KEY, subcomponent_type INTEGER, keyentryid INTEGER, blob BLOB, state INTEGER DEFAULT 0)",
                                    "SELECT * FROM blobentry WHERE keyentryid IN (SELECT id FROM keyentry WHERE namespace = ?)",
                                    arrayOf(uid.toString())
                                )
                                if (tableExists(sourceDb, "blobmetadata")) {
                                    copyTable(
                                        sourceDb, exportDb, "blobmetadata",
                                        "CREATE TABLE IF NOT EXISTS blobmetadata (id INTEGER PRIMARY KEY, blobentryid INTEGER, tag INTEGER, data ANY, UNIQUE (blobentryid, tag))",
                                        "SELECT * FROM blobmetadata WHERE blobentryid IN (SELECT id FROM blobentry WHERE keyentryid IN (SELECT id FROM keyentry WHERE namespace = ?))",
                                        arrayOf(uid.toString())
                                    )
                                }
                                copyTable(
                                    sourceDb, exportDb, "keyparameter",
                                    "CREATE TABLE IF NOT EXISTS keyparameter (keyentryid INTEGER, tag INTEGER, data ANY, security_level INTEGER)",
                                    "SELECT * FROM keyparameter WHERE keyentryid IN (SELECT id FROM keyentry WHERE namespace = ?)",
                                    arrayOf(uid.toString())
                                )
                                copyTable(
                                    sourceDb, exportDb, "keymetadata",
                                    "CREATE TABLE IF NOT EXISTS keymetadata (keyentryid INTEGER, tag INTEGER, data ANY, UNIQUE (keyentryid, tag))",
                                    "SELECT * FROM keymetadata WHERE keyentryid IN (SELECT id FROM keyentry WHERE namespace = ?)",
                                    arrayOf(uid.toString())
                                )
                                copyTable(
                                    sourceDb, exportDb, "grant",
                                    "CREATE TABLE IF NOT EXISTS grant (id INTEGER UNIQUE, grantee INTEGER, keyentryid INTEGER, access_vector INTEGER)",
                                    "SELECT * FROM grant WHERE keyentryid IN (SELECT id FROM keyentry WHERE namespace = ?)",
                                    arrayOf(uid.toString())
                                )
                                exportDb.setTransactionSuccessful()
                            } finally {
                                exportDb.endTransaction()
                            }
                            exportDb.close()
                            exportDb = null

                            // Move exported database to target staging directory via root
                            RootShell.execute(
                                "mkdir -p ${targetStagingDir.absolutePath} && " +
                                "cp ${localExportDb.absolutePath} ${targetDb.absolutePath} && " +
                                "chmod 644 ${targetDb.absolutePath}"
                            )
                            backedUpAny = targetDb.exists() && targetDb.length() > 0
                            Log.d(TAG, "Successfully backed up Keystore 2.0 keys for UID $uid (${targetDb.length()} bytes)")
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error querying/backing up Keystore 2.0: ${e.message}", e)
                    } finally {
                        exportDb?.close()
                        sourceDb?.close()
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
        if (newUid <= 0 || !sourceStagingDir.exists()) return@withContext false
        var restoredAny = false

        // 1. Keystore 2.0 Restore
        val backupDbFile = File(sourceStagingDir, SQLITE_BACKUP_NAME)
        val hasKeystore2 = RootShell.execute("[ -f $KEYSTORE2_DB ] && echo 1 || echo 0").stdout.trim() == "1"
        if (hasKeystore2 && backupDbFile.exists() && backupDbFile.length() > 0) {
            val appUid = context.applicationInfo.uid
            val appCtx = RootShell.execute("ls -Zd ${context.cacheDir.absolutePath} 2>/dev/null | awk '{print \$1}'").stdout.trim()
            val timestamp = System.currentTimeMillis()
            val tempWorkingDb = File(context.cacheDir, "keystore2_restore_work_$timestamp.sqlite")
            val localImportDb = File(context.cacheDir, "keystore2_restore_import_$timestamp.sqlite")

            try {
                val chconPart = if (appCtx.isNotBlank() && appCtx.startsWith("u:object_r:")) " && chcon $appCtx ${tempWorkingDb.absolutePath} ${localImportDb.absolutePath}" else ""
                RootShell.execute(
                    "cp -p $KEYSTORE2_DB ${tempWorkingDb.absolutePath} && " +
                    "cp ${backupDbFile.absolutePath} ${localImportDb.absolutePath} && " +
                    "chown $appUid:$appUid ${tempWorkingDb.absolutePath} ${localImportDb.absolutePath} && " +
                    "chmod 600 ${tempWorkingDb.absolutePath} ${localImportDb.absolutePath}$chconPart"
                )

                if (tempWorkingDb.exists() && tempWorkingDb.length() > 0 && localImportDb.exists() && localImportDb.length() > 0) {
                    var targetDb: SQLiteDatabase? = null
                    var backupDb: SQLiteDatabase? = null
                    try {
                        targetDb = SQLiteDatabase.openDatabase(tempWorkingDb.absolutePath, null, SQLiteDatabase.OPEN_READWRITE)
                        backupDb = SQLiteDatabase.openDatabase(localImportDb.absolutePath, null, SQLiteDatabase.OPEN_READONLY)

                        // Check if backup has keys
                        var backupKeyCount = 0
                        backupDb.rawQuery("SELECT count(*) FROM keyentry", null).use { c ->
                            if (c.moveToFirst()) backupKeyCount = c.getInt(0)
                        }
                        Log.d(TAG, "Restoring Keystore 2.0: found $backupKeyCount key(s) in backup for remapping to UID $newUid")

                        if (backupKeyCount > 0) {
                            targetDb.beginTransaction()
                            try {
                                // 1. Clean up existing keys for newUid
                                val hasBlobMeta = tableExists(targetDb, "blobmetadata")
                                if (hasBlobMeta) {
                                    targetDb.execSQL("DELETE FROM blobmetadata WHERE blobentryid IN (SELECT id FROM blobentry WHERE keyentryid IN (SELECT id FROM keyentry WHERE namespace = $newUid))")
                                }
                                targetDb.execSQL("DELETE FROM blobentry WHERE keyentryid IN (SELECT id FROM keyentry WHERE namespace = $newUid)")
                                targetDb.execSQL("DELETE FROM keyparameter WHERE keyentryid IN (SELECT id FROM keyentry WHERE namespace = $newUid)")
                                targetDb.execSQL("DELETE FROM keymetadata WHERE keyentryid IN (SELECT id FROM keyentry WHERE namespace = $newUid)")
                                targetDb.execSQL("DELETE FROM grant WHERE keyentryid IN (SELECT id FROM keyentry WHERE namespace = $newUid)")
                                targetDb.execSQL("DELETE FROM keyentry WHERE namespace = $newUid")

                                // 2. Insert restored keyentry with namespace remapped to newUid
                                copyTableWithRemap(
                                    sourceDb = backupDb,
                                    targetDb = targetDb,
                                    tableName = "keyentry",
                                    selectQuery = "SELECT * FROM keyentry",
                                    remapColumn = "namespace",
                                    remapValue = newUid.toLong()
                                )

                                // 3. Insert blobentry
                                copyTable(
                                    sourceDb = backupDb,
                                    targetDb = targetDb,
                                    tableName = "blobentry",
                                    createTableSql = "",
                                    selectQuery = "SELECT * FROM blobentry"
                                )

                                // 4. Insert blobmetadata if present
                                if (hasBlobMeta && tableExists(backupDb, "blobmetadata")) {
                                    copyTable(
                                        sourceDb = backupDb,
                                        targetDb = targetDb,
                                        tableName = "blobmetadata",
                                        createTableSql = "",
                                        selectQuery = "SELECT * FROM blobmetadata"
                                    )
                                }

                                // 5. Insert keyparameter
                                copyTable(
                                    sourceDb = backupDb,
                                    targetDb = targetDb,
                                    tableName = "keyparameter",
                                    createTableSql = "",
                                    selectQuery = "SELECT * FROM keyparameter"
                                )

                                // 6. Insert keymetadata
                                copyTable(
                                    sourceDb = backupDb,
                                    targetDb = targetDb,
                                    tableName = "keymetadata",
                                    createTableSql = "",
                                    selectQuery = "SELECT * FROM keymetadata"
                                )

                                // 7. Insert grant
                                copyTable(
                                    sourceDb = backupDb,
                                    targetDb = targetDb,
                                    tableName = "grant",
                                    createTableSql = "",
                                    selectQuery = "SELECT * FROM grant"
                                )

                                targetDb.setTransactionSuccessful()
                                restoredAny = true
                                Log.d(TAG, "Successfully restored and remapped $backupKeyCount Keystore 2.0 key(s) to UID $newUid")
                            } finally {
                                targetDb.endTransaction()
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error restoring Keystore 2.0 database: ${e.message}", e)
                    } finally {
                        backupDb?.close()
                        targetDb?.close()
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
