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
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.project.vault.core.backup.BackupEngine
import io.github.project.vault.core.backup.BackupProgress
import io.github.project.vault.core.backup.BackupResult
import io.github.project.vault.core.root.RootShell
import io.github.project.vault.data.model.AppInfo
import io.github.project.vault.data.model.BackupConfig
import io.github.project.vault.data.repository.AppRepository
import io.github.project.vault.data.repository.BackupRepository
import io.github.project.vault.ui.components.AppItemCard
import io.github.project.vault.ui.components.BackupProgressDialog
import io.github.project.vault.ui.components.StatusBadge
import kotlinx.coroutines.launch

enum class AppFilter { USER, ALL, BACKED_UP }

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
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(AppFilter.USER) }
    val selectedPackages = remember { mutableStateListOf<String>() }

    // Backup Sheet & Progress State
    var targetBackupApps by remember { mutableStateOf<List<AppInfo>?>(null) }
    var activeProgress by remember { mutableStateOf<BackupProgress?>(null) }

    suspend fun reloadApps() {
        isLoading = true
        isRooted = RootShell.isRootAvailable()
        val allInstalled = appRepository.getInstalledApps(includeSystem = true)
        apps = allInstalled
        isLoading = false
    }

    LaunchedEffect(Unit) {
        reloadApps()
    }

    val filteredApps = remember(apps, searchQuery, selectedFilter) {
        apps.filter { app ->
            val matchesFilter = when (selectedFilter) {
                AppFilter.USER -> !app.isSystemApp
                AppFilter.ALL -> true
                AppFilter.BACKED_UP -> app.hasBackup
            }
            val matchesSearch = searchQuery.isBlank() ||
                    app.appName.contains(searchQuery, ignoreCase = true) ||
                    app.packageName.contains(searchQuery, ignoreCase = true)

            matchesFilter && matchesSearch
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Applications",
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                        )
                        StatusBadge(
                            text = if (isRooted) "Root Granted" else "No Root",
                            containerColor = if (isRooted) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.errorContainer,
                            contentColor = if (isRooted) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { scope.launch { reloadApps() } }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh")
                    }
                }
            )
        },
        floatingActionButton = {
            if (selectedPackages.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    text = { Text("Backup (${selectedPackages.size})") },
                    icon = { Icon(Icons.Default.Archive, contentDescription = null) },
                    onClick = {
                        val selected = apps.filter { it.packageName in selectedPackages }
                        targetBackupApps = selected
                    }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search Input
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
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            )

            // Filter Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = selectedFilter == AppFilter.USER,
                    onClick = { selectedFilter = AppFilter.USER },
                    label = { Text("User Apps") },
                    leadingIcon = if (selectedFilter == AppFilter.USER) {
                        { Icon(Icons.Default.Check, contentDescription = null) }
                    } else null
                )

                FilterChip(
                    selected = selectedFilter == AppFilter.BACKED_UP,
                    onClick = { selectedFilter = AppFilter.BACKED_UP },
                    label = { Text("Backed Up") },
                    leadingIcon = if (selectedFilter == AppFilter.BACKED_UP) {
                        { Icon(Icons.Default.Check, contentDescription = null) }
                    } else null
                )

                FilterChip(
                    selected = selectedFilter == AppFilter.ALL,
                    onClick = { selectedFilter = AppFilter.ALL },
                    label = { Text("All") },
                    leadingIcon = if (selectedFilter == AppFilter.ALL) {
                        { Icon(Icons.Default.Check, contentDescription = null) }
                    } else null
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // App List
            if (isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (filteredApps.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No applications found",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
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

                    for ((index, app) in appsToBackup.withIndex()) {
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
