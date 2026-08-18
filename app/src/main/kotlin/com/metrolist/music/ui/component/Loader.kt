/**
 * Jugnu Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.component

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.metrolist.music.constants.PureBlackKey
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.ui.utils.glassCard

@Composable
fun MetrolistCircularProgressIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary,
    strokeWidth: Dp = 2.dp,
    size: Dp = 24.dp
) {
    val infiniteTransition = rememberInfiniteTransition(label = "rotation")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "rotationVal"
    )

    Canvas(modifier = modifier.size(size)) {
        drawArc(
            color = color.copy(alpha = 0.15f),
            startAngle = 0f,
            sweepAngle = 360f,
            useCenter = false,
            style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
        )
        drawArc(
            color = color,
            startAngle = rotation,
            sweepAngle = 90f,
            useCenter = false,
            style = Stroke(width = strokeWidth.toPx(), cap = StrokeCap.Round)
        )
    }
}

@Composable
fun MetrolistContainedLoadingIndicator(
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.primary
) {
    val isSystemDark = isSystemInDarkTheme()
    val pureBlack by rememberPreference(PureBlackKey, defaultValue = false)
    val isPureBlack = pureBlack && isSystemDark
    val shape = RoundedCornerShape(16.dp)

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.padding(16.dp)
    ) {
        Card(
            shape = shape,
            colors = CardDefaults.cardColors(
                containerColor = Color.Transparent
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
            modifier = Modifier
                .size(56.dp)
                .then(
                    if (isPureBlack) {
                        Modifier.background(Color.Black, shape)
                    } else {
                        Modifier.glassCard(shape = shape)
                    }
                )
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(56.dp)
            ) {
                MetrolistCircularProgressIndicator(
                    color = color,
                    strokeWidth = 2.5.dp,
                    size = 32.dp
                )
            }
        }
    }
}
