package io.github.project.vault.core.session

import io.github.project.vault.core.root.RootShell
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.regex.Pattern

object SSAIDManager {
    private const val SSAID_XML_PATH = "/data/system/users/0/settings_ssaid.xml"

    suspend fun getSSAID(packageName: String): String? = withContext(Dispatchers.IO) {
        val cmd = "grep -F 'name=\"$packageName\"' $SSAID_XML_PATH"
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

    suspend fun restoreSSAID(packageName: String, ssaid: String): Boolean = withContext(Dispatchers.IO) {
        if (ssaid.isBlank() || packageName.isBlank()) return@withContext false

        // Check if package already exists in settings_ssaid.xml
        val checkCmd = "grep -F 'name=\"$packageName\"' $SSAID_XML_PATH"
        val checkResult = RootShell.execute(checkCmd)

        val success = if (checkResult.isSuccess && checkResult.stdout.isNotBlank()) {
            // Update existing entry using sed
            val sedCmd = "sed -i -E 's/(name=\"$packageName\"[^>]*value=\")[^\"]*(\")/\\1$ssaid\\2/' $SSAID_XML_PATH"
            RootShell.execute(sedCmd).isSuccess
        } else {
            // Append new setting node right before </settings>
            val newEntry = "<setting id=\"0\" name=\"$packageName\" value=\"$ssaid\" package=\"$packageName\" defaultValue=\"$ssaid\" defaultSysSet=\"true\" />"
            val insertCmd = "sed -i 's|<\\/settings>|  $newEntry\\n<\\/settings>|' $SSAID_XML_PATH"
            RootShell.execute(insertCmd).isSuccess
        }

        if (success) {
            // Fix file permissions on settings_ssaid.xml
            RootShell.execute("chmod 600 $SSAID_XML_PATH && chown system:system $SSAID_XML_PATH")
        }
        success
    }
}
