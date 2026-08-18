/**
 * Jugnu Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.SliderColors
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SliderState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.lerp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerSliderTrack(
    sliderState: SliderState,
    modifier: Modifier = Modifier,
    colors: SliderColors = SliderDefaults.colors(),
    trackHeight: Dp = 4.dp
) {
    val inactiveTrackColor = colors.inactiveTrackColor
    val activeTrackColor = colors.activeTrackColor
    val inactiveTickColor = colors.inactiveTickColor
    val activeTickColor = colors.activeTickColor
    val valueRange = sliderState.valueRange

    val progress = calcFraction(
        valueRange.start,
        valueRange.endInclusive,
        sliderState.value.coerceIn(valueRange.start, valueRange.endInclusive)
    )

    // Breathing pulse glow transition when song is near completion (>= 95%)
    val completedFactor = ((progress - 0.95f) / 0.05f).coerceIn(0f, 1f)
    val infiniteTransition = rememberInfiniteTransition(label = "completedGlow")
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowPulse"
    )

    Canvas(
        modifier
            .fillMaxWidth()
            .height(trackHeight)
    ) {
        val isRtl = layoutDirection == LayoutDirection.Rtl
        val sliderLeft = Offset(0f, center.y)
        val sliderRight = Offset(size.width, center.y)
        val sliderStart = if (isRtl) sliderRight else sliderLeft
        val sliderEnd = if (isRtl) sliderLeft else sliderRight
        val tickSize = 2.0.dp.toPx()
        val trackStrokeWidth = trackHeight.toPx()

        // Draw standard inactive track
        drawLine(
            inactiveTrackColor,
            sliderStart,
            sliderEnd,
            trackStrokeWidth,
            StrokeCap.Round
        )

        val activeRangeEnd = progress
        val activeRangeStart = 0f
        val sliderValueEnd = Offset(
            sliderStart.x + (sliderEnd.x - sliderStart.x) * activeRangeEnd,
            center.y
        )
        val sliderValueStart = Offset(
            sliderStart.x + (sliderEnd.x - sliderStart.x) * activeRangeStart,
            center.y
        )

        // Draw breathing pulse glow behind the active track if completedFactor > 0f
        if (completedFactor > 0f) {
            val glowWidth = trackStrokeWidth + (12.dp.toPx() * glowPulse * completedFactor)
            val glowColor = activeTrackColor.copy(alpha = 0.5f * completedFactor * (1f - glowPulse * 0.4f))
            drawLine(
                color = glowColor,
                start = sliderValueStart,
                end = sliderValueEnd,
                strokeWidth = glowWidth,
                cap = StrokeCap.Round
            )
        }

        // Draw standard active track
        drawLine(
            activeTrackColor,
            sliderValueStart,
            sliderValueEnd,
            trackStrokeWidth,
            StrokeCap.Round
        )

        // Draw ticks
        val tickFractions = stepsToTickFractions(sliderState.steps)
        for (tick in tickFractions) {
            val outsideFraction = tick > activeRangeEnd || tick < activeRangeStart
            drawCircle(
                color = if (outsideFraction) inactiveTickColor else activeTickColor,
                center = Offset(lerp(sliderStart, sliderEnd, tick).x, center.y),
                radius = tickSize / 2f
            )
        }
    }
}

private fun stepsToTickFractions(steps: Int): FloatArray {
    return if (steps == 0) floatArrayOf() else FloatArray(steps + 2) { it.toFloat() / (steps + 1) }
}

private fun calcFraction(a: Float, b: Float, pos: Float) =
    (if (b - a == 0f) 0f else (pos - a) / (b - a)).coerceIn(0f, 1f)
