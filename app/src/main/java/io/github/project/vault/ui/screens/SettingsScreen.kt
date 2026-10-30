package io.github.project.vault.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Refresh
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
import androidx.compose.material3.IconButton
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
import io.github.project.vault.ui.components.StorageSelectorSheet
import io.github.project.vault.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    backupRepository: BackupRepository,
    isSelected: Boolean = true,
    currentThemeMode: ThemeMode = ThemeMode.SYSTEM,
    currentKeyColor: Int = 0,
    onThemeChanged: (ThemeMode) -> Unit = {},
    onKeyColorChanged: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showThemeScreen by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var rootInfo by remember { mutableStateOf("Checking…") }
    var selinuxMode by remember { mutableStateOf("Checking…") }
    var isRooted by remember { mutableStateOf(false) }
    var autoPromptReboot by remember { mutableStateOf(true) }

    var availableTargets by remember { mutableStateOf<List<StorageTarget>>(emptyList()) }
    var currentStoragePath by remember { mutableStateOf(backupRepository.getBackupDirectory().absolutePath) }
    var showStorageSheet by remember { mutableStateOf(false) }

    LaunchedEffect(isSelected) {
        if (isSelected && (rootInfo == "Checking…" || availableTargets.isEmpty())) {
            withContext(Dispatchers.IO) {
                val targets = StorageLocationManager.getAvailableStorageTargets(context)
                val currentPath = backupRepository.getBackupDirectory().absolutePath
                val rootAvailable = RootShell.isRootAvailable()
                val idOutput = if (rootAvailable) {
                    RootShell.execute("id").stdout.ifBlank { "Root Available" }
                } else {
                    "No root access granted"
                }
                val selinuxOutput = if (rootAvailable) {
                    RootShell.execute("getenforce").stdout.ifBlank { "Unknown" }
                } else {
                    "Unknown"
                }

                withContext(Dispatchers.Main) {
                    availableTargets = targets
                    currentStoragePath = currentPath
                    isRooted = rootAvailable
                    rootInfo = idOutput
                    selinuxMode = selinuxOutput
                }
            }
        }
    }

    val outlineColor = MaterialTheme.colorScheme.outline
    val frostedBorder = BorderStroke(1.dp, outlineColor.copy(alpha = 0.20f))
    val glassCardColors = CardDefaults.cardColors(
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
        contentColor = MaterialTheme.colorScheme.onSurface
    )

    AnimatedContent(
        targetState = showThemeScreen,
        transitionSpec = {
            if (targetState) {
                // Opening Theme: Slide in from right (forward) + fade in
                (slideInHorizontally(
                    initialOffsetX = { fullWidth -> fullWidth },
                    animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(220, easing = FastOutSlowInEasing)))
                    .togetherWith(
                        slideOutHorizontally(
                            targetOffsetX = { fullWidth -> -fullWidth / 4 },
                            animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
                        ) + fadeOut(animationSpec = tween(180, easing = FastOutSlowInEasing))
                    )
            } else {
                // Closing Theme: Slide out to right (backward) + fade out, Settings slides back in from left
                (slideInHorizontally(
                    initialOffsetX = { fullWidth -> -fullWidth / 4 },
                    animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
                ) + fadeIn(animationSpec = tween(220, easing = FastOutSlowInEasing)))
                    .togetherWith(
                        slideOutHorizontally(
                            targetOffsetX = { fullWidth -> fullWidth },
                            animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing)
                        ) + fadeOut(animationSpec = tween(180, easing = FastOutSlowInEasing))
                    )
            }
        },
        label = "ThemeScreenTransition",
        modifier = modifier.fillMaxSize()
    ) { isThemeOpen ->
        if (isThemeOpen) {
            ThemeScreen(
                currentThemeMode = currentThemeMode,
                currentKeyColor = currentKeyColor,
                onThemeModeChanged = onThemeChanged,
                onKeyColorChanged = onKeyColorChanged,
                onBack = { showThemeScreen = false }
            )
        } else {
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
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Appearance & Theme Card (Custom Palettes, Monet, AMOLED, & Live Preview)
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = glassCardColors,
                        border = frostedBorder,
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { showThemeScreen = true }
                    ) {
                        Column(modifier = Modifier.padding(18.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.50f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Palette,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(22.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(14.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Appearance & Theme",
                                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                    )
                                    val accentLabel = if (currentKeyColor == 0) "Dynamic Monet" else "Custom Palette"
                                    Text(
                                        text = "${currentThemeMode.title} • $accentLabel",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(onClick = { showThemeScreen = true }) {
                                    Icon(
                                        imageVector = Icons.Default.ChevronRight,
                                        contentDescription = "Open Theme Customizer",
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(14.dp))

                            // Mini Palette Preview Dots Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val previewColors = listOf(
                                    MaterialTheme.colorScheme.primary,
                                    Color(0xFF006C50),
                                    Color(0xFF00BCD4),
                                    Color(0xFF2196F3),
                                    Color(0xFF9C27B0),
                                    Color(0xFFE91E63),
                                    Color(0xFFFF9800)
                                )
                                previewColors.forEach { dotColor ->
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(dotColor)
                                    )
                                }
                                Spacer(modifier = Modifier.weight(1f))
                                Text(
                                    text = "Customize",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = MaterialTheme.colorScheme.primary
                                )
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

                    // Backup Storage Location Card
                    Card(
                        shape = RoundedCornerShape(20.dp),
                        colors = glassCardColors,
                        border = frostedBorder,
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(20.dp))
                            .clickable { showStorageSheet = true }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(18.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SdCard,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Backup Storage Location",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = currentStoragePath,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            IconButton(onClick = { showStorageSheet = true }) {
                                Icon(Icons.Default.Edit, contentDescription = "Change Storage Location")
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

                            val appVersionName = remember {
                                try {
                                    context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "1.0.4"
                                } catch (_: Exception) {
                                    "1.0.4"
                                }
                            }

                            Text(
                                text = "Version $appVersionName (FOSS Release)\n" +
                                       "Preserves application login states using SSAID preservation and Keystore token migration.\n" +
                                       "Licensed under GNU General Public License v3.0 (GPL-3.0).",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            var checkingUpdate by remember { mutableStateOf(false) }
                            var updateFound by remember { mutableStateOf<io.github.project.vault.core.updater.UpdateInfo?>(null) }

                            if (updateFound != null) {
                                io.github.project.vault.ui.components.UpdateDialog(
                                    updateInfo = updateFound!!,
                                    onDismiss = { updateFound = null }
                                )
                            }

                            OutlinedButton(
                                onClick = {
                                    checkingUpdate = true
                                    scope.launch {
                                        val info = io.github.project.vault.core.updater.UpdateChecker.checkForUpdate(context)
                                        checkingUpdate = false
                                        if (info != null && info.hasUpdate) {
                                            updateFound = info
                                        } else {
                                            android.widget.Toast.makeText(context, "You are on the latest version!", android.widget.Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                },
                                enabled = !checkingUpdate,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                if (checkingUpdate) {
                                    Text("Checking GitHub for updates...")
                                } else {
                                    Icon(
                                        imageVector = Icons.Default.Refresh,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Check for Updates")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showStorageSheet) {
        StorageSelectorSheet(
            availableTargets = availableTargets,
            currentStoragePath = currentStoragePath,
            onSelectTarget = { target ->
                currentStoragePath = target.backupDirectoryPath
                backupRepository.setBackupDirectory(target.backupDirectoryPath)
                AppRepository.invalidateCache()
                showStorageSheet = false
            },
            onCustomPathSelected = { path ->
                currentStoragePath = path
                backupRepository.setBackupDirectory(path)
                AppRepository.invalidateCache()
                scope.launch {
                    availableTargets = StorageLocationManager.getAvailableStorageTargets(context)
                }
                showStorageSheet = false
            },
            onDismissRequest = { showStorageSheet = false }
        )
    }
}
