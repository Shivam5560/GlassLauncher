package com.shivam.glasslauncher.ui.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shivam.glasslauncher.data.AppEntry
import com.shivam.glasslauncher.data.FolderItem
import com.shivam.glasslauncher.ui.glass.GlassColors
import com.shivam.glasslauncher.ui.glass.GlassIconButton
import com.shivam.glasslauncher.ui.glass.GlassIcons
import com.shivam.glasslauncher.ui.glass.GlassShape
import com.shivam.glasslauncher.ui.glass.GlassSurface
import com.shivam.glasslauncher.ui.glass.GlassType
import com.shivam.glasslauncher.ui.glass.GlassVariant
import kotlin.random.Random

/** The rendered (squircle or icon-pack) icon of [entry]. */
@Composable
fun AppIconImage(entry: AppEntry, size: Dp, modifier: Modifier = Modifier) {
    val renderer = LocalIconRenderer.current
    val generation = LocalIconGeneration.current
    val px = with(LocalDensity.current) { size.roundToPx() }
    val bitmap by produceState(renderer.cached(entry, px), entry.key, px, generation) {
        value = renderer.icon(entry, px)
    }
    val b = bitmap
    if (b != null) Image(b, contentDescription = entry.label, modifier = modifier.size(size)) else Spacer(modifier.size(size))
}

/** iOS folder icon: a Clear glass squircle with a 3x3 preview of the folder's apps. */
@Composable
fun FolderIcon(folder: FolderItem, apps: Map<String, AppEntry>, size: Dp, highlighted: Boolean = false, modifier: Modifier = Modifier) {
    GlassSurface(
        modifier = modifier.size(size),
        shape = GlassShape.Squircle,
        variant = GlassVariant.Clear,
        highlighted = highlighted,
        role = null,
        contentAlignment = Alignment.Center,
    ) {
        val mini = size * 0.22f
        val gap = size * 0.045f
        Column {
            for (r in 0 until 3) {
                Row {
                    for (c in 0 until 3) {
                        val entry = folder.apps.getOrNull(r * 3 + c)?.let { apps[it] }
                        Box(Modifier.padding(gap / 2).size(mini)) {
                            if (entry != null) AppIconImage(entry, mini)
                        }
                    }
                }
            }
        }
    }
}

/** Red glass notification badge. */
@Composable
fun NotificationBadge(count: Int, modifier: Modifier = Modifier) {
    GlassSurface(
        modifier = modifier.heightIn(min = 21.dp).widthIn(min = 21.dp),
        shape = GlassShape.Capsule,
        variant = GlassVariant.Clear,
        tintOverride = GlassColors.Badge,
        contentAlignment = Alignment.Center,
        role = null,
    ) {
        Text(
            if (count > 99) "99+" else count.toString(),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
            style = GlassType.Caption.copy(fontSize = GlassType.Caption.fontSize * 1.05f),
            color = Color.White,
            maxLines = 1,
        )
    }
}

/**
 * Label under an icon: white with a soft shadow (dark text on very light wallpapers). Size and
 * two-line wrapping come from Settings → Appearance → Icons.
 */
@Composable
fun IconLabel(text: String, lightWallpaper: Boolean, modifier: Modifier = Modifier, sizeSp: Float = 11.5f, twoLines: Boolean = false) {
    Text(
        text,
        modifier = modifier,
        style = GlassType.IconLabel.copy(
            fontSize = sizeSp.sp,
            lineHeight = (sizeSp * 1.2f).sp,
            shadow = if (lightWallpaper) null else Shadow(Color(0x99000000), Offset(0f, 1f), 4f),
        ),
        color = if (lightWallpaper) Color(0xFF1C1C1E) else Color.White,
        maxLines = if (twoLines) 2 else 1,
        overflow = TextOverflow.Ellipsis,
        textAlign = TextAlign.Center,
    )
}

