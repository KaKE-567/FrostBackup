package io.github.project.vault.core.session

import io.github.project.vault.core.root.RootShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.regex.Pattern

object SSAIDManager {
    private const val SSAID_XML_PATH = "/data/system/users/0/settings_ssaid.xml"
    private const val ABX2XML = "/system/bin/abx2xml"
    private const val XML2ABX = "/system/bin/xml2abx"

    suspend fun getSSAID(packageName: String): String? = withContext(Dispatchers.IO) {
        if (packageName.isBlank()) return@withContext null

        val isAbx = RootShell.execute("head -c 3 $SSAID_XML_PATH 2>/dev/null").stdout.startsWith("ABX")
        val xmlContent = if (isAbx) {
            val dumpRes = RootShell.execute("$ABX2XML $SSAID_XML_PATH - 2>/dev/null")
            if (dumpRes.isSuccess && dumpRes.stdout.isNotBlank()) {
                dumpRes.stdout
            } else {
                RootShell.execute("$ABX2XML $SSAID_XML_PATH /data/local/tmp/ssaid_read.xml 2>/dev/null && cat /data/local/tmp/ssaid_read.xml && rm -f /data/local/tmp/ssaid_read.xml").stdout
            }
        } else {
            RootShell.execute("cat $SSAID_XML_PATH 2>/dev/null").stdout
        }

        if (xmlContent.isBlank()) return@withContext null

        // In settings_ssaid.xml, the entry looks like:
        // <setting id="1" name="10294" value="1e92f793b08a9b66" package="com.android.vending" ... />
        val pattern = Pattern.compile("""<setting[^>]+package="$packageName"[^>]+value="([0-9a-fA-F]+)"""")
        val matcher = pattern.matcher(xmlContent)
        if (matcher.find()) {
            return@withContext matcher.group(1)
        }

        val altPattern = Pattern.compile("""<setting[^>]+value="([0-9a-fA-F]+)"[^>]+package="$packageName"""")
        val altMatcher = altPattern.matcher(xmlContent)
        if (altMatcher.find()) {
            return@withContext altMatcher.group(1)
        }

        null
    }

    suspend fun restoreSSAID(packageName: String, ssaid: String, newUid: Int = 0): Boolean = withContext(Dispatchers.IO) {
        if (ssaid.isBlank() || packageName.isBlank()) return@withContext false

        val isAbx = RootShell.execute("head -c 3 $SSAID_XML_PATH 2>/dev/null").stdout.startsWith("ABX")
        val xmlContent = if (isAbx) {
            val dumpRes = RootShell.execute("$ABX2XML $SSAID_XML_PATH - 2>/dev/null")
            if (dumpRes.isSuccess && dumpRes.stdout.isNotBlank()) {
                dumpRes.stdout
            } else {
                RootShell.execute("$ABX2XML $SSAID_XML_PATH /data/local/tmp/ssaid_temp.xml 2>/dev/null && cat /data/local/tmp/ssaid_temp.xml").stdout
            }
        } else {
            RootShell.execute("cat $SSAID_XML_PATH 2>/dev/null").stdout
        }

        if (xmlContent.isBlank()) return@withContext false

        val updatedXml: String
        val settingRegex = Regex("""<setting[^>]*package="$packageName"[^>]*/>""")
        if (settingRegex.containsMatchIn(xmlContent)) {
            // Update existing entry with new value and new UID name
            updatedXml = settingRegex.replace(xmlContent) { match ->
                var node = match.value
                node = node.replace(Regex("""value="[^"]*""""), """value="$ssaid"""")
                node = node.replace(Regex("""defaultValue="[^"]*""""), """defaultValue="$ssaid"""")
                if (newUid > 0) {
                    node = node.replace(Regex("""name="[^"]*""""), """name="$newUid"""")
                }
                node
            }
        } else {
            // Insert new entry before </settings>
            val idMatches = Regex("""id="(\d+)"""").findAll(xmlContent).mapNotNull { it.groupValues[1].toIntOrNull() }.toList()
            val nextId = (idMatches.maxOrNull() ?: 0) + 1
            val nameAttr = if (newUid > 0) "$newUid" else packageName
            val newEntry = """  <setting id="$nextId" name="$nameAttr" value="$ssaid" package="$packageName" defaultValue="$ssaid" defaultSysSet="false" tag="null" />"""
            updatedXml = xmlContent.replace("</settings>", "$newEntry\n</settings>")
        }

        // Write updated XML via temp file and root
        val tempFile = File.createTempFile("settings_ssaid", ".xml")
        try {
            tempFile.writeText(updatedXml)
            val writeCmd = if (isAbx) {
                "cp ${tempFile.absolutePath} /data/local/tmp/ssaid_in.xml && " +
                "$XML2ABX /data/local/tmp/ssaid_in.xml $SSAID_XML_PATH && " +
                "rm -f /data/local/tmp/ssaid_in.xml /data/local/tmp/ssaid_temp.xml && " +
                "chmod 600 $SSAID_XML_PATH && chown system:system $SSAID_XML_PATH && restorecon $SSAID_XML_PATH"
            } else {
                "cp ${tempFile.absolutePath} $SSAID_XML_PATH && chmod 600 $SSAID_XML_PATH && chown system:system $SSAID_XML_PATH && restorecon $SSAID_XML_PATH"
            }
            val copyRes = RootShell.execute(writeCmd)
            copyRes.isSuccess
        } finally {
            tempFile.delete()
        }
    }
}
