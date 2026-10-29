package com.shivam.glasslauncher.ui.glass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PremiumDimTest {

    @Test
    fun alphaIsMonotonicAndBounded() {
        var previous = -1f
        for (i in 0..100) {
            val a = PremiumDim.alphaFor(i / 100f)
            assertTrue("lum=${i / 100f} alpha=$a below previous $previous", a >= previous)
            assertTrue("alpha=$a out of bounds", a >= PremiumDim.MIN_ALPHA && a <= PremiumDim.MAX_ALPHA)
            previous = a
        }
        assertEquals(PremiumDim.MIN_ALPHA, PremiumDim.alphaFor(0f), 1e-6f)
    }

    @Test
    fun whiteWallpaperGetsTheAaSolutionNotTheCeiling() {
        // Least dim that keeps white text at exactly 4.5:1 on a white wallpaper: 1 - (1.05 / 4.5 - 0.05).
        val a = PremiumDim.alphaFor(1f)
        assertEquals(0.8167f, a, 1e-3f)
        assertTrue("white wallpaper must not hit the ceiling", a < PremiumDim.MAX_ALPHA)
        val ratio = Contrast.ratio(Contrast.LIGHT_TEXT, Contrast.gammaToLinear(1f) * (1f - a))
        assertTrue("white text ratio=$ratio below AA", ratio >= Contrast.AA - 0.01f)
    }

    @Test
    fun whiteTextStaysAaAcrossWallpaperLuminance() {
        for (i in 0..100) {
            val lum = i / 100f
            val a = PremiumDim.alphaFor(lum)
            if (a >= PremiumDim.MAX_ALPHA) continue
            val dimmed = Contrast.gammaToLinear(lum) * (1f - a)
            val ratio = Contrast.ratio(Contrast.LIGHT_TEXT, dimmed)
            assertTrue("lum=$lum alpha=$a white ratio=$ratio", ratio >= Contrast.AA - 0.01f)
        }
    }
}
