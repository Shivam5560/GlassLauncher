package com.shivam.glasslauncher.ui.glass

import android.app.WallpaperColors
import android.app.WallpaperManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.hardware.display.DisplayManager
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.Display
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Where the refraction backdrop comes from (see docs/CONSTRAINTS.md, "Liquid Glass refraction"). */
enum class WallpaperPath(val title: String, val detail: String) {
    OwnCopy("Path A · own copy", "Wallpaper set through Glass, so its bitmap is kept for refraction."),
    AllFilesAccess("Path B · All files access", "Read from the system with All files access."),
    None("Path C · blur only", "Live wallpaper or no access: glass uses blur, tint and rim only, no refraction."),
}

/**
 * Provides a downscaled copy of the static system wallpaper, centre-cropped to the screen, for
 * in-window glass backdrops and luminance sampling. No screen capture is involved: the image comes
 * from [WallpaperManager], which on Android 13+ only hands it out to apps with
 * "All files access" (MANAGE_EXTERNAL_STORAGE). Without it (or with a live wallpaper) only
 * [WallpaperColors] are available, which still drive the adaptive tint.
 */
class WallpaperSource(private val context: Context, private val scope: CoroutineScope) {

    class Snapshot(
        /** Screen-aligned wallpaper, downscaled by [scale]; null when the wallpaper is not readable. */
        val image: ImageBitmap?,
        val scale: Float,
        val screenWidth: Int,
        val screenHeight: Int,
        private val lumGrid: FloatArray?,
        private val gridW: Int,
        private val gridH: Int,
        /** Whole-wallpaper luminance (0 dark .. 1 light), from pixels or WallpaperColors. */
        val globalLuminance: Float,
        val path: WallpaperPath = WallpaperPath.None,
        val isLive: Boolean = false,
    ) {
        /** Average luminance of a screen-space rectangle. */
        fun luminance(left: Float, top: Float, right: Float, bottom: Float): Float {
            val grid = lumGrid ?: return globalLuminance
            if (screenWidth <= 0 || screenHeight <= 0) return globalLuminance
            val x0 = ((left / screenWidth) * gridW).toInt().coerceIn(0, gridW - 1)
            val x1 = ((right / screenWidth) * gridW).toInt().coerceIn(x0, gridW - 1)
            val y0 = ((top / screenHeight) * gridH).toInt().coerceIn(0, gridH - 1)
            val y1 = ((bottom / screenHeight) * gridH).toInt().coerceIn(y0, gridH - 1)
            var sum = 0f
            var n = 0
            for (y in y0..y1) for (x in x0..x1) { sum += grid[y * gridW + x]; n++ }
            return if (n == 0) globalLuminance else sum / n
        }

        companion object {
            val EMPTY = Snapshot(null, 1f, 0, 0, null, 0, 0, 0.3f, WallpaperPath.None)
        }
    }

    private val wm = WallpaperManager.getInstance(context)
    private val _snapshot = MutableStateFlow(Snapshot.EMPTY)
    val snapshot: StateFlow<Snapshot> = _snapshot.asStateFlow()

    private val listener = WallpaperManager.OnColorsChangedListener { _, _ -> refresh() }

    fun start() {
        try {
            wm.addOnColorsChangedListener(listener, Handler(Looper.getMainLooper()))
        } catch (e: Exception) {
            Log.w(TAG, "Could not listen for wallpaper changes", e)
        }
        refresh()
    }

    /** True when the wallpaper pixels are readable (needed for refraction over the wallpaper). */
    fun canReadWallpaper(): Boolean = Environment.isExternalStorageManager()

    fun refresh() {
        scope.launch(Dispatchers.Default) { _snapshot.value = load() }
    }

    @Suppress("DEPRECATION")
    private fun screenSize(): Pair<Int, Int> {
        val display = context.getSystemService(DisplayManager::class.java).getDisplay(Display.DEFAULT_DISPLAY)
        val m = DisplayMetrics()
        display.getRealMetrics(m)
        return m.widthPixels to m.heightPixels
    }

    private fun colorsLuminance(): Float {
        val colors: WallpaperColors? = try { wm.getWallpaperColors(WallpaperManager.FLAG_SYSTEM) } catch (e: Exception) { null }
        colors ?: return 0.3f
        if (colors.colorHints and WallpaperColors.HINT_SUPPORTS_DARK_TEXT != 0) return 0.78f
        val c = colors.primaryColor
        return luminance(c.red(), c.green(), c.blue())
    }

    private fun load(): Snapshot {
        val (sw, sh) = screenSize()
        val fallbackLum = colorsLuminance()
        val live = try { wm.wallpaperInfo != null } catch (e: Exception) { false }
        if (live) return Snapshot(null, 1f, sw, sh, null, 0, 0, fallbackLum, WallpaperPath.None, isLive = true)
        // Path A: a wallpaper we set ourselves and still current.
        ownCopy()?.let { own ->
            try {
                return render(android.graphics.drawable.BitmapDrawable(context.resources, own), sw, sh, fallbackLum, WallpaperPath.OwnCopy)
            } finally {
                own.recycle() // render() copies it into a 1/3-size bitmap; the decoded copy is no longer needed
            }
        }
        // Path B: All files access lets WallpaperManager hand out the bitmap.
        val drawable = try {
            wm.drawable
        } catch (e: SecurityException) {
            null
        } catch (e: Exception) {
            Log.w(TAG, "Wallpaper unavailable", e)
            null
        } ?: return Snapshot(null, 1f, sw, sh, null, 0, 0, fallbackLum, WallpaperPath.None)
        return render(drawable, sw, sh, fallbackLum, WallpaperPath.AllFilesAccess)
    }

