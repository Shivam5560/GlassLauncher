package com.shivam.glasslauncher.polish

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import java.io.File
import org.json.JSONObject

/** One Glass wallpaper; each has a light and a dark version. [user] = added from Photos. */
data class GlassWallpaper(val id: String, val name: String, val category: String,
    val focusX: Float = 0.5f, val focusY: Float = 0.5f, val user: Boolean = false)

/**
 * The original wallpapers rendered by `tools/wallpapers/generate_wallpapers.py` and bundled in
 * `assets/wallpapers` (full 1440x3200 WebP plus 270x600 thumbnails), followed by pictures the
 * user added from Photos, kept in `files/wallpapers_user` in the same light/dark/thumb layout.
 */
class GlassWallpapers(private val context: Context) {

    private val userDir: File get() = File(context.filesDir, USER_DIR).apply { mkdirs() }

    // Strong references are byte bounded; evicted images may still be displayed by Compose.
    // Never recycle cache entries; Android releases them when their last owner is gone.
    private val thumbs = object : android.util.LruCache<String, Bitmap>(4 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.allocationByteCount
    }
    fun trimCache() = thumbs.evictAll()

    private val bundled: List<GlassWallpaper> by lazy {
        try {
            val json = context.assets.open("wallpapers/index.json").use { String(it.readBytes()) }
            val arr = JSONObject(json).getJSONArray("wallpapers")
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                GlassWallpaper(o.getString("id"), o.getString("name"), o.getString("category"),
                    o.optDouble("focusX", 0.5).toFloat(), o.optDouble("focusY", 0.5).toFloat())
            }
        } catch (e: Exception) {
            Log.w("GlassWallpapers", "No bundled wallpapers", e)
            emptyList()
        }
    }

    /** Bundled wallpapers, then the user's in the order they were added. Reads a small directory: call off the main thread. */
    val all: List<GlassWallpaper> get() = bundled + userWallpapers()

    /** Read once, then kept until an import or removal changes the folder. */
    @Volatile private var userCache: List<GlassWallpaper>? = null

    private fun userWallpapers(): List<GlassWallpaper> = userCache
        ?: userDir.listFiles { f -> f.name.endsWith(LIGHT_SUFFIX) }.orEmpty().sortedBy { it.name }.map { f ->
            val id = f.name.removeSuffix(LIGHT_SUFFIX)
            GlassWallpaper(id, UserWallpaperNames.nameOf(id), USER_CATEGORY, user = true)
        }.also { userCache = it }

    val categories: List<String> get() = all.map { it.category }.distinct()

    fun byId(id: String?): GlassWallpaper? = all.firstOrNull { it.id == id }

    fun loadThumb(id: String, mode: String): Bitmap? {
        if (byId(id) == null || mode !in setOf("light", "dark")) return null
        val key = "${id}_$mode"
        return thumbs.get(key) ?: decode(id, "${id}_${mode}_thumb.webp", null)?.also { thumbs.put(key, it) }
    }

    fun loadPreview(id: String, mode: String): Bitmap? =
        if (byId(id) == null || mode !in setOf("light", "dark")) null
        else decode(id, "${id}_$mode.webp", BitmapFactory.Options().apply { inSampleSize = 4 })

    /** Full-size version, software-allocated so WallpaperManager can read it. Blocking. */
    fun loadFull(id: String, mode: String): Bitmap? =
        decode(id, "${id}_$mode.webp", BitmapFactory.Options().apply { inPreferredConfig = Bitmap.Config.ARGB_8888 })

    /** Bundled files come from assets; a user wallpaper added as one picture uses it for both modes. */
    private fun decode(id: String, name: String, opts: BitmapFactory.Options?): Bitmap? = try {
        if (!UserWallpaperNames.isUser(id)) context.assets.open("wallpapers/$name").use { BitmapFactory.decodeStream(it, null, opts) }
        else {
            val f = File(userDir, name).takeIf { it.exists() } ?: File(userDir, name.replace("_dark", "_light"))
            BitmapFactory.decodeFile(f.path, opts)
        }
    } catch (e: Exception) {
        null
    }

    /**
     * Adds one or two pictures from Photos as a wallpaper. With two, the brighter becomes Light and
     * the darker Dark. Pictures are scaled to fit 1440x3200, so a huge photo costs no more than a
     * bundled wallpaper. Blocking; returns null if no picture could be read.
     */
    fun import(uris: List<Uri>): GlassWallpaper? {
        val pictures = uris.take(2).mapNotNull { uri -> decodeScaled(uri)?.let { it to displayName(uri) } }
        if (pictures.isEmpty()) return null
        val sorted = pictures.sortedByDescending { luminance(it.first) }
        val id = UserWallpaperNames.newId(UserWallpaperNames.clean(sorted.first().second), System.currentTimeMillis())
        save(sorted.first().first, id, "light")
        sorted.getOrNull(1)?.let { save(it.first, id, "dark") }
        pictures.forEach { it.first.recycle() }
        userCache = null
        return byId(id)
    }

    /** Renames a user wallpaper; the name lives in the id, so its files move. Returns the new id. */
    fun rename(id: String, name: String): String? {
        if (!UserWallpaperNames.isUser(id)) return null
        val newId = UserWallpaperNames.renamed(id, UserWallpaperNames.clean(name))
        if (newId == id) return id
        userDir.listFiles { f -> f.name.startsWith("${id}_") }?.forEach { f ->
            f.renameTo(File(userDir, newId + f.name.removePrefix(id)))
        }
        thumbs.evictAll()
        userCache = null
        return newId
    }

    fun remove(id: String) {
        if (!UserWallpaperNames.isUser(id)) return
        userDir.listFiles { f -> f.name.startsWith("${id}_") }?.forEach { it.delete() }
        userCache = null
        thumbs.snapshot().keys.filter { it.startsWith("${id}_") }.forEach { thumbs.remove(it) }
    }

    private fun save(bmp: Bitmap, id: String, mode: String) {
        File(userDir, "${id}_$mode.webp").outputStream().use { bmp.compress(Bitmap.CompressFormat.WEBP_LOSSY, 92, it) }
        // Thumbnail: centre crop to the bundled 270x600 shape.
        val ratio = THUMB_W.toFloat() / THUMB_H
        val cw = minOf(bmp.width, (bmp.height * ratio).toInt()); val ch = minOf(bmp.height, (bmp.width / ratio).toInt())
        val crop = Bitmap.createBitmap(bmp, (bmp.width - cw) / 2, (bmp.height - ch) / 2, cw, ch)
        val thumb = Bitmap.createScaledBitmap(crop, THUMB_W, THUMB_H, true)
        File(userDir, "${id}_${mode}_thumb.webp").outputStream().use { thumb.compress(Bitmap.CompressFormat.WEBP_LOSSY, 85, it) }
        if (crop !== bmp) crop.recycle()
        thumb.recycle()
    }

    private fun decodeScaled(uri: Uri): Bitmap? = try {
        ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { d, info, _ ->
            d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val s = minOf(1f, MAX_W.toFloat() / info.size.width, MAX_H.toFloat() / info.size.height)
            d.setTargetSize((info.size.width * s).toInt().coerceAtLeast(1), (info.size.height * s).toInt().coerceAtLeast(1))
        }
    } catch (e: Exception) {
        Log.w("GlassWallpapers", "Couldn't read picture", e)
        null
    }

    private fun displayName(uri: Uri): String? = try {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    } catch (e: Exception) {
        null
    }

    /** Mean luminance from a tiny downscale. */
    private fun luminance(bmp: Bitmap): Float {
        val small = Bitmap.createScaledBitmap(bmp, 8, 16, true)
        var sum = 0f
        for (y in 0 until small.height) for (x in 0 until small.width) {
            val c = small.getPixel(x, y)
            sum += 0.2126f * ((c shr 16) and 255) + 0.7152f * ((c shr 8) and 255) + 0.0722f * (c and 255)
        }
        if (small !== bmp) small.recycle()
        return sum / (small.width * small.height * 255f)
    }

    companion object {
        const val USER_CATEGORY = "My Wallpapers"
        private const val USER_DIR = "wallpapers_user"
        private const val LIGHT_SUFFIX = "_light.webp"
        private const val MAX_W = 1440
        private const val MAX_H = 3200
        private const val THUMB_W = 270
        private const val THUMB_H = 600
    }
}

