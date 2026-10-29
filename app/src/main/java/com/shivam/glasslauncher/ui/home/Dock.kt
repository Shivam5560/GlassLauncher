package com.shivam.glasslauncher.ui.home

import androidx.compose.runtime.remember
import android.view.RoundedCorner
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import com.shivam.glasslauncher.data.DOCK_SIZE
import com.shivam.glasslauncher.data.DropTarget
import com.shivam.glasslauncher.data.HomeItem
import com.shivam.glasslauncher.data.Slot
import com.shivam.glasslauncher.ui.glass.GlassIcon
import com.shivam.glasslauncher.ui.glass.GlassIcons
import com.shivam.glasslauncher.ui.glass.GlassShape
import com.shivam.glasslauncher.ui.glass.GlassSurface
import com.shivam.glasslauncher.ui.glass.GlassVariant
import com.shivam.glasslauncher.ui.glass.LocalGlassContentColor

/** The 4-slot glass dock (Regular variant). */
@Composable
fun Dock(
    items: List<HomeItem?>,
    ctx: HomeContext,
    drag: DragController,
    callbacks: HomeCallbacks,
    onBounds: (Rect) -> Unit,
    modifier: Modifier = Modifier,
) {
    // iOS 26: the dock is concentric with the screen's rounded corners (same margin on the sides and
    // bottom, radius = screen radius - margin), so its corners are never clipped by the display.
    val view = LocalView.current
    val density = LocalDensity.current
    val screenRadius = remember(view) {
        view.rootWindowInsets?.getRoundedCorner(RoundedCorner.POSITION_BOTTOM_LEFT)?.radius?.let { with(density) { it.toDp() } } ?: 0.dp
    }
    val barModifier = modifier
        .fillMaxWidth()
        .padding(start = DockMargin, end = DockMargin, bottom = DockBottom)
        .onGloballyPositioned { onBounds(it.boundsInWindow()) }
    val iconSize = 60.dp * (ctx.appearance.iconScale * ctx.appearance.dockScale).coerceIn(0.7f, 1.3f)
    val icons: @Composable () -> Unit = {
        Row(
            Modifier.fillMaxWidth().padding(vertical = 14.dp, horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (i in 0 until DOCK_SIZE) {
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    HomeCell(
                        slot = Slot.Dock(i),
                        item = items.getOrNull(i),
                        iconSize = iconSize,
                        ctx = ctx,
                        drag = drag,
                        callbacks = callbacks,
                        showLabel = false,
                        register = { drag.dockCells[i] = it },
                        hoverMatches = { t -> t is DropTarget.DockCell && t.index == i },
                    )
                }
            }
        }
    }
    if (ctx.appearance.dockGlass) {
        GlassSurface(
            modifier = barModifier,
            shape = GlassShape.rounded(maxOf(34.dp, screenRadius - DockMargin)),
            role = null,
        ) { icons() }
    } else {
        Box(barModifier) { icons() }
    }
}

/**
 * Page indicator on a Clear glass capsule. Tapping it opens Spotlight (like the iOS "Search"
 * pill); the last glyph stands for the App Library.
 */
@Composable
fun PageDots(
    homePages: Int,
    current: Int,
    onClick: (Rect?) -> Unit,
    modifier: Modifier = Modifier,
    /** iOS 26: "Search" at rest, page dots only while (and just after) the pages move. */
    showDots: Boolean = true,
) {
    val bounds = androidx.compose.runtime.remember { arrayOfNulls<Rect>(1) }
    GlassSurface(
        modifier = modifier.onGloballyPositioned { bounds[0] = it.boundsInWindow() },
        shape = GlassShape.Capsule,
        variant = GlassVariant.Clear,
        onClick = { onClick(bounds[0]) },
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val c = LocalGlassContentColor.current
            GlassIcon(GlassIcons.Search, size = 13.dp, tint = c.copy(alpha = 0.85f))
            Spacer(Modifier.width(7.dp))
            androidx.compose.animation.AnimatedContent(
                targetState = showDots,
                transitionSpec = {
                    (androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(180)) togetherWith
                        androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(120)))
                        .using(androidx.compose.animation.SizeTransform(clip = false))
                },
                label = "pill",
            ) { dots ->
                if (!dots) {
                    com.shivam.glasslauncher.ui.glass.GlassText("Search", style = com.shivam.glasslauncher.ui.glass.GlassType.Footnote, color = c.copy(alpha = 0.9f))
                } else Row(verticalAlignment = Alignment.CenterVertically) { PageDotRow(homePages, current, c) }
            }
        }
    }
}

@Composable
private fun PageDotRow(homePages: Int, current: Int, c: Color) {
            for (p in 0 until homePages) {
                Box(
                    Modifier
                        .padding(horizontal = 3.dp)
                        .size(7.dp)
                        .dot(if (p == current) c else c.copy(alpha = 0.35f)),
                )
            }
            Box(Modifier.padding(start = 3.dp)) {
                GlassIcon(GlassIcons.Grid, size = 10.dp, tint = if (current >= homePages) c else c.copy(alpha = 0.35f))
            }
}

private fun Modifier.dot(color: Color): Modifier = this.drawBehind { drawCircle(color) }

private val DockMargin = 14.dp
/** Extra room under the dock so the gesture pill sits clear of it. */
private val DockBottom = 22.dp
