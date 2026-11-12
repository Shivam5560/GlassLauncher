package com.shivam.glasslauncher.polish

import org.junit.Assert.*
import org.junit.Test

class WallpaperEditTest {
    @Test fun outputCapPreservesTallAspectRatioAndOrientation() {
        assertEquals(1152 to 3200, wallpaperOutputSize(1440, 4000))
        assertEquals(1152 to 3200, wallpaperOutputSize(4000, 1440))
        assertEquals(1268 to 2756, wallpaperOutputSize(1268, 2756))
    }
    @Test fun neutralPreservesMatchingAspectRatio() {
        val crop = WallpaperEdit().crop(1440, 3200, 720, 1600)
        assertEquals(WallpaperCrop(0f, 0f, 1440f, 3200f), crop)
    }
    @Test fun landscapeSourceCropsWithoutStretching() {
        val crop = WallpaperEdit().crop(4000, 2000, 1000, 2000)
        assertEquals(WallpaperCrop(1500f, 0f, 2500f, 2000f), crop)
    }
    @Test fun zoomAndPanReachBothEdges() {
        val topLeft = WallpaperEdit(2f, 0f, 0f).crop(1440, 3200, 720, 1600)
        val bottomRight = WallpaperEdit(2f, 1f, 1f).crop(1440, 3200, 720, 1600)
        assertEquals(WallpaperCrop(0f, 0f, 720f, 1600f), topLeft)
        assertEquals(WallpaperCrop(720f, 1600f, 1440f, 3200f), bottomRight)
    }
    @Test fun cropIsResolutionIndependent() {
        val edit = WallpaperEdit(1.4f, 0.1f, 0.9f)
        val full = edit.crop(1440, 3200, 1268, 2756)
        val preview = edit.crop(360, 800, 317, 689)
        assertEquals(full.left, preview.left * 4, 0.001f)
        assertEquals(full.top, preview.top * 4, 0.001f)
        assertEquals(full.right, preview.right * 4, 0.001f)
        assertEquals(full.bottom, preview.bottom * 4, 0.001f)
    }
    @Test fun importedInvalidValuesAreSanitized() {
        assertEquals(WallpaperEdit(), WallpaperEdit(Float.NaN, Float.POSITIVE_INFINITY, Float.NaN, Float.NaN, Float.NEGATIVE_INFINITY).normalized())
        assertEquals(WallpaperEdit(2f, 0f, 1f, 0.6f, -1f), WallpaperEdit(4f, -2f, 3f, 1f, -3f).normalized())
    }
    @Test fun cropStaysWithinSourceAcrossAspectRatios() {
        for (w in listOf(1, 320, 1440, 4000)) for (h in listOf(1, 800, 3200)) {
            for (z in listOf(1f, 1.1f, 2f)) for (x in listOf(0f, 0.5f, 1f)) {
                val c = WallpaperEdit(z, x, 1 - x).crop(w, h, 1268, 2756)
                assertTrue(c.left >= 0f && c.top >= 0f)
                assertTrue(c.right <= w + 0.001f && c.bottom <= h + 0.001f)
                assertTrue(c.right > c.left && c.bottom > c.top)
            }
        }
    }
    @Test(expected = IllegalArgumentException::class) fun invalidTargetIsRejected() { WallpaperEdit().crop(100, 100, 0, 20) }
    @Test fun dimDoesNotChangeAlphaAndWarmthIsBalanced() {
        val m = WallpaperEdit(dim = 0.5f, warmth = 1f).colorMatrix()
        assertEquals(0.5f, m[0], 0f); assertEquals(12f, m[4], 0f)
        assertEquals(-12f, m[14], 0f); assertEquals(1f, m[18], 0f)
    }
    @Test fun autoSwitchRequiresPositiveVerifiedOwnership() {
        assertTrue(shouldSwitchWallpaper(12, 12, "light", "dark"))
        assertFalse(shouldSwitchWallpaper(12, 12, "dark", "dark"))
        assertFalse(shouldSwitchWallpaper(12, 13, "light", "dark"))
        assertFalse(shouldSwitchWallpaper(12, null, "light", "dark"))
        assertFalse(shouldSwitchWallpaper(0, 0, "light", "dark"))
    }
}
