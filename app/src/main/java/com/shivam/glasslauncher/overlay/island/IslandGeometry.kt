package com.shivam.glasslauncher.overlay.island

import kotlin.math.max

/** Camera exclusion geometry in dp, independent of rendering and safe to regression-test. */
object IslandGeometry {
    fun contentTop(cameraBottom: Float?, pillTop: Float, compactHeight: Float): Float =
        max(compactHeight, cameraBottom?.let { it - pillTop + 10f } ?: 14f)

    fun trailingStart(cameraRight: Float?, pillLeft: Float, width: Float, glyphLane: Float): Float =
        max(glyphLane, cameraRight?.let { it - pillLeft + 10f } ?: glyphLane).coerceIn(0f, width)

    fun peekMessageLines(fontScale: Float): Int = if (fontScale > 1.2f) 3 else 2

    fun peekHeight(cameraBand: Float, fontScale: Float, title: String? = null, message: String? = null): Float {
        val scale = fontScale.coerceAtLeast(1f)
        // ponytail: ~38 characters per line at 1x on a 420 dp card; measure text if previews still pad oddly.
        val perLine = (38f / scale).toInt().coerceAtLeast(10)
        fun lines(t: String?, max: Int) = if (t == null) max else ((t.length + perLine - 1) / perLine).coerceIn(if (t.isEmpty()) 0 else 1, max)
        val titleLines = lines(title, 2)
        val messageLines = lines(message, peekMessageLines(scale))
        // App row, gaps/insets, title lines (22 dp) and message lines (20 dp).
        return cameraBand + max(26f, 18f * scale) + 28f + (22f * titleLines + 20f * messageLines) * scale
    }
}
