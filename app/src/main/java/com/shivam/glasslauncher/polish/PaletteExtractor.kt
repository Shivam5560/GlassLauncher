package com.shivam.glasslauncher.polish

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Colours derived from a wallpaper: [accent] for buttons, switches and highlights, [glassTint] for
 * the glass panels, [isDark] for the wallpaper's overall tone. All colours are opaque ARGB ints.
 */
data class ThemePalette(val accent: Int, val glassTint: Int, val isDark: Boolean)

/**
 * Pure-Kotlin palette extraction (no Android types, so it is unit tested on the JVM).
 *
 * [fromPixels] buckets pixels into a 4-bit-per-channel histogram, picks the most vivid colour with
 * a meaningful share of the image as the accent, and the dominant colour (darkened into a deep,
 * low-saturation glass tint) for the panels. [fromWallpaperColors] does the same from the three
 * colours of `android.app.WallpaperColors`, used when the pixels are not readable (Path C).
 */
object PaletteExtractor {

    val DEFAULT = ThemePalette(accent = 0xFF0A84FF.toInt(), glassTint = 0xFF1E2028.toInt(), isDark = true)

    fun fromPixels(pixels: IntArray, step: Int = 1): ThemePalette {
        if (pixels.isEmpty()) return DEFAULT
        val counts = IntArray(4096)
        val sumR = LongArray(4096)
        val sumG = LongArray(4096)
        val sumB = LongArray(4096)
        var lumSum = 0.0
        var n = 0
        var i = 0
        while (i < pixels.size) {
            val c = pixels[i]
            if ((c ushr 24) >= 0x80) {
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val b = c and 0xFF
                val bin = ((r shr 4) shl 8) or ((g shr 4) shl 4) or (b shr 4)
                counts[bin]++
                sumR[bin] += r.toLong(); sumG[bin] += g.toLong(); sumB[bin] += b.toLong()
                lumSum += luminance(r, g, b)
                n++
            }
            i += step.coerceAtLeast(1)
        }
        if (n == 0) return DEFAULT
        val swatches = (0 until 4096).filter { counts[it] > 0 }.map { bin ->
            val k = counts[bin]
            Swatch(rgb((sumR[bin] / k).toInt(), (sumG[bin] / k).toInt(), (sumB[bin] / k).toInt()), k.toFloat() / n)
        }
        return build(swatches, (lumSum / n).toFloat())
    }

    /** From WallpaperColors' primary/secondary/tertiary (nullable) colours. */
    fun fromWallpaperColors(primary: Int, secondary: Int?, tertiary: Int?, supportsDarkText: Boolean): ThemePalette {
        val sw = listOfNotNull(Swatch(primary or OPAQUE, 0.6f), secondary?.let { Swatch(it or OPAQUE, 0.25f) }, tertiary?.let { Swatch(it or OPAQUE, 0.15f) })
        val lum = if (supportsDarkText) 0.75f else luminance(primary)
        return build(sw, lum)
    }

    private class Swatch(val color: Int, val share: Float)

    private fun build(swatches: List<Swatch>, meanLum: Float): ThemePalette {
        val dominant = swatches.maxByOrNull { it.share }?.color ?: return DEFAULT
        // Vivid = saturated, mid-light, and not a speck: share is weighted with a square root so a
        // large calm area does not always beat a smaller vivid one.
        val vivid = swatches
            .filter { it.share >= 0.004f }
            .maxByOrNull { s ->
                val hsl = toHsl(s.color)
                val lightnessFit = 1f - abs(hsl[2] - 0.55f) * 1.6f
                hsl[1] * lightnessFit.coerceAtLeast(0.05f) * kotlin.math.sqrt(s.share)
            }?.color ?: dominant
        val accentHsl = toHsl(vivid)
        val accent = if (accentHsl[1] < 0.12f) {
            DEFAULT.accent // grey wallpaper: keep the system blue rather than a muddy grey accent
        } else {
            fromHsl(accentHsl[0], accentHsl[1].coerceIn(0.55f, 0.95f), accentHsl[2].coerceIn(0.48f, 0.62f))
        }
        val domHsl = toHsl(dominant)
        val tint = fromHsl(domHsl[0], (domHsl[1] * 0.45f).coerceAtMost(0.35f), 0.13f)
        return ThemePalette(accent, tint, meanLum < 0.5f)
    }

    // ---- colour maths ------------------------------------------------------------------------

    private const val OPAQUE = 0xFF000000.toInt()

    fun rgb(r: Int, g: Int, b: Int): Int = OPAQUE or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

    fun luminance(c: Int): Float = luminance((c shr 16) and 0xFF, (c shr 8) and 0xFF, c and 0xFF)

    private fun luminance(r: Int, g: Int, b: Int): Float = (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f

    /** [h 0..360, s 0..1, l 0..1] */
    fun toHsl(c: Int): FloatArray {
        val r = ((c shr 16) and 0xFF) / 255f
        val g = ((c shr 8) and 0xFF) / 255f
        val b = (c and 0xFF) / 255f
        val mx = max(r, max(g, b))
        val mn = min(r, min(g, b))
        val l = (mx + mn) / 2f
        if (mx == mn) return floatArrayOf(0f, 0f, l)
        val d = mx - mn
        val s = if (l > 0.5f) d / (2f - mx - mn) else d / (mx + mn)
        val h = when (mx) {
            r -> ((g - b) / d + (if (g < b) 6f else 0f))
            g -> ((b - r) / d + 2f)
            else -> ((r - g) / d + 4f)
        } * 60f
        return floatArrayOf(h, s, l)
    }

    fun fromHsl(h: Float, s: Float, l: Float): Int {
        val c = (1f - abs(2f * l - 1f)) * s
        val hp = (h % 360f + 360f) % 360f / 60f
        val x = c * (1f - abs(hp % 2f - 1f))
        val (r1, g1, b1) = when (hp.toInt()) {
            0 -> Triple(c, x, 0f)
            1 -> Triple(x, c, 0f)
            2 -> Triple(0f, c, x)
            3 -> Triple(0f, x, c)
            4 -> Triple(x, 0f, c)
            else -> Triple(c, 0f, x)
        }
        val m = l - c / 2f
        return rgb(((r1 + m) * 255f + 0.5f).toInt(), ((g1 + m) * 255f + 0.5f).toInt(), ((b1 + m) * 255f + 0.5f).toInt())
    }
}