/** Jiggle rotation for edit mode, with a random phase per icon. */
@Composable
fun jiggleRotation(enabled: Boolean): Float {
    if (!enabled) return 0f
    val phase = remember { Random.nextInt(0, 140) }
    val t = rememberInfiniteTransition(label = "jiggle")
    val r by t.animateFloat(
        initialValue = -1.6f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(tween(140 + phase / 4, delayMillis = 0, easing = LinearEasing), RepeatMode.Reverse),
        label = "jiggleRot",
    )
    return r
}

/** Glass "minus" badge shown on icons in edit mode. */
@Composable
fun RemoveBadge(onClick: () -> Unit, modifier: Modifier = Modifier) {
    GlassIconButton(GlassIcons.Minus, onClick = onClick, modifier = modifier, size = 24.dp)
}

/**
 * One gesture detector for launcher icons: tap, long-press (menu), and long-press-then-drag
 * (or immediate drag in edit mode). Positions passed to drag callbacks are window coordinates.
 */
fun Modifier.iconGestures(
    key: Any?,
    editMode: Boolean,
    coordinates: () -> LayoutCoordinates?,
    onTap: () -> Unit,
    onLongPress: () -> Unit,
    onDragStart: (window: Offset, local: Offset) -> Unit,
    onDrag: (window: Offset) -> Unit,
    onDragEnd: () -> Unit,
    onDragCancel: () -> Unit,
): Modifier = pointerInput(key, editMode) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        down.consume()
        val slop = viewConfiguration.touchSlop
        // 1 = tap released, 2 = cancelled (moved / consumed), 3 = drag start in edit mode, null = long press
        var last: PointerInputChange = down
        val outcome: Int? = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
            var r = 0
            while (r == 0) {
                val ev = awaitPointerEvent()
                val ch = ev.changes.firstOrNull { it.id == down.id }
                if (ch == null) { r = 2; break }
                last = ch
                r = when {
                    ch.changedToUpIgnoreConsumed() -> { ch.consume(); 1 }
                    ch.isConsumed -> 2
                    (ch.position - down.position).getDistance() > slop -> if (editMode) 3 else 2
                    else -> 0
                }
            }
            r
        }
        val toWindow = { p: Offset -> coordinates()?.localToWindow(p) ?: p }
        when (outcome) {
            1 -> onTap()
            2 -> Unit
            else -> {
                var dragging = false
                if (outcome == 3) {
                    dragging = true
                    onDragStart(toWindow(last.position), down.position)
                    last.consume()
                } else {
                    onLongPress()
                }
                while (true) {
                    val ev = awaitPointerEvent()
                    val ch = ev.changes.firstOrNull { it.id == down.id }
                    if (ch == null) {
                        if (dragging) onDragCancel()
                        break
                    }
                    if (ch.changedToUpIgnoreConsumed()) {
                        ch.consume()
                        if (dragging) onDragEnd()
                        break
                    }
                    // Long-press fired: this gesture is ours until release, so the pager never grabs the first sideways move.
                    ch.consume()
                    if (!dragging && (ch.position - down.position).getDistance() > slop * 1.5f) {
                        dragging = true
                        onDragStart(toWindow(ch.position), down.position)
                    }
                    if (dragging) {
                        ch.consume()
                        onDrag(toWindow(ch.position))
                    }
                }
            }
        }
    }
}

/** Places [badge] at the top-right corner over an icon of [iconSize]. */
@Composable
fun BadgedIcon(
    iconSize: Dp,
    badgeCount: Int,
    showRemove: Boolean,
    onRemove: () -> Unit,
    rotation: Float,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    Box(modifier.size(iconSize).graphicsLayer { rotationZ = rotation }) {
        icon()
        if (badgeCount > 0) NotificationBadge(badgeCount, Modifier.align(Alignment.TopEnd).offset(x = 7.dp, y = (-6).dp))
        if (showRemove) RemoveBadge(onRemove, Modifier.align(Alignment.TopStart).offset(x = (-8).dp, y = (-8).dp))
    }
}

