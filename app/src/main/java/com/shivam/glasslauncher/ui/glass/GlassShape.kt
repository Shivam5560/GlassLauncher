package com.shivam.glasslauncher.ui.glass

import androidx.compose.runtime.Immutable
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * A rectangle whose corners are quarter superellipses `|x|^n + |y|^n = r^n`.
 * The lens shader uses the same signed-distance function, so the refracting rim follows the
 * outline exactly. [cornerRadius] null means "half the short side" (capsule or squircle).
 */
@Immutable
class GlassShape(val cornerRadius: Dp?, val exponent: Float) : Shape {

    fun radiusPx(size: Size, density: Density): Float {
        val max = min(size.width, size.height) / 2f
        return cornerRadius?.let { with(density) { it.toPx() }.coerceAtMost(max) } ?: max
    }

    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path()
        addSuperellipseRect(path, size.width, size.height, radiusPx(size, density), exponent)
        return Outline.Generic(path)
    }

    override fun equals(other: Any?) = other is GlassShape && other.cornerRadius == cornerRadius && other.exponent == exponent
    override fun hashCode() = 31 * (cornerRadius?.hashCode() ?: 0) + exponent.hashCode()

    companion object {
        /** Continuous-corner panel radius used for dock, folders and cards. */
        fun rounded(radius: Dp) = GlassShape(radius, 3.6f)
        val Panel = rounded(28.dp)
        val Card = rounded(22.dp)
        val Row = rounded(16.dp)
        /** Fully rounded ends (buttons, search fields, pills). */
        val Capsule = GlassShape(null, 2f)
        /** iOS-style app icon shape. */
        val Squircle = GlassShape(null, 5f)
        val Circle = GlassShape(null, 2f)
    }
}

/** Appends a superellipse-cornered rectangle to [path]. */
fun addSuperellipseRect(path: Path, w: Float, h: Float, radius: Float, n: Float, left: Float = 0f, top: Float = 0f) {
    val r = radius.coerceIn(0f, min(w, h) / 2f)
    val seg = 14
    val e = 2.0 / n
    fun cx(t: Double) = (cos(t).coerceAtLeast(0.0)).pow(e).toFloat() * r
    fun cy(t: Double) = (sin(t).coerceAtLeast(0.0)).pow(e).toFloat() * r
    // corner centres
    val l = left + r; val t0 = top + r; val rr = left + w - r; val b = top + h - r
    // top-left: from left edge (theta 0) to top edge (theta pi/2)
    for (i in 0..seg) {
        val th = i * (PI / 2) / seg
        val x = l - cx(th); val y = t0 - cy(th)
        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    for (i in seg downTo 0) { val th = i * (PI / 2) / seg; path.lineTo(rr + cx(th), t0 - cy(th)) }
    for (i in 0..seg) { val th = i * (PI / 2) / seg; path.lineTo(rr + cx(th), b + cy(th)) }
    for (i in seg downTo 0) { val th = i * (PI / 2) / seg; path.lineTo(l - cx(th), b + cy(th)) }
    path.close()
}

/** Same outline as an [android.graphics.Path], used for icon masking and View-based glass. */
fun superellipseRectPath(w: Float, h: Float, radius: Float, n: Float, left: Float = 0f, top: Float = 0f): android.graphics.Path {
    val p = Path()
    addSuperellipseRect(p, w, h, radius, n, left, top)
    return p.asAndroidPath()
}
