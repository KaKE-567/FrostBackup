package io.github.project.vault.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.project.vault.core.restore.RestoreEngine
import io.github.project.vault.core.restore.RestoreProgress
import io.github.project.vault.core.restore.RestoreResult
import io.github.project.vault.core.root.RootShell
import io.github.project.vault.data.model.BackupMetadata
import io.github.project.vault.data.repository.BackupRepository
import io.github.project.vault.ui.components.AppIcon
import io.github.project.vault.ui.components.BackupProgressDialog
import io.github.project.vault.ui.components.StatusBadge
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RestoreScreen(
    backupRepository: BackupRepository,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var isLoading by remember { mutableStateOf(true) }
    var backups by remember { mutableStateOf<List<BackupMetadata>>(emptyList()) }
    var activeProgress by remember { mutableStateOf<RestoreProgress?>(null) }

    // Dialog state for Password prompt & Reboot prompt
    var targetRestoreBackup by remember { mutableStateOf<BackupMetadata?>(null) }
    var passwordInput by remember { mutableStateOf("") }
    var showPasswordDialog by remember { mutableStateOf(false) }
    var showRebootDialog by remember { mutableStateOf(false) }

    suspend fun reloadBackups() {
        isLoading = true
        backups = backupRepository.getLocalBackups()
        isLoading = false
    }

    LaunchedEffect(Unit) {
        reloadBackups()
    }

    val totalSizeBytes = remember(backups) { backups.sumOf { it.archiveSizeBytes } }
    val totalSizeMbFormatted = remember(totalSizeBytes) {
        if (totalSizeBytes > 1024L * 1024L * 1024L) {
            String.format(Locale.US, "%.2f GB", totalSizeBytes / (1024.0 * 1024.0 * 1024.0))
        } else {
            String.format(Locale.US, "%.1f MB", totalSizeBytes / (1024.0 * 1024.0))
        }
    }

    val outlineColor = MaterialTheme.colorScheme.outline

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Restore & Archives",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        StatusBadge(
                            text = "${backups.size} Archives",
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                            contentColor = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
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
                },
                actions = {
                    IconButton(onClick = { scope.launch { reloadBackups() } }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 12.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
        ) {
            // Archives Summary Hero Card
            item(key = "restore_hero_card") {
                Card(
                    shape = RoundedCornerShape(22.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    border = BorderStroke(1.dp, outlineColor.copy(alpha = 0.20f)),
                    elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Archive,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(14.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Local Backup Vault",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "$totalSizeMbFormatted total on storage • /sdcard/FrostBackup",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (isLoading) {
                item(key = "loading_state") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
            } else if (backups.isEmpty()) {
                item(key = "empty_state") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No backup archives found.\nCreate one from the Applications tab.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                items(backups, key = { it.archiveFileName }) { item ->
                    BackupArchiveCard(
                        metadata = item,
                        onRestore = {
                            targetRestoreBackup = item
                            if (item.isEncrypted) {
                                passwordInput = ""
                                showPasswordDialog = true
                            } else {
                                scope.launch {
                                    executeRestore(context, item, null, backupRepository, { activeProgress = it }, {
                                        activeProgress = null
                                        if (it) showRebootDialog = true
                                    }, snackbarHostState)
                                }
                            }
                        },
                        onDelete = {
                            scope.launch {
                                backupRepository.deleteBackup(item.archiveFileName)
                                reloadBackups()
                                snackbarHostState.showSnackbar("Backup deleted")
                            }
                        },
                        onUploadToCloud = {
                            scope.launch {
                                snackbarHostState.showSnackbar("Uploading ${item.appName} to Google Drive…")
                                val ok = backupRepository.uploadToCloud(item.archiveFileName)
                                if (ok) {
                                    snackbarHostState.showSnackbar("Uploaded ${item.appName} to Google Drive!")
                                } else {
                                    snackbarHostState.showSnackbar("Cloud upload failed. Please verify Google Drive sign-in.")
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    // Password Prompt Dialog
    if (showPasswordDialog && targetRestoreBackup != null) {
        AlertDialog(
            onDismissRequest = { showPasswordDialog = false },
            title = { Text("Encrypted Backup") },
            text = {
                Column {
                    Text("Enter the encryption password used when this backup was created:")
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = passwordInput,
                        onValueChange = { passwordInput = it },
                        label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val item = targetRestoreBackup!!
                        val pass = passwordInput
                        showPasswordDialog = false
                        scope.launch {
                            executeRestore(context, item, pass, backupRepository, { activeProgress = it }, {
                                activeProgress = null
                                if (it) showRebootDialog = true
                            }, snackbarHostState)
                        }
                    }
                ) {
                    Text("Decrypt & Restore")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPasswordDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Reboot Recommended Dialog (For SSAID commit)
    if (showRebootDialog) {
        AlertDialog(
            onDismissRequest = { showRebootDialog = false },
            icon = { Icon(Icons.Default.RestartAlt, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Reboot Recommended") },
            text = {
                Text("App login sessions and SSAID signatures have been restored successfully. A quick reboot ensures the Android framework registers the restored device IDs immediately.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        showRebootDialog = false
                        scope.launch {
                            RootShell.execute("svc power reboot || reboot")
                        }
                    }
                ) {
                    Text("Reboot Now")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRebootDialog = false }) {
                    Text("Later")
                }
            }
        )
    }

    // Progress Dialog during restore
    activeProgress?.let { prog ->
        BackupProgressDialog(
            title = "Restoring Application",
            currentApp = prog.currentApp,
            currentStep = prog.currentStep,
            progressFraction = prog.progressFraction
        )
    }
}

private suspend fun executeRestore(
    context: android.content.Context,
    item: BackupMetadata,
    pass: String?,
    backupRepository: BackupRepository,
    onProgress: (RestoreProgress) -> Unit,
    onFinished: (Boolean) -> Unit,
    snackbarHostState: SnackbarHostState
) {
    val engine = RestoreEngine(context)
    val archiveFile = File(backupRepository.getBackupDirectory(), item.archiveFileName)

    onProgress(RestoreProgress("Preparing restore…", 0.05f, item.appName))
    val result = engine.performRestore(archiveFile, pass) { prog ->
        onProgress(prog)
    }

    when (result) {
        is RestoreResult.Success -> {
            snackbarHostState.showSnackbar("Restored ${item.appName} successfully!")
            onFinished(result.needsReboot)
        }
        is RestoreResult.Error -> {
            snackbarHostState.showSnackbar("Restore failed for ${item.appName}: ${result.message}")
            onFinished(false)
        }
    }
}

@Composable
fun BackupArchiveCard(
    metadata: BackupMetadata,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    onUploadToCloud: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault()) }
    val formattedDate = remember(metadata.backupTimestamp) {
        dateFormatter.format(Date(metadata.backupTimestamp))
    }
    val sizeMb = remember(metadata.archiveSizeBytes) {
        if (metadata.archiveSizeBytes > 1024L * 1024L * 1024L) {
            String.format(Locale.US, "%.2f GB", metadata.archiveSizeBytes / (1024.0 * 1024.0 * 1024.0))
        } else {
            String.format(Locale.US, "%.1f MB", metadata.archiveSizeBytes / (1024.0 * 1024.0))
        }
    }

    val outlineColor = MaterialTheme.colorScheme.outline
    val frostedBorder = BorderStroke(1.dp, outlineColor.copy(alpha = 0.20f))

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
            contentColor = MaterialTheme.colorScheme.onSurface
        ),
        border = frostedBorder,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // High-Res App Icon
                AppIcon(
                    packageName = metadata.packageName,
                    size = 46.dp
                )

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = metadata.appName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = metadata.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "$formattedDate • $sizeMb",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }

                if (metadata.isEncrypted) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.40f))
                            .padding(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Encrypted",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Component tags included in this backup archive
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (metadata.hasApk) ArchiveTag(icon = Icons.Default.Layers, text = "APK")
                if (metadata.hasData) ArchiveTag(icon = Icons.Default.Storage, text = "Data")
                if (metadata.hasKeystore) ArchiveTag(icon = Icons.Default.Key, text = "Keystore")
                if (metadata.hasExternalData) ArchiveTag(icon = Icons.Default.Folder, text = "External")
                if (metadata.ssaid != null) ArchiveTag(icon = Icons.Default.Lock, text = "Session")
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(onClick = onUploadToCloud) {
                        Icon(
                            imageVector = Icons.Default.CloudUpload,
                            contentDescription = "Upload to Cloud",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }

                    IconButton(onClick = onDelete) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Delete",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }

                Button(
                    onClick = onRestore,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.height(40.dp)
                ) {
                    Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Restore", fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
private fun ArchiveTag(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.50f))
            .padding(horizontal = 6.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(11.dp)
        )
        Spacer(modifier = Modifier.width(3.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
