package com.shivam.glasslauncher.ui.glass

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.blur
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize

/**
 * What a [GlassSurface] refracts: an ordered stack of recorded [GraphicsLayer]s in window
 * coordinates (typically the wallpaper copy and the home screen content), plus a luminance
 * sampler for the adaptive tint. Everything here is drawn by this app - nothing is captured
 * from the screen.
 *
 * A surface must never use a backdrop that records the surface itself (that would recurse), so
 * screens provide nested backdrops: e.g. the dock refracts "wallpaper + pages", while folders and
 * Spotlight refract "wallpaper + pages + dock".
 */
@Stable
class Backdrop(
    private val layers: List<GraphicsLayer>,
    private val wallpaper: WallpaperSource.Snapshot,
    /** Layers are already blurred stills (as Smart Launcher 6 does): panels skip their own live blur. */
    val preBlurred: Boolean = false,
    /** Window position of the node the layers were recorded in (full-screen roots: zero). */
    private val origin: () -> Offset = { Offset.Zero },
) {
    /** Draws the backdrop so that it lines up with a panel located at [panelInWindow]. */
    fun DrawScope.drawBackdrop(panelInWindow: Offset) {
        val o = origin()
        translate(o.x - panelInWindow.x, o.y - panelInWindow.y) {
            for (l in layers) drawLayer(l)
        }
    }

    fun luminance(rect: Rect): Float = wallpaper.luminance(rect.left, rect.top, rect.right, rect.bottom)

    /** A backdrop that additionally includes [layer] on top. */
    fun plus(layer: GraphicsLayer) = Backdrop(layers + layer, wallpaper, preBlurred, origin)

    companion object {
        fun empty(wallpaper: WallpaperSource.Snapshot) = Backdrop(emptyList(), wallpaper)
    }
}

/**
 * Records this node's drawing into [layer] so glass surfaces can refract it. With
 * [drawToScreen] false the content is only recorded (used for the wallpaper copy, which the system
 * already draws behind the transparent window).
 */
fun Modifier.recordBackdrop(layer: GraphicsLayer, drawToScreen: Boolean = true): Modifier = drawWithContent {
    layer.record { this@drawWithContent.drawContent() }
    if (drawToScreen) drawLayer(layer)
}

/**
 * Full-screen wallpaper copy recorded into [layer] for use in a [Backdrop]. Normally invisible (the
 * system draws the wallpaper behind the window). With [parallax] non-null, the copy is drawn on
 * screen, slightly enlarged and shifted by the tilt, covering the system wallpaper; glass refracts
 * the same shifted copy, so refraction stays aligned.
 */
@Composable
fun WallpaperBackdropLayer(
    snapshot: WallpaperSource.Snapshot,
    layer: GraphicsLayer,
    enabled: Boolean,
    parallax: (() -> Offset)? = null,
    /** Black dim alpha baked into the recorded copy, so glass refracts the dimmed picture, not a brighter one. */
    dim: (() -> Float)? = null,
) {
    val image = snapshot.image
    Canvas(Modifier.fillMaxSize().recordBackdrop(layer, drawToScreen = parallax != null)) {
        if (enabled && image != null) {
            if (parallax == null) {
                drawImage(
                    image = image,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(image.width, image.height),
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize(size.width.toInt(), size.height.toInt()),
                )
            } else {
                val t = parallax()
                val over = size.width * PARALLAX_OVERSCAN
                val w = size.width + over * 2
                val h = size.height + over * 2
                drawImage(
                    image = image,
                    srcOffset = IntOffset.Zero,
                    srcSize = IntSize(image.width, image.height),
                    dstOffset = IntOffset((-over - t.x * over).toInt(), (-over - t.y * over).toInt()),
                    dstSize = IntSize(w.toInt(), h.toInt()),
                )
            }
            val a = dim?.invoke() ?: 0f
            if (a > 0.001f) drawRect(Color.Black.copy(alpha = a))
        }
    }
}

/** Extra wallpaper on each side for parallax travel, as a fraction of the screen width. */
private const val PARALLAX_OVERSCAN = 0.05f

/**
 * Invisible full-screen layer that replays a still [image] (a snapshot of another layer). Overlays
 * refract this instead of the live home layer: replaying the home re-runs every home glass panel's
 * blur and lens for every overlay panel, every frame (measured on a Redmi Turbo 5: drawer frames
 * 28 ms → 13 ms).
 */
@Composable
fun SnapshotBackdropLayer(image: androidx.compose.ui.graphics.ImageBitmap?, layer: GraphicsLayer) {
    Canvas(Modifier.fillMaxSize().recordBackdrop(layer, drawToScreen = false)) {
        if (image != null) drawImage(image, dstSize = IntSize(size.width.toInt(), size.height.toInt()))
    }
}

/**
 * Records the wallpaper under an overlay window. Lock cards keep the system background visible;
 * full-screen panels can draw a softly blurred copy, excluding underlying app UI from the backdrop.
 */
@Composable
fun OverlayWallpaperBackdrop(
    snapshot: WallpaperSource.Snapshot,
    drawToScreen: Boolean = false,
    blurRadius: Dp = 0.dp,
    dimAlpha: Float = 0f,
    content: @Composable () -> Unit,
) {
    val image = snapshot.image
    if (image == null) { content(); return }
    val layer = androidx.compose.ui.graphics.rememberGraphicsLayer()
    val view = androidx.compose.ui.platform.LocalView.current
    val backdrop = androidx.compose.runtime.remember(snapshot, blurRadius) {
        Backdrop(listOf(layer), snapshot, preBlurred = blurRadius > 0.dp)
    }
    androidx.compose.foundation.layout.Box {
        Canvas(Modifier.matchParentSize().recordBackdrop(layer, drawToScreen = drawToScreen).blur(blurRadius)) {
            val loc = IntArray(2).also(view::getLocationOnScreen)
            val sx = image.width.toFloat() / snapshot.screenWidth
            val sy = image.height.toFloat() / snapshot.screenHeight
            drawImage(
                image = image,
                srcOffset = IntOffset((loc[0] * sx).toInt(), (loc[1] * sy).toInt()),
                srcSize = IntSize((size.width * sx).toInt().coerceAtLeast(1), (size.height * sy).toInt().coerceAtLeast(1)),
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(size.width.toInt(), size.height.toInt()),
            )
            if (dimAlpha > 0f) drawRect(Color.Black.copy(alpha = dimAlpha.coerceIn(0f, 1f)))
        }
        androidx.compose.runtime.CompositionLocalProvider(LocalBackdrop provides backdrop, content = content)
    }
}