    private fun render(drawable: android.graphics.drawable.Drawable, sw: Int, sh: Int, fallbackLum: Float, path: WallpaperPath): Snapshot {

        val scale = 1f / 3f
        val bw = (sw * scale).toInt().coerceAtLeast(1)
        val bh = (sh * scale).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(bw, bh, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        val iw = drawable.intrinsicWidth.takeIf { it > 0 } ?: bw
        val ih = drawable.intrinsicHeight.takeIf { it > 0 } ?: bh
        // centre-crop, matching how the system draws a static wallpaper at offset 0.5
        val s = maxOf(bw.toFloat() / iw, bh.toFloat() / ih)
        val dw = iw * s
        val dh = ih * s
        val l = ((bw - dw) / 2f).toInt()
        val t = ((bh - dh) / 2f).toInt()
        drawable.setBounds(l, t, l + dw.toInt(), t + dh.toInt())
        drawable.draw(canvas)

        val gw = 12
        val gh = 24
        val grid = FloatArray(gw * gh)
        val pixels = IntArray(bw * bh)
        bmp.getPixels(pixels, 0, bw, 0, 0, bw, bh)
        var total = 0f
        for (gy in 0 until gh) for (gx in 0 until gw) {
            val x0 = gx * bw / gw; val x1 = ((gx + 1) * bw / gw).coerceAtLeast(x0 + 1)
            val y0 = gy * bh / gh; val y1 = ((gy + 1) * bh / gh).coerceAtLeast(y0 + 1)
            var sum = 0f; var n = 0
            var y = y0
            while (y < y1 && y < bh) {
                var x = x0
                while (x < x1 && x < bw) {
                    val c = pixels[y * bw + x]
                    sum += luminance(((c shr 16) and 0xFF) / 255f, ((c shr 8) and 0xFF) / 255f, (c and 0xFF) / 255f)
                    n++
                    x += 2
                }
                y += 2
            }
            val v = if (n == 0) fallbackLum else sum / n
            grid[gy * gw + gx] = v
            total += v
        }
        return Snapshot(bmp.asImageBitmap(), scale, sw, sh, grid, gw, gh, total / (gw * gh), path)
    }

    private val copyFile get() = java.io.File(context.filesDir, "glass_wallpaper.png")
    private val idFile get() = java.io.File(context.filesDir, "glass_wallpaper.id")

    fun currentWallpaperId(): Int? = try { wm.getWallpaperId(WallpaperManager.FLAG_SYSTEM) } catch (e: Exception) { null }

    /** Our saved copy, if the system wallpaper is still the one we set. */
    private fun ownCopy(): Bitmap? {
        if (!copyFile.exists()) return null
        val savedId = try { idFile.readText().trim().toInt() } catch (e: Exception) { null }
        val current = currentWallpaperId()
        if (savedId != null && current != null && savedId != current) return null
        return try { android.graphics.BitmapFactory.decodeFile(copyFile.path) } catch (e: Exception) { null }
    }

    /**
     * Path A: decodes [uri] (from the system Photo Picker, no storage permission), sets it as the
     * home-screen wallpaper and keeps a copy for refraction. Blocking; call off the main thread.
     */
    fun setWallpaper(uri: android.net.Uri, alsoLockScreen: Boolean): Result<Unit> = runCatching {
        val (sw, sh) = screenSize()
        val source = android.graphics.ImageDecoder.createSource(context.contentResolver, uri)
        val full = android.graphics.ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = android.graphics.ImageDecoder.ALLOCATOR_SOFTWARE
            val s = maxOf(sw.toFloat() / info.size.width, sh.toFloat() / info.size.height).coerceAtMost(1f)
            decoder.setTargetSize((info.size.width * s).toInt().coerceAtLeast(1), (info.size.height * s).toInt().coerceAtLeast(1))
        }
        setWallpaperBitmap(full, alsoLockScreen)
        Unit
    }

    /**
     * Path A for a bitmap we already have (the bundled Glass wallpapers): sets it as the home-screen
     * wallpaper and keeps a copy for refraction. Returns the system wallpaper id. Blocking.
     */
    fun setWallpaperBitmap(full: Bitmap, alsoLockScreen: Boolean): Int {
        val flags = if (alsoLockScreen) WallpaperManager.FLAG_SYSTEM or WallpaperManager.FLAG_LOCK else WallpaperManager.FLAG_SYSTEM
        val id = wm.setBitmap(full, null, true, flags)
        val small = Bitmap.createScaledBitmap(full, (full.width / 2).coerceAtLeast(1), (full.height / 2).coerceAtLeast(1), true)
        copyFile.outputStream().use { small.compress(Bitmap.CompressFormat.PNG, 100, it) }
        val saved = if (id > 0) id else currentWallpaperId() ?: 0
        idFile.writeText(saved.toString())
        refresh()
        return saved
    }

    private fun luminance(r: Float, g: Float, b: Float) = 0.2126f * r + 0.7152f * g + 0.0722f * b

    companion object { private const val TAG = "GlassWallpaper" }
}
