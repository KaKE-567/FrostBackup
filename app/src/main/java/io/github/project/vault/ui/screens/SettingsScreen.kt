package io.github.project.vault.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.SdStorage
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SettingsBrightness
import androidx.compose.material.icons.filled.Usb
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.project.vault.core.root.RootShell
import io.github.project.vault.core.storage.StorageLocationManager
import io.github.project.vault.data.model.StorageTarget
import io.github.project.vault.data.model.StorageType
import io.github.project.vault.data.repository.AppRepository
import io.github.project.vault.data.repository.BackupRepository
import io.github.project.vault.ui.components.StatusBadge
import io.github.project.vault.ui.theme.ThemeMode
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    backupRepository: BackupRepository,
    currentThemeMode: ThemeMode = ThemeMode.SYSTEM,
    onThemeChanged: (ThemeMode) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var rootInfo by remember { mutableStateOf("Checking…") }
    var selinuxMode by remember { mutableStateOf("Checking…") }
    var isRooted by remember { mutableStateOf(false) }
    var autoPromptReboot by remember { mutableStateOf(true) }

    var availableTargets by remember { mutableStateOf<List<StorageTarget>>(emptyList()) }
    var currentStoragePath by remember { mutableStateOf(backupRepository.getBackupDirectory().absolutePath) }
    var showCustomPathDialog by remember { mutableStateOf(false) }
    var customPathInput by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        val targets = StorageLocationManager.getAvailableStorageTargets(context)
        availableTargets = targets
        currentStoragePath = backupRepository.getBackupDirectory().absolutePath
        isRooted = RootShell.isRootAvailable()
        if (isRooted) {
            val idRes = RootShell.execute("id")
            rootInfo = idRes.stdout.ifBlank { "Root Available" }

            val selinuxRes = RootShell.execute("getenforce")
            selinuxMode = selinuxRes.stdout.ifBlank { "Unknown" }
        } else {
            rootInfo = "No root access granted"
            selinuxMode = "Unknown"
        }
    }

    val outlineColor = MaterialTheme.colorScheme.outline
    val frostedBorder = BorderStroke(1.dp, outlineColor.copy(alpha = 0.20f))
    val glassCardColors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        contentColor = MaterialTheme.colorScheme.onSurface
    )

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = MaterialTheme.colorScheme.onSurface
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.80f),
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.drawBehind {
                    drawLine(
                        color = outlineColor.copy(alpha = 0.15f),
                        start = Offset(0f, size.height),
                        end = Offset(size.width, size.height),
                        strokeWidth = 1.dp.toPx()
                    )
                }
            )
        },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Appearance & Theme Card (White Mode / Dark Mode / System)
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = glassCardColors,
                border = frostedBorder,
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Appearance & Theme",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    ThemeMode.values().forEach { mode ->
                        val isSelected = currentThemeMode == mode
                        val modeIcon = when (mode) {
                            ThemeMode.LIGHT -> Icons.Default.LightMode
                            ThemeMode.DARK -> Icons.Default.DarkMode
                            ThemeMode.SYSTEM -> Icons.Default.SettingsBrightness
                        }
                        val modeDescription = when (mode) {
                            ThemeMode.LIGHT -> "Clean crisp white mode with emerald highlights"
                            ThemeMode.DARK -> "Deep slate dark mode with mint highlights"
                            ThemeMode.SYSTEM -> "Match your Android system display setting"
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onThemeChanged(mode) }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { onThemeChanged(mode) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Icon(
                                imageVector = modeIcon,
                                contentDescription = null,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = mode.title,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                )
                                Text(
                                    text = modeDescription,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }

            // Root Status Card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = glassCardColors,
                border = frostedBorder,
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Security, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                        Text(
                            text = "Root & System Privileges",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        StatusBadge(
                            text = if (isRooted) "Granted" else "Denied",
                            containerColor = if (isRooted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                            contentColor = if (isRooted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Text(
                        text = "Identity: $rootInfo",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Text(
                        text = "SELinux Status: $selinuxMode",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Backup Storage Location Card (Internal / MicroSD / USB Pendrive / Custom)
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = glassCardColors,
                border = frostedBorder,
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.SdStorage,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Backup Storage Location",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Text(
                                text = "Select Internal storage, MicroSD card, or USB Pendrive",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    if (availableTargets.isEmpty()) {
                        Text(
                            text = currentStoragePath,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        availableTargets.forEach { target ->
                            val isSelected = currentStoragePath == target.backupDirectoryPath
                            val targetIcon = when (target.type) {
                                StorageType.INTERNAL -> Icons.Default.PhoneAndroid
                                StorageType.SD_CARD -> Icons.Default.SdCard
                                StorageType.USB_DRIVE -> Icons.Default.Usb
                                StorageType.CUSTOM -> Icons.Default.Folder
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(
                                        if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)
                                        else Color.Transparent
                                    )
                                    .clickable {
                                        currentStoragePath = target.backupDirectoryPath
                                        backupRepository.setBackupDirectory(target.backupDirectoryPath)
                                        AppRepository.invalidateCache()
                                    }
                                    .padding(horizontal = 8.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        currentStoragePath = target.backupDirectoryPath
                                        backupRepository.setBackupDirectory(target.backupDirectoryPath)
                                        AppRepository.invalidateCache()
                                    }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = targetIcon,
                                    contentDescription = null,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = target.name,
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                        )
                                    )
                                    Text(
                                        text = target.backupDirectoryPath,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    if (target.totalBytes > 0) {
                                        Text(
                                            text = "${target.formattedFreeSpace} free of ${target.formattedTotalSpace}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    OutlinedButton(
                        onClick = {
                            customPathInput = currentStoragePath
                            showCustomPathDialog = true
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Custom Storage Directory")
                    }
                }
            }

            // Restore Behaviors
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = glassCardColors,
                border = frostedBorder,
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Text(
                        text = "Restoration Preferences",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Prompt to Reboot after SSAID Restore",
                                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                            )
                            Text(
                                text = "Rebooting applies restored Android session IDs so apps stay logged in.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = autoPromptReboot,
                            onCheckedChange = { autoPromptReboot = it }
                        )
                    }
                }
            }

            // About Card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = glassCardColors,
                border = frostedBorder,
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                        Text(
                            text = "About FrostBackup",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Version 1.0.0 (FOSS Release)\n" +
                               "Preserves application login states using SSAID preservation and Keystore token migration.\n" +
                               "Licensed under GNU General Public License v3.0 (GPL-3.0).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showCustomPathDialog) {
        AlertDialog(
            onDismissRequest = { showCustomPathDialog = false },
            title = {
                Text(
                    text = "Custom Storage Directory",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )
            },
            text = {
                Column {
                    Text(
                        text = "Enter the directory path on your device, MicroSD card, or USB pendrive where backups will be stored:",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = customPathInput,
                        onValueChange = { customPathInput = it },
                        singleLine = true,
                        placeholder = { Text("/storage/xxxx-xxxx/FrostBackup/Backups") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = customPathInput.trim()
                        if (trimmed.isNotBlank()) {
                            currentStoragePath = trimmed
                            backupRepository.setBackupDirectory(trimmed)
                            AppRepository.invalidateCache()
                            scope.launch {
                                availableTargets = StorageLocationManager.getAvailableStorageTargets(context)
                            }
                        }
                        showCustomPathDialog = false
                    }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCustomPathDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
