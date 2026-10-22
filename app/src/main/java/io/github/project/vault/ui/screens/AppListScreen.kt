package io.github.project.vault.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.RemoveDone
import androidx.compose.material.icons.filled.SdCard
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.project.vault.R
import io.github.project.vault.core.backup.BackupEngine
import io.github.project.vault.core.backup.BackupProgress
import io.github.project.vault.core.backup.BackupResult
import io.github.project.vault.core.root.RootShell
import io.github.project.vault.core.storage.StorageLocationManager
import io.github.project.vault.data.model.AppInfo
import io.github.project.vault.data.model.StorageTarget
import io.github.project.vault.data.repository.AppRepository
import io.github.project.vault.data.repository.BackupRepository
import io.github.project.vault.ui.components.AppItemCard
import io.github.project.vault.ui.components.BackupProgressDialog
import io.github.project.vault.ui.components.FloatingBackupBar
import io.github.project.vault.ui.components.StatusBadge
import io.github.project.vault.ui.components.StorageHeroCard
import io.github.project.vault.ui.components.StorageSelectorSheet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class AppSortOption(val label: String) {
    NAME_ASC("Name (A–Z)"),
    NAME_DESC("Name (Z–A)"),
    SIZE_DESC("App Size (Largest)"),
    RECENTLY_UPDATED("Recently Updated"),
    RECENTLY_INSTALLED("Recently Installed")
}

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

    var isRooted by remember { mutableStateOf(RootShell.isRootAvailableSync() ?: true) }
    var rawApps by remember { mutableStateOf(AppRepository.getCachedApps() ?: emptyList()) }
    var isLoading by remember { mutableStateOf(rawApps.isEmpty()) }

    val backups by backupRepository.backups.collectAsState()
    val backupCount = backups.size

    val backupMap = remember(backups) {
        backups.associateBy { it.packageName.trim().lowercase() }
    }

    val apps = remember(rawApps, backupMap, backups) {
        rawApps.map { app ->
            val pkgKey = app.packageName.trim().lowercase()
            val backup = backupMap[pkgKey] ?: backups.find { b ->
                val cleanBackupSlug = b.archiveFileName.substringBeforeLast("_").lowercase().replace("-", "_")
                val cleanAppSlug = app.appName.trim().lowercase().replace("\\s+".toRegex(), "_").replace("[^a-z0-9_-]".toRegex(), "")
                cleanBackupSlug.isNotEmpty() && (cleanBackupSlug == cleanAppSlug || b.packageName.equals(app.packageName, ignoreCase = true))
            }

            if (backup != null) {
                app.copy(hasBackup = true, lastBackupTimestamp = backup.backupTimestamp)
            } else {
                app.copy(hasBackup = false, lastBackupTimestamp = null)
            }
        }
    }

    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(AppFilter.USER) }
    val selectedPackages = remember { mutableStateListOf<String>() }

    // Sorting and Menu State
    var sortOption by remember { mutableStateOf(AppSortOption.NAME_ASC) }
    var showSortMenu by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }

    // Storage Selector Sheet State
    var showStorageSheet by remember { mutableStateOf(false) }
    var availableTargets by remember { mutableStateOf<List<StorageTarget>>(emptyList()) }
    var currentStorageDirectory by remember { mutableStateOf(backupRepository.getBackupDirectory()) }

    // Backup Sheet & Progress State
    var targetBackupApps by remember { mutableStateOf<List<AppInfo>?>(null) }
    var activeProgress by remember { mutableStateOf<BackupProgress?>(null) }

    suspend fun reloadApps(forceRefresh: Boolean = false) {
        if (rawApps.isEmpty()) {
            isLoading = true
        }
        currentStorageDirectory = backupRepository.getBackupDirectory()
        backupRepository.refreshBackups(forceRefresh = forceRefresh)
        val fastList = appRepository.getInstalledAppsFast(includeSystem = true, forceRefresh = forceRefresh)
        rawApps = fastList
        isLoading = false
    }

    LaunchedEffect(Unit) {
        reloadApps(forceRefresh = true)
        withContext(Dispatchers.IO) {
            val targets = StorageLocationManager.getAvailableStorageTargets(context)
            val currentDir = backupRepository.getBackupDirectory()
            withContext(Dispatchers.Main) {
                availableTargets = targets
                currentStorageDirectory = currentDir
            }
        }
    }

    val userAppsCount = remember(apps) { apps.count { !it.isSystemApp } }
    val backedUpCount = remember(apps) { apps.count { it.hasBackup } }
    val systemAppsCount = remember(apps) { apps.count { it.isSystemApp } }

    val currentTarget = remember(availableTargets, currentStorageDirectory) {
        availableTargets.find { it.backupDirectoryPath == currentStorageDirectory.absolutePath }
    }

    val filteredApps = remember(apps, searchQuery, selectedFilter, sortOption) {
        val list = apps.filter { app ->
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
        when (sortOption) {
            AppSortOption.NAME_ASC -> list.sortedBy { it.appName.lowercase() }
            AppSortOption.NAME_DESC -> list.sortedByDescending { it.appName.lowercase() }
            AppSortOption.SIZE_DESC -> list.sortedByDescending { it.appSizeBytes }
            AppSortOption.RECENTLY_UPDATED -> list.sortedByDescending { it.lastUpdateTime }
            AppSortOption.RECENTLY_INSTALLED -> list.sortedByDescending { it.firstInstallTime }
        }
    }

    val outlineColor = MaterialTheme.colorScheme.outline

    Box(modifier = modifier.fillMaxSize()) {
        Scaffold(
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            topBar = {
                if (selectedPackages.isNotEmpty()) {
                    TopAppBar(
                        navigationIcon = {
                            IconButton(onClick = { selectedPackages.clear() }) {
                                Icon(Icons.Default.Close, contentDescription = "Clear Selection")
                            }
                        },
                        title = {
                            Text(
                                text = "${selectedPackages.size} Selected",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.95f),
                            titleContentColor = MaterialTheme.colorScheme.onSurface,
                            actionIconContentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.drawBehind {
                            drawLine(
                                color = outlineColor.copy(alpha = 0.20f),
                                start = Offset(0f, size.height),
                                end = Offset(size.width, size.height),
                                strokeWidth = 1.dp.toPx()
                            )
                        },
                        actions = {
                            val allVisibleSelected = filteredApps.isNotEmpty() && filteredApps.all { it.packageName in selectedPackages }
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
                            Button(
                                onClick = {
                                    val selected = apps.filter { it.packageName in selectedPackages }
                                    targetBackupApps = selected
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primary,
                                    contentColor = MaterialTheme.colorScheme.onPrimary
                                ),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                modifier = Modifier.padding(end = 8.dp)
                            ) {
                                Icon(Icons.Default.Archive, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Backup", style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold))
                            }
                        }
                    )
                } else {
                    TopAppBar(
                        title = {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_frost_brand_logo),
                                        contentDescription = "FrostBackup Logo",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                                Column(verticalArrangement = Arrangement.Center) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Text(
                                            text = "Applications",
                                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                            color = MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        StatusBadge(
                                            text = if (isRooted) "Superuser" else "No Root",
                                            containerColor = if (isRooted) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.45f),
                                            contentColor = if (isRooted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                        )
                                    }
                                    val subtitle = remember(userAppsCount, backedUpCount, filteredApps.size, selectedFilter, apps.size) {
                                        when (selectedFilter) {
                                            AppFilter.USER -> "$userAppsCount User Apps • $backedUpCount Backed Up"
                                            AppFilter.BACKED_UP -> "$backedUpCount Backed Up Apps"
                                            AppFilter.SYSTEM -> "$systemAppsCount System Apps"
                                            AppFilter.ALL -> "${apps.size} Total Applications"
                                        }
                                    }
                                    Text(
                                        text = subtitle,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
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
                            // Sort Dropdown Button
                            Box {
                                IconButton(onClick = { showSortMenu = true }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.Sort,
                                        contentDescription = "Sort Applications"
                                    )
                                }
                                DropdownMenu(
                                    expanded = showSortMenu,
                                    onDismissRequest = { showSortMenu = false }
                                ) {
                                    AppSortOption.values().forEach { option ->
                                        DropdownMenuItem(
                                            text = {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    if (sortOption == option) {
                                                        Icon(
                                                            imageVector = Icons.Default.Check,
                                                            contentDescription = null,
                                                            tint = MaterialTheme.colorScheme.primary,
                                                            modifier = Modifier.size(16.dp)
                                                        )
                                                    } else {
                                                        Spacer(modifier = Modifier.size(16.dp))
                                                    }
                                                    Text(option.label)
                                                }
                                            },
                                            onClick = {
                                                sortOption = option
                                                showSortMenu = false
                                            }
                                        )
                                    }
                                }
                            }

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

                            // Overflow Menu
                            Box {
                                IconButton(onClick = { showMoreMenu = true }) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "More Options"
                                    )
                                }
                                DropdownMenu(
                                    expanded = showMoreMenu,
                                    onDismissRequest = { showMoreMenu = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text("Storage Destination") },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.SdCard,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        },
                                        onClick = {
                                            showMoreMenu = false
                                            scope.launch {
                                                withContext(Dispatchers.IO) {
                                                    val targets = StorageLocationManager.getAvailableStorageTargets(context)
                                                    val currentDir = backupRepository.getBackupDirectory()
                                                    withContext(Dispatchers.Main) {
                                                        availableTargets = targets
                                                        currentStorageDirectory = currentDir
                                                        showStorageSheet = true
                                                    }
                                                }
                                            }
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Select Unbacked Apps") },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.DoneAll,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        },
                                        onClick = {
                                            showMoreMenu = false
                                            filteredApps.filter { !it.hasBackup }.forEach {
                                                if (it.packageName !in selectedPackages) selectedPackages.add(it.packageName)
                                            }
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Refresh Applications") },
                                        leadingIcon = {
                                            Icon(
                                                imageVector = Icons.Default.Refresh,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        },
                                        onClick = {
                                            showMoreMenu = false
                                            scope.launch { reloadApps(forceRefresh = true) }
                                        }
                                    )
                                }
                            }
                        }
                    )
                }
            },
            snackbarHost = { SnackbarHost(snackbarHostState) },
            modifier = Modifier.fillMaxSize()
        ) { innerPadding ->
            LazyColumn(
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 12.dp,
                    bottom = innerPadding.calculateBottomPadding() + 90.dp
                ),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = innerPadding.calculateTopPadding())
            ) {
                // Frost Hero Storage & Keystore Card
                item(key = "storage_hero_header") {
                    StorageHeroCard(
                        userAppCount = userAppsCount,
                        backupCount = backupCount,
                        activeTarget = currentTarget,
                        storageDirectory = currentStorageDirectory,
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

                // Native Material 3 Filter Chips Row
                item(key = "filter_chips") {
                    val chipScrollState = rememberScrollState()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(chipScrollState),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val filters = listOf(
                            Triple(AppFilter.USER, "User", userAppsCount),
                            Triple(AppFilter.BACKED_UP, "Backed", backedUpCount),
                            Triple(AppFilter.SYSTEM, "System", systemAppsCount),
                            Triple(AppFilter.ALL, "All", apps.size)
                        )

                        filters.forEach { (filter, title, count) ->
                            val isSelected = selectedFilter == filter
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedFilter = filter },
                                label = {
                                    Text(
                                        text = "$title ($count)",
                                        style = MaterialTheme.typography.labelMedium.copy(
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                        )
                                    )
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.85f),
                                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                                    labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = isSelected,
                                    borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.20f),
                                    selectedBorderColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.60f),
                                    borderWidth = 1.dp,
                                    selectedBorderWidth = 1.dp
                                )
                            )
                        }
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
                    BackupRepository.invalidateCache()
                    AppRepository.invalidateCache()
                    scope.launch {
                        backupRepository.refreshBackups(forceRefresh = true)
                        reloadApps(forceRefresh = true)
                    }
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

    // Storage Location Selector Sheet
    if (showStorageSheet) {
        StorageSelectorSheet(
            availableTargets = availableTargets,
            currentStoragePath = currentStorageDirectory.absolutePath,
            onSelectTarget = { target ->
                backupRepository.setBackupDirectory(target.backupDirectoryPath)
                currentStorageDirectory = File(target.backupDirectoryPath)
                AppRepository.invalidateCache()
                scope.launch {
                    reloadApps(forceRefresh = true)
                }
                showStorageSheet = false
            },
            onCustomPathSelected = { customPath ->
                backupRepository.setBackupDirectory(customPath)
                currentStorageDirectory = File(customPath)
                AppRepository.invalidateCache()
                scope.launch {
                    reloadApps(forceRefresh = true)
                }
                showStorageSheet = false
            },
            onDismissRequest = { showStorageSheet = false }
        )
    }
}