/** Ids of wallpapers added from Photos: `user-<millis>-<name>`, so the name needs no separate store. */
object UserWallpaperNames {
    private const val PREFIX = "user-"

    fun isUser(id: String) = id.startsWith(PREFIX)

    fun newId(name: String, millis: Long) = "$PREFIX$millis-$name"

    fun nameOf(id: String): String = id.removePrefix(PREFIX).substringAfter('-', "").ifBlank { "My wallpaper" }

    /** Same wallpaper (same timestamp), new name. */
    fun renamed(id: String, name: String) = PREFIX + id.removePrefix(PREFIX).substringBefore('-') + "-" + name

    /** "iOS27_Default_Light.png" -> "iOS27 Default"; keeps letters, digits and spaces (safe as a file name). */
    fun clean(displayName: String?): String {
        val base = displayName.orEmpty().substringBeforeLast('.').replace('_', ' ').replace('-', ' ')
            .replace(Regex("(?i)\\s*\\b(light|dark)\\b\\s*$"), "")
        val name = base.filter { it.isLetterOrDigit() || it == ' ' }.trim().replace(Regex(" +"), " ").take(40)
        // The photo picker hands out media ids ("14472.jpg") instead of real file names.
        return if (name.any { it.isLetter() }) name else "My wallpaper"
    }
}
