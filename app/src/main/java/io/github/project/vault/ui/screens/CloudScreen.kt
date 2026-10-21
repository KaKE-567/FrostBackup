package io.github.project.vault.ui.screens

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
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
import io.github.project.vault.ui.components.AppIcon
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

    var isConnected by remember { mutableStateOf(GoogleDriveManager.cachedIsConnected ?: driveManager.hasCachedConnection()) }
    var cloudBackups by remember { mutableStateOf(GoogleDriveManager.cachedBackups ?: emptyList()) }
    var isLoading by remember { mutableStateOf(isConnected && cloudBackups.isEmpty()) }

    suspend fun refreshCloudState(forceRefresh: Boolean = false) {
        val connected = driveManager.isConnected()
        isConnected = connected
        GoogleDriveManager.cachedIsConnected = connected

        if (connected) {
            if (GoogleDriveManager.cachedBackups == null || forceRefresh) {
                if (cloudBackups.isEmpty()) isLoading = true
                val backups = driveManager.listBackups()
                cloudBackups = backups
                GoogleDriveManager.cachedBackups = backups
                isLoading = false
            } else {
                cloudBackups = GoogleDriveManager.cachedBackups!!
                isLoading = false
            }
        } else {
            cloudBackups = emptyList()
            GoogleDriveManager.cachedBackups = null
            isLoading = false
        }
    }

    LaunchedEffect(Unit) {
        if (cloudBackups.isEmpty()) {
            refreshCloudState(forceRefresh = false)
        }
    }

    val outlineColor = MaterialTheme.colorScheme.outline

    Scaffold(
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Cloud Synchronization",
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
                },
                actions = {
                    if (isConnected) {
                        IconButton(onClick = { scope.launch { refreshCloudState(forceRefresh = true) } }) {
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
                    containerColor = if (isConnected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.85f)
                ),
                border = BorderStroke(1.dp, outlineColor.copy(alpha = 0.20f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
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
                        text = "Encrypted backups are stored inside your private Google Drive AppData folder. Operates completely in-browser without requiring Google Play Services or token copy-pasting.",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isConnected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.9f) else MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    if (!isConnected) {
                        // 1-Tap Browser Sign In
                        Button(
                            onClick = {
                                OAuthManager.startBrowserAuth(context) { success, msg ->
                                    scope.launch {
                                        refreshCloudState(forceRefresh = true)
                                        snackbarHostState.showSnackbar(msg ?: "Connected")
                                    }
                                }
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
                                text = "Sign in with Google",
                                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Bold)
                            )
                        }
                    } else {
                        OutlinedButton(
                            onClick = {
                                scope.launch {
                                    driveManager.disconnect()
                                    refreshCloudState(forceRefresh = true)
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
                                shape = RoundedCornerShape(20.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                                    contentColor = MaterialTheme.colorScheme.onSurface
                                ),
                                border = BorderStroke(1.dp, outlineColor.copy(alpha = 0.20f)),
                                elevation = CardDefaults.cardElevation(1.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    val inferredPkg = item.name.substringBefore("_")
                                    if (inferredPkg.contains(".")) {
                                        AppIcon(packageName = inferredPkg, size = 42.dp)
                                        Spacer(modifier = Modifier.width(12.dp))
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = item.name,
                                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = String.format("%.1f MB", item.sizeBytes / (1024.0 * 1024.0)),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                                    refreshCloudState(forceRefresh = true)
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
}
