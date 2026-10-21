package io.github.project.vault.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import io.github.project.vault.data.repository.AppRepository
import io.github.project.vault.data.repository.BackupRepository
import io.github.project.vault.ui.navigation.Screen
import io.github.project.vault.ui.screens.AppListScreen
import io.github.project.vault.ui.screens.CloudScreen
import io.github.project.vault.ui.screens.RestoreScreen
import io.github.project.vault.ui.screens.SettingsScreen
import io.github.project.vault.ui.theme.AppVaultTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AppVaultTheme {
                val navController = rememberNavController()
                val backupRepository = remember { BackupRepository(applicationContext) }
                val appRepository = remember { AppRepository(applicationContext, backupRepository) }

                val screens = listOf(
                    Screen.Apps,
                    Screen.Restore,
                    Screen.Cloud,
                    Screen.Settings
                )

                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = navBackStackEntry?.destination?.route

                Scaffold(
                    bottomBar = {
                        NavigationBar {
                            screens.forEach { screen ->
                                val selected = currentRoute == screen.route
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = {
                                        if (currentRoute != screen.route) {
                                            navController.navigate(screen.route) {
                                                popUpTo(navController.graph.findStartDestination().id) {
                                                    saveState = true
                                                }
                                                launchSingleTop = true
                                                restoreState = true
                                            }
                                        }
                                    },
                                    icon = { Icon(screen.icon, contentDescription = screen.title) },
                                    label = { Text(screen.title) }
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                ) { innerPadding ->
                    NavHost(
                        navController = navController,
                        startDestination = Screen.Apps.route,
                        modifier = Modifier.padding(innerPadding)
                    ) {
                        composable(Screen.Apps.route) {
                            AppListScreen(appRepository = appRepository, backupRepository = backupRepository)
                        }
                        composable(Screen.Restore.route) {
                            RestoreScreen(backupRepository = backupRepository)
                        }
                        composable(Screen.Cloud.route) {
                            CloudScreen(backupRepository = backupRepository)
                        }
                        composable(Screen.Settings.route) {
                            SettingsScreen(backupRepository = backupRepository)
                        }
                    }
                }
            }
        }
    }
}
