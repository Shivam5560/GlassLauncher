package com.shivam.glasslauncher.polish

/** Normalized, resolution-independent adjustments shared by preview and final rendering. */
data class WallpaperEdit(
    val zoom: Float = 1f,
    val focusX: Float = 0.5f,
    val focusY: Float = 0.5f,
    val dim: Float = 0f,
    val warmth: Float = 0f,
) {
    fun normalized() = WallpaperEdit(
        zoom.safe(1f, 1f, 2f), focusX.safe(0.5f, 0f, 1f), focusY.safe(0.5f, 0f, 1f),
        dim.safe(0f, 0f, 0.6f), warmth.safe(0f, -1f, 1f),
    )

    fun crop(width: Int, height: Int, targetWidth: Int, targetHeight: Int): WallpaperCrop {
        require(width > 0 && height > 0 && targetWidth > 0 && targetHeight > 0)
        val e = normalized()
        val scale = maxOf(targetWidth.toFloat() / width, targetHeight.toFloat() / height) * e.zoom
        val w = targetWidth / scale
        val h = targetHeight / scale
        // Focus is the pan fraction of the available crop travel, so both edges remain reachable.
        val left = (width - w).coerceAtLeast(0f) * e.focusX
        val top = (height - h).coerceAtLeast(0f) * e.focusY
        return WallpaperCrop(left, top, left + w, top + h)
    }

    /** Color matrix applies warmth before dimming, keeping preview and exported pixels identical. */
    fun colorMatrix(): FloatArray {
        val e = normalized()
        val gain = 1f - e.dim
        return floatArrayOf(
            gain, 0f, 0f, 0f, e.warmth * 24f * gain,
            0f, gain, 0f, 0f, e.warmth * 4f * gain,
            0f, 0f, gain, 0f, -e.warmth * 24f * gain,
            0f, 0f, 0f, 1f, 0f,
        )
    }
}

data class WallpaperCrop(val left: Float, val top: Float, val right: Float, val bottom: Float)

private fun Float.safe(fallback: Float, min: Float, max: Float) = if (isFinite()) coerceIn(min, max) else fallback

/** An external wallpaper must never be replaced by automatic switching. Unknown ownership is unsafe. */
internal fun shouldSwitchWallpaper(savedId: Int, currentId: Int?, applied: String?, desired: String): Boolean =
    savedId > 0 && savedId == currentId && applied != desired

/** Cap both axes together so tall phones retain the same crop as the studio preview. */
internal fun wallpaperOutputSize(screenWidth: Int, screenHeight: Int): Pair<Int, Int> {
    require(screenWidth > 0 && screenHeight > 0)
    val width = minOf(screenWidth, screenHeight)
    val height = maxOf(screenWidth, screenHeight)
    val scale = minOf(1f, 1440f / width, 3200f / height)
    return (width * scale).toInt().coerceAtLeast(1) to (height * scale).toInt().coerceAtLeast(1)
}
