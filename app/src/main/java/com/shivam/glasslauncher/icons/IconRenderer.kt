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

    fun invalidatePackage(pkg: String?) {
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
        val mapped = p?.iconFor(entry.component, density)
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(out)
        if (mapped != null) {
            drawDrawable(canvas, mapped, 0, 0, size, size)
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

    /** Adaptive icons: background + foreground layers expanded so the 72/108 safe zone fills the squircle. */
    private fun renderSquircle(canvas: Canvas, icon: Drawable, size: Int) {
        val clip = superellipseRectPath(size.toFloat(), size.toFloat(), size / 2f, 5f)
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
        val path = superellipseRectPath(size, size, size / 2f, 5f)
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
