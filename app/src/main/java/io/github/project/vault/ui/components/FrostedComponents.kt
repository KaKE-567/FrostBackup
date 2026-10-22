package io.github.project.vault.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

@Composable
fun FrostedBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    val bgColor = MaterialTheme.colorScheme.background
    val isAmoled = bgColor == Color.Black
    val primaryColor = MaterialTheme.colorScheme.primary
    val secondaryColor = MaterialTheme.colorScheme.secondary

    val backgroundModifier = if (isAmoled) {
        modifier
            .fillMaxSize()
            .background(bgColor)
    } else {
        modifier
            .fillMaxSize()
            .background(bgColor)
            .drawWithCache {
                val primaryGlow = primaryColor.copy(alpha = 0.08f)
                val secondaryGlow = secondaryColor.copy(alpha = 0.06f)
                val brush1 = Brush.radialGradient(
                    colors = listOf(primaryGlow, Color.Transparent),
                    center = Offset(size.width * 0.9f, size.height * 0.1f),
                    radius = size.width * 0.65f
                )
                val brush2 = Brush.radialGradient(
                    colors = listOf(secondaryGlow, Color.Transparent),
                    center = Offset(size.width * 0.1f, size.height * 0.8f),
                    radius = size.width * 0.75f
                )
                onDrawBehind {
                    drawCircle(brush = brush1, center = Offset(size.width * 0.9f, size.height * 0.1f), radius = size.width * 0.65f)
                    drawCircle(brush = brush2, center = Offset(size.width * 0.1f, size.height * 0.8f), radius = size.width * 0.75f)
                }
            }
    }

    Box(modifier = backgroundModifier) {
        CompositionLocalProvider(
            LocalContentColor provides MaterialTheme.colorScheme.onBackground
        ) {
            content()
        }
    }
}
