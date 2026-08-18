/**
 * Jugnu Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.ui.utils

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.metrolist.music.constants.GlassStyle
import com.metrolist.music.constants.GlassStyleKey
import com.metrolist.music.constants.GlassOpacityKey
import com.metrolist.music.utils.rememberPreference
import com.metrolist.music.utils.rememberEnumPreference


fun Modifier.glassCard(
    cornerRadius: Dp = 16.dp,
    shape: Shape = RoundedCornerShape(cornerRadius),
    borderColor: Color? = null,
    backgroundColor: Color? = null
): Modifier = this.composed {
    val dynamicPrimary = MaterialTheme.colorScheme.primary
    val dynamicSecondary = MaterialTheme.colorScheme.secondary
    val isDark = isSystemInDarkTheme()
    
    val (glassStyle, _) = rememberEnumPreference(GlassStyleKey, defaultValue = GlassStyle.LIQUID)
    val (glassOpacity, _) = rememberPreference(GlassOpacityKey, defaultValue = 0.96f)
    
    val opacity = when (glassStyle) {
        GlassStyle.LIQUID -> 0.98f
        GlassStyle.STANDARD -> 0.96f
        GlassStyle.CUSTOM -> glassOpacity
    }
    
    val baseColor = if (isDark) {
        Color(0xFF141518)
    } else {
        Color(0xFFF3F4F7)
    }
    
    val bg = if (backgroundColor == Color.Transparent) {
        Color.Transparent
    } else if (backgroundColor != null) {
        backgroundColor.copy(alpha = opacity)
    } else {
        baseColor.copy(alpha = opacity)
    }
    
    val highlightColor = if (isDark) Color.White else Color.Black
    val borderBrush = if (glassStyle == GlassStyle.LIQUID) {
        Brush.linearGradient(
            colors = listOf(
                highlightColor.copy(alpha = 0.35f),
                (borderColor ?: dynamicPrimary).copy(alpha = 0.15f),
                (borderColor ?: dynamicSecondary).copy(alpha = 0.15f),
                highlightColor.copy(alpha = 0.08f)
            )
        )
    } else {
        Brush.linearGradient(
            colors = listOf(
                (borderColor ?: dynamicPrimary).copy(alpha = 0.35f),
                (borderColor ?: dynamicSecondary).copy(alpha = 0.35f)
            )
        )
    }
    
    this
        .background(bg, shape)
        .border(1.dp, borderBrush, shape)
}


fun Modifier.neonGlow(
    cornerRadius: Dp = 16.dp,
    shape: Shape = RoundedCornerShape(cornerRadius),
    glowColor: Color? = null
): Modifier = this.composed {
    val infiniteTransition = rememberInfiniteTransition(label = "glowPulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.15f,
        targetValue = 0.45f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    
    val color = glowColor ?: MaterialTheme.colorScheme.primary
    this.border(
        width = 1.dp,
        brush = Brush.linearGradient(
            colors = listOf(
                color.copy(alpha = alpha),
                MaterialTheme.colorScheme.secondary.copy(alpha = alpha)
            )
        ),
        shape = shape
    )
}

fun Modifier.scrollEntrance(): Modifier = this.composed {
    val density = androidx.compose.ui.platform.LocalDensity.current
    val offsetPx = remember(density) { with(density) { 16.dp.toPx() } }
    
    val alpha = remember { Animatable(0.6f) }
    val offsetY = remember { Animatable(offsetPx) }
    
    LaunchedEffect(Unit) {
        alpha.animateTo(1f, animationSpec = tween(300, easing = LinearOutSlowInEasing))
    }
    LaunchedEffect(Unit) {
        offsetY.animateTo(0f, animationSpec = tween(300, easing = LinearOutSlowInEasing))
    }
    
    this.graphicsLayer {
        this.alpha = alpha.value
        this.translationY = offsetY.value
    }
}

