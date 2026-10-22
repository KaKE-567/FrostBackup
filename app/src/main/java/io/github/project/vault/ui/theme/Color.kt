package io.github.project.vault.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import kotlin.math.abs

// Preset Key Color Palettes (KernelSU-aligned vibrancies)
val keyColorOptions = listOf(
    0xFF006C50.toInt(), // Frost Emerald (Signature)
    0xFF009688.toInt(), // Teal
    0xFF00BCD4.toInt(), // Cyan
    0xFF2196F3.toInt(), // Ocean Blue
    0xFF3F51B5.toInt(), // Indigo
    0xFF673AB7.toInt(), // Deep Purple
    0xFF9C27B0.toInt(), // Purple / Violet
    0xFFE91E63.toInt(), // Rose Pink
    0xFFF44336.toInt(), // Crimson Red
    0xFFFF9800.toInt(), // Sunset Orange
    0xFFFFC107.toInt(), // Amber Gold
    0xFF4CAF50.toInt(), // Fresh Green
    0xFF607D8F.toInt(), // Blue Grey
    0xFFFF9CA8.toInt(), // Sakura Blossom
    0xFF795548.toInt()  // Coffee Brown
)

// Light Mode (Default Emerald)
val EmeraldPrimary = Color(0xFF006C50)
val EmeraldOnPrimary = Color(0xFFFFFFFF)
val EmeraldPrimaryContainer = Color(0xFF88F8CD)
val EmeraldOnPrimaryContainer = Color(0xFF002116)

val MintSecondary = Color(0xFF4C6358)
val MintOnSecondary = Color(0xFFFFFFFF)
val MintSecondaryContainer = Color(0xFFCEE9DA)
val MintOnSecondaryContainer = Color(0xFF092016)

val SlateBackground = Color(0xFFF7FBF8) // clean crisp white slate
val SlateOnBackground = Color(0xFF141D19)
val SlateSurface = Color(0xFFFFFFFF) // pure white card surface
val SlateOnSurface = Color(0xFF141D19)
val SlateSurfaceVariant = Color(0xFFE9F0EC)
val SlateOnSurfaceVariant = Color(0xFF404B45)
val SlateOutline = Color(0xFFBCCBC3)

// Dark Mode (Default Slate Emerald)
val DarkEmeraldPrimary = Color(0xFF6CDBB2)
val DarkEmeraldOnPrimary = Color(0xFF003828)
val DarkEmeraldPrimaryContainer = Color(0xFF00513B)
val DarkEmeraldOnPrimaryContainer = Color(0xFF88F8CD)

val DarkMintSecondary = Color(0xFFB3CCBF)
val DarkMintOnSecondary = Color(0xFF1E352B)
val DarkMintSecondaryContainer = Color(0xFF2E4238)
val DarkMintOnSecondaryContainer = Color(0xFFCEE9DA)

val DarkSlateBackground = Color(0xFF101513)
val DarkSlateOnBackground = Color(0xFFE1E5E2)
val DarkSlateSurface = Color(0xFF171E1A)
val DarkSlateOnSurface = Color(0xFFE1E5E2)
val DarkSlateSurfaceVariant = Color(0xFF222B26)
val DarkSlateOnSurfaceVariant = Color(0xFFBCCBC3)
val DarkSlateOutline = Color(0xFF889990)
val DarkSlateOutlineVariant = Color(0xFF384640)

// Frosted Glass Constants
val GlassDarkSurface = Color(0xD8171E1A) // 85% alpha dark surface
val GlassLightSurface = Color(0xF2FFFFFF) // 95% alpha light surface
val GlassDarkBorder = Color(0x33FFFFFF) // subtle white rim
val GlassLightBorder = Color(0x1F000000) // subtle dark rim

// --- Dynamic Color Generation Helpers ---

private fun colorToHsl(color: Color): FloatArray {
    val r = color.red
    val g = color.green
    val b = color.blue
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min
    val l = (max + min) / 2f
    val s = if (delta == 0f) 0f else if (l <= 0.5f) delta / (max + min) else delta / (2f - max - min)
    var h = 0f
    if (delta != 0f) {
        h = when (max) {
            r -> ((g - b) / delta + (if (g < b) 6f else 0f))
            g -> ((b - r) / delta + 2f)
            else -> ((r - g) / delta + 4f)
        } * 60f
    }
    return floatArrayOf(h, s, l)
}

