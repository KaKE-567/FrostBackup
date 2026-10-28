package io.github.project.vault.core.telephony

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.provider.CallLog
import android.provider.Telephony
import io.github.project.vault.core.root.RootShell
import io.github.project.vault.core.wifi.WifiEngine
import io.github.project.vault.data.model.CallLogEntry
import io.github.project.vault.data.model.SmsMessage
import io.github.project.vault.data.model.TelephonyArchiveMetadata
import io.github.project.vault.data.model.TelephonyProgress
import io.github.project.vault.data.model.WifiNetwork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object TelephonyEngine {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        isLenient = true
    }

    suspend fun grantPermissionsViaRoot(context: Context) = withContext(Dispatchers.IO) {
        val pkg = context.packageName
        RootShell.execute(
            """
            pm grant $pkg android.permission.READ_SMS 2>/dev/null
            pm grant $pkg android.permission.READ_CALL_LOG 2>/dev/null
            pm grant $pkg android.permission.WRITE_CALL_LOG 2>/dev/null
            pm grant $pkg android.permission.READ_CONTACTS 2>/dev/null
            """.trimIndent()
        )
    }

    suspend fun getDeviceSmsCount(context: Context): Int = withContext(Dispatchers.IO) {
        var count = 0
        try {
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                arrayOf("_id"),
                null,
                null,
                null
            )?.use { cursor ->
                count = cursor.count
            }
        } catch (_: Exception) {
            val sqlRes = RootShell.execute("sqlite3 /data/data/com.android.providers.telephony/databases/mmssms.db 'SELECT count(*) FROM sms;' 2>/dev/null")
            count = sqlRes.stdout.trim().toIntOrNull() ?: 0
        }
        count
    }

    suspend fun getDeviceCallCount(context: Context): Int = withContext(Dispatchers.IO) {
        var count = 0
        try {
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                arrayOf(CallLog.Calls._ID),
                null,
                null,
                null
            )?.use { cursor ->
                count = cursor.count
            }
        } catch (_: Exception) {
            val sqlRes = RootShell.execute("sqlite3 /data/data/com.android.providers.contacts/databases/calllog.db 'SELECT count(*) FROM calls;' 2>/dev/null")
            count = sqlRes.stdout.trim().toIntOrNull() ?: 0
        }
        count
    }

    suspend fun queryAllSms(context: Context): List<SmsMessage> = withContext(Dispatchers.IO) {
        val messages = mutableListOf<SmsMessage>()
        try {
            val projection = arrayOf(
                "_id",
                "address",
                "body",
                "date",
                "date_sent",
                "type",
                "read",
                "status",
                "thread_id",
                "sub_id"
            )
            context.contentResolver.query(
                Telephony.Sms.CONTENT_URI,
                projection,
                null,
                null,
                "date DESC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex("_id")
                val addressIdx = cursor.getColumnIndex("address")
                val bodyIdx = cursor.getColumnIndex("body")
                val dateIdx = cursor.getColumnIndex("date")
                val dateSentIdx = cursor.getColumnIndex("date_sent")
                val typeIdx = cursor.getColumnIndex("type")
                val readIdx = cursor.getColumnIndex("read")
                val statusIdx = cursor.getColumnIndex("status")
                val threadIdIdx = cursor.getColumnIndex("thread_id")
                val subIdIdx = cursor.getColumnIndex("sub_id")

                while (cursor.moveToNext()) {
                    val id = if (idIdx >= 0) cursor.getLong(idIdx) else 0L
                    val address = if (addressIdx >= 0) cursor.getString(addressIdx) ?: "" else ""
                    val body = if (bodyIdx >= 0) cursor.getString(bodyIdx) ?: "" else ""
                    val date = if (dateIdx >= 0) cursor.getLong(dateIdx) else 0L
                    val dateSent = if (dateSentIdx >= 0) cursor.getLong(dateSentIdx) else 0L
                    val type = if (typeIdx >= 0) cursor.getInt(typeIdx) else 1
                    val read = if (readIdx >= 0) cursor.getInt(readIdx) else 1
                    val status = if (statusIdx >= 0) cursor.getInt(statusIdx) else -1
                    val threadId = if (threadIdIdx >= 0) cursor.getLong(threadIdIdx) else 0L
                    val subId = if (subIdIdx >= 0) cursor.getInt(subIdIdx) else -1

                    messages.add(
                        SmsMessage(
                            id = id,
                            address = address,
                            body = body,
                            date = date,
                            dateSent = dateSent,
                            type = type,
                            read = read,
                            status = status,
                            threadId = threadId,
                            subId = subId
                        )
                    )
                }
            }
        } catch (_: Exception) {
            // Fallback: If ContentProvider query failed, query via root sqlite3
            val sqlCmd = "sqlite3 -separator '|' /data/data/com.android.providers.telephony/databases/mmssms.db 'SELECT _id, address, date, date_sent, read, type, status, thread_id, sub_id, body FROM sms ORDER BY date DESC;' 2>/dev/null"
            val res = RootShell.execute(sqlCmd)
            if (res.isSuccess && res.stdout.isNotBlank()) {
                for (line in res.stdout.lines()) {
                    val parts = line.split("|")
                    if (parts.size >= 10) {
                        messages.add(
                            SmsMessage(
                                id = parts[0].toLongOrNull() ?: 0L,
                                address = parts[1],
                                date = parts[2].toLongOrNull() ?: 0L,
                                dateSent = parts[3].toLongOrNull() ?: 0L,
                                read = parts[4].toIntOrNull() ?: 1,
                                type = parts[5].toIntOrNull() ?: 1,
                                status = parts[6].toIntOrNull() ?: -1,
                                threadId = parts[7].toLongOrNull() ?: 0L,
                                subId = parts[8].toIntOrNull() ?: -1,
                                body = parts.subList(9, parts.size).joinToString("|")
                            )
                        )
                    }
                }
            }
        }
        messages
    }

    suspend fun queryAllCalls(context: Context): List<CallLogEntry> = withContext(Dispatchers.IO) {
        val calls = mutableListOf<CallLogEntry>()
        try {
            val projection = arrayOf(
                CallLog.Calls._ID,
                CallLog.Calls.NUMBER,
                CallLog.Calls.CACHED_NAME,
                CallLog.Calls.TYPE,
                CallLog.Calls.DATE,
                CallLog.Calls.DURATION,
                CallLog.Calls.PHONE_ACCOUNT_ID
            )
            context.contentResolver.query(
                CallLog.Calls.CONTENT_URI,
                projection,
                null,
                null,
                "${CallLog.Calls.DATE} DESC"
            )?.use { cursor ->
                val idIdx = cursor.getColumnIndex(CallLog.Calls._ID)
                val numberIdx = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                val nameIdx = cursor.getColumnIndex(CallLog.Calls.CACHED_NAME)
                val typeIdx = cursor.getColumnIndex(CallLog.Calls.TYPE)
                val dateIdx = cursor.getColumnIndex(CallLog.Calls.DATE)
                val durationIdx = cursor.getColumnIndex(CallLog.Calls.DURATION)
                val subIdIdx = cursor.getColumnIndex(CallLog.Calls.PHONE_ACCOUNT_ID)

                while (cursor.moveToNext()) {
                    val id = if (idIdx >= 0) cursor.getLong(idIdx) else 0L
                    val number = if (numberIdx >= 0) cursor.getString(numberIdx) ?: "" else ""
                    val name = if (nameIdx >= 0) cursor.getString(nameIdx) else null
                    val type = if (typeIdx >= 0) cursor.getInt(typeIdx) else 1
                    val date = if (dateIdx >= 0) cursor.getLong(dateIdx) else 0L
                    val duration = if (durationIdx >= 0) cursor.getLong(durationIdx) else 0L
                    val subId = if (subIdIdx >= 0) cursor.getString(subIdIdx)?.toIntOrNull() ?: -1 else -1

                    calls.add(
                        CallLogEntry(
                            id = id,
                            number = number,
                            name = name,
                            type = type,
                            date = date,
                            duration = duration,
                            subId = subId
                        )
                    )
                }
            }
        } catch (_: Exception) {
            val sqlCmd = "sqlite3 -separator '|' /data/data/com.android.providers.contacts/databases/calllog.db 'SELECT _id, number, name, type, date, duration, sub_id FROM calls ORDER BY date DESC;' 2>/dev/null"
            val res = RootShell.execute(sqlCmd)
            if (res.isSuccess && res.stdout.isNotBlank()) {
                for (line in res.stdout.lines()) {
                    val parts = line.split("|")
                    if (parts.size >= 7) {
                        calls.add(
                            CallLogEntry(
                                id = parts[0].toLongOrNull() ?: 0L,
                                number = parts[1],
                                name = parts[2].ifBlank { null },
                                type = parts[3].toIntOrNull() ?: 1,
                                date = parts[4].toLongOrNull() ?: 0L,
                                duration = parts[5].toLongOrNull() ?: 0L,
                                subId = parts[6].toIntOrNull() ?: -1
                            )
                        )
                    }
                }
            }
        }
        calls
    }

    suspend fun backup(
        context: Context,
        destinationDir: File,
        includeSms: Boolean = true,
        includeCalls: Boolean = true,
        includeWifi: Boolean = true,
        onProgress: (TelephonyProgress) -> Unit = {}
    ): Result<TelephonyArchiveMetadata> = withContext(Dispatchers.IO) {
        runCatching {
            grantPermissionsViaRoot(context)

            val now = System.currentTimeMillis()
            val timeString = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date(now))
            val archiveFileName = "records_${timeString}.vault"
            val stagingDir = File(destinationDir, ".staging_${archiveFileName.removeSuffix(".vault")}")

            RootShell.execute("rm -rf \"${stagingDir.absolutePath}\" && mkdir -p \"${stagingDir.absolutePath}/databases\"")

            var totalSmsCount = 0
            var totalCallCount = 0
            var totalWifiCount = 0

            // 1. Export SMS
            if (includeSms) {
                onProgress(TelephonyProgress.BackingUp(1, 4, "Exporting SMS messages…"))
                val messages = queryAllSms(context)
                totalSmsCount = messages.size
                val smsFile = File(stagingDir, "sms.json")
                smsFile.writeText(json.encodeToString(messages))

                // Copy mmssms.db if accessible
                RootShell.execute(
                    """
                    cp -f /data/data/com.android.providers.telephony/databases/mmssms.db* "${stagingDir.absolutePath}/databases/" 2>/dev/null
                    """.trimIndent()
                )
            }

            // 2. Export Calls
            if (includeCalls) {
                onProgress(TelephonyProgress.BackingUp(2, 4, "Exporting Call Logs…"))
                val calls = queryAllCalls(context)
                totalCallCount = calls.size
                val callsFile = File(stagingDir, "calls.json")
                callsFile.writeText(json.encodeToString(calls))

                // Copy calllog.db if accessible
                RootShell.execute(
                    """
                    cp -f /data/data/com.android.providers.contacts/databases/calllog.db* "${stagingDir.absolutePath}/databases/" 2>/dev/null
                    """.trimIndent()
                )
            }

            // 3. Export Wi-Fi
            if (includeWifi) {
                onProgress(TelephonyProgress.BackingUp(3, 4, "Exporting Wi-Fi passwords & networks…"))
                val networks = try {
                    WifiEngine.readSavedNetworks()
                } catch (_: Exception) {
                    emptyList()
                }
                totalWifiCount = networks.size
                val wifiFile = File(stagingDir, "wifi_networks.json")
                wifiFile.writeText(json.encodeToString(networks))
            }

            // 4. Metadata
            onProgress(TelephonyProgress.BackingUp(4, 4, "Packing archive…"))
            val meta = TelephonyArchiveMetadata(
                backupTimestamp = now,
                smsCount = totalSmsCount,
                callCount = totalCallCount,
                wifiCount = totalWifiCount,
                archiveFileName = archiveFileName,
                deviceModel = Build.MODEL ?: "",
                isEncrypted = false
            )
            val metaFile = File(stagingDir, "metadata.json")
            metaFile.writeText(json.encodeToString(meta))

            // 5. Create Tar Bundle
            if (!destinationDir.exists()) destinationDir.mkdirs()
            val finalArchivePath = File(destinationDir, archiveFileName).absolutePath
            val tarCmd = "tar -cf \"$finalArchivePath\" -C \"${stagingDir.absolutePath}\" ."
            val tarResult = RootShell.execute(tarCmd)
            if (!tarResult.isSuccess) {
                throw Exception("Failed to package archive: ${tarResult.stderr}")
            }

            // Measure archive size
            val sizeRes = RootShell.execute("stat -c %s \"$finalArchivePath\" 2>/dev/null")
            val sizeBytes = sizeRes.stdout.trim().toLongOrNull() ?: File(finalArchivePath).length()

            // Cleanup staging
            RootShell.execute("rm -rf \"${stagingDir.absolutePath}\"")

            val completedMeta = meta.copy(archiveSizeBytes = sizeBytes)
            val summaryText = buildList {
                if (totalSmsCount > 0) add("$totalSmsCount SMS")
                if (totalCallCount > 0) add("$totalCallCount Calls")
                if (totalWifiCount > 0) add("$totalWifiCount Wi-Fi")
            }.joinToString(", ").ifBlank { "Records" }
            onProgress(TelephonyProgress.Completed("Backup created successfully: $summaryText"))
            completedMeta
        }
    }

    suspend fun restore(
        context: Context,
        archiveFile: File,
        onProgress: (TelephonyProgress) -> Unit = {}
    ): Result<String> = withContext(Dispatchers.IO) {
        runCatching {
            grantPermissionsViaRoot(context)

            val stagingDir = File(archiveFile.parentFile, ".staging_restore_${archiveFile.nameWithoutExtension}")
            RootShell.execute("rm -rf \"${stagingDir.absolutePath}\" && mkdir -p \"${stagingDir.absolutePath}\"")

            onProgress(TelephonyProgress.Restoring(1, 4, "Unpacking telephony archive…"))
            val unpackRes = RootShell.execute("tar -xf \"${archiveFile.absolutePath}\" -C \"${stagingDir.absolutePath}\"")
            if (!unpackRes.isSuccess) {
                throw Exception("Failed to unpack telephony archive: ${unpackRes.stderr}")
            }

            var restoredSmsCount = 0
            var restoredCallCount = 0
            var restoredWifiCount = 0

            // 1. Restore Calls
            val callsFile = File(stagingDir, "calls.json")
            if (callsFile.exists()) {
                onProgress(TelephonyProgress.Restoring(2, 4, "Restoring Call Logs…"))
                val calls = try {
                    json.decodeFromString<List<CallLogEntry>>(callsFile.readText())
                } catch (_: Exception) { emptyList() }

                // Fetch existing call signatures (number + date) to prevent duplicates
                val existingCalls = mutableSetOf<String>()
                try {
                    context.contentResolver.query(
                        CallLog.Calls.CONTENT_URI,
                        arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.DATE),
                        null,
                        null,
                        null
                    )?.use { cursor ->
                        val numIdx = cursor.getColumnIndex(CallLog.Calls.NUMBER)
                        val dateIdx = cursor.getColumnIndex(CallLog.Calls.DATE)
                        while (cursor.moveToNext()) {
                            val num = if (numIdx >= 0) cursor.getString(numIdx) ?: "" else ""
                            val date = if (dateIdx >= 0) cursor.getLong(dateIdx) else 0L
                            existingCalls.add("${num}_$date")
                        }
                    }
                } catch (_: Exception) {}

                val valuesList = mutableListOf<ContentValues>()
                for (call in calls) {
                    val key = "${call.number}_${call.date}"
                    if (key !in existingCalls) {
                        val cv = ContentValues().apply {
                            put(CallLog.Calls.NUMBER, call.number)
                            put(CallLog.Calls.DATE, call.date)
                            put(CallLog.Calls.DURATION, call.duration)
                            put(CallLog.Calls.TYPE, call.type)
                            put(CallLog.Calls.NEW, 0)
                            if (!call.name.isNullOrBlank()) {
                                put(CallLog.Calls.CACHED_NAME, call.name)
                            }
                            if (call.subId != -1) {
                                put(CallLog.Calls.PHONE_ACCOUNT_ID, call.subId.toString())
                            }
                        }
                        valuesList.add(cv)
                    }
                }

                if (valuesList.isNotEmpty()) {
                    try {
                        context.contentResolver.bulkInsert(
                            CallLog.Calls.CONTENT_URI,
                            valuesList.toTypedArray()
                        )
                        restoredCallCount = valuesList.size
                    } catch (_: Exception) {
                        // Fallback SQLite insert for calls
                        val sqlBuilder = StringBuilder("BEGIN TRANSACTION;\n")
                        for (cv in valuesList) {
                            val num = escapeSql(cv.getAsString(CallLog.Calls.NUMBER) ?: "")
                            val dt = cv.getAsLong(CallLog.Calls.DATE) ?: 0L
                            val dur = cv.getAsLong(CallLog.Calls.DURATION) ?: 0L
                            val tp = cv.getAsInteger(CallLog.Calls.TYPE) ?: 1
                            val nm = escapeSql(cv.getAsString(CallLog.Calls.CACHED_NAME) ?: "")
                            sqlBuilder.append("INSERT OR IGNORE INTO calls (number, date, duration, type, name, new) VALUES ('$num', $dt, $dur, $tp, '$nm', 0);\n")
                        }
                        sqlBuilder.append("COMMIT;\n")

                        val sqlFile = File(stagingDir, "restore_calls.sql")
                        sqlFile.writeText(sqlBuilder.toString())
                        RootShell.execute("sqlite3 /data/data/com.android.providers.contacts/databases/calllog.db < \"${sqlFile.absolutePath}\"")
                        restoredCallCount = valuesList.size
                    }
                }
            }

            // 2. Restore SMS
            val smsFile = File(stagingDir, "sms.json")
            if (smsFile.exists()) {
                onProgress(TelephonyProgress.Restoring(3, 4, "Restoring SMS messages…"))
                val messages = try {
                    json.decodeFromString<List<SmsMessage>>(smsFile.readText())
                } catch (_: Exception) { emptyList() }

                // Fetch existing signatures
                val existingSms = mutableSetOf<String>()
                val existingRes = RootShell.execute("sqlite3 -separator '|' /data/data/com.android.providers.telephony/databases/mmssms.db 'SELECT address, date FROM sms;' 2>/dev/null")
                if (existingRes.isSuccess) {
                    for (line in existingRes.stdout.lines()) {
                        existingSms.add(line.trim())
                    }
                }

                val sqlBuilder = StringBuilder("BEGIN TRANSACTION;\n")
                var toInsertCount = 0
                for (msg in messages) {
                    val sig = "${msg.address}|${msg.date}"
                    if (sig !in existingSms) {
                        val addr = escapeSql(msg.address)
                        val body = escapeSql(msg.body)
                        val dt = msg.date
                        val dtSent = msg.dateSent
                        val tp = msg.type
                        val rd = msg.read
                        val st = msg.status
                        val th = msg.threadId
                        val sub = msg.subId
                        sqlBuilder.append(
                            "INSERT OR IGNORE INTO sms (address, date, date_sent, read, type, status, thread_id, sub_id, body) VALUES ('$addr', $dt, $dtSent, $rd, $tp, $st, $th, $sub, '$body');\n"
                        )
                        toInsertCount++
                    }
                }
                sqlBuilder.append("COMMIT;\n")

                if (toInsertCount > 0) {
                    val sqlFile = File(stagingDir, "restore_sms.sql")
                    sqlFile.writeText(sqlBuilder.toString())
                    RootShell.execute("sqlite3 /data/data/com.android.providers.telephony/databases/mmssms.db < \"${sqlFile.absolutePath}\"")
                    restoredSmsCount = toInsertCount

                    // Force telephony provider reload so SMS apps show restored messages instantly
                    RootShell.execute("killall -9 com.android.providers.telephony 2>/dev/null")
                }
            }

            // 3. Restore Wi-Fi
            val wifiFile = File(stagingDir, "wifi_networks.json")
            if (wifiFile.exists()) {
                onProgress(TelephonyProgress.Restoring(4, 5, "Restoring Wi-Fi configurations…"))
                try {
                    val incomingNetworks = json.decodeFromString<List<WifiNetwork>>(wifiFile.readText())
                    if (incomingNetworks.isNotEmpty()) {
                        val targetConfigPath = WifiEngine.getActiveWifiConfigPath()
                            ?: "/data/misc/apexdata/com.android.wifi/WifiConfigStore.xml"

                        val targetDir = File(targetConfigPath).parentFile ?: File("/data/misc/wifi")
                        RootShell.execute("mkdir -p ${targetDir.absolutePath}")

                        val existingXmlResult = RootShell.execute("cat $targetConfigPath 2>/dev/null")
                        val existingXml = if (existingXmlResult.isSuccess && existingXmlResult.stdout.contains("<WifiConfigStoreData>")) {
                            existingXmlResult.stdout
                        } else {
                            """<?xml version='1.0' encoding='utf-8' standalone='yes' ?>
<WifiConfigStoreData>
<int name="Version" value="3" />
<NetworkList>
</NetworkList>
</WifiConfigStoreData>"""
                        }

                        var mergedXml = existingXml
                        for (net in incomingNetworks) {
                            val cleanSsid = net.cleanSsid
                            val escapedSsid = cleanSsid.replace("&", "&amp;").replace("\"", "&quot;")

                            if (mergedXml.contains("\"$cleanSsid\"") || mergedXml.contains("&quot;$cleanSsid&quot;")) {
                                if (!net.cleanPassword.isNullOrBlank()) {
                                    val pskEscaped = net.cleanPassword!!.replace("&", "&amp;").replace("\"", "&quot;")
                                    mergedXml = mergedXml.replace(
                                        Regex("""(<string name="SSID">&quot;$cleanSsid&quot;<\/string>[\s\S]*?<string name="PreSharedKey">)([^<]*)(<\/string>)"""),
                                        "$1&quot;$pskEscaped&quot;$3"
                                    )
                                    restoredWifiCount++
                                }
                            } else {
                                val blockToInject = if (!net.rawConfigXml.isNullOrBlank()) {
                                    "<Network>\n${net.rawConfigXml}\n</Network>"
                                } else {
                                    val pskLine = if (!net.cleanPassword.isNullOrBlank()) {
                                        val pskEscaped = net.cleanPassword!!.replace("&", "&amp;").replace("\"", "&quot;")
                                        "<string name=\"PreSharedKey\">&quot;$pskEscaped&quot;</string>"
                                    } else {
                                        "<null name=\"PreSharedKey\" />"
                                    }

                                    """<Network>
<WifiConfiguration>
<string name="ConfigKey">&quot;$escapedSsid&quot;${net.keyMgmt}</string>
<string name="SSID">&quot;$escapedSsid&quot;</string>
<null name="BSSID" />
$pskLine
<null name="WEPKeys" />
<int name="WEPTxKeyIndex" value="0" />
<boolean name="HiddenSSID" value="${net.hiddenSSID}" />
<boolean name="RequirePMF" value="false" />
<boolean name="Shared" value="true" />
<int name="Status" value="2" />
<int name="CreatorUid" value="1000" />
<string name="CreatorName">android.uid.system:1000</string>
</WifiConfiguration>
</Network>"""
                                }

                                if (mergedXml.contains("</NetworkList>")) {
                                    mergedXml = mergedXml.replace("</NetworkList>", "$blockToInject\n</NetworkList>")
                                    restoredWifiCount++
                                }
                            }
                        }

                        val tempWriteFile = File(stagingDir, "WifiConfigStore.xml")
                        tempWriteFile.writeText(mergedXml)
                        RootShell.execute("cp -f ${tempWriteFile.absolutePath} $targetConfigPath")
                        RootShell.execute("chmod 600 $targetConfigPath")
                        RootShell.execute("chown system:system $targetConfigPath 2>/dev/null || chown wifi:wifi $targetConfigPath 2>/dev/null")
                        RootShell.execute("restorecon $targetConfigPath 2>/dev/null")
                        RootShell.execute("svc wifi disable && sleep 1 && svc wifi enable")
                    }
                } catch (_: Exception) {}
            }

            onProgress(TelephonyProgress.Restoring(5, 5, "Finalizing restore…"))
            RootShell.execute("rm -rf \"${stagingDir.absolutePath}\"")

            val summaryList = buildList {
                if (restoredSmsCount > 0) add("$restoredSmsCount SMS")
                if (restoredCallCount > 0) add("$restoredCallCount Call Logs")
                if (restoredWifiCount > 0) add("$restoredWifiCount Wi-Fi Networks")
            }.joinToString(", ").ifBlank { "Records" }
            val msg = "Restored: $summaryList"
            onProgress(TelephonyProgress.Completed(msg))
            msg
        }
    }

    private fun escapeSql(value: String): String {
        return value.replace("'", "''")
    }
}
