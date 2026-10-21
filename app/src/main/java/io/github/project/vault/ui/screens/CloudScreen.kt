package io.github.project.vault.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.ui.unit.dp
import io.github.project.vault.core.cloud.CloudBackupItem
import io.github.project.vault.core.cloud.GoogleDriveManager
import io.github.project.vault.data.repository.BackupRepository
import io.github.project.vault.ui.components.StatusBadge
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudScreen(
    backupRepository: BackupRepository,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    val driveManager = remember { GoogleDriveManager(context) }

    var isConnected by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var cloudBackups by remember { mutableStateOf<List<CloudBackupItem>>(emptyList()) }
    var tokenInput by remember { mutableStateOf("") }
    var showTokenDialog by remember { mutableStateOf(false) }

    suspend fun refreshCloudState() {
        isLoading = true
        isConnected = driveManager.isConnected()
        if (isConnected) {
            cloudBackups = driveManager.listBackups()
        }
        isLoading = false
    }

    LaunchedEffect(Unit) {
        refreshCloudState()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Cloud Synchronization",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                    )
                },
                actions = {
                    if (isConnected) {
                        IconButton(onClick = { scope.launch { refreshCloudState() } }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
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
                .padding(16.dp)
        ) {
            // Cloud Status Card
            Card(
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isConnected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isConnected) Icons.Default.CloudDone else Icons.Default.CloudOff,
                            contentDescription = null,
                            tint = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.padding(horizontal = 6.dp))
                        Text(
                            text = "Google Drive (AppData)",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                        )
                        Spacer(modifier = Modifier.weight(1f))
                        StatusBadge(
                            text = if (isConnected) "Active" else "Disconnected",
                            containerColor = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            contentColor = MaterialTheme.colorScheme.surface
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = "Backups are stored inside a private, hidden AppData folder on your Google Drive. Only this app can access it, keeping your documents and drive files completely isolated.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    if (!isConnected) {
                        Button(
                            onClick = { showTokenDialog = true },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.CloudSync, contentDescription = null)
                            Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                            Text("Connect Google Drive Account")
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    driveManager.disconnect()
                                    refreshCloudState()
                                    snackbarHostState.showSnackbar("Disconnected from Google Drive")
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Disconnect Account")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Cloud Backups List
            if (isConnected) {
                Text(
                    text = "Cloud Backups (${cloudBackups.size})",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                )

                Spacer(modifier = Modifier.height(8.dp))

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else if (cloudBackups.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 40.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No backups stored in Google Drive yet.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(cloudBackups, key = { it.fileId }) { item ->
                            Card(
                                shape = RoundedCornerShape(14.dp),
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                elevation = CardDefaults.cardElevation(2.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.name,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold)
                                        )
                                        Text(
                                            text = String.format("%.1f MB", item.sizeBytes / (1024.0 * 1024.0)),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.outline
                                        )
                                    }

                                    // Download Button
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                val dest = File(backupRepository.getBackupDirectory(), item.name)
                                                snackbarHostState.showSnackbar("Downloading ${item.name}…")
                                                val ok = driveManager.downloadBackup(item.fileId, dest) {}
                                                if (ok) {
                                                    snackbarHostState.showSnackbar("Downloaded to local storage!")
                                                } else {
                                                    snackbarHostState.showSnackbar("Download failed")
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(Icons.Default.CloudDownload, contentDescription = "Download")
                                    }

                                    // Delete Button
                                    IconButton(
                                        onClick = {
                                            scope.launch {
                                                val ok = driveManager.deleteBackup(item.fileId)
                                                if (ok) {
                                                    refreshCloudState()
                                                    snackbarHostState.showSnackbar("Removed from Google Drive")
                                                }
                                            }
                                        }
                                    ) {
                                        Icon(
                                            Icons.Default.Delete,
                                            contentDescription = "Delete",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Connect Token Input Dialog
    if (showTokenDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showTokenDialog = false },
            title = { Text("Connect Google Drive") },
            text = {
                Column {
                    Text("Enter OAuth 2.0 Access Token or sign in via Google services:")
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = tokenInput,
                        onValueChange = { tokenInput = it },
                        label = { Text("Access Token") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (tokenInput.isNotBlank()) {
                            driveManager.setTokens(tokenInput.trim())
                            showTokenDialog = false
                            scope.launch { refreshCloudState() }
                        }
                    }
                ) {
                    Text("Save & Connect")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showTokenDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
