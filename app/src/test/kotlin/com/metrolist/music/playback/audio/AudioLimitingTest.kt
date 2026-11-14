/**
 * Jugnu Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.metrolist.music.playback.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioLimitingTest {

    @Test
    fun `samples below -1 dBFS pass through bit-transparently`() {
        val samples = listOf(0.0, 0.25, -0.5, 0.89, -0.89)
        for (sample in samples) {
            assertEquals(sample, AudioLimiting.softLimit(sample), 1e-12)
        }
    }

    @Test
    fun `hot samples are smoothly limited below unity without hard clipping`() {
        val boosted1 = AudioLimiting.softLimit(1.2)
        val boosted2 = AudioLimiting.softLimit(2.5)
        val negativeBoosted = AudioLimiting.softLimit(-2.0)

        assertTrue("Expected $boosted1 > 0.89125 and < 1.0", boosted1 > 0.89125 && boosted1 < 1.0)
        assertTrue("Expected monotonic soft limiting ($boosted2 > $boosted1)", boosted2 > boosted1 && boosted2 < 1.0)
        assertTrue("Expected negative symmetry ($negativeBoosted)", negativeBoosted < -0.89125 && negativeBoosted > -1.0)
    }
}

