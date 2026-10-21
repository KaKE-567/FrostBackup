package io.github.project.vault.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.project.vault.core.cloud.CloudBackupItem
import io.github.project.vault.core.cloud.GoogleDriveManager
import io.github.project.vault.core.cloud.OAuthManager
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
    var showCustomClientDialog by remember { mutableStateOf(false) }
    var customClientIdInput by remember { mutableStateOf("") }

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
            // Google Account Status Card
            Card(
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isConnected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (isConnected) {
                            val initial = (driveManager.userEmail?.firstOrNull() ?: 'U').uppercaseChar()
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = initial.toString(),
                                    color = MaterialTheme.colorScheme.onPrimary,
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = driveManager.displayName ?: "Google Account",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                                Text(
                                    text = driveManager.userEmail ?: "Connected",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                                )
                            }
                        } else {
                            Icon(
                                imageVector = Icons.Default.CloudOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Google Drive (AppData)",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            )
                            Spacer(modifier = Modifier.weight(1f))
                        }

                        StatusBadge(
                            text = if (isConnected) "Connected" else "Not Signed In",
                            containerColor = if (isConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                            contentColor = MaterialTheme.colorScheme.surface
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "Universal Browser Authentication: Works smoothly on Vanilla AOSP ROMs without Google Play Services, LineageOS, GrapheneOS, or stock devices. Zero token copy-pasting required.",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isConnected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    if (!isConnected) {
                        // Universal In-Browser OAuth Button
                        Button(
                            onClick = {
                                OAuthManager.startBrowserAuth(context, customClientIdInput.ifBlank { null })
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp)
                        ) {
                            Icon(Icons.Default.OpenInBrowser, contentDescription = null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Sign in via Browser (Universal)",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Custom Client ID toggle for power users
                        TextButton(
                            onClick = { showCustomClientDialog = true },
                            modifier = Modifier.align(Alignment.CenterHorizontally)
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Configure Custom Client ID (Optional)", style = MaterialTheme.typography.labelMedium)
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    driveManager.disconnect()
                                    refreshCloudState()
                                    snackbarHostState.showSnackbar("Signed out of Google Drive")
                                }
                            },
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Sign Out")
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
                            text = "No backups stored in Google Drive yet.\nTap 'Backup' on any app and toggle 'Upload to Google Drive'.",
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

    // Custom Client ID dialog for advanced users
    if (showCustomClientDialog) {
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { showCustomClientDialog = false },
            title = { Text("Custom OAuth Client ID") },
            text = {
                Column {
                    Text("By default, FrostBackup uses its built-in open-source client ID. If you have your own Google Cloud Console client ID, paste it below:")
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = customClientIdInput,
                        onValueChange = { customClientIdInput = it },
                        label = { Text("Client ID (.apps.googleusercontent.com)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { showCustomClientDialog = false }
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showCustomClientDialog = false }) {
                    Text("Done")
                }
            }
        )
    }
}
