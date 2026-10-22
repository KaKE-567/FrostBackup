package io.github.project.vault.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowInsetsControllerCompat

@Composable
fun ColorScheme.animateAsState(): ColorScheme {
    val animSpec = tween<Color>(durationMillis = 220, easing = FastOutSlowInEasing)

    @Composable
    fun anim(color: Color, label: String): Color = animateColorAsState(
        targetValue = color,
        animationSpec = animSpec,
        label = label
    ).value

    return copy(
        primary = anim(primary, "primary"),
        onPrimary = anim(onPrimary, "onPrimary"),
        primaryContainer = anim(primaryContainer, "primaryContainer"),
        onPrimaryContainer = anim(onPrimaryContainer, "onPrimaryContainer"),
        secondary = anim(secondary, "secondary"),
        secondaryContainer = anim(secondaryContainer, "secondaryContainer"),
        background = anim(background, "background"),
        onBackground = anim(onBackground, "onBackground"),
        surface = anim(surface, "surface"),
        onSurface = anim(onSurface, "onSurface"),
        surfaceVariant = anim(surfaceVariant, "surfaceVariant"),
        onSurfaceVariant = anim(onSurfaceVariant, "onSurfaceVariant"),
        outline = anim(outline, "outline")
    )
}

@Composable
fun rememberAppVaultColorScheme(
    seedColor: Color,
    isDark: Boolean,
    isAmoled: Boolean
): ColorScheme {
    val context = LocalContext.current
    return remember(seedColor, isDark, isAmoled) {
        if (seedColor == Color.Unspecified) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val dynamic = if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                dynamic.amoledBackground(isAmoled)
            } else {
                generateDynamicColorScheme(Color(0xFF006C50), isDark = isDark, isAmoled = isAmoled)
            }
        } else {
            generateDynamicColorScheme(seedColor, isDark = isDark, isAmoled = isAmoled)
        }
    }
}

@Composable
fun AppVaultTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    keyColor: Int = 0,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val darkTheme = when (themeMode) {
        ThemeMode.LIGHT -> false
        ThemeMode.DARK -> true
        ThemeMode.AMOLED -> true
        ThemeMode.SYSTEM -> systemDark
    }
    val amoled = (themeMode == ThemeMode.AMOLED)

    val seed = if (keyColor == 0) Color.Unspecified else Color(keyColor)
    val colorScheme = rememberAppVaultColorScheme(
        seedColor = seed,
        isDark = darkTheme,
        isAmoled = amoled
    ).animateAsState()

    val context = LocalContext.current
    LaunchedEffect(darkTheme) {
        val window = (context as? Activity)?.window ?: return@LaunchedEffect
        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = !darkTheme
            isAppearanceLightNavigationBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
