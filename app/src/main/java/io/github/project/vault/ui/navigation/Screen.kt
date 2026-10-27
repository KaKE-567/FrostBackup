package io.github.project.vault.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.ui.graphics.vector.ImageVector

sealed class Screen(val route: String, val title: String, val icon: ImageVector) {
    data object Apps : Screen("apps", "Apps", Icons.Default.Apps)
    data object Telephony : Screen("telephony", "Records", Icons.Default.Forum)
    data object Restore : Screen("restore", "Restore", Icons.Default.Restore)
    data object Cloud : Screen("cloud", "Cloud", Icons.Default.CloudSync)
    data object Settings : Screen("settings", "Settings", Icons.Default.Settings)
}
