package com.shivam.glasslauncher.polish

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

class PaletteExtractorTest {

    private fun hue(c: Int) = PaletteExtractor.toHsl(c)[0]
    private fun hueDistance(a: Float, b: Float) = abs(((a - b + 540f) % 360f) - 180f)

    private fun image(vararg parts: Pair<Int, Int>): IntArray = parts.flatMap { (color, count) -> List(count) { color } }.toIntArray()

    @Test
    fun vividColourBecomesAccentEvenWhenNotDominant() {
        // 80% dark navy, 20% vivid orange: the accent should be orange, the tint navy-ish and dark.
        val px = image(0xFF101830.toInt() to 8000, 0xFFFF8A1E.toInt() to 2000)
        val p = PaletteExtractor.fromPixels(px)
        assertTrue("accent hue ${hue(p.accent)}", hueDistance(hue(p.accent), hue(0xFFFF8A1E.toInt())) < 15f)
        assertTrue("tint should be dark", PaletteExtractor.luminance(p.glassTint) < 0.2f)
        assertTrue("tint hue follows the dominant colour", hueDistance(hue(p.glassTint), hue(0xFF101830.toInt())) < 25f)
        assertTrue(p.isDark)
    }

    @Test
    fun lightWallpaperIsNotDark() {
        val px = image(0xFFF2F4F8.toInt() to 9000, 0xFF3E9BFF.toInt() to 1000)
        val p = PaletteExtractor.fromPixels(px)
        assertFalse(p.isDark)
        assertTrue(hueDistance(hue(p.accent), hue(0xFF3E9BFF.toInt())) < 15f)
    }

    @Test
    fun greyWallpaperKeepsDefaultAccent() {
        val px = image(0xFF808080.toInt() to 5000, 0xFF404040.toInt() to 5000)
        assertEquals(PaletteExtractor.DEFAULT.accent, PaletteExtractor.fromPixels(px).accent)
    }

    @Test
    fun accentIsReadable() {
        // A very dark saturated green still yields a mid-lightness accent usable on glass.
        val px = image(0xFF032B0A.toInt() to 10000)
        val l = PaletteExtractor.toHsl(PaletteExtractor.fromPixels(px).accent)[2]
        assertTrue("lightness $l", l in 0.47f..0.63f)
    }

    @Test
    fun transparentPixelsAndEmptyInputAreIgnored() {
        assertEquals(PaletteExtractor.DEFAULT, PaletteExtractor.fromPixels(IntArray(0)))
        assertEquals(PaletteExtractor.DEFAULT, PaletteExtractor.fromPixels(IntArray(100) { 0x00FF0000 }))
    }

    @Test
    fun fromWallpaperColorsUsesHints() {
        val p = PaletteExtractor.fromWallpaperColors(0xFF1B5E20.toInt(), 0xFFE91E63.toInt(), null, supportsDarkText = true)
        assertFalse(p.isDark)
        val q = PaletteExtractor.fromWallpaperColors(0xFF0D1B2A.toInt(), null, null, supportsDarkText = false)
        assertTrue(q.isDark)
    }

    @Test
    fun hslRoundTrip() {
        for (c in listOf(0xFF0A84FF, 0xFFFF453A, 0xFF30D158, 0xFF5E5CE6, 0xFFFFD60A).map { it.toInt() }) {
            val h = PaletteExtractor.toHsl(c)
            val back = PaletteExtractor.fromHsl(h[0], h[1], h[2])
            for (shift in listOf(16, 8, 0)) {
                assertTrue(abs(((c shr shift) and 0xFF) - ((back shr shift) and 0xFF)) <= 1)
            }
        }
    }
}
