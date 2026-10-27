package io.github.project.vault.core.session

import io.github.project.vault.core.root.RootShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.regex.Pattern

object SSAIDManager {
    private const val SSAID_XML_PATH = "/data/system/users/0/settings_ssaid.xml"

    suspend fun getSSAID(packageName: String): String? = withContext(Dispatchers.IO) {
        if (packageName.isBlank()) return@withContext null
        // Android settings_ssaid.xml uses package="<pkg>" (or name="<pkg>")
        val cmd = "grep -F 'package=\"$packageName\"' $SSAID_XML_PATH 2>/dev/null || grep -F 'name=\"$packageName\"' $SSAID_XML_PATH 2>/dev/null"
        val result = RootShell.execute(cmd)
        if (!result.isSuccess || result.stdout.isBlank()) {
            return@withContext null
        }

        // Pattern matches: value="([0-9a-fA-F]+)"
        val pattern = Pattern.compile("value=\"([0-9a-fA-F]+)\"")
        val matcher = pattern.matcher(result.stdout)
        if (matcher.find()) {
            matcher.group(1)
        } else {
            null
        }
    }

    suspend fun restoreSSAID(packageName: String, ssaid: String, newUid: Int = 0): Boolean = withContext(Dispatchers.IO) {
        if (ssaid.isBlank() || packageName.isBlank()) return@withContext false

        val readRes = RootShell.execute("cat $SSAID_XML_PATH 2>/dev/null")
        if (!readRes.isSuccess || readRes.stdout.isBlank()) return@withContext false

        val xmlContent = readRes.stdout
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
            val copyRes = RootShell.execute(
                "cp ${tempFile.absolutePath} $SSAID_XML_PATH && chmod 600 $SSAID_XML_PATH && chown system:system $SSAID_XML_PATH && restorecon $SSAID_XML_PATH"
            )
            copyRes.isSuccess
        } finally {
            tempFile.delete()
        }
    }
}
