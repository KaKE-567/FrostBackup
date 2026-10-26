package io.github.project.vault.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin

/**
 * Pixel-style animated squiggly / zigzag wavy progress bar inspired by Android 13/14/15 Material You.
 * Displays a smooth undulating sine wave across the active progress region and seamlessly terminates
 * at a rounded thumb head before transitioning into the inactive track.
 */
@Composable
fun PixelSquigglyProgressIndicator(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
    strokeWidth: Dp = 4.dp,
    amplitude: Dp = 3.5.dp,
    wavelength: Dp = 18.dp,
    animateWave: Boolean = true
) {
    val infiniteTransition = rememberInfiniteTransition(label = "PixelSquigglyWave")
    val phase by if (animateWave) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = (2 * PI).toFloat(),
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1300, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "wavePhase"
        )
    } else {
        androidx.compose.runtime.remember { androidx.compose.runtime.mutableFloatStateOf(0f) }
    }

    val clampedProgress = progress.coerceIn(0f, 1f)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp)
    ) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f
        val strokePx = strokeWidth.toPx()
        val ampPx = amplitude.toPx()
        val waveLenPx = wavelength.toPx()

        // 1. Inactive background track spanning the whole slider width
        drawLine(
            color = trackColor,
            start = Offset(0f, centerY),
            end = Offset(width, centerY),
            strokeWidth = strokePx,
            cap = StrokeCap.Round
        )

        val activeWidth = width * clampedProgress

        if (activeWidth > 1f) {
            val wavePath = Path()
            var first = true

            // Fine sampling resolution along X for butter-smooth 60/120Hz display
            val step = 2f
            var x = 0f
            while (x <= activeWidth) {
                // Dampen the wave amplitude smoothly near the tip so it lands exactly on the center line
                val distToEnd = activeWidth - x
                val dampening = if (distToEnd < waveLenPx * 0.75f) {
                    (distToEnd / (waveLenPx * 0.75f)).coerceIn(0f, 1f)
                } else {
                    1f
                }

                val currentAmp = ampPx * dampening
                val angle = ((x / waveLenPx) * 2 * PI).toFloat() - phase
                val y = centerY + sin(angle) * currentAmp

                if (first) {
                    wavePath.moveTo(x, y)
                    first = false
                } else {
                    wavePath.lineTo(x, y)
                }
                x += step
            }

            // Lock the final vertex to the center line at activeWidth
            wavePath.lineTo(activeWidth, centerY)

            drawPath(
                path = wavePath,
                color = color,
                style = Stroke(
                    width = strokePx,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )

            // 2. Pixel-style rounded thumb head indicator at current progress point
            drawCircle(
                color = color,
                radius = strokePx * 1.35f,
                center = Offset(activeWidth, centerY)
            )
        }
    }
}

/**
 * Indeterminate Pixel-style squiggly wave that flows continuously across the entire track.
 */
@Composable
fun PixelSquigglyIndeterminateProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
    strokeWidth: Dp = 4.dp,
    amplitude: Dp = 3.5.dp,
    wavelength: Dp = 18.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "PixelIndeterminateWave")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1100, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "indeterminatePhase"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(24.dp)
    ) {
        val width = size.width
        val height = size.height
        val centerY = height / 2f
        val strokePx = strokeWidth.toPx()
        val ampPx = amplitude.toPx()
        val waveLenPx = wavelength.toPx()

        // Background subtle base line
        drawLine(
            color = trackColor,
            start = Offset(0f, centerY),
            end = Offset(width, centerY),
            strokeWidth = strokePx,
            cap = StrokeCap.Round
        )

        val wavePath = Path()
        var first = true
        val step = 2f
        var x = 0f
        while (x <= width) {
            val angle = ((x / waveLenPx) * 2 * PI).toFloat() - phase
            val y = centerY + sin(angle) * ampPx

            if (first) {
                wavePath.moveTo(x, y)
                first = false
            } else {
                wavePath.lineTo(x, y)
            }
            x += step
        }

        drawPath(
            path = wavePath,
            color = color,
            style = Stroke(
                width = strokePx,
                cap = StrokeCap.Round,
                join = StrokeJoin.Round
            )
        )
    }
}
