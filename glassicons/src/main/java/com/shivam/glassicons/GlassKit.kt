package com.shivam.glassicons

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * A small, self-contained Liquid Glass kit for the Glass Icons app (it is a separate APK, so it
 * can't share the launcher's `ui/glass`). Every surface is frosted glass: the wallpaper behind the
 * panel is re-drawn inside it, blurred, then tinted and given a light rim. No screen capture.
 */
class Backdrop(val image: ImageBitmap?, val rootSize: IntSize, val dark: Boolean)

val LocalBackdrop = staticCompositionLocalOf { Backdrop(null, IntSize.Zero, true) }
val LocalContent = staticCompositionLocalOf { Color.White }

/** Draws [image] centre-cropped into a [size] canvas, shifted by [offset] (screen alignment). */
fun DrawScope.drawCropped(image: ImageBitmap, root: IntSize, offset: Offset = Offset.Zero) {
    if (root.width == 0 || root.height == 0) return
    val s = max(root.width.toFloat() / image.width, root.height.toFloat() / image.height)
    val dw = (image.width * s).roundToInt()
    val dh = (image.height * s).roundToInt()
    val dx = ((root.width - dw) / 2f - offset.x).roundToInt()
    val dy = ((root.height - dh) / 2f - offset.y).roundToInt()
    drawImage(image, dstOffset = IntOffset(dx, dy), dstSize = IntSize(dw, dh))
}

@Composable
fun GlassPanel(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(28.dp),
    clear: Boolean = false,
    highlighted: Boolean = false,
    onClick: (() -> Unit)? = null,
    contentAlignment: Alignment = Alignment.TopStart,
    content: @Composable BoxScope.() -> Unit,
) {
    val backdrop = LocalBackdrop.current
    var origin by remember { mutableStateOf(Offset.Zero) }
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val dark = backdrop.dark
    val tint = when {
        highlighted -> if (dark) Color.White.copy(alpha = 0.32f) else Color.White.copy(alpha = 0.7f)
        clear -> if (dark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.22f)
        else -> if (dark) Color(0x661C1C22) else Color(0x80FFFFFF)
    }
    val base = modifier
        .onGloballyPositioned { origin = it.positionInRoot() }
        .clip(shape)
        .let { if (onClick != null) it.clickable(interaction, indication = null, onClick = onClick) else it }
    Box(base, contentAlignment = contentAlignment) {
        val img = backdrop.image
        if (img != null) {
            Canvas(Modifier.matchParentSize().blur(if (clear) 10.dp else 26.dp)) {
                drawCropped(img, backdrop.rootSize, origin)
            }
        }
        Box(
            Modifier
                .matchParentSize()
                .background(tint)
                .background(Brush.verticalGradient(listOf(Color.White.copy(alpha = if (pressed) 0.28f else 0.14f), Color.Transparent)))
                .border(
                    1.dp,
                    Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0.25f))),
                    shape,
                ),
        )
        CompositionLocalProvider(LocalContent provides if (dark) Color.White else Color(0xFF111114)) {
            content()
        }
    }
}

@Composable
fun GlassLabel(text: String, modifier: Modifier = Modifier, size: Int = 15, weight: FontWeight = FontWeight.Normal, alpha: Float = 1f, color: Color? = null, maxLines: Int = Int.MAX_VALUE) {
    val c = color ?: LocalContent.current
    BasicText(text, modifier, style = TextStyle(color = c.copy(alpha = c.alpha * alpha), fontSize = size.sp, fontWeight = weight, letterSpacing = (-0.2).sp), maxLines = maxLines)
}

@Composable
fun GlassButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, prominent: Boolean = false) {
    GlassPanel(modifier.height(46.dp), shape = RoundedCornerShape(23.dp), clear = !prominent, highlighted = prominent, onClick = onClick, contentAlignment = Alignment.Center) {
        GlassLabel(text, Modifier.padding(horizontal = 20.dp), weight = FontWeight.SemiBold)
    }
}

/** Segmented control: a Clear glass track with a brighter glass thumb on the selected item. */
@Composable
fun GlassSegmented(options: List<String>, selected: Int, onSelect: (Int) -> Unit, modifier: Modifier = Modifier) {
    GlassPanel(modifier.height(40.dp), shape = RoundedCornerShape(20.dp), clear = true) {
        Row(Modifier.padding(3.dp)) {
            options.forEachIndexed { i, label ->
                val sel = i == selected
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(17.dp))
                        .background(if (sel) Color.White.copy(alpha = if (LocalBackdrop.current.dark) 0.28f else 0.75f) else Color.Transparent)
                        .clickable { onSelect(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    GlassLabel(label, size = 14, weight = if (sel) FontWeight.SemiBold else FontWeight.Medium)
                }
            }
        }
    }
}
