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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RemoveDone
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.project.vault.core.backup.BackupEngine
import io.github.project.vault.core.backup.BackupProgress
import io.github.project.vault.core.backup.BackupResult
import io.github.project.vault.core.root.RootShell
import io.github.project.vault.data.model.AppInfo
import io.github.project.vault.data.repository.AppRepository
import io.github.project.vault.data.repository.BackupRepository
import io.github.project.vault.ui.components.AppItemCard
import io.github.project.vault.ui.components.BackupProgressDialog
import io.github.project.vault.ui.components.FloatingBackupBar
import io.github.project.vault.ui.components.StatusBadge
import io.github.project.vault.ui.components.StorageHeroCard
import kotlinx.coroutines.launch

enum class AppFilter { USER, BACKED_UP, SYSTEM, ALL }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppListScreen(
    appRepository: AppRepository,
    backupRepository: BackupRepository,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var isRooted by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var apps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }
    var backupCount by remember { mutableStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(AppFilter.USER) }
    val selectedPackages = remember { mutableStateListOf<String>() }

    // Backup Sheet & Progress State
    var targetBackupApps by remember { mutableStateOf<List<AppInfo>?>(null) }
    var activeProgress by remember { mutableStateOf<BackupProgress?>(null) }

    suspend fun reloadApps() {
        isLoading = apps.isEmpty()
        isRooted = RootShell.isRootAvailable()
        backupCount = runCatching { backupRepository.getLocalBackups().size }.getOrDefault(0)
        val fastList = appRepository.getInstalledAppsFast(includeSystem = true)
        apps = fastList
        isLoading = false
        val enriched = appRepository.enrichWithBackupStatus(fastList)
        apps = enriched
    }

    LaunchedEffect(Unit) {
        reloadApps()
    }

    val userAppsCount = remember(apps) { apps.count { !it.isSystemApp } }
    val backedUpCount = remember(apps) { apps.count { it.hasBackup } }
    val systemAppsCount = remember(apps) { apps.count { it.isSystemApp } }

    val filteredApps = remember(apps, searchQuery, selectedFilter) {
        apps.filter { app ->
            val matchesFilter = when (selectedFilter) {
                AppFilter.USER -> !app.isSystemApp
                AppFilter.BACKED_UP -> app.hasBackup
                AppFilter.SYSTEM -> app.isSystemApp
                AppFilter.ALL -> true
            }
            val matchesSearch = searchQuery.isBlank() ||
                    app.appName.contains(searchQuery, ignoreCase = true) ||
                    app.packageName.contains(searchQuery, ignoreCase = true)

            matchesFilter && matchesSearch
        }
    }

    val outlineColor = MaterialTheme.colorScheme.outline

    Box(modifier = modifier.fillMaxSize()) {
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
                                text = "Applications",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            StatusBadge(
                                text = if (isRooted) "Superuser" else "No Root",
                                containerColor = if (isRooted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
                                contentColor = if (isRooted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
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
                        // Multi-select / Deselect toggle
                        if (filteredApps.isNotEmpty()) {
                            val allVisibleSelected = filteredApps.all { it.packageName in selectedPackages }
                            IconButton(onClick = {
                                if (allVisibleSelected) {
                                    filteredApps.forEach { selectedPackages.remove(it.packageName) }
                                } else {
                                    filteredApps.forEach {
                                        if (it.packageName !in selectedPackages) selectedPackages.add(it.packageName)
                                    }
                                }
                            }) {
                                Icon(
                                    imageVector = if (allVisibleSelected) Icons.Default.RemoveDone else Icons.Default.DoneAll,
                                    contentDescription = if (allVisibleSelected) "Deselect All" else "Select All"
                                )
                            }
                        }

                        IconButton(onClick = { scope.launch { reloadApps() } }) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                        }
                    }
                )
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = innerPadding.calculateTopPadding() + 12.dp,
                    bottom = innerPadding.calculateBottomPadding() + 90.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                // Frost Hero Storage & Keystore Card
                item(key = "storage_hero_header") {
                    StorageHeroCard(
                        userAppCount = userAppsCount,
                        backupCount = backupCount,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }

                // Frosted Search Box
                item(key = "search_box") {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        placeholder = { Text("Search apps or package name…") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (searchQuery.isNotEmpty()) {
                                IconButton(onClick = { searchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear")
                                }
                            }
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                            unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.70f),
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.25f)
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Filter Carousel Row
                item(key = "filter_chips") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = selectedFilter == AppFilter.USER,
                            onClick = { selectedFilter = AppFilter.USER },
                            label = { Text("User ($userAppsCount)") },
                            leadingIcon = if (selectedFilter == AppFilter.USER) {
                                { Icon(Icons.Default.Check, contentDescription = null) }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            )
                        )

                        FilterChip(
                            selected = selectedFilter == AppFilter.BACKED_UP,
                            onClick = { selectedFilter = AppFilter.BACKED_UP },
                            label = { Text("Backed ($backedUpCount)") },
                            leadingIcon = if (selectedFilter == AppFilter.BACKED_UP) {
                                { Icon(Icons.Default.Check, contentDescription = null) }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            )
                        )

                        FilterChip(
                            selected = selectedFilter == AppFilter.SYSTEM,
                            onClick = { selectedFilter = AppFilter.SYSTEM },
                            label = { Text("System ($systemAppsCount)") },
                            leadingIcon = if (selectedFilter == AppFilter.SYSTEM) {
                                { Icon(Icons.Default.Check, contentDescription = null) }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            )
                        )

                        FilterChip(
                            selected = selectedFilter == AppFilter.ALL,
                            onClick = { selectedFilter = AppFilter.ALL },
                            label = { Text("All (${apps.size})") },
                            leadingIcon = if (selectedFilter == AppFilter.ALL) {
                                { Icon(Icons.Default.Check, contentDescription = null) }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            )
                        )
                    }
                }

                // Loading State or Empty State or App List
                if (isLoading) {
                    item(key = "loading_indicator") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                } else if (filteredApps.isEmpty()) {
                    item(key = "empty_indicator") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No applications match your filter",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    items(filteredApps, key = { it.packageName }) { app ->
                        AppItemCard(
                            app = app,
                            isSelected = app.packageName in selectedPackages,
                            onSelectionChanged = { checked ->
                                if (checked) {
                                    selectedPackages.add(app.packageName)
                                } else {
                                    selectedPackages.remove(app.packageName)
                                }
                            },
                            onBackupClicked = {
                                targetBackupApps = listOf(app)
                            }
                        )
                    }
                }
            }
        }

        // Floating Frosted Bottom Capsule Bar
        FloatingBackupBar(
            selectedCount = selectedPackages.size,
            onClearSelection = { selectedPackages.clear() },
            onBackupClicked = {
                val selected = apps.filter { it.packageName in selectedPackages }
                targetBackupApps = selected
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
        )
    }

    // Modal Sheet for Backup Options
    targetBackupApps?.let { appsToBackup ->
        BackupSheet(
            targetApps = appsToBackup,
            onDismissRequest = { targetBackupApps = null },
            onConfirmBackup = { config ->
                targetBackupApps = null
                scope.launch {
                    val engine = BackupEngine(context)
                    val destination = backupRepository.getBackupDirectory()

                    for (app in appsToBackup) {
                        activeProgress = BackupProgress("Initializing…", 0.05f, app.appName)
                        val result = engine.performBackup(app, config, destination) { prog ->
                            activeProgress = prog
                        }

                        when (result) {
                            is BackupResult.Success -> {
                                snackbarHostState.showSnackbar("Backed up ${app.appName}")
                            }
                            is BackupResult.Error -> {
                                snackbarHostState.showSnackbar("Error on ${app.appName}: ${result.message}")
                            }
                        }
                    }
                    activeProgress = null
                    selectedPackages.clear()
                    reloadApps()
                }
            }
        )
    }

    // Progress Dialog during active backup
    activeProgress?.let { prog ->
        BackupProgressDialog(
            title = "Backing Up Application",
            currentApp = prog.currentApp,
            currentStep = prog.currentStep,
            progressFraction = prog.progressFraction
        )
    }
}
