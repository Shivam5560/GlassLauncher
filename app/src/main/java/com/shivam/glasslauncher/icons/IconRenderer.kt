package com.shivam.glasslauncher.icons

import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.util.LruCache
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.shivam.glasslauncher.data.AppEntry
import com.shivam.glasslauncher.ui.glass.superellipseRectPath
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Renders app icons: icon-pack drawable if mapped; otherwise the app's own icon masked to an
 * iOS-style squircle (superellipse n=5), themed with the pack's iconback/mask/upon when present.
 * Results are cached; [generation] changes when the pack changes so UI reloads.
 */
class IconRenderer(private val context: Context, private val scope: CoroutineScope) {

    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val cache = object : LruCache<String, ImageBitmap>(48 * 1024 * 1024) {
        override fun sizeOf(key: String, value: ImageBitmap) = value.width * value.height * 4
    }
    @Volatile private var pack: IconPack? = null
    private val _generation = MutableStateFlow(0)
    val generation: StateFlow<Int> = _generation.asStateFlow()
    private var currentPackName = ""

    fun setIconPack(packageName: String) {
        if (packageName == currentPackName) return
        currentPackName = packageName
        scope.launch(Dispatchers.IO) {
            pack = if (packageName.isEmpty()) null else IconPack.load(context, packageName)
            cache.evictAll()
            _generation.value++
        }
    }

    @Volatile private var variant = IconVariant.Light
    @Volatile private var tintColor = Color.WHITE
    @Volatile private var overrides: Map<String, IconOverride> = emptyMap()
    @Volatile var customDir: java.io.File? = null
    private val packs = HashMap<String, IconPack?>()

    /** Icon style (Phase 4): which variant of pack artwork to use, and the tinted glyph colour. */
    fun setStyle(variant: IconVariant, tintColor: Int) {
        if (variant == this.variant && tintColor == this.tintColor) return
        this.variant = variant
        this.tintColor = tintColor
        cache.evictAll()
        _generation.value++
    }

    @Volatile private var systemAppIcons = false
    @Volatile private var greyscale = false

    fun setGreyscale(on: Boolean) {
        if (on == greyscale) return
        greyscale = on
        cache.evictAll()
        _generation.value++
    }

    /** With no pack chosen, core system apps take Glass Icons' iOS-style art; others keep their logo. */
    fun setSystemAppIcons(on: Boolean) {
        if (on == systemAppIcons) return
        systemAppIcons = on
        cache.evictAll()
        _generation.value++
    }

    @Volatile private var shape = IconShape.Squircle

    /** Adaptive icon shape (Phase 5, Settings → Appearance → Icons). */
    fun setShape(shape: IconShape) {
        if (shape == this.shape) return
        this.shape = shape
        cache.evictAll()
        _generation.value++
    }

    /** Outline of the current [IconShape] for a [size]-px icon. */
    private fun shapePath(size: Float): android.graphics.Path = when (shape) {
        IconShape.Squircle -> superellipseRectPath(size, size, size / 2f, 5f)
        IconShape.Circle -> android.graphics.Path().apply { addCircle(size / 2f, size / 2f, size / 2f, android.graphics.Path.Direction.CW) }
        IconShape.RoundedSquare -> superellipseRectPath(size, size, size * 0.22f, 2f)
        IconShape.Teardrop -> android.graphics.Path().apply {
            val r = size / 2f
            val small = size * 0.16f
            addRoundRect(0f, 0f, size, size, floatArrayOf(r, r, r, r, small, small, r, r), android.graphics.Path.Direction.CW)
        }
    }

    /** Per-app icons chosen with long-press → Edit Icon. */
    fun setOverrides(map: Map<String, IconOverride>) {
        if (map == overrides) return
        overrides = map
        cache.evictAll()
        _generation.value++
    }

    /** Any installed pack, parsed once and cached. Call off the main thread. */
    fun loadPack(packageName: String): IconPack? = synchronized(packs) {
        if (packageName == currentPackName) pack?.let { return it }
        packs.getOrPut(packageName) { IconPack.load(context, packageName) }
    }

