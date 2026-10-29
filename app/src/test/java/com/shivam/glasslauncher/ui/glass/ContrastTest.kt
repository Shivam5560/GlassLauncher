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
        // Mid-grey wallpaper under default dark glass: white text.
        assertEquals(Contrast.LIGHT_TEXT, Contrast.textOnGlass(0.5f, tint, GlassStyle().tintOpacity))
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
    fun toneDefaultsAreToneDownAndRolesRoundTrip() {
        val d = GlassStyle()
        assertEquals(0.4f, d.refraction, 0f)
        assertEquals(0.12f, d.chromaticAberration, 0f)
        assertEquals(0.6f, d.specular, 0f)
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
