package com.shivam.glasslauncher.ui.glass

import android.graphics.Bitmap
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.CoroutineContext

/**
 * [image] blurred once on a background thread and drawn as a plain texture, instead of a full-screen
 * `Modifier.blur` that the GPU re-runs on every frame the layer is dirty (drawer/Spotlight opening).
 * Null until ready. Blurs a copy at 1/[SCALE] size, which is smooth when stretched back up.
 */
@Composable
fun rememberBlurredCopy(image: ImageBitmap?, radiusDp: Float): ImageBitmap? {
    val density = LocalDensity.current.density
    // produceState keeps its last value when keys change. A fresh holder prevents a previous
    // wallpaper / frozen home from flashing through the new overlay while its blur is prepared.
    val blurred = remember(image, radiusDp, density) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(image, radiusDp, density) {
        blurred.value = image?.let {
            if (radiusDp <= 0f) it else withContext(Dispatchers.Default) {
                blurSmall(it.asAndroidBitmap(), radiusDp * density).asImageBitmap()
            }
        }
    }
    return blurred.value
}

private const val SCALE = 8

private suspend fun blurSmall(src: Bitmap, radiusPx: Float): Bitmap {
    val context = currentCoroutineContext()
    context.ensureActive()
    val w = (src.width / SCALE).coerceAtLeast(2)
    val h = (src.height / SCALE).coerceAtLeast(2)
    // Convert hardware inputs explicitly: createScaledBitmap(HARDWARE) itself performs a
    // full software readback and then copies its result back to hardware. Avoid that extra
    // roundtrip, and release our owned temporaries as soon as the pixels have been extracted.
    val readable = if (src.config == Bitmap.Config.ARGB_8888) src
        else checkNotNull(src.copy(Bitmap.Config.ARGB_8888, false))
    val px = try {
        context.ensureActive()
        val small = Bitmap.createScaledBitmap(readable, w, h, true)
        try {
            context.ensureActive()
            IntArray(w * h).also { small.getPixels(it, 0, w, 0, 0, w, h) }
        } finally {
            if (small !== readable && small !== src) small.recycle()
        }
    } finally {
        // The original image belongs to the recorded backdrop and must never be recycled here.
        if (readable !== src) readable.recycle()
    }
    val r = (radiusPx / SCALE).toInt().coerceAtLeast(1)
    // All six passes can share one line buffer; no per-pass allocations.
    val line = IntArray(maxOf(w, h))
    repeat(3) {
        boxPass(px, w, h, r, horizontal = true, line, context)
        boxPass(px, w, h, r, horizontal = false, line, context)
    }
    context.ensureActive()
    return Bitmap.createBitmap(px, w, h, Bitmap.Config.ARGB_8888)
}

/** One sliding-window box blur pass along rows or columns, edges clamped. */
private fun boxPass(
    px: IntArray, w: Int, h: Int, r: Int, horizontal: Boolean,
    line: IntArray, context: CoroutineContext,
) {
    val len = if (horizontal) w else h
    val lines = if (horizontal) h else w
    val step = if (horizontal) 1 else w
    val win = 2 * r + 1
    for (l in 0 until lines) {
        if (l % 32 == 0) context.ensureActive()
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
