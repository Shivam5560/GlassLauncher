package com.shivam.glasslauncher.overlay

import android.content.Context
import android.util.LruCache
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import com.shivam.glasslauncher.ui.glass.GlassShape
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** App icons for notifications and the island (plain system icons, cached). */
object OverlayAppIcons {
    private val cache = LruCache<String, ImageBitmap>(64)

    fun cached(pkg: String, sizePx: Int): ImageBitmap? = cache.get("$pkg#$sizePx")

    fun get(context: Context, pkg: String, sizePx: Int): ImageBitmap? {
        val key = "$pkg#$sizePx"
        cache.get(key)?.let { return it }
        return try {
            val d = context.packageManager.getApplicationIcon(pkg)
            d.toBitmap(sizePx, sizePx).asImageBitmap().also { cache.put(key, it) }
        } catch (e: Exception) { null }
    }
}

@Composable
fun OverlayAppIcon(pkg: String, size: Dp, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val px = (size.value * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
    val bmp by produceState(OverlayAppIcons.cached(pkg, px), pkg, px) {
        value = withContext(Dispatchers.IO) { OverlayAppIcons.get(context, pkg, px) }
    }
    val b = bmp
    if (b != null) {
        Image(b, contentDescription = null, modifier = modifier.size(size).clip(GlassShape.rounded(size * 0.24f)))
    } else {
        Spacer(modifier.size(size))
    }
}

/** Circular progress ring (charging, downloads). */
@Composable
fun ProgressRing(fraction: Float, color: Color, size: Dp, stroke: Dp = 3.dp, modifier: Modifier = Modifier, track: Color = Color.White.copy(alpha = 0.18f)) {
    Canvas(modifier.size(size)) {
        val w = stroke.toPx()
        val d = this.size.minDimension - w
        val tl = Offset(w / 2, w / 2)
        drawArc(track, 0f, 360f, false, tl, Size(d, d), style = Stroke(w))
        drawArc(color, -90f, 360f * fraction.coerceIn(0f, 1f), false, tl, Size(d, d), style = Stroke(w, cap = StrokeCap.Round))
    }
}

/** "1:05:09" / "4:09" for durations in milliseconds. */
fun formatDuration(ms: Long): String {
    val total = (ms.coerceAtLeast(0) + 500) / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
