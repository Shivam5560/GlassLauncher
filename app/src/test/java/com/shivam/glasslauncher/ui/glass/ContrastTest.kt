package com.shivam.glasslauncher.ui.glass

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ContrastTest {

    @Test
    fun relativeLuminanceEndpoints() {
        assertEquals(1f, Contrast.relativeLuminance(0xFFFFFFFF.toInt()), 1e-4f)
        assertEquals(0f, Contrast.relativeLuminance(0xFF000000.toInt()), 1e-4f)
        assertEquals(21f, Contrast.ratio(1f, 0f), 1e-3f)
    }

    @Test
    fun knownRatio() {
        // #767676 on white is the classic 4.54:1 AA threshold grey.
        val grey = Contrast.relativeLuminance(0xFF767676.toInt())
        assertEquals(4.54f, Contrast.ratio(1f, grey), 0.02f)
    }

    @Test
    fun pickedTextMeetsAaOnAnyBackground() {
        for (i in 0..1000) {
            val bg = i / 1000f
            val text = Contrast.pickText(bg)
            val r = Contrast.ratio(Contrast.relativeLuminance(text), bg)
            assertTrue("bg=$bg ratio=$r", r >= Contrast.AA)
        }
    }

    @Test
    fun neverWhiteOnLight() {
        assertEquals(Contrast.DARK_TEXT, Contrast.pickText(Contrast.relativeLuminance(0xFFF2F2F7.toInt())))
        assertEquals(Contrast.DARK_TEXT, Contrast.pickText(0.5f))
        assertEquals(Contrast.LIGHT_TEXT, Contrast.pickText(Contrast.relativeLuminance(0xFF1C1C1E.toInt())))
    }

    @Test
    fun darkGlassOverBrightWallpaperStillChoosesByMeasuredLuminance() {
        val tint = GlassStyle().tintColor
        // Mid-grey wallpaper under 8% Liquid glass: the backdrop shows, so dark text (white misses AA).
        assertEquals(Contrast.DARK_TEXT, Contrast.textOnGlass(0.5f, tint, GlassStyle().tintOpacity))
        // Mid-grey under the same glass at a dense tint still picks white.
        assertEquals(Contrast.LIGHT_TEXT, Contrast.textOnGlass(0.5f, tint, 0.5f))
        // White wallpaper under light (Clear) glass: dark text, never white on light.
        assertEquals(Contrast.DARK_TEXT, Contrast.textOnGlass(1f, tint, 0.15f))
        // Nearly opaque dark glass over white: white text again.
        assertEquals(Contrast.LIGHT_TEXT, Contrast.textOnGlass(1f, tint, 0.92f))
    }

    @Test
    fun glassLuminanceMixesLinearly() {
        assertEquals(1f, Contrast.glassLuminance(1f, 0xFF000000.toInt(), 0f), 1e-5f)
        assertEquals(0.5f, Contrast.glassLuminance(1f, 0xFF000000.toInt(), 0.5f), 1e-5f)
        assertEquals(0f, Contrast.glassLuminance(1f, 0xFF000000.toInt(), 1f), 1e-5f)
    }

    @Test
    fun legibilityScrimIsZeroOverDarkBackdrops() {
        for (g in listOf(0f, 0.1f, 0.25f, 0.4f, Contrast.linearToGamma(Contrast.WHITE_AA_MAX_LUMINANCE))) {
            assertEquals("g=$g", 0f, Contrast.legibilityScrim(g), 0f)
        }
    }

    @Test
    fun legibilityScrimIsBoundedAndMonotonic() {
        var prev = -1f
        for (i in 0..1000) {
            val a = Contrast.legibilityScrim(i / 1000f)
            assertTrue("a=$a at ${i}", a >= 0f && a <= Contrast.SCRIM_MAX + 1e-6f)
            assertTrue("not monotonic at $i", a >= prev - 1e-6f)
            prev = a
        }
        // The cap is never the binding limit: a pure white backdrop needs about 0.535.
        assertTrue(Contrast.legibilityScrim(1f) < Contrast.SCRIM_MAX)
    }

    @Test
    fun scrimmedGlassAlwaysMeetsAaWithWhiteText() {
        // Thin glass at the lowest and highest tint, every backdrop brightness, at the Liquid defaults.
        for (tintAmount in listOf(GlassStyle().tintOpacity, GlassStyle().resolve(GlassVariant.Clear).tintOpacity)) {
            for (i in 0..1000) {
                val backdropGamma = i / 1000f
                val glass = Contrast.glassLuminance(Contrast.gammaToLinear(backdropGamma), GlassStyle().tintColor, tintAmount)
                val lum = Contrast.scrimmed(glass, Contrast.legibilityScrim(Contrast.linearToGamma(glass)))
                val text = Contrast.LIGHT_TEXT // thin dark glass always takes white text; the scrim makes that pass
                val r = Contrast.ratio(Contrast.relativeLuminance(text), lum)
                assertTrue("g=$backdropGamma tint=$tintAmount ratio=$r", r >= Contrast.AA)
            }
        }
    }

    @Test
    fun toneDefaultsAreToneDownAndRolesRoundTrip() {
        val d = GlassStyle()
        assertEquals(0.4f, d.refraction, 0f)
        assertEquals(0.12f, d.chromaticAberration, 0f)
        assertEquals(0.6f, d.specular, 0f)
        assertEquals(0.08f, d.tintOpacity, 0f)
        val roles = mapOf(
            SurfaceKind.Panel to RoleStyle(color = 0xFF112233.toInt(), opacity = 0.6f, contentColor = 0xFFFFFFFF.toInt(), blur = 12f, stroke = 1f, accent = 0xFFFF9F0A.toInt()),
            SurfaceKind.Widget to RoleStyle(stroke = 2f),
            SurfaceKind.Cards to RoleStyle(),
        )
        val back = RoleStyle.decode(RoleStyle.encode(roles))
        assertEquals(roles - SurfaceKind.Cards, back)
        assertEquals(emptyMap<SurfaceKind, RoleStyle>(), RoleStyle.decode("not json"))
    }
}
