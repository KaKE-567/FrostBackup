package io.github.project.vault.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import io.github.project.vault.core.restore.RestoreEngine
import io.github.project.vault.core.restore.RestoreProgress
import io.github.project.vault.core.restore.RestoreResult
import io.github.project.vault.core.root.RootShell
import io.github.project.vault.data.model.BackupMetadata
import io.github.project.vault.data.repository.BackupRepository
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Available Backups",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (backups.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No backup archives found.\nCreate one in the Apps tab.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(backups, key = { it.archiveFileName }) { item ->
                        BackupItemCard(
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
                            }
                        )
                    }
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
    metadata: BackupMetadata,
    password: String?,
    repository: BackupRepository,
    onProgress: (RestoreProgress) -> Unit,
    onComplete: (Boolean) -> Unit,
    snackbar: SnackbarHostState
) {
    val engine = RestoreEngine(context)
    val archiveFile = File(repository.getBackupDirectory(), metadata.archiveFileName)

    val result = engine.performRestore(archiveFile, password) { prog ->
        onProgress(prog)
    }

    when (result) {
        is RestoreResult.Success -> {
            snackbar.showSnackbar("Restored ${metadata.appName}")
            onComplete(result.needsReboot)
        }
        is RestoreResult.Error -> {
            snackbar.showSnackbar("Restore error: ${result.message}")
            onComplete(false)
        }
    }
}

@Composable
private fun BackupItemCard(
    metadata: BackupMetadata,
    onRestore: () -> Unit,
    onDelete: () -> Unit
) {
    val dateFormatter = remember { SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault()) }
    val formattedDate = remember(metadata.backupTimestamp) {
        dateFormatter.format(Date(metadata.backupTimestamp))
    }
    val sizeMb = remember(metadata.archiveSizeBytes) {
        String.format("%.1f MB", metadata.archiveSizeBytes / (1024.0 * 1024.0))
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = metadata.appName,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                    Text(
                        text = metadata.packageName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (metadata.isEncrypted) {
                    StatusBadge(
                        text = "Encrypted",
                        containerColor = MaterialTheme.colorScheme.secondaryContainer,
                        contentColor = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$formattedDate • $sizeMb",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )

                if (metadata.ssaid != null) {
                    StatusBadge(
                        text = "Signed-In Session",
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                        contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = MaterialTheme.colorScheme.error
                    )
                }

                Button(
                    onClick = onRestore,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Restore, contentDescription = null)
                    Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                    Text("Restore")
                }
            }
        }
    }
}
