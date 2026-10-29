package com.shivam.glasslauncher.ui.home

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Shader
import android.util.Log
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.core.content.FileProvider
import com.shivam.glasslauncher.ui.glass.WallpaperSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * "Share your home screen" (Phase 5): renders the home page from Glass's own Compose tree — the
 * recorded home [GraphicsLayer] (pages, widgets, icons, dock) over Glass's copy of the wallpaper —
 * into a PNG and shares it with ACTION_SEND through a FileProvider. No screen capture or
 * MediaProjection is involved (REVIEW.md rule 3). Without a readable wallpaper (Path C, live
 * wallpapers) a neutral gradient stands in for it.
 */
object HomeShare {

    suspend fun share(activity: Activity, home: GraphicsLayer, wallpaper: WallpaperSource.Snapshot): Boolean {
        val homeBitmap = try {
            home.toImageBitmap().asAndroidBitmap()
        } catch (e: Exception) {
            Log.w(TAG, "Could not render home", e)
            return false
        }
        val file = withContext(Dispatchers.Default) {
            val w = homeBitmap.width.coerceAtLeast(1)
            val h = homeBitmap.height.coerceAtLeast(1)
            val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(out)
            val wp = wallpaper.image?.asAndroidBitmap()
            if (wp != null) {
                val soft = if (wp.config == Bitmap.Config.HARDWARE) wp.copy(Bitmap.Config.ARGB_8888, false) else wp
                c.drawBitmap(soft, null, Rect(0, 0, w, h), Paint(Paint.FILTER_BITMAP_FLAG))
            } else {
                c.drawPaint(Paint().apply { shader = LinearGradient(0f, 0f, 0f, h.toFloat(), 0xFF2B3A55.toInt(), 0xFF0E1220.toInt(), Shader.TileMode.CLAMP) })
            }
            // Hardware bitmaps can't be drawn on a software canvas: copy first.
            val soft = if (homeBitmap.config == Bitmap.Config.HARDWARE) homeBitmap.copy(Bitmap.Config.ARGB_8888, false) else homeBitmap
            c.drawBitmap(soft, 0f, 0f, null)
            withContext(Dispatchers.IO) {
                val dir = File(activity.cacheDir, "share").apply { mkdirs() }
                val f = File(dir, "glass-home.png")
                f.outputStream().use { out.compress(Bitmap.CompressFormat.PNG, 100, it) }
                f
            }
        }
        return try {
            val uri = FileProvider.getUriForFile(activity, activity.packageName + ".share", file)
            val send = Intent(Intent.ACTION_SEND)
                .setType("image/png")
                .putExtra(Intent.EXTRA_STREAM, uri)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            send.clipData = ClipData.newRawUri("Home screen", uri)
            activity.startActivity(Intent.createChooser(send, "Share home screen").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
            true
        } catch (e: Exception) {
            Log.w(TAG, "Share failed", e)
            false
        }
    }

    private const val TAG = "GlassShare"
}
