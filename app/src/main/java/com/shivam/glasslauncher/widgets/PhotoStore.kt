package com.shivam.glasslauncher.widgets

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Photos for the Photos widget. Picked through the Photo Picker (no storage permission,
 * docs/CONSTRAINTS.md) and copied, downscaled, into app storage so they keep working without
 * long-lived URI grants.
 */
object PhotoStore {

    private const val MAX_SIDE = 1440

    private fun dir(context: Context, widgetId: String) = File(context.filesDir, "photos/$widgetId")

    fun files(context: Context, widgetId: String): List<File> =
        dir(context, widgetId).listFiles { f -> f.extension == "jpg" }?.sortedBy { it.name } ?: emptyList()

    /** Replaces the widget's photos with [uris]; returns how many were imported. */
    suspend fun import(context: Context, widgetId: String, uris: List<Uri>): Int = withContext(Dispatchers.IO) {
        val d = dir(context, widgetId)
        d.deleteRecursively()
        d.mkdirs()
        var n = 0
        uris.forEachIndexed { i, uri ->
            try {
                val source = ImageDecoder.createSource(context.contentResolver, uri)
                val bmp = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                    val w = info.size.width
                    val h = info.size.height
                    val scale = MAX_SIDE.toFloat() / maxOf(w, h)
                    if (scale < 1f) decoder.setTargetSize((w * scale).toInt().coerceAtLeast(1), (h * scale).toInt().coerceAtLeast(1))
                    decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                }
                File(d, "%03d.jpg".format(i)).outputStream().use { bmp.compress(Bitmap.CompressFormat.JPEG, 88, it) }
                bmp.recycle()
                n++
            } catch (_: Exception) { }
        }
        n
    }

    suspend fun load(file: File, maxSide: Int): Bitmap? = withContext(Dispatchers.IO) {
        try {
            ImageDecoder.decodeBitmap(ImageDecoder.createSource(file)) { decoder, info, _ ->
                val scale = maxSide.toFloat() / maxOf(info.size.width, info.size.height)
                if (scale < 1f) decoder.setTargetSize((info.size.width * scale).toInt().coerceAtLeast(1), (info.size.height * scale).toInt().coerceAtLeast(1))
            }
        } catch (e: Exception) {
            null
        }
    }

    fun clear(context: Context, widgetId: String) {
        dir(context, widgetId).deleteRecursively()
    }
}
