package com.shivam.glasslauncher.polish

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import org.json.JSONObject

/** One bundled Glass wallpaper; each has a light and a dark version. */
data class GlassWallpaper(val id: String, val name: String, val category: String)

/**
 * The original wallpapers rendered by `tools/wallpapers/generate_wallpapers.py` and bundled in
 * `assets/wallpapers` (full 1440x3200 WebP plus 270x600 thumbnails).
 */
class GlassWallpapers(private val context: Context) {

    val all: List<GlassWallpaper> by lazy {
        try {
            val json = context.assets.open("wallpapers/index.json").use { String(it.readBytes()) }
            val arr = JSONObject(json).getJSONArray("wallpapers")
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                GlassWallpaper(o.getString("id"), o.getString("name"), o.getString("category"))
            }
        } catch (e: Exception) {
            Log.w("GlassWallpapers", "No bundled wallpapers", e)
            emptyList()
        }
    }

    val categories: List<String> get() = all.map { it.category }.distinct()

    fun byId(id: String?): GlassWallpaper? = all.firstOrNull { it.id == id }

    fun loadThumb(id: String, mode: String): Bitmap? = decode("wallpapers/${id}_${mode}_thumb.webp", null)

    /** Full-size version, software-allocated so WallpaperManager can read it. Blocking. */
    fun loadFull(id: String, mode: String): Bitmap? =
        decode("wallpapers/${id}_$mode.webp", BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 })

    private fun decode(path: String, opts: BitmapFactory.Options?): Bitmap? = try {
        context.assets.open(path).use { BitmapFactory.decodeStream(it, null, opts) }
    } catch (e: Exception) {
        null
    }
}
