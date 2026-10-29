package com.shivam.glasslauncher.ui.glass

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.json.JSONObject

/** iOS 26-style material variants. Regular is frosted, Clear is transparent with stronger lensing. */
enum class GlassVariant { Regular, Clear }

/**
 * Glass tone. Auto is dark-tinted glass (lightened only over light backdrops in light mode); the
 * text colour always follows the measured contrast ([Contrast]), whatever the tone.
 */
enum class GlassTone { Auto, Light, Dark }

/**
 * What a glass surface is, for the Global appearance editor's per-role overrides
 * (Settings → Appearance → Edit theme). A [GlassSurface] without an explicit kind is [Panel]
 * when Regular and [Cards] when Clear.
 */
enum class SurfaceKind(val title: String) {
    /** Full-screen backgrounds: the dim behind Settings and the app drawer. */
    Background("Background"),
    /** Buttons, chips, switches, list cards and small controls. */
    Cards("Cards & buttons"),
    Widget("Widget"),
    /** Dock, folders, menus, sheets, the drawer tab bar and other large panels. */
    Panel("Panel"),
}

/** Launcher theme presets (Settings → Appearance). */
enum class ThemePreset(val title: String) { Solid("Solid"), Frosted("Frosted"), Liquid("Liquid Glass"), Custom("Custom") }

/** Per-role overrides; null means "use the global glass value". */
@Immutable
data class RoleStyle(
    /** Tint colour (ARGB, alpha ignored). */
    val color: Int? = null,
    /** Tint opacity 0..1. */
    val opacity: Float? = null,
    /** Text and icon colour; null picks white or black by measured contrast. */
    val contentColor: Int? = null,
    /** Blur radius in dp. */
    val blur: Float? = null,
    /** Outline width in dp; 0 = none. */
    val stroke: Float = 0f,
    /** Accent for switches, sliders, prominent buttons inside this role. */
    val accent: Int? = null,
) {
    val isDefault: Boolean get() = this == RoleStyle()

    fun toJson(): JSONObject = JSONObject().apply {
        color?.let { put("color", it) }
        opacity?.let { put("opacity", it.toDouble()) }
        contentColor?.let { put("content", it) }
        blur?.let { put("blur", it.toDouble()) }
        put("stroke", stroke.toDouble())
        accent?.let { put("accent", it) }
    }

    companion object {
        fun fromJson(o: JSONObject) = RoleStyle(
            color = if (o.has("color")) o.getInt("color") else null,
            opacity = if (o.has("opacity")) o.getDouble("opacity").toFloat() else null,
            contentColor = if (o.has("content")) o.getInt("content") else null,
            blur = if (o.has("blur")) o.getDouble("blur").toFloat() else null,
            stroke = o.optDouble("stroke", 0.0).toFloat(),
            accent = if (o.has("accent")) o.getInt("accent") else null,
        )

        /** Map of role overrides to a compact JSON string (default roles are left out). */
        fun encode(roles: Map<SurfaceKind, RoleStyle>): String {
            val root = JSONObject()
            roles.filterValues { !it.isDefault }.forEach { (k, v) -> root.put(k.name, v.toJson()) }
            return root.toString()
        }

        fun decode(text: String?): Map<SurfaceKind, RoleStyle> {
            if (text.isNullOrBlank()) return emptyMap()
            return try {
                val root = JSONObject(text)
                SurfaceKind.entries.mapNotNull { k -> root.optJSONObject(k.name)?.let { k to fromJson(it) } }.toMap()
            } catch (e: Exception) {
                emptyMap()
            }
        }
    }
}

/**
 * User-tunable parameters shared by every [GlassSurface]. Persisted by the settings repository and
 * provided through [LocalGlassStyle]. All lengths are in dp.
 *
 * Phase 5 defaults are toned down (refraction 0.3, chroma 0.15, specular 0.45) and the tint is
 * darker so white text stays legible; text colour is picked by contrast in [GlassSurface].
 */
@Immutable
data class GlassStyle(
    /** Variant used by large panels. Small controls always use [GlassVariant.Clear]. */
    val variant: GlassVariant = GlassVariant.Regular,
    val blurRadius: Float = 28f,
    /** ARGB base tint; its alpha is ignored in favour of [tintOpacity]. */
    val tintColor: Int = 0xFF16171D.toInt(),
    val tintOpacity: Float = 0.42f,
    /** Strength of the rim light, inner glow and sheen (0..1). */
    val specular: Float = 0.45f,
    /** Lens displacement strength (0..1). */
    val refraction: Float = 0.3f,
    /** Width of the curved rim where refraction happens. */
    val rimWidth: Float = 14f,
    /** Colour fringing on the rim (0..1). */
    val chromaticAberration: Float = 0.15f,
    val tone: GlassTone = GlassTone.Dark,
    /** Specular angle follows the device tilt. */
    val tiltLighting: Boolean = false,
    /** Draw a copy of the wallpaper behind in-window panels when it is readable. */
    val wallpaperBackdrop: Boolean = true,
    /** Master switch for the AGSL lens shader; off = blur + tint + edge highlight only. */
    val liquidEffects: Boolean = true,
    /** Per-role overrides from the Global appearance editor. */
    val roles: Map<SurfaceKind, RoleStyle> = emptyMap(),
    /** Corner radius of widgets (dp). */
    val widgetCornerRadius: Float = 24f,
    val preset: ThemePreset = ThemePreset.Liquid,
) {
    fun role(kind: SurfaceKind): RoleStyle = roles[kind] ?: RoleStyle()

    /**
     * Tint opacity a role uses when the user has not set one. Like iOS: widgets are dense dark glass
     * and the dim behind the drawer is firm; other surfaces use the global [tintOpacity].
     */
    fun defaultOpacity(kind: SurfaceKind): Float = when (kind) {
        SurfaceKind.Widget -> maxOf(tintOpacity, 0.86f)
        SurfaceKind.Background -> 0.5f
        else -> tintOpacity
    }

    fun withRole(kind: SurfaceKind, role: RoleStyle): GlassStyle =
        copy(roles = if (role.isDefault) roles - kind else roles + (kind to role), preset = ThemePreset.Custom)

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

    companion object {
        /** Applies a preset's material values, keeping colours, roles and other choices. */
        fun applyPreset(base: GlassStyle, preset: ThemePreset): GlassStyle {
            val d = GlassStyle()
            return when (preset) {
                ThemePreset.Solid -> base.copy(
                    preset = preset, blurRadius = 0f, tintOpacity = 0.92f, refraction = 0f, chromaticAberration = 0f,
                    specular = 0.2f, liquidEffects = false, wallpaperBackdrop = false,
                )
                ThemePreset.Frosted -> base.copy(
                    preset = preset, blurRadius = 34f, tintOpacity = 0.5f, refraction = 0f, chromaticAberration = 0f,
                    specular = 0.3f, liquidEffects = false, wallpaperBackdrop = true,
                )
                ThemePreset.Liquid -> base.copy(
                    preset = preset, blurRadius = d.blurRadius, tintOpacity = d.tintOpacity, refraction = d.refraction,
                    chromaticAberration = d.chromaticAberration, specular = d.specular, liquidEffects = true, wallpaperBackdrop = true,
                )
                ThemePreset.Custom -> base.copy(preset = preset)
            }
        }
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
