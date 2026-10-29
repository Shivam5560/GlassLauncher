package com.shivam.glasslauncher.overlay.island

import kotlin.math.max

/** Camera exclusion geometry in dp, independent of rendering and safe to regression-test. */
object IslandGeometry {
    fun contentTop(cameraBottom: Float?, pillTop: Float, compactHeight: Float): Float =
        max(compactHeight, cameraBottom?.let { it - pillTop + 10f } ?: 14f)

    fun trailingStart(cameraRight: Float?, pillLeft: Float, width: Float, glyphLane: Float): Float =
        max(glyphLane, cameraRight?.let { it - pillLeft + 10f } ?: glyphLane).coerceIn(0f, width)

    fun peekHeight(cameraBand: Float, fontScale: Float): Float =
        cameraBand + 140f * fontScale.coerceAtLeast(1f) + 20f
}
