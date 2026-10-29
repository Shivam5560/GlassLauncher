package com.shivam.glasslauncher.ui.home

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
    GlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp)
            .onGloballyPositioned { onBounds(it.boundsInWindow()) },
        shape = GlassShape.rounded(34.dp),
        role = null,
    ) {
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
                        iconSize = 60.dp * ctx.appearance.iconScale.coerceIn(0.7f, 1.2f),
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
    }
}

private fun Modifier.dot(color: Color): Modifier = this.drawBehind { drawCircle(color) }

