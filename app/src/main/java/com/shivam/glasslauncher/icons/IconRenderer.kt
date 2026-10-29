package com.shivam.glasslauncher.icons

import android.content.Context
import android.content.pm.LauncherApps
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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
        val mapped = if (overridden) null else p?.iconFor(entry.component, density, variant)
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
            }
        }
        if (entry.isWorkProfile) {
            val badged = context.packageManager.getUserBadgedIcon(BitmapDrawable(context.resources, out), entry.user)
            val b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            drawDrawable(Canvas(b), badged, 0, 0, size, size)
            return b
        }
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
        if (icon is AdaptiveIconDrawable) {
            val inset = (size * 0.25f).toInt()
            icon.background?.let { drawDrawable(canvas, it, -inset, -inset, size + inset, size + inset) }
                ?: canvas.drawColor(Color.WHITE)
            icon.foreground?.let { drawDrawable(canvas, it, -inset, -inset, size + inset, size + inset) }
        } else {
            // Legacy icon on a white tile, like iOS does for odd-shaped artwork.
            canvas.drawColor(Color.WHITE)
            val pad = (size * 0.14f).toInt()
            drawDrawable(canvas, icon, pad, pad, size - pad, size - pad)
        }
        canvas.restore()
    }

    private fun renderThemed(canvas: Canvas, icon: Drawable, pack: IconPack, seed: Int, size: Int, density: Int) {
        pack.backFor(seed, density)?.let { drawDrawable(canvas, it, 0, 0, size, size) }
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
}
