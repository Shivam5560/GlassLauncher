package com.shivam.glasslauncher.ui.glass

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** iOS 26-style material variants. Regular is frosted, Clear is transparent with stronger lensing. */
enum class GlassVariant { Regular, Clear }

/** Auto picks light or dark glass from the luminance of what is behind each panel. */
enum class GlassTone { Auto, Light, Dark }

/**
 * User-tunable parameters shared by every [GlassSurface]. Persisted by the settings repository and
 * provided through [LocalGlassStyle]. All lengths are in dp.
 */
@Immutable
data class GlassStyle(
    /** Variant used by large panels. Small controls always use [GlassVariant.Clear]. */
    val variant: GlassVariant = GlassVariant.Regular,
    val blurRadius: Float = 28f,
    /** ARGB base tint; its alpha is ignored in favour of [tintOpacity]. */
    val tintColor: Int = 0xFF1E2028.toInt(),
    val tintOpacity: Float = 0.30f,
    /** Strength of the rim light, inner glow and sheen (0..1). */
    val specular: Float = 0.65f,
    /** Lens displacement strength (0..1). */
    val refraction: Float = 0.6f,
    /** Width of the curved rim where refraction happens. */
    val rimWidth: Float = 14f,
    /** Colour fringing on the rim (0..1). */
    val chromaticAberration: Float = 0.35f,
    val tone: GlassTone = GlassTone.Auto,
    /** Specular angle follows the device tilt. */
    val tiltLighting: Boolean = true,
    /** Draw a copy of the wallpaper behind in-window panels when it is readable. */
    val wallpaperBackdrop: Boolean = true,
    /** Master switch for the AGSL lens shader; off = blur + tint + edge highlight only. */
    val liquidEffects: Boolean = true,
) {
    /** Concrete numbers for one variant. */
    fun resolve(variant: GlassVariant): ResolvedGlass = when (variant) {
        GlassVariant.Regular -> ResolvedGlass(
            blur = blurRadius.dp,
            tintOpacity = tintOpacity,
            refraction = (refraction * 14f).dp,
            rim = rimWidth.dp,
            chroma = chromaticAberration,
            specular = specular,
        )
        GlassVariant.Clear -> ResolvedGlass(
            blur = (blurRadius * 0.22f).dp,
            tintOpacity = tintOpacity * 0.35f,
            refraction = (refraction * 22f).dp,
            rim = rimWidth.dp,
            chroma = (chromaticAberration * 1.4f).coerceAtMost(1f),
            specular = (specular * 1.15f).coerceAtMost(1f),
        )
    }
}

@Immutable
data class ResolvedGlass(
    val blur: Dp,
    val tintOpacity: Float,
    val refraction: Dp,
    val rim: Dp,
    val chroma: Float,
    val specular: Float,
)
