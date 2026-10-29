package com.shivam.glasslauncher.ui.glass

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [image] blurred once on a background thread and drawn as a plain texture, instead of a full-screen
 * `Modifier.blur` that the GPU re-runs on every frame the layer is dirty (drawer/Spotlight opening).
 * Null until ready. Blurs a copy at 1/[SCALE] size, which is smooth when stretched back up.
 */
@Composable
fun rememberBlurredCopy(image: ImageBitmap?, radiusDp: Float): ImageBitmap? {
    val density = LocalDensity.current.density
    val blurred by produceState<ImageBitmap?>(null, image, radiusDp, density) {
        value = image?.let { withContext(Dispatchers.Default) { blurSmall(it.asAndroidBitmap(), radiusDp * density).asImageBitmap() } }
    }
    return blurred
}

private const val SCALE = 8

private fun blurSmall(src: Bitmap, radiusPx: Float): Bitmap {
    val w = (src.width / SCALE).coerceAtLeast(2)
    val h = (src.height / SCALE).coerceAtLeast(2)
    val small = Bitmap.createScaledBitmap(src.copy(Bitmap.Config.ARGB_8888, false), w, h, true)
    val px = IntArray(w * h).also { small.getPixels(it, 0, w, 0, 0, w, h) }
    val r = (radiusPx / SCALE).toInt().coerceAtLeast(1)
    repeat(3) { boxPass(px, w, h, r, horizontal = true); boxPass(px, w, h, r, horizontal = false) }
    return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
}

/** One sliding-window box blur pass along rows or columns, edges clamped. */
private fun boxPass(px: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean) {
    val len = if (horizontal) w else h
    val lines = if (horizontal) h else w
    val step = if (horizontal) 1 else w
    val line = IntArray(len)
    val win = 2 * r + 1
    for (l in 0 until lines) {
        val base = if (horizontal) l * w else l
        for (i in 0 until len) line[i] = px[base + i * step]
        var a = 0; var rr = 0; var g = 0; var b = 0
        for (k in -r..r) {
            val c = line[k.coerceIn(0, len - 1)]
            a += c ushr 24; rr += (c shr 16) and 255; g += (c shr 8) and 255; b += c and 255
        }
        for (i in 0 until len) {
            px[base + i * step] = ((a / win) shl 24) or ((rr / win) shl 16) or ((g / win) shl 8) or (b / win)
            val out = line[(i - r).coerceIn(0, len - 1)]
            val inn = line[(i + r + 1).coerceIn(0, len - 1)]
            a += (inn ushr 24) - (out ushr 24)
            rr += ((inn shr 16) and 255) - ((out shr 16) and 255)
            g += ((inn shr 8) and 255) - ((out shr 8) and 255)
            b += (inn and 255) - (out and 255)
        }
    }
}