    /** A pack drawable rendered the way the home screen would show it (current variant). */
    fun renderPackIcon(packageName: String, drawable: String, size: Int): ImageBitmap? {
        val pd = loadPack(packageName)?.named(drawable, context.resources.displayMetrics.densityDpi, variant) ?: return null
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        drawPack(Canvas(out), pd, size)
        return out.asImageBitmap()
    }

    fun invalidatePackage(pkg: String?) {
        synchronized(packs) { if (pkg == null) packs.clear() else packs.remove(pkg) }
        if (pkg == null) cache.evictAll() else {
            cache.snapshot().keys.filter { it.contains(pkg) }.forEach { cache.remove(it) }
        }
        _generation.value++
    }

    fun cached(entry: AppEntry, sizePx: Int): ImageBitmap? = cache.get(cacheKey(entry, sizePx))

    suspend fun icon(entry: AppEntry, sizePx: Int): ImageBitmap {
        val key = cacheKey(entry, sizePx)
        cache.get(key)?.let { return it }
        return withContext(Dispatchers.Default) {
            val bmp = render(entry, sizePx).asImageBitmap()
            cache.put(key, bmp)
            bmp
        }
    }

    private fun cacheKey(entry: AppEntry, sizePx: Int) = "${entry.key}#$sizePx#${_generation.value}"

    private fun render(entry: AppEntry, size: Int): Bitmap {
        val density = context.resources.displayMetrics.densityDpi
        val p = pack
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        val overridden = overrides[entry.key]?.let { renderOverride(canvas, it, size, density) } ?: false
        val mapped = when {
            overridden -> null
            p != null -> p.iconFor(entry.component, density, variant)
            systemAppIcons && isCoreSystemApp(entry) -> loadPack(SYSTEM_ART_PACK)?.iconFor(entry.component, density, variant)
            else -> null
        }
        if (entry.packageName.startsWith("com.android.")) android.util.Log.d("GlassDBG", "icon ${entry.component.flattenToShortString()} sys=$systemAppIcons core=${isCoreSystemApp(entry)} pack=${p != null} mapped=${mapped != null} name=${loadPack(SYSTEM_ART_PACK)?.drawableNameFor(entry.component)}")
        if (overridden) {
            // drawn above
        } else if (mapped != null) {
            drawPack(canvas, mapped, size)
        } else {
            val raw = try { entry.info.getIcon(density) } catch (e: Exception) { null }
                ?: context.packageManager.defaultActivityIcon
            if (p != null && p.hasBack) {
                renderThemed(canvas, raw, p, entry.component.hashCode(), size, density)
                maskToSquircle(out)
            } else {
                renderSquircle(canvas, raw, size)
                drawGlassFinish(canvas, size)
            }
        }
        if (entry.isWorkProfile) {
            val badged = context.packageManager.getUserBadgedIcon(BitmapDrawable(context.resources, out), entry.user)
            val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            drawDrawable(Canvas(b), badged, 0, 0, size, size)
            return grey(b)
        }
        return grey(out)
    }

    private fun grey(src: Bitmap): Bitmap {
        if (!greyscale) return src
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        Canvas(out).drawBitmap(src, 0f, 0f, Paint().apply { colorFilter = android.graphics.ColorMatrixColorFilter(android.graphics.ColorMatrix().apply { setSaturation(0f) }) })
        return out
    }

    private fun renderOverride(canvas: Canvas, o: IconOverride, size: Int, density: Int): Boolean = when (o) {
        is IconOverride.Pack -> {
            val pd = loadPack(o.packageName)?.named(o.drawable, density, variant)
            if (pd != null) drawPack(canvas, pd, size)
            pd != null
        }
        is IconOverride.Custom -> {
            val f = customDir?.let { java.io.File(it, o.file) }
            val bmp = f?.takeIf { it.exists() }?.let { android.graphics.BitmapFactory.decodeFile(it.path) }
            if (bmp != null) {
                canvas.save()
                canvas.clipPath(shapePath(size.toFloat()))
                canvas.drawBitmap(bmp, null, android.graphics.Rect(0, 0, size, size), Paint(Paint.FILTER_BITMAP_FLAG))
                canvas.restore()
            }
            bmp != null
        }
    }

