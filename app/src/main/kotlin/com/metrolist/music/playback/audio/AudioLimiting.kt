/**
 * Jugnu Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback.audio

import kotlin.math.abs
import kotlin.math.sign
import kotlin.math.tanh

/**
 * Transparent soft-knee peak limiter for normalized [-1.0, 1.0] audio samples.
 *
 * Below [SOFT_LIMIT_THRESHOLD] (-1.0 dBFS), samples pass through 1:1 with zero coloration.
 * Above the threshold, peaks are smoothly rounded using a hyperbolic tangent curve up to +/-1.0
 * instead of square-wave hard clipping, eliminating harsh digital distortion on boosted tracks.
 */
internal object AudioLimiting {
    // 10^(-1.0 / 20.0) ≈ -1.0 dBFS
    private const val SOFT_LIMIT_THRESHOLD = 0.8912509381337456
    private const val SOFT_LIMIT_HEADROOM = 1.0 - SOFT_LIMIT_THRESHOLD

    fun softLimit(sample: Double): Double {
        val absSample = abs(sample)
        if (absSample <= SOFT_LIMIT_THRESHOLD) return sample
        val excess = (absSample - SOFT_LIMIT_THRESHOLD) / SOFT_LIMIT_HEADROOM
        return sample.sign * (SOFT_LIMIT_THRESHOLD + SOFT_LIMIT_HEADROOM * tanh(excess))
    }
}

