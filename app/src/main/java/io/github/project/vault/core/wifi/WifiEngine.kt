package io.github.project.vault.core.wifi

import android.os.Build
import io.github.project.vault.core.crypto.CryptoManager
import io.github.project.vault.core.root.RootShell
import io.github.project.vault.data.model.WifiArchiveMetadata
import io.github.project.vault.data.model.WifiNetwork
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object WifiEngine {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        isLenient = true
    }

    private val possibleConfigPaths = listOf(
        "/data/misc/apexdata/com.android.wifi/WifiConfigStore.xml",
        "/data/misc/wifi/WifiConfigStore.xml",
        "/data/misc/wifi/wpa_supplicant.conf"
    )

    suspend fun getActiveWifiConfigPath(): String? = withContext(Dispatchers.IO) {
        for (path in possibleConfigPaths) {
            val check = RootShell.execute("test -f $path && echo exists")
            if (check.stdout.contains("exists")) {
                return@withContext path
            }
        }
        null
    }

    suspend fun readSavedNetworks(): List<WifiNetwork> = withContext(Dispatchers.IO) {
        val configPath = getActiveWifiConfigPath() ?: return@withContext emptyList()
        val catResult = RootShell.execute("cat $configPath")
        if (!catResult.isSuccess || catResult.stdout.isBlank()) return@withContext emptyList()

        if (configPath.endsWith(".conf")) {
            return@withContext parseWpaSupplicant(catResult.stdout)
        } else {
            return@withContext parseWifiConfigStoreXml(catResult.stdout)
        }
    }

    fun parseWifiConfigStoreXml(xmlContent: String): List<WifiNetwork> {
        val networks = mutableListOf<WifiNetwork>()
        val networkRegex = Regex("""<Network>([\s\S]*?)<\/Network>""", RegexOption.MULTILINE)
        val configRegex = Regex("""<WifiConfiguration>([\s\S]*?)<\/WifiConfiguration>""", RegexOption.MULTILINE)

        val matches = networkRegex.findAll(xmlContent).map { it.groupValues[1] }
            .ifEmpty { configRegex.findAll(xmlContent).map { it.groupValues[1] } }

        for (block in matches) {
            val ssidMatch = Regex("""<string name="SSID">([^<]+)<\/string>""").find(block)
                ?: Regex("""<string name="ConfigKey">&quot;([^"&]+)&quot;""").find(block)
            val ssid = ssidMatch?.groupValues?.get(1) ?: continue

            val pskMatch = Regex("""<string name="PreSharedKey">([^<]+)<\/string>""").find(block)
            val psk = pskMatch?.groupValues?.get(1)?.takeIf { it != "&null;" && it != "null" }

            val wepMatch = Regex("""<string name="WEPKey\d?">([^<]+)<\/string>""").find(block)
            val wep = wepMatch?.groupValues?.get(1)?.takeIf { it != "&null;" && it != "null" }

            val hiddenMatch = Regex("""<boolean name="HiddenSSID" value="([^"]+)" \/>""").find(block)
            val isHidden = hiddenMatch?.groupValues?.get(1)?.toBooleanStrictOrNull() ?: false

            val keyMgmtMatch = Regex("""<byte-array name="AllowedKeyMgmt"[^>]*>([^<]*)<\/byte-array>""").find(block)
                ?: Regex("""<string name="ConfigKey">[^<]*?(WPA_PSK|WPA2_PSK|SAE|NONE|OWE)<\/string>""").find(block)
            val keyMgmt = keyMgmtMatch?.groupValues?.get(1) ?: "WPA_PSK"

            networks.add(
                WifiNetwork(
                    ssid = ssid,
                    preSharedKey = psk,
                    wepKey = wep,
                    keyMgmt = keyMgmt,
                    hiddenSSID = isHidden,
                    rawConfigXml = block.trim()
                )
            )
        }

        return networks.distinctBy { it.cleanSsid }
    }

    private fun parseWpaSupplicant(confContent: String): List<WifiNetwork> {
        val networks = mutableListOf<WifiNetwork>()
        val networkBlocks = Regex("""network=\{([\s\S]*?)\}""", RegexOption.MULTILINE).findAll(confContent)

        for (match in networkBlocks) {
            val body = match.groupValues[1]
            val ssid = Regex("""ssid="([^"]+)"""").find(body)?.groupValues?.get(1) ?: continue
            val psk = Regex("""psk="([^"]+)"""").find(body)?.groupValues?.get(1)
            val keyMgmt = Regex("""key_mgmt=([A-Z0-9_\-]+)""").find(body)?.groupValues?.get(1) ?: "WPA-PSK"
            val scanSsid = Regex("""scan_ssid=(\d+)""").find(body)?.groupValues?.get(1) == "1"

            networks.add(
                WifiNetwork(
                    ssid = ssid,
                    preSharedKey = psk,
                    keyMgmt = keyMgmt,
                    hiddenSSID = scanSsid
                )
            )
        }
        return networks.distinctBy { it.cleanSsid }
    }

    suspend fun createBackup(
        destinationDir: File,
        encrypt: Boolean = false,
        password: String? = null
    ): Result<File> = withContext(Dispatchers.IO) {
        try {
            val networks = readSavedNetworks()
            if (networks.isEmpty()) {
                return@withContext Result.failure(Exception("No saved Wi-Fi networks found on this device"))
            }

            val timestamp = System.currentTimeMillis()
            val dateFormatted = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault()).format(Date(timestamp))
            val baseFileName = "wifi_backup_${dateFormatted}.vault"
            val finalFile = File(destinationDir, baseFileName)
            val stagingDir = File(destinationDir, "stage_wifi_$timestamp")

            RootShell.execute("mkdir -p ${stagingDir.absolutePath} ${destinationDir.absolutePath}")
            RootShell.execute("chmod 777 ${destinationDir.absolutePath}")

            val metadata = WifiArchiveMetadata(
                backupTimestamp = timestamp,
                networkCount = networks.size,
                archiveFileName = baseFileName,
                deviceModel = Build.MODEL ?: "Android",
                isEncrypted = encrypt
            )

            val metaJson = json.encodeToString(metadata)
            val networksJson = json.encodeToString(networks)

            val metaFile = File(stagingDir, "metadata.json")
            val dataFile = File(stagingDir, "wifi_networks.json")
            metaFile.writeText(metaJson)
            dataFile.writeText(networksJson)

            val rawBundle = File(stagingDir, "bundle.tar")
            val tarResult = RootShell.execute("tar -cf ${rawBundle.absolutePath} -C ${stagingDir.absolutePath} metadata.json wifi_networks.json")
            if (!tarResult.isSuccess) {
                RootShell.execute("rm -rf ${stagingDir.absolutePath}")
                return@withContext Result.failure(Exception("Failed to package Wi-Fi archive: ${tarResult.stderr}"))
            }

            if (encrypt && !password.isNullOrBlank()) {
                CryptoManager.encryptFile(rawBundle, finalFile, password)
            } else {
                RootShell.execute("cp -f ${rawBundle.absolutePath} ${finalFile.absolutePath}")
            }

            RootShell.execute("chmod 666 ${finalFile.absolutePath}")
            RootShell.execute("rm -rf ${stagingDir.absolutePath}")
            RootShell.execute("am broadcast -a android.intent.action.MEDIA_SCANNER_SCAN_FILE -d \"file://${finalFile.absolutePath}\" 2>/dev/null")

            Result.success(finalFile)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun restoreBackup(
        archiveFile: File,
        password: String? = null
    ): Result<Int> = withContext(Dispatchers.IO) {
        val timestamp = System.currentTimeMillis()
        val stagingDir = File("/data/local/tmp/wifi_restore_$timestamp")
        RootShell.execute("mkdir -p ${stagingDir.absolutePath}")
        RootShell.execute("chmod 777 ${stagingDir.absolutePath}")

        try {
            val decryptedTar = if (!password.isNullOrBlank()) {
                val temp = File(stagingDir, "decrypted.tar")
                CryptoManager.decryptFile(archiveFile, temp, password)
                temp
            } else {
                archiveFile
            }

            val unpack = RootShell.execute("tar -xf ${decryptedTar.absolutePath} -C ${stagingDir.absolutePath}")
            if (!unpack.isSuccess) {
                return@withContext Result.failure(Exception("Failed to unpack Wi-Fi backup archive"))
            }

            val dataFile = File(stagingDir, "wifi_networks.json")
            if (!dataFile.exists()) {
                return@withContext Result.failure(Exception("wifi_networks.json missing from backup"))
            }

            val incomingNetworks = json.decodeFromString<List<WifiNetwork>>(dataFile.readText())
            if (incomingNetworks.isEmpty()) {
                return@withContext Result.success(0)
            }

            val targetConfigPath = getActiveWifiConfigPath()
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
            var restoredCount = 0

            for (net in incomingNetworks) {
                val cleanSsid = net.cleanSsid
                val escapedSsid = cleanSsid.replace("&", "&amp;").replace("\"", "&quot;")

                if (mergedXml.contains("\"$cleanSsid\"") || mergedXml.contains("&quot;$cleanSsid&quot;")) {
                    // Update password if present
                    if (!net.cleanPassword.isNullOrBlank()) {
                        val pskEscaped = net.cleanPassword!!.replace("&", "&amp;").replace("\"", "&quot;")
                        mergedXml = mergedXml.replace(
                            Regex("""(<string name="SSID">&quot;$cleanSsid&quot;<\/string>[\s\S]*?<string name="PreSharedKey">)([^<]*)(<\/string>)"""),
                            "$1&quot;$pskEscaped&quot;$3"
                        )
                        restoredCount++
                    }
                } else {
                    // Inject fresh network
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
                        restoredCount++
                    }
                }
            }

            // Write merged content to temporary file then copy as root
            val tempWriteFile = File(stagingDir, "WifiConfigStore.xml")
            tempWriteFile.writeText(mergedXml)

            RootShell.execute("cp -f ${tempWriteFile.absolutePath} $targetConfigPath")
            RootShell.execute("chmod 600 $targetConfigPath")
            RootShell.execute("chown system:system $targetConfigPath 2>/dev/null || chown wifi:wifi $targetConfigPath 2>/dev/null")
            RootShell.execute("restorecon $targetConfigPath 2>/dev/null")

            // Cycle WiFi service
            RootShell.execute("svc wifi disable && sleep 1 && svc wifi enable")

            Result.success(restoredCount)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            RootShell.execute("rm -rf ${stagingDir.absolutePath}")
        }
    }
}
