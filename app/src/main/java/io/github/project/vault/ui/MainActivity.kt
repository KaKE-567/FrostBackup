package io.github.project.vault.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import io.github.project.vault.core.cloud.OAuthManager
import io.github.project.vault.core.updater.UpdateChecker
import io.github.project.vault.core.updater.UpdateInfo
import io.github.project.vault.data.repository.AppRepository
import io.github.project.vault.data.repository.BackupRepository
import io.github.project.vault.data.repository.TelephonyRepository
import io.github.project.vault.ui.components.FrostedBackground
import io.github.project.vault.ui.components.UpdateDialog
import io.github.project.vault.ui.navigation.Screen
import io.github.project.vault.ui.screens.AppListScreen
import io.github.project.vault.ui.screens.CloudScreen
import io.github.project.vault.ui.screens.RestoreScreen
import io.github.project.vault.ui.screens.RootGateScreen
import io.github.project.vault.ui.screens.SettingsScreen
import io.github.project.vault.ui.screens.TelephonyScreen
import io.github.project.vault.ui.theme.AppVaultTheme
import io.github.project.vault.ui.theme.ThemeMode
import io.github.project.vault.ui.theme.ThemePreference
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalFoundationApi::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleOAuthIntent(intent)

        setContent {
            var currentThemeMode by remember {
                mutableStateOf(ThemePreference.getThemeMode(applicationContext))
            }
            var currentKeyColor by remember {
                mutableStateOf(ThemePreference.getKeyColor(applicationContext))
            }
            var availableUpdate by remember { mutableStateOf<UpdateInfo?>(null) }

            LaunchedEffect(Unit) {
                val info = UpdateChecker.checkForUpdate(applicationContext)
                if (info != null && info.hasUpdate) {
                    availableUpdate = info
                }
            }

            AppVaultTheme(themeMode = currentThemeMode, keyColor = currentKeyColor) {
                FrostedBackground {
                    if (availableUpdate != null) {
                        UpdateDialog(
                            updateInfo = availableUpdate!!,
                            onDismiss = { availableUpdate = null }
                        )
                    }
                    var isRootGranted by remember { mutableStateOf<Boolean?>(null) }

                    AnimatedContent(
                        targetState = (isRootGranted == true),
                        transitionSpec = {
                            (fadeIn(animationSpec = tween(450, delayMillis = 60, easing = FastOutSlowInEasing)) +
                             scaleIn(initialScale = 0.94f, animationSpec = tween(450, delayMillis = 60, easing = FastOutSlowInEasing)) +
                             slideInVertically(initialOffsetY = { it / 25 }, animationSpec = tween(450, delayMillis = 60, easing = FastOutSlowInEasing)))
                                .togetherWith(
                                    fadeOut(animationSpec = tween(350, easing = FastOutSlowInEasing)) +
                                    scaleOut(targetScale = 1.04f, animationSpec = tween(350, easing = FastOutSlowInEasing))
                                )
                        },
                        label = "RootGateTransition",
                        modifier = Modifier.fillMaxSize()
                    ) { hasRoot ->
                        if (!hasRoot) {
                            RootGateScreen(
                                onRootGranted = {
                                    isRootGranted = true
                                }
                            )
                        } else {
                            val pagerScope = rememberCoroutineScope()
                            val backupRepository = remember { BackupRepository(applicationContext) }
                            val appRepository = remember { AppRepository(applicationContext, backupRepository) }
                            val telephonyRepository = remember { TelephonyRepository(applicationContext, backupRepository) }

                            val screens = listOf(
                                Screen.Apps,
                                Screen.Telephony,
                                Screen.Restore,
                                Screen.Cloud,
                                Screen.Settings
                            )
                            var selectedTab by remember { mutableIntStateOf(0) }
                            val pagerState = rememberPagerState(initialPage = 0, pageCount = { screens.size })

                            LaunchedEffect(pagerState.currentPage) {
                                if (pagerState.currentPage != selectedTab) {
                                    selectedTab = pagerState.currentPage
                                }
                            }

                            BackHandler(enabled = selectedTab != 0) {
                                selectedTab = 0
                                pagerScope.launch {
                                    pagerState.animateScrollToPage(0)
                                }
                            }

                            Scaffold(
                                containerColor = Color.Transparent,
                                contentColor = MaterialTheme.colorScheme.onBackground,
                                bottomBar = {
                                    Surface(
                                        modifier = Modifier
                                            .navigationBarsPadding()
                                            .padding(horizontal = 14.dp, vertical = 10.dp)
                                            .height(64.dp)
                                            .fillMaxWidth()
                                            .clip(CircleShape),
                                        color = MaterialTheme.colorScheme.surfaceContainer,
                                        tonalElevation = 0.dp
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .padding(horizontal = 6.dp),
                                            horizontalArrangement = Arrangement.SpaceEvenly,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            screens.forEachIndexed { index, screen ->
                                                val isSelected = selectedTab == index

                                                val iconScale by animateFloatAsState(
                                                    targetValue = if (isSelected) 1.12f else 1f,
                                                    animationSpec = spring(
                                                        dampingRatio = 0.76f,
                                                        stiffness = Spring.StiffnessMediumLow
                                                    ),
                                                    label = "tabIconScale"
                                                )

                                                val tabWeight by animateFloatAsState(
                                                    targetValue = if (isSelected) 1.85f else 1.0f,
                                                    animationSpec = spring(
                                                        dampingRatio = 0.82f,
                                                        stiffness = Spring.StiffnessMediumLow
                                                    ),
                                                    label = "tabWeight"
                                                )

                                                Box(
                                                    modifier = Modifier
                                                        .weight(tabWeight)
                                                        .fillMaxHeight()
                                                        .clickable(
                                                            interactionSource = remember { MutableInteractionSource() },
                                                            indication = null
                                                        ) {
                                                            selectedTab = index
                                                            if (pagerState.currentPage != index) {
                                                                pagerScope.launch {
                                                                    if (kotlin.math.abs(pagerState.currentPage - index) > 1) {
                                                                        val jumpTo = if (index > pagerState.currentPage) index - 1 else index + 1
                                                                        pagerState.scrollToPage(jumpTo)
                                                                    }
                                                                    pagerState.animateScrollToPage(
                                                                        page = index,
                                                                        animationSpec = tween(
                                                                            durationMillis = 280,
                                                                            easing = FastOutSlowInEasing
                                                                        )
                                                                    )
                                                                }
                                                            }
                                                        },
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Box(
                                                        modifier = Modifier
                                                            .graphicsLayer {
                                                                scaleX = iconScale
                                                                scaleY = iconScale
                                                            }
                                                            .height(42.dp)
                                                            .fillMaxWidth(if (isSelected) 0.98f else 0.65f)
                                                            .background(
                                                                color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
                                                                shape = CircleShape
                                                            ),
                                                        contentAlignment = Alignment.Center
                                                    ) {
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.Center,
                                                            modifier = Modifier.padding(horizontal = if (isSelected) 10.dp else 0.dp)
                                                        ) {
                                                            Icon(
                                                                imageVector = screen.icon,
                                                                contentDescription = screen.title,
                                                                tint = if (isSelected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
                                                                modifier = Modifier.size(22.dp)
                                                            )
                                                            AnimatedVisibility(
                                                                visible = isSelected,
                                                                enter = fadeIn(animationSpec = tween(140, delayMillis = 30)) + expandHorizontally(
                                                                    animationSpec = spring(
                                                                        dampingRatio = 0.85f,
                                                                        stiffness = Spring.StiffnessMediumLow
                                                                    ),
                                                                    expandFrom = Alignment.Start
                                                                ),
                                                                exit = fadeOut(animationSpec = tween(80)) + shrinkHorizontally(
                                                                    animationSpec = spring(
                                                                        dampingRatio = 0.85f,
                                                                        stiffness = Spring.StiffnessMediumLow
                                                                    ),
                                                                    shrinkTowards = Alignment.Start
                                                                )
                                                            ) {
                                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                                    Spacer(modifier = Modifier.width(5.dp))
                                                                    Text(
                                                                        text = screen.title,
                                                                        style = MaterialTheme.typography.labelMedium,
                                                                        fontWeight = FontWeight.SemiBold,
                                                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                                                        maxLines = 1,
                                                                        softWrap = false
                                                                    )
                                                                }
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxSize()
                            ) { innerPadding ->
                                HorizontalPager(
                                    state = pagerState,
                                    beyondBoundsPageCount = 1,
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(innerPadding)
                                        .graphicsLayer { clip = true }
                                ) { page ->
                                    when (page) {
                                        0 -> AppListScreen(
                                            appRepository = appRepository,
                                            backupRepository = backupRepository,
                                            isSelected = (selectedTab == 0)
                                        )
                                        1 -> TelephonyScreen(
                                            telephonyRepository = telephonyRepository,
                                            isSelected = (selectedTab == 1)
                                        )
                                        2 -> RestoreScreen(
                                            backupRepository = backupRepository,
                                            isSelected = (selectedTab == 2)
                                        )
                                        3 -> CloudScreen(
                                            backupRepository = backupRepository,
                                            isSelected = (selectedTab == 3)
                                        )
                                        4 -> SettingsScreen(
                                            backupRepository = backupRepository,
                                            isSelected = (selectedTab == 4),
                                            currentThemeMode = currentThemeMode,
                                            currentKeyColor = currentKeyColor,
                                            onThemeChanged = { newMode ->
                                                currentThemeMode = newMode
                                                ThemePreference.setThemeMode(applicationContext, newMode)
                                            },
                                            onKeyColorChanged = { newColor ->
                                                currentKeyColor = newColor
                                                ThemePreference.setKeyColor(applicationContext, newColor)
                                            }
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOAuthIntent(intent)
    }

    private fun handleOAuthIntent(intent: Intent?) {
        val uri = intent?.data ?: return
        if (uri.scheme == "io.github.project.vault" && uri.host == "oauth2redirect") {
            val code = uri.getQueryParameter("code")
            if (!code.isNullOrBlank()) {
                lifecycleScope.launch {
                    val success = OAuthManager.handleAuthCode(applicationContext, code)
                    if (success) {
                        Toast.makeText(applicationContext, "Successfully connected to Google Drive!", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(applicationContext, "Google authentication failed", Toast.LENGTH_SHORT).show()
                    }
                }
            }
        }
    }
}
