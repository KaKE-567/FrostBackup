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
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Sms
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material.icons.filled.Wifi
import io.github.project.vault.data.model.WifiNetwork
import io.github.project.vault.ui.components.WifiPasswordViewerDialog
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import io.github.project.vault.ui.components.PixelSquigglyProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.project.vault.data.model.TelephonyArchiveMetadata
import io.github.project.vault.data.model.TelephonyProgress
import io.github.project.vault.data.repository.TelephonyRepository
import io.github.project.vault.ui.components.StatusBadge
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TelephonyScreen(
    telephonyRepository: TelephonyRepository,
    isSelected: Boolean = false,
    modifier: Modifier = Modifier
) {
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    val backups by telephonyRepository.telephonyBackups.collectAsState()
    var smsCount by remember { mutableIntStateOf(0) }
    var callCount by remember { mutableIntStateOf(0) }
    var wifiCount by remember { mutableIntStateOf(0) }
    var isLoadingStats by remember { mutableStateOf(true) }

    var includeSms by remember { mutableStateOf(true) }
    var includeCalls by remember { mutableStateOf(true) }
    var includeWifi by remember { mutableStateOf(true) }

    var savedNetworks by remember { mutableStateOf<List<WifiNetwork>>(emptyList()) }
    var showWifiViewer by remember { mutableStateOf(false) }

    var activeProgress by remember { mutableStateOf<TelephonyProgress?>(null) }
    var targetRestoreArchive by remember { mutableStateOf<TelephonyArchiveMetadata?>(null) }
    var targetDeleteArchive by remember { mutableStateOf<TelephonyArchiveMetadata?>(null) }

    var cloudUploadActive by remember { mutableStateOf(false) }
    var cloudUploadStatus by remember { mutableStateOf("") }
    var cloudUploadProgress by remember { mutableFloatStateOf(0f) }

    suspend fun refreshData() {
        isLoadingStats = true
        val (sms, calls, wifi) = telephonyRepository.getDeviceStats()
        smsCount = sms
        callCount = calls
        wifiCount = wifi
        savedNetworks = telephonyRepository.getSavedWifiNetworks()
        telephonyRepository.refreshBackups()
        isLoadingStats = false
    }

    LaunchedEffect(isSelected) {
        if (isSelected && (smsCount == 0 && callCount == 0 && wifiCount == 0)) {
            kotlinx.coroutines.delay(350)
            refreshData()
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
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Device Records",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Calls, SMS & Saved Wi-Fi Passwords",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { scope.launch { refreshData() } }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
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
        modifier = modifier.fillMaxSize()
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(key = "telephony_hero") {
                Spacer(modifier = Modifier.height(4.dp))
                Card(
                    shape = RoundedCornerShape(24.dp),
                    colors = glassCardColors,
                    border = frostedBorder,
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Forum,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Device Communications",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = "Instant backup to SQLite & portable JSON",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            StatusBadge(
                                text = "Root Active",
                                containerColor = MaterialTheme.colorScheme.primaryContainer,
                                contentColor = MaterialTheme.colorScheme.primary
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Stats Tiles Row (SMS, Calls, Wi-Fi)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // SMS Tile
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                ),
                                border = frostedBorder,
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Sms,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "SMS",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (isLoadingStats) "…" else "$smsCount",
                                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Messages",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                    )
                                }
                            }

                            // Calls Tile
                            Card(
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                ),
                                border = frostedBorder,
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Call,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.secondary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Calls",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (isLoadingStats) "…" else "$callCount",
                                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Records",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                                    )
                                }
                            }

                            // Wi-Fi Tile
                            Card(
                                onClick = { showWifiViewer = true },
                                shape = RoundedCornerShape(16.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                                ),
                                border = frostedBorder,
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            imageVector = Icons.Default.Wifi,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.tertiary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "Wi-Fi",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = if (isLoadingStats) "…" else "$wifiCount",
                                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                    Text(
                                        text = "Saved Passwords",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Selection Checkboxes
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Checkbox(
                                    checked = includeSms,
                                    onCheckedChange = { includeSms = it }
                                )
                                Text(
                                    text = "SMS ($smsCount)",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Checkbox(
                                    checked = includeCalls,
                                    onCheckedChange = { includeCalls = it }
                                )
                                Text(
                                    text = "Calls ($callCount)",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.weight(1f)
                            ) {
                                Checkbox(
                                    checked = includeWifi,
                                    onCheckedChange = { includeWifi = it }
                                )
                                Text(
                                    text = "Wi-Fi ($wifiCount)",
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Action Buttons Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        telephonyRepository.backup(
                                            includeSms = includeSms,
                                            includeCalls = includeCalls,
                                            includeWifi = includeWifi,
                                            onProgress = { activeProgress = it }
                                        ).onSuccess {
                                            snackbarHostState.showSnackbar("Backup created successfully")
                                            refreshData()
                                        }.onFailure { err ->
                                            snackbarHostState.showSnackbar("Backup failed: ${err.message}")
                                        }
                                    }
                                },
                                enabled = includeSms || includeCalls || includeWifi,
                                shape = RoundedCornerShape(16.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                modifier = Modifier
                                    .weight(1.3f)
                                    .height(48.dp)
                            ) {
                                Icon(Icons.Default.Archive, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Backup Records",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }

                            OutlinedButton(
                                onClick = { showWifiViewer = true },
                                shape = RoundedCornerShape(16.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                Icon(Icons.Default.VpnKey, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Passwords",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
                                    maxLines = 1,
                                    softWrap = false,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            }

            item(key = "section_header") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.History,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Saved Archives (${backups.size})",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            if (backups.isEmpty()) {
                item(key = "empty_placeholder") {
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = glassCardColors,
                        border = frostedBorder,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(36.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Forum,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Text(
                                text = "No Telephony Backups Yet",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "Tap 'Backup Calls & SMS' above to preserve your SMS messages and call logs.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                items(backups, key = { it.archiveFileName }) { archive ->
                    TelephonyArchiveCard(
                        archive = archive,
                        onRestore = { targetRestoreArchive = archive },
                        onDelete = { targetDeleteArchive = archive },
                        onUpload = {
                            scope.launch {
                                cloudUploadStatus = "Connecting to Google Drive…"
                                cloudUploadProgress = 0f
                                cloudUploadActive = true
                                val ok = telephonyRepository.uploadToCloud(archive.archiveFileName) { uploadedMb, totalMb, percent, frac ->
                                    cloudUploadStatus = String.format(java.util.Locale.US, "Uploading: %.1f / %.1f MB (%d%%)", uploadedMb, totalMb, percent)
                                    cloudUploadProgress = frac
                                }
                                cloudUploadActive = false
                                if (ok) {
                                    snackbarHostState.showSnackbar("Uploaded to Google Drive successfully")
                                } else {
                                    snackbarHostState.showSnackbar("Cloud upload failed. Check Drive connection.")
                                }
                            }
                        }
                    )
                }
            }

            item(key = "bottom_spacer") {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    // Restore Confirmation Dialog
    targetRestoreArchive?.let { archive ->
        AlertDialog(
            onDismissRequest = { targetRestoreArchive = null },
            icon = { Icon(Icons.Default.Restore, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Restore Device Records") },
            text = {
                val summaryText = buildList {
                    if (archive.smsCount > 0) add("${archive.smsCount} SMS messages")
                    if (archive.callCount > 0) add("${archive.callCount} Call Logs")
                    if (archive.wifiCount > 0) add("${archive.wifiCount} Wi-Fi Networks")
                }.joinToString(", ").ifBlank { "records" }
                Text(
                    "This will restore $summaryText from archive '${archive.archiveFileName}'.\n\nExisting records and network configurations on your device will NOT be duplicated (safe merge)."
                )
            },
            confirmButton = {
                Button(onClick = {
                    val arch = archive
                    targetRestoreArchive = null
                    scope.launch {
                        telephonyRepository.restore(
                            archiveFileName = arch.archiveFileName,
                            onProgress = { activeProgress = it }
                        ).onSuccess { msg ->
                            snackbarHostState.showSnackbar(msg)
                            refreshData()
                        }.onFailure { err ->
                            snackbarHostState.showSnackbar("Restore error: ${err.message}")
                        }
                    }
                }) {
                    Text("Restore Now")
                }
            },
            dismissButton = {
                TextButton(onClick = { targetRestoreArchive = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showWifiViewer) {
        WifiPasswordViewerDialog(
            networks = savedNetworks,
            onDismiss = { showWifiViewer = false },
            onCopied = { ssid ->
                scope.launch {
                    snackbarHostState.showSnackbar("Password copied for $ssid")
                }
            }
        )
    }

    // Delete Confirmation Dialog
    targetDeleteArchive?.let { archive ->
        AlertDialog(
            onDismissRequest = { targetDeleteArchive = null },
            icon = { Icon(Icons.Default.Delete, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text("Delete Telephony Archive?") },
            text = {
                Text("Are you sure you want to delete '${archive.archiveFileName}'? This cannot be undone.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        val fileName = archive.archiveFileName
                        targetDeleteArchive = null
                        scope.launch {
                            val ok = telephonyRepository.deleteBackup(fileName)
                            if (ok) {
                                snackbarHostState.showSnackbar("Archive deleted")
                            } else {
                                snackbarHostState.showSnackbar("Failed to delete archive")
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Delete")
                }
            },
            dismissButton = {
                TextButton(onClick = { targetDeleteArchive = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Live Progress Dialog
    activeProgress?.let { prog ->
        when (prog) {
            is TelephonyProgress.BackingUp -> {
                AlertDialog(
                    onDismissRequest = {},
                    title = { Text("Creating Telephony Backup") },
                    text = {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            PixelSquigglyProgressIndicator(
                                progress = prog.current.toFloat() / prog.total.coerceAtLeast(1),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(text = prog.status, style = MaterialTheme.typography.bodyMedium)
                        }
                    },
                    confirmButton = {}
                )
            }
            is TelephonyProgress.Restoring -> {
                AlertDialog(
                    onDismissRequest = {},
                    title = { Text("Restoring Calls & SMS") },
                    text = {
                        Column(modifier = Modifier.fillMaxWidth()) {
                            PixelSquigglyProgressIndicator(
                                progress = prog.current.toFloat() / prog.total.coerceAtLeast(1),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            )
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(text = prog.status, style = MaterialTheme.typography.bodyMedium)
                        }
                    },
                    confirmButton = {}
                )
            }
            is TelephonyProgress.Completed -> {
                LaunchedEffect(Unit) {
                    activeProgress = null
                }
            }
            is TelephonyProgress.Failed -> {
                AlertDialog(
                    onDismissRequest = { activeProgress = null },
                    title = { Text("Operation Failed") },
                    text = { Text(prog.error) },
                    confirmButton = {
                        TextButton(onClick = { activeProgress = null }) {
                            Text("OK")
                        }
                    }
                )
            }
        }
    }

    if (cloudUploadActive) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Uploading to Google Drive") },
            text = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    PixelSquigglyProgressIndicator(
                        progress = cloudUploadProgress,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(text = cloudUploadStatus, style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = {}
        )
    }
}

@Composable
fun TelephonyArchiveCard(
    archive: TelephonyArchiveMetadata,
    onRestore: () -> Unit,
    onDelete: () -> Unit,
    onUpload: () -> Unit
) {
    val outlineColor = MaterialTheme.colorScheme.outline
    val frostedBorder = BorderStroke(1.dp, outlineColor.copy(alpha = 0.20f))
    val glassCardColors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        contentColor = MaterialTheme.colorScheme.onSurface
    )

    val formattedDate = remember(archive.backupTimestamp) {
        SimpleDateFormat("MMM dd, yyyy • hh:mm a", Locale.getDefault()).format(Date(archive.backupTimestamp))
    }

    val formattedSize = remember(archive.archiveSizeBytes) {
        val bytes = archive.archiveSizeBytes
        val kb = bytes / 1024.0
        val mb = kb / 1024.0
        if (mb >= 1.0) String.format(Locale.US, "%.1f MB", mb)
        else if (kb >= 1.0) String.format(Locale.US, "%.0f KB", kb)
        else "$bytes B"
    }

    Card(
        shape = RoundedCornerShape(20.dp),
        colors = glassCardColors,
        border = frostedBorder,
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Forum,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = formattedDate,
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${archive.archiveFileName} • $formattedSize",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Badges Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                StatusBadge(
                    text = "${archive.smsCount} SMS",
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                    contentColor = MaterialTheme.colorScheme.primary
                )
                StatusBadge(
                    text = "${archive.callCount} Calls",
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                    contentColor = MaterialTheme.colorScheme.secondary
                )
                if (archive.wifiCount > 0) {
                    StatusBadge(
                        text = "${archive.wifiCount} Wi-Fi",
                        containerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f),
                        contentColor = MaterialTheme.colorScheme.tertiary
                    )
                }
                if (archive.deviceModel.isNotBlank()) {
                    Text(
                        text = archive.deviceModel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onRestore,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Restore", style = MaterialTheme.typography.labelLarge)
                }

                IconButton(
                    onClick = onUpload,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudUpload,
                        contentDescription = "Upload to Google Drive",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.25f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete Archive",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}
