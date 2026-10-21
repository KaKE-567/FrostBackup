package io.github.project.vault.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import io.github.project.vault.data.model.AppInfo
import io.github.project.vault.data.model.BackupConfig

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupSheet(
    targetApps: List<AppInfo>,
    onDismissRequest: () -> Unit,
    onConfirmBackup: (BackupConfig) -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var includeApk by remember { mutableStateOf(true) }
    var includeInternalData by remember { mutableStateOf(true) }
    var includeExternalData by remember { mutableStateOf(false) }
    var includeSSAID by remember { mutableStateOf(true) }
    var includeKeystore by remember { mutableStateOf(true) }
    var encrypt by remember { mutableStateOf(false) }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var uploadToCloud by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 12.dp)
                .verticalScroll(rememberScrollState())
        ) {
            val title = if (targetApps.size == 1) {
                targetApps.first().appName
            } else {
                "Backup ${targetApps.size} Applications"
            }

            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
            )

            if (targetApps.size == 1) {
                Text(
                    text = targetApps.first().packageName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Options list
            BackupOptionRow(
                title = "Split APKs & Binaries",
                subtitle = "Include installer files for offline reinstallation",
                checked = includeApk,
                onCheckedChange = { includeApk = it }
            )

            BackupOptionRow(
                title = "Internal App Data (/data/data)",
                subtitle = "App databases, caches, preferences and files",
                checked = includeInternalData,
                onCheckedChange = { includeInternalData = it }
            )

            BackupOptionRow(
                title = "External Storage Data",
                subtitle = "Files inside /sdcard/Android/data",
                checked = includeExternalData,
                onCheckedChange = { includeExternalData = it }
            )

            BackupOptionRow(
                title = "Preserve SSAID (Keep Signed In)",
                subtitle = "Clones app device signature to avoid forced logouts",
                checked = includeSSAID,
                onCheckedChange = { includeSSAID = it },
                isHighlight = true
            )

            BackupOptionRow(
                title = "Preserve Keystore Tokens",
                subtitle = "Extracts cryptographic tokens from Keystore system",
                checked = includeKeystore,
                onCheckedChange = { includeKeystore = it }
            )

            BackupOptionRow(
                title = "Zero-Knowledge Encryption",
                subtitle = "Encrypt backup package with AES-256-GCM password",
                checked = encrypt,
                onCheckedChange = { encrypt = it }
            )

            if (encrypt) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Encryption Password") },
                    leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null) },
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = null
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            BackupOptionRow(
                title = "Upload to Google Drive",
                subtitle = "Store encrypted backup safely in your Google Cloud",
                checked = uploadToCloud,
                onCheckedChange = { uploadToCloud = it }
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismissRequest,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }

                Button(
                    onClick = {
                        onConfirmBackup(
                            BackupConfig(
                                includeApk = includeApk,
                                includeInternalData = includeInternalData,
                                includeExternalData = includeExternalData,
                                includeSSAID = includeSSAID,
                                includeKeystore = includeKeystore,
                                encrypt = encrypt,
                                encryptionPassword = if (encrypt) password else null,
                                uploadToCloud = uploadToCloud
                            )
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Start Backup")
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@Composable
private fun BackupOptionRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    isHighlight: Boolean = false
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = if (isHighlight) FontWeight.Bold else FontWeight.SemiBold
                ),
                color = if (isHighlight) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange
        )
    }
}