    /** Pack artwork; a tinted glyph goes on our own dark glass tile, coloured with the tint colour. */
    private fun drawPack(canvas: Canvas, pd: PackDrawable, size: Int) {
        if (pd.variant != IconVariant.Tinted) {
            drawDrawable(canvas, pd.drawable, 0, 0, size, size)
            return
        }
        val s = size.toFloat()
        canvas.save()
        canvas.clipPath(shapePath(s))
        canvas.drawPaint(Paint().apply {
            shader = android.graphics.LinearGradient(0f, 0f, 0f, s, 0xFF303136.toInt(), 0xFF111113.toInt(), android.graphics.Shader.TileMode.CLAMP)
        })
        canvas.restore()
        val glyph = pd.drawable.mutate()
        glyph.colorFilter = android.graphics.PorterDuffColorFilter(tintColor, PorterDuff.Mode.SRC_IN)
        drawDrawable(canvas, glyph, 0, 0, size, size)
        glyph.colorFilter = null
    }

    /** Adaptive icons: background + foreground layers expanded so the 72/108 safe zone fills the squircle. */
    private fun renderSquircle(canvas: Canvas, icon: Drawable, size: Int) {
        val clip = shapePath(size.toFloat())
        canvas.save()
        canvas.clipPath(clip)
        if (icon is AdaptiveIconDrawable && variant == IconVariant.Dark && renderDarkAdaptive(canvas, icon, size)) {
            // drawn in the iOS dark appearance
        } else if (icon is AdaptiveIconDrawable) {
            val inset = (size * 0.25f).toInt()
            icon.background?.let { drawDrawable(canvas, it, -inset, -inset, size + inset, size + inset) }
                ?: canvas.drawColor(Color.WHITE)
            icon.foreground?.let { drawDrawable(canvas, it, -inset, -inset, size + inset, size + inset) }
        } else {
            val art = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            drawDrawable(Canvas(art), icon, 0, 0, size, size)
            val tile = solidTileBounds(art)
            if (tile != null) {
                // Old-style icon that is itself a filled tile (HyperOS Camera, Gallery, Music) inside
                // transparent padding: crop to it and fill the squircle edge to edge, overscanned a
                // little so its own rounded corners fall outside our shape.
                val over = size * 0.04f
                canvas.drawBitmap(art, tile, android.graphics.RectF(-over, -over, size + over, size + over), Paint(Paint.FILTER_BITMAP_FLAG or Paint.ANTI_ALIAS_FLAG))
            } else {
                // Odd-shaped artwork on a white tile, like iOS does.
                canvas.drawColor(Color.WHITE)
                val pad = (size * 0.14f).toInt()
                drawDrawable(canvas, icon, pad, pad, size - pad, size - pad)
            }
        }
        canvas.restore()
    }