private fun hslToColor(h: Float, s: Float, l: Float, alpha: Float = 1f): Color {
    val normH = ((h % 360f) + 360f) % 360f
    val normS = s.coerceIn(0f, 1f)
    val normL = l.coerceIn(0f, 1f)
    val c = (1f - abs(2f * normL - 1f)) * normS
    val x = c * (1f - abs((normH / 60f) % 2f - 1f))
    val m = normL - c / 2f
    val (r, g, b) = when {
        normH < 60f -> Triple(c, x, 0f)
        normH < 120f -> Triple(x, c, 0f)
        normH < 180f -> Triple(0f, c, x)
        normH < 240f -> Triple(0f, x, c)
        normH < 300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    return Color(
        red = (r + m).coerceIn(0f, 1f),
        green = (g + m).coerceIn(0f, 1f),
        blue = (b + m).coerceIn(0f, 1f),
        alpha = alpha
    )
}

fun ColorScheme.amoledBackground(amoled: Boolean): ColorScheme =
    if (!amoled) this
    else copy(
        background = Color.Black,
        surface = Color.Black,
        surfaceDim = Color.Black,
        surfaceContainerLowest = Color.Black,
        surfaceContainerLow = Color(0xFF0A0A0A),
        surfaceContainer = Color(0xFF121212),
        surfaceContainerHigh = Color(0xFF1C1C1C),
        surfaceContainerHighest = Color(0xFF262626),
        surfaceBright = Color(0xFF2A2A2A),
        outlineVariant = Color(0xFF262626)
    )

fun generateDynamicColorScheme(
    seedColor: Color,
    isDark: Boolean,
    isAmoled: Boolean
): ColorScheme {
    val (h, s, _) = colorToHsl(seedColor)
    val secondaryH = (h + 15f) % 360f
    val tertiaryH = (h + 60f) % 360f

    return if (isDark) {
        val baseScheme = darkColorScheme(
            primary = hslToColor(h, (s * 0.90f).coerceIn(0.55f, 0.92f), 0.74f),
            onPrimary = hslToColor(h, 0.50f, 0.12f),
            primaryContainer = hslToColor(h, (s * 0.80f).coerceIn(0.40f, 0.75f), 0.28f),
            onPrimaryContainer = hslToColor(h, (s * 0.90f).coerceIn(0.60f, 0.95f), 0.90f),
            secondary = hslToColor(secondaryH, (s * 0.40f).coerceIn(0.20f, 0.40f), 0.75f),
            onSecondary = hslToColor(secondaryH, 0.30f, 0.14f),
            secondaryContainer = hslToColor(secondaryH, (s * 0.35f).coerceIn(0.20f, 0.40f), 0.26f),
            onSecondaryContainer = hslToColor(secondaryH, 0.35f, 0.88f),
            tertiary = hslToColor(tertiaryH, (s * 0.55f).coerceIn(0.30f, 0.60f), 0.75f),
            onTertiary = hslToColor(tertiaryH, 0.40f, 0.14f),
            tertiaryContainer = hslToColor(tertiaryH, (s * 0.50f).coerceIn(0.30f, 0.55f), 0.26f),
            onTertiaryContainer = hslToColor(tertiaryH, 0.45f, 0.90f),
            background = hslToColor(h, 0.08f, 0.06f),
            onBackground = Color(0xFFE2E4E1),
            surface = hslToColor(h, 0.08f, 0.09f),
            onSurface = Color(0xFFE2E4E1),
            surfaceVariant = hslToColor(h, 0.09f, 0.15f),
            onSurfaceVariant = Color(0xFFBFC9C2),
            outline = Color(0xFF8A938C),
            outlineVariant = Color(0xFF384640)
        )
        baseScheme.amoledBackground(isAmoled)
    } else {
        lightColorScheme(
            primary = hslToColor(h, (s * 0.95f).coerceIn(0.65f, 0.95f), 0.36f),
            onPrimary = Color.White,
            primaryContainer = hslToColor(h, (s * 0.85f).coerceIn(0.50f, 0.85f), 0.88f),
            onPrimaryContainer = hslToColor(h, 0.90f, 0.12f),
            secondary = hslToColor(secondaryH, 0.25f, 0.40f),
            onSecondary = Color.White,
            secondaryContainer = hslToColor(secondaryH, 0.25f, 0.90f),
            onSecondaryContainer = hslToColor(secondaryH, 0.30f, 0.12f),
            tertiary = hslToColor(tertiaryH, 0.35f, 0.40f),
            onTertiary = Color.White,
            tertiaryContainer = hslToColor(tertiaryH, 0.35f, 0.90f),
            onTertiaryContainer = hslToColor(tertiaryH, 0.35f, 0.12f),
            background = hslToColor(h, 0.15f, 0.98f),
            onBackground = Color(0xFF141D19),
            surface = Color.White,
            onSurface = Color(0xFF141D19),
            surfaceVariant = hslToColor(h, 0.10f, 0.93f),
            onSurfaceVariant = Color(0xFF404B45),
            outline = Color(0xFF707973),
            outlineVariant = Color(0xFFBCCBC3)
        )
    }
}
