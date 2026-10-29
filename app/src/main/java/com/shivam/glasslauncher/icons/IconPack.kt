package com.shivam.glasslauncher.icons

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Resources
import android.graphics.drawable.Drawable
import android.util.Log
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory

data class IconPackInfo(val packageName: String, val label: String)

/**
 * Artwork variant of a pack drawable. Optional convention (documented in README):
 * for a drawable `name` mapped in appfilter.xml, `name_dark` is the dark-tile version and
 * `name_tinted` is a white monochrome glyph on transparent that the launcher puts on its own tile
 * and tints. Packs without these drawables simply fall back to `name`.
 */
enum class IconVariant(val suffix: String) { Light(""), Dark("_dark"), Tinted("_tinted") }

/** A drawable resolved from a pack, and which variant it actually is. */
class PackDrawable(val drawable: Drawable, val variant: IconVariant)

/**
 * A parsed ADW/Nova-style icon pack: `appfilter.xml` maps `ComponentInfo{pkg/cls}` to drawable
 * names; `iconback`, `iconmask`, `iconupon` and `scale` describe how to theme unmapped icons.
 */
class IconPack(
    val packageName: String,
    private val res: Resources,
    private val components: Map<String, String>,
    private val packages: Map<String, String>,
    private val backs: List<String>,
    private val mask: String?,
    private val upon: String?,
    val scale: Float,
) {
    val hasBack: Boolean get() = backs.isNotEmpty()

    @SuppressLint("DiscouragedApi")
    private fun drawable(name: String, density: Int): Drawable? {
        val id = res.getIdentifier(name, "drawable", packageName).takeIf { it != 0 }
            ?: res.getIdentifier(name, "mipmap", packageName).takeIf { it != 0 }
            ?: return null
        return try { res.getDrawableForDensity(id, density, null) } catch (e: Exception) { null }
    }

    fun iconFor(component: ComponentName, density: Int): Drawable? =
        iconFor(component, density, IconVariant.Light)?.drawable

    /** The mapped drawable name for [component], or null if the pack doesn't cover it. */
    fun drawableNameFor(component: ComponentName): String? =
        components["${component.packageName}/${component.className}"] ?: packages[component.packageName]

    /** The mapped icon in [variant] if the pack has it, else its base drawable. */
    fun iconFor(component: ComponentName, density: Int, variant: IconVariant): PackDrawable? =
        drawableNameFor(component)?.let { named(it, density, variant) }

    fun named(name: String, density: Int, variant: IconVariant): PackDrawable? {
        if (variant != IconVariant.Light) drawable(name + variant.suffix, density)?.let { return PackDrawable(it, variant) }
        return drawable(name, density)?.let { PackDrawable(it, IconVariant.Light) }
    }

    /**
     * Every icon the pack offers, for the per-app icon picker: `drawable.xml` if the pack ships one,
     * else the drawables named in appfilter.xml. Variant drawables are left out.
     */
    @SuppressLint("DiscouragedApi")
    fun allDrawableNames(): List<String> {
        val names = LinkedHashSet<String>()
        val id = res.getIdentifier("drawable", "xml", packageName)
        if (id != 0) {
            try {
                val parser = res.getXml(id)
                var event = parser.eventType
                while (event != XmlPullParser.END_DOCUMENT) {
                    if (event == XmlPullParser.START_TAG && parser.name == "item") {
                        parser.getAttributeValue(null, "drawable")?.let { names += it }
                    }
                    event = parser.next()
                }
                parser.close()
            } catch (e: Exception) {
                Log.w(TAG, "Bad drawable.xml in $packageName", e)
            }
        }
        if (names.isEmpty()) names += components.values
        return names.filterNot { n -> IconVariant.entries.any { v -> v != IconVariant.Light && n.endsWith(v.suffix) } }
    }

    /** Tile behind an unmapped icon, in the same variant as the mapped ones (Tinted sits on Dark). */
    fun backFor(seed: Int, density: Int, variant: IconVariant = IconVariant.Light): Drawable? {
        if (backs.isEmpty()) return null
        val v = if (variant == IconVariant.Tinted) IconVariant.Dark else variant
        return named(backs[Math.floorMod(seed, backs.size)], density, v)?.drawable
    }

    fun mask(density: Int): Drawable? = mask?.let { drawable(it, density) }
    fun upon(density: Int): Drawable? = upon?.let { drawable(it, density) }

    companion object {
        private const val TAG = "IconPack"

        val THEME_INTENTS = listOf(
            "org.adw.launcher.THEMES",
            "com.novalauncher.THEME",
            "com.gau.go.launcherex.theme",
            "com.anddoes.launcher.THEME",
            "com.teslacoilsw.launcher.THEME",
        )

        /** Installed icon packs, detected through the standard theme intent filters. */
        fun installed(context: Context): List<IconPackInfo> {
            val pm = context.packageManager
            val found = LinkedHashMap<String, IconPackInfo>()
            for (action in THEME_INTENTS) {
                val list = try {
                    pm.queryIntentActivities(Intent(action), PackageManager.MATCH_ALL)
                } catch (e: Exception) { emptyList() }
                for (ri in list) {
                    val pkg = ri.activityInfo.packageName
                    if (pkg !in found) found[pkg] = IconPackInfo(pkg, ri.loadLabel(pm).toString())
                }
            }
            return found.values.sortedBy { it.label.lowercase() }
        }

        /** Loads and parses a pack; returns null if it cannot be read. Call off the main thread. */
        @SuppressLint("DiscouragedApi")
        fun load(context: Context, packageName: String): IconPack? {
            val res = try {
                context.packageManager.getResourcesForApplication(packageName)
            } catch (e: PackageManager.NameNotFoundException) {
                return null
            }
            val parser: XmlPullParser = run {
                val id = res.getIdentifier("appfilter", "xml", packageName)
                if (id != 0) {
                    res.getXml(id)
                } else {
                    try {
                        val stream = res.assets.open("appfilter.xml")
                        XmlPullParserFactory.newInstance().newPullParser().apply { setInput(stream, "UTF-8") }
                    } catch (e: Exception) {
                        Log.w(TAG, "No appfilter in $packageName")
                        return null
                    }
                }
            }
            val components = HashMap<String, String>()
            val packages = HashMap<String, String>()
            val backs = ArrayList<String>()
            var mask: String? = null
            var upon: String? = null
            var scale = 1f
            try {
                var event = parser.eventType
                while (event != XmlPullParser.END_DOCUMENT) {
                    if (event == XmlPullParser.START_TAG) {
                        when (parser.name) {
                            "item" -> {
                                val comp = parser.getAttributeValue(null, "component")
                                val drawable = parser.getAttributeValue(null, "drawable")
                                if (comp != null && !drawable.isNullOrEmpty()) {
                                    parseComponent(comp)?.let { (pkg, cls) ->
                                        components.putIfAbsent("$pkg/$cls", drawable)
                                        packages.putIfAbsent(pkg, drawable)
                                    }
                                }
                            }
                            "iconback" -> for (i in 0 until parser.attributeCount) {
                                if (parser.getAttributeName(i).startsWith("img")) backs += parser.getAttributeValue(i)
                            }
                            "iconmask" -> mask = parser.getAttributeValue(null, "img1") ?: mask
                            "iconupon" -> upon = parser.getAttributeValue(null, "img1") ?: upon
                            "scale" -> scale = parser.getAttributeValue(null, "factor")?.toFloatOrNull() ?: scale
                        }
                    }
                    event = parser.next()
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse appfilter of $packageName", e)
            }
            return IconPack(packageName, res, components, packages, backs, mask, upon, scale)
        }

        /** "ComponentInfo{com.app/com.app.Main}" -> (com.app, com.app.Main); expands ".Main". */
        fun parseComponent(text: String): Pair<String, String>? {
            val inner = text.substringAfter("{", "").substringBefore("}", "")
            val slash = inner.indexOf('/')
            if (slash <= 0 || slash == inner.length - 1) return null
            val pkg = inner.substring(0, slash)
            var cls = inner.substring(slash + 1)
            if (cls.startsWith(".")) cls = pkg + cls
            return pkg to cls
        }
    }
}

/** Adaptive icon shape for icons Glass masks itself (Settings → Appearance → Icons). */
enum class IconShape(val title: String) { Squircle("Squircle"), Circle("Circle"), RoundedSquare("Rounded square"), Teardrop("Teardrop") }
