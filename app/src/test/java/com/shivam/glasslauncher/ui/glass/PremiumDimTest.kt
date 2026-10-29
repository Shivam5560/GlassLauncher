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
    fun brightWallpaperIsCappedAtTheCeiling() {
        // The AA solution on white would be about 0.82; the ceiling keeps the picture visible.
        assertEquals(PremiumDim.MAX_ALPHA, PremiumDim.alphaFor(1f), 1e-6f)
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
