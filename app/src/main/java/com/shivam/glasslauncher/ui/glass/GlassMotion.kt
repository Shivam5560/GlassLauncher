package com.shivam.glasslauncher.ui.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.lifecycle.repeatOnLifecycle

/** Spring presets tuned to feel like iOS. */
object GlassSprings {
    /** Liquid open: visible overshoot. */
    val Open: AnimationSpec<Float> = spring(dampingRatio = 0.66f, stiffness = 300f)
    /** Close: critically damped and quick. */
    val Close: AnimationSpec<Float> = spring(dampingRatio = 1f, stiffness = 520f)
    val Snappy: AnimationSpec<Float> = spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessMedium)
}

/**
 * Morphs [content] out of [origin] (window coordinates, e.g. the tapped icon or the dock) with a
 * liquid spring: it grows from the origin's size and position, overshoots, and stretches
 * slightly along the direction of motion while moving fast. Content stays composed until the
 * close animation finishes. [progress] (0..1) is reported so callers can fade scrims in sync.
 */
@Composable
fun GlassMorph(
    visible: Boolean,
    origin: Rect?,
    modifier: Modifier = Modifier,
    contentAlignment: Alignment = Alignment.Center,
    onProgress: (Float) -> Unit = {},
    content: @Composable BoxScope.() -> Unit,
) {
    val progress = remember { Animatable(0f) }
    var composed by remember { mutableStateOf(visible) }
    var bounds by remember { mutableStateOf<Rect?>(null) }

    LaunchedEffect(visible) {
        if (visible) {
            composed = true
            progress.animateTo(1f, GlassSprings.Open) { onProgress(value) }
        } else {
            progress.animateTo(0f, GlassSprings.Close) { onProgress(value) }
            composed = false
        }
    }
    if (!composed) return

    Box(
        modifier = modifier
            .onGloballyPositioned { bounds = it.boundsInWindow() }
            .graphicsLayer {
                val p = progress.value
                val target = bounds
                val o = origin
                if (target != null && o != null && target.width > 0 && target.height > 0) {
                    val sx = lerp(o.width / target.width, 1f, p)
                    val sy = lerp(o.height / target.height, 1f, p)
                    // liquid stretch: elongate along the motion while velocity is high
                    val v = (progress.velocity / 12f).coerceIn(-1f, 1f)
                    val stretch = 0.07f * v
                    scaleX = sx * (1f - stretch * 0.6f)
                    scaleY = sy * (1f + stretch)
                    transformOrigin = TransformOrigin.Center
                    translationX = lerp(o.center.x - target.center.x, 0f, p)
                    translationY = lerp(o.center.y - target.center.y, 0f, p)
                } else {
                    val s = lerp(0.85f, 1f, p)
                    scaleX = s
                    scaleY = s
                }
                alpha = (p * 2.5f).coerceIn(0f, 1f)
            },
        contentAlignment = contentAlignment,
        content = content,
    )
}

internal fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

/**
 * A polling loop that only runs while the host activity is RESUMED: it is cancelled when Glass goes to
 * the background and restarted (from the top) on resume, so idle widgets stop waking the CPU.
 */
@Composable
fun ResumedLoop(vararg keys: Any?, block: suspend kotlinx.coroutines.CoroutineScope.() -> Unit) {
    val lifecycle = androidx.lifecycle.compose.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(lifecycle, *keys) {
        lifecycle.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.RESUMED, block)
    }
}