    /**
     * iOS 26 dark appearance for an app's own adaptive icon, logo shape untouched: the logo moves
     * onto a dark glass tile. A light logo designed for a coloured background is filled with that
     * background (LinkedIn's "in" stays blue, Instagram's glyph keeps its gradient); a colourful logo
     * on a white background goes on as is (Gmail), with dark ink turned white. Icons that are
     * already dark are left alone (returns false).
     */
    private fun renderDarkAdaptive(canvas: Canvas, icon: AdaptiveIconDrawable, size: Int): Boolean {
        val bgLayer = icon.background ?: return false
        val fgLayer = icon.foreground ?: return false
        val inset = (size * 0.25f).toInt()
        fun layer(d: Drawable) = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also {
            drawDrawable(Canvas(it), d, -inset, -inset, size + inset, size + inset)
        }
        val bg = layer(bgLayer)
        val fg = layer(fgLayer)
        val bgStats = ColourStats.of(bg)
        if (bgStats.luminance < 0.18f) return false
        val fgStats = ColourStats.of(fg)
        // A foreground covering most of the icon is a tile of its own (Tumbledry), not a logo.
        if (fgStats.coverage > 0.6f) return false
        canvas.drawPaint(Paint().apply {
            shader = android.graphics.LinearGradient(0f, 0f, 0f, size.toFloat(), 0xFF2B2C31.toInt(), 0xFF0B0B0D.toInt(), android.graphics.Shader.TileMode.CLAMP)
        })
        val glyph = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val gc = Canvas(glyph)
        gc.drawBitmap(fg, 0f, 0f, null)
        when {
            // White/grey logo on a coloured background: paint the logo with that background.
            fgStats.saturation < 0.18f && fgStats.luminance > 0.6f && bgStats.saturation >= 0.18f ->
                gc.drawBitmap(bg, 0f, 0f, Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN) })
            // Dark ink on a light background would vanish on the dark tile: make it white.
            fgStats.luminance < 0.3f && fgStats.saturation < 0.25f ->
                gc.drawColor(Color.WHITE, PorterDuff.Mode.SRC_IN)
        }
        canvas.drawBitmap(glyph, 0f, 0f, null)
        return true
    }

    /** Average saturation and luminance of the visible pixels of a layer, and how much of it they cover (sampled). */
    private class ColourStats(val saturation: Float, val luminance: Float, val coverage: Float) {
        companion object {
            fun of(bmp: Bitmap): ColourStats {
                val step = (bmp.width / 24).coerceAtLeast(1)
                var n = 0; var total = 0; var sat = 0f; var lum = 0f
                val hsv = FloatArray(3)
                for (y in 0 until bmp.height step step) for (x in 0 until bmp.width step step) {
                    val c = bmp.getPixel(x, y)
                    total++
                    if ((c ushr 24) < 128) continue
                    Color.colorToHSV(c, hsv)
                    sat += hsv[1] * hsv[2]
                    lum += (0.2126f * Color.red(c) + 0.7152f * Color.green(c) + 0.0722f * Color.blue(c)) / 255f
                    n++
                }
                return if (n == 0) ColourStats(0f, 0f, 0f) else ColourStats(sat / n, lum / n, n.toFloat() / total)
            }
        }
    }

    /**
     * Bounds of the visible art when it is a filled tile (opaque at the middle of each edge of its
     * bounding box and at its centre), else null for free-form logos. Needs the art to cover at
     * least 60% of the width, so small glyphs are never blown up.
     */
    private fun solidTileBounds(art: Bitmap): android.graphics.Rect? {
        val w = art.width; val h = art.height
        val px = IntArray(w * h)
        art.getPixels(px, 0, w, 0, 0, w, h)
        var l = w; var t = h; var r = -1; var b = -1
        for (y in 0 until h) for (x in 0 until w) {
            if ((px[y * w + x] ushr 24) > 40) { if (x < l) l = x; if (x > r) r = x; if (y < t) t = y; if (y > b) b = y }
        }
        if (r < 0 || (r - l + 1) < w * 0.6f || (b - t + 1) < h * 0.6f) return null
        fun opaque(x: Int, y: Int) = (px[y.coerceIn(0, h - 1) * w + x.coerceIn(0, w - 1)] ushr 24) > 200
        val cx = (l + r) / 2; val cy = (t + b) / 2; val inset = 2
        val solid = opaque(cx, cy) && opaque(l + inset, cy) && opaque(r - inset, cy) && opaque(cx, t + inset) && opaque(cx, b - inset)
        return if (solid) android.graphics.Rect(l, t, r + 1, b + 1) else null
    }

    private fun renderThemed(canvas: Canvas, icon: Drawable, pack: IconPack, seed: Int, size: Int, density: Int) {
        pack.backFor(seed, density, variant)?.let { drawDrawable(canvas, it, 0, 0, size, size) }
        // icon scaled by the pack factor, punched out by the mask
        val layer = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val lc = Canvas(layer)
        val s = (size * pack.scale).toInt()
        val o = (size - s) / 2
        if (icon is AdaptiveIconDrawable) {
            val tmp = Bitmap.createBitmap(s, s, Bitmap.Config.ARGB_8888)
            renderSquircle(Canvas(tmp), icon, s)
            lc.drawBitmap(tmp, o.toFloat(), o.toFloat(), null)
        } else {
            drawDrawable(lc, icon, o, o, o + s, o + s)
        }
        pack.mask(density)?.let { mask ->
            val maskBmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            drawDrawable(Canvas(maskBmp), mask, 0, 0, size, size)
            lc.drawBitmap(maskBmp, 0f, 0f, Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_OUT) })
        }
        canvas.drawBitmap(layer, 0f, 0f, null)
        pack.upon(density)?.let { drawDrawable(canvas, it, 0, 0, size, size) }
    }

    /**
     * iOS 26-style glass over an app's own icon, logo and colours untouched: a sheen across the top,
     * a faint shade at the bottom for depth, and a specular rim lit from the top-left that runs
     * faint along the sides and glints again at the bottom-right.
     */
    private fun drawGlassFinish(canvas: Canvas, size: Int) {
        val s = size.toFloat()
        val clip = shapePath(s)
        canvas.save()
        canvas.clipPath(clip)
        canvas.drawPaint(Paint().apply {
            shader = android.graphics.LinearGradient(0f, 0f, 0f, s, intArrayOf(0x38FFFFFF, 0x00FFFFFF, 0x00000000, 0x1F000000), floatArrayOf(0f, 0.42f, 0.7f, 1f), android.graphics.Shader.TileMode.CLAMP)
        })
        canvas.restore()
        val w = (s * 0.016f).coerceAtLeast(1f)
        val inset = w / 2f
        val rim = Path(clip).apply {
            val m = android.graphics.Matrix()
            m.setScale((s - 2 * inset) / s, (s - 2 * inset) / s)
            m.postTranslate(inset, inset)
            transform(m)
        }
        canvas.drawPath(rim, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = w
            shader = android.graphics.LinearGradient(s * 0.14f, s * 0.06f, s * 0.86f, s * 0.94f,
                intArrayOf(0xCCFFFFFF.toInt(), 0x33FFFFFF, 0x10FFFFFF, 0x10FFFFFF, 0x66FFFFFF), null, android.graphics.Shader.TileMode.CLAMP)
        })
    }

    /**
     * Apps iOS also ships as part of the phone (dialer, messages, camera, settings, clock…): AOSP and
     * Xiaomi system packages plus Google's core phone apps. Branded apps that merely come
     * preinstalled (Chrome, YouTube, Gmail, Maps) are not, and keep their own logo.
     */
    private fun isCoreSystemApp(entry: AppEntry): Boolean {
        val pkg = entry.packageName
        val system = (entry.info.applicationInfo.flags and (android.content.pm.ApplicationInfo.FLAG_SYSTEM or android.content.pm.ApplicationInfo.FLAG_UPDATED_SYSTEM_APP)) != 0
        return system && pkg !in BRANDED && (CORE_PREFIXES.any { pkg.startsWith(it) } || pkg in CORE_GOOGLE)
    }

    private fun maskToSquircle(bmp: Bitmap) {
        val size = bmp.width.toFloat()
        val c = Canvas(bmp)
        val path = shapePath(size)
        path.toggleInverseFillType()
        c.drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.CLEAR) })
    }

    private fun drawDrawable(canvas: Canvas, d: Drawable, l: Int, t: Int, r: Int, b: Int) {
        val old = d.copyBounds()
        d.setBounds(l, t, r, b)
        d.draw(canvas)
        d.bounds = old
    }

    /** Renders an arbitrary drawable (e.g. a shortcut icon) into the squircle. */
    fun renderShortcut(drawable: Drawable?, size: Int): ImageBitmap? {
        drawable ?: return null
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        renderSquircle(Canvas(out), drawable, size)
        return out.asImageBitmap()
    }

    private companion object {
        const val SYSTEM_ART_PACK = "com.shivam.glassicons"
        val CORE_PREFIXES = listOf("com.android.", "com.miui.", "com.xiaomi.", "com.mi.")
        /** Branded apps that live under a core prefix on some phones (Xiaomi ships Chrome as com.android.chrome). */
        val BRANDED = setOf("com.android.chrome", "com.android.vending")
        val CORE_GOOGLE = setOf(
            "com.google.android.dialer", "com.google.android.contacts", "com.google.android.apps.messaging",
            "com.google.android.deskclock", "com.google.android.calculator", "com.google.android.calendar",
            "com.google.android.GoogleCamera", "com.google.android.apps.photos", "com.google.android.apps.nbu.files",
            "com.google.android.apps.recorder", "com.google.android.keep", "com.google.android.documentsui",
        )
    }
}
