package com.example.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlin.math.sin

/**
 * Clean audio waveform visualizer responding to mic amplitude and active audio playback.
 */
@Composable
fun VisualizerWave(
    amplitude: Float,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    waveColor: Color = MaterialTheme.colorScheme.primary,
    secondaryColor: Color = MaterialTheme.colorScheme.tertiary
) {
    val infiniteTransition = rememberInfiniteTransition(label = "waveAnimation")
    val phase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = (2 * Math.PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "phase"
    )

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(72.dp)
    ) {
        val barCount = 28
        val totalWidth = size.width
        val barWidth = (totalWidth / (barCount * 1.6f)).coerceIn(4f, 16f)
        val spacing = (totalWidth - (barCount * barWidth)) / (barCount - 1)
        val centerY = size.height / 2f
        val maxHeight = size.height * 0.85f

        val brush = Brush.verticalGradient(
            colors = listOf(
                waveColor.copy(alpha = 0.9f),
                secondaryColor.copy(alpha = 0.8f),
                waveColor.copy(alpha = 0.9f)
            )
        )

        for (i in 0 until barCount) {
            val normalizedX = i.toFloat() / (barCount - 1)
            // Gaussian center weighting
            val centerWeight = 1.0f - (2f * normalizedX - 1f) * (2f * normalizedX - 1f) * 0.6f

            val animatedFactor = if (isActive) {
                val wave = (sin(phase + i * 0.35f) + 1.0f) / 2.0f
                val dynamicAmp = amplitude.coerceIn(0.12f, 1.0f)
                (dynamicAmp * 0.7f + wave * 0.3f) * centerWeight
            } else {
                0.08f * centerWeight
            }

            val barHeight = (maxHeight * animatedFactor).coerceIn(6f, maxHeight)
            val left = i * (barWidth + spacing)
            val top = centerY - (barHeight / 2f)

            drawRoundRect(
                brush = brush,
                topLeft = Offset(left, top),
                size = Size(barWidth, barHeight),
                cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f)
            )
        }
    }
}
