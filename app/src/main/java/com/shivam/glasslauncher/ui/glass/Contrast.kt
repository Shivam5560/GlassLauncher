package com.shivam.glasslauncher.ui.glass

import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/**
 * WCAG 2.x contrast maths used to pick text colours on glass. Pure functions on ARGB ints and
 * floats (no Android types) so they are unit-tested.
 *
 * Luminance sampled from the wallpaper (`WallpaperSource`) is a weighted average of gamma-encoded
 * sRGB channels, so it is converted with [gammaToLinear] before it is mixed with the glass tint.
 */
object Contrast {

    /** Light text on dark glass. */
    const val LIGHT_TEXT: Int = 0xFFFFFFFF.toInt()
    /** Dark text on light glass (near black; any lighter and the crossover drops below AA). */
    const val DARK_TEXT: Int = 0xFF050507.toInt()

    /** WCAG AA for normal-size text. */
    const val AA = 4.5f

    fun channelToLinear(c: Float): Float =
        if (c <= 0.04045f) c / 12.92f else ((c + 0.055f) / 1.055f).toDouble().pow(2.4).toFloat()

    /** Gamma-encoded luminance (0..1) to linear relative luminance. */
    fun gammaToLinear(l: Float): Float = channelToLinear(l.coerceIn(0f, 1f))

    /** WCAG relative luminance of an opaque ARGB colour (alpha ignored). */
    fun relativeLuminance(argb: Int): Float {
        val r = channelToLinear(((argb shr 16) and 0xFF) / 255f)
        val g = channelToLinear(((argb shr 8) and 0xFF) / 255f)
        val b = channelToLinear((argb and 0xFF) / 255f)
        return 0.2126f * r + 0.7152f * g + 0.0722f * b
    }

    /** Contrast ratio between two relative luminances (1..21). */
    fun ratio(l1: Float, l2: Float): Float = (max(l1, l2) + 0.05f) / (min(l1, l2) + 0.05f)

    fun ratio(fg: Int, bgLinear: Float): Float = ratio(relativeLuminance(fg), bgLinear)

    /**
     * Linear luminance of glass: [tintArgb] at [tintAmount] over a backdrop of linear luminance
     * [backdropLinear] (the blur keeps the average, so mixing luminances is a fair estimate).
     */
    fun glassLuminance(backdropLinear: Float, tintArgb: Int, tintAmount: Float): Float {
        val a = tintAmount.coerceIn(0f, 1f)
        return backdropLinear.coerceIn(0f, 1f) * (1f - a) + relativeLuminance(tintArgb) * a
    }

    /**
     * The text colour with the higher contrast on a background of linear luminance [bgLinear]:
     * white on dark glass, near-black on light glass. Never white on light. The worse of the two
     * choices is still at least about 4.5:1 at the crossover, so AA holds for any background.
     */
    fun pickText(bgLinear: Float): Int =
        if (ratio(LIGHT_TEXT, bgLinear) >= ratio(DARK_TEXT, bgLinear)) LIGHT_TEXT else DARK_TEXT

    /** [pickText] for glass with the given tint over a gamma-encoded backdrop luminance. */
    fun textOnGlass(backdropGamma: Float, tintArgb: Int, tintAmount: Float): Int =
        pickText(glassLuminance(gammaToLinear(backdropGamma), tintArgb, tintAmount))

    /** Linear luminance above which white text is below AA (1.05 / 4.5 - 0.05, about 0.183). */
    val WHITE_AA_MAX_LUMINANCE: Float = 1.05f / AA - 0.05f

    /** Most black the legibility scrim may add, so thin glass stays clear over bright wallpapers. */
    const val SCRIM_MAX = 0.55f

    /** Glass whose effective tint is below this is "thin" and gets the legibility scrim. */
    const val THIN_GLASS_TINT = 0.3f

    /** Gamma-encoded sRGB value of linear luminance [l] (inverse of [gammaToLinear]). */
    fun linearToGamma(l: Float): Float {
        val c = l.coerceIn(0f, 1f)
        return if (c <= 0.0031308f) c * 12.92f else 1.055f * c.toDouble().pow(1.0 / 2.4).toFloat() - 0.055f
    }

    /**
     * Black scrim alpha (0..[SCRIM_MAX]) for thin glass with gamma-encoded luminance [glassGamma].
     * Zero while white text already meets AA (dark and mid backdrops), then the least darkening that
     * brings white text to AA, capped at [SCRIM_MAX]. The cap is high enough that white text passes AA even over a pure white backdrop (needs about 0.535).
     * Works in gamma space, the same space compositing blends a black overlay in.
     */
    fun legibilityScrim(glassGamma: Float): Float {
        val g = glassGamma.coerceIn(0f, 1f)
        val limit = linearToGamma(WHITE_AA_MAX_LUMINANCE)
        if (g <= limit) return 0f
        return (1f - limit / g).coerceAtMost(SCRIM_MAX)
    }

    /** Linear luminance of glass [glassLinear] after a black scrim of alpha [scrim] (composited in gamma space). */
    fun scrimmed(glassLinear: Float, scrim: Float): Float =
        gammaToLinear(linearToGamma(glassLinear) * (1f - scrim.coerceIn(0f, 1f)))

    /** True when [fg] on [bgArgb] meets WCAG AA. */
    fun meetsAA(fg: Int, bgArgb: Int): Boolean = ratio(relativeLuminance(fg), relativeLuminance(bgArgb)) >= AA
}
