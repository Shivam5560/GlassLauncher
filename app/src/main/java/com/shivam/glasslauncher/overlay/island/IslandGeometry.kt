package com.shivam.glasslauncher.overlay.island

import kotlin.math.max

/** Camera exclusion geometry in dp, independent of rendering and safe to regression-test. */
object IslandGeometry {
    fun contentTop(cameraBottom: Float?, pillTop: Float, compactHeight: Float): Float =
        max(compactHeight, cameraBottom?.let { it - pillTop + 10f } ?: 14f)

    fun trailingStart(cameraRight: Float?, pillLeft: Float, width: Float, glyphLane: Float): Float =
        max(glyphLane, cameraRight?.let { it - pillLeft + 10f } ?: glyphLane).coerceIn(0f, width)

    fun peekMessageLines(fontScale: Float): Int = if (fontScale > 1.2f) 3 else 2

    fun peekHeight(cameraBand: Float, fontScale: Float): Float {
        val scale = fontScale.coerceAtLeast(1f)
        // App row, gaps/insets, two title lines and the available message lines.
        return cameraBand + max(26f, 18f * scale) + 28f + (44f + 20f * peekMessageLines(scale)) * scale
    }
}
