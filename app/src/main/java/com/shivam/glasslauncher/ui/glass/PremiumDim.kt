package com.shivam.glasslauncher.ui.glass

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color

/**
 * Balanced black dim behind text-heavy screens (Today, App Library, Spotlight, notification centre,
 * darker home pages). The alpha is the least dim that keeps white text at WCAG AA over the
 * dimmed wallpaper, so bright wallpapers get a firm dim and already-dark ones only a light touch.
 * The input is the wallpaper luminance from [WallpaperSource.Snapshot.luminance] (gamma-encoded, 0..1).
 */
object PremiumDim {
    /** Floor: dark wallpapers keep nearly all their detail. */
    const val MIN_ALPHA = 0.12f

    /** Ceiling guard. The AA formula never exceeds about 0.817 for luminance <= 1, so this only backs the clamp. */
    const val MAX_ALPHA = 0.85f

    /** Largest linear luminance under the dim that still gives white text AA: (1.05 / AA) - 0.05. */
    private val MAX_WHITE_TEXT_LINEAR = 1.05f / Contrast.AA - 0.05f

    /**
     * Black dim alpha for a wallpaper of gamma-encoded luminance [luminance]. Solves
     * (1 - a) * Y <= MAX_WHITE_TEXT_LINEAR for a, then clamps to [MIN_ALPHA, MAX_ALPHA].
     * Monotonic non-decreasing in luminance.
     */
    fun alphaFor(luminance: Float): Float {
        val y = Contrast.gammaToLinear(luminance)
        if (y <= 0f) return MIN_ALPHA
        return (1f - MAX_WHITE_TEXT_LINEAR / y).coerceIn(MIN_ALPHA, MAX_ALPHA)
    }
}

/** Draws the black dim at [alpha] (0..1) behind this node. Does not consume pointer input. */
fun Modifier.premiumDim(alpha: Float): Modifier = drawBehind {
    drawRect(Color.Black.copy(alpha = alpha.coerceIn(0f, 1f)))
}
