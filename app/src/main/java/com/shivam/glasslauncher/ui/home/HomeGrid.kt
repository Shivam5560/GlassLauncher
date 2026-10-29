package com.shivam.glasslauncher.ui.home

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.shivam.glasslauncher.data.AppEntry
import com.shivam.glasslauncher.data.AppItem
import com.shivam.glasslauncher.data.DropTarget
import com.shivam.glasslauncher.data.FolderItem
import com.shivam.glasslauncher.data.GRID_COLUMNS
import com.shivam.glasslauncher.data.GRID_ROWS
import com.shivam.glasslauncher.data.HomeItem
import com.shivam.glasslauncher.data.Slot
import com.shivam.glasslauncher.ui.glass.GlassShape
import com.shivam.glasslauncher.ui.glass.GlassSurface
import com.shivam.glasslauncher.ui.glass.GlassVariant

/** Callbacks from home-screen items up to the launcher root. */
interface HomeCallbacks {
    fun launch(entry: AppEntry, bounds: android.graphics.Rect?)
    fun openFolder(folder: FolderItem, bounds: Rect)
    fun showMenu(target: MenuTarget)
    fun startDrag(info: DragInfo, at: Offset)
    fun drag(at: Offset)
    fun endDrag()
    fun cancelDrag()
    fun remove(slot: Slot)
    fun lockScreen()
}

@Immutable
data class HomeContext(
    val apps: Map<String, AppEntry>,
    val labels: Map<String, String>,
    val badges: Map<String, Int>,
    val showLabels: Boolean,
    val showBadges: Boolean,
    val editMode: Boolean,
    val lightWallpaper: Boolean,
    /** Phase 5 icon/label/folder appearance. */
    val appearance: com.shivam.glasslauncher.data.Appearance = com.shivam.glasslauncher.data.Appearance(),
) {
    fun label(entry: AppEntry) = labels[entry.key] ?: entry.label
    fun badge(item: HomeItem): Int {
        if (!showBadges) return 0
        return when (item) {
            is AppItem -> apps[item.appKey]?.let { badges[it.packageName] } ?: 0
            is FolderItem -> item.apps.sumOf { k -> apps[k]?.let { badges[it.packageName] } ?: 0 }
        }
    }
}

/**
 * One 4x6 page. Double-tapping empty space runs [onDoubleTap] (Settings → Home gestures). Cells in [blocked] belong to widgets
 * (phase 2 free placement): they hold no icon and are not drop targets; [widgetLayer] draws the
 * widgets on top, given the cell size.
 */
@Composable
fun HomeGridPage(
    page: Int,
    items: List<HomeItem?>,
    ctx: HomeContext,
    drag: DragController,
    callbacks: HomeCallbacks,
    onDoubleTap: (() -> Unit)?,
    blocked: BooleanArray? = null,
    widgetLayer: (@Composable BoxScope.(cellW: Dp, cellH: Dp) -> Unit)? = null,
) {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 10.dp)
            .then(
                if (onDoubleTap == null) Modifier
                else Modifier.pointerInput(onDoubleTap) { detectTapGestures(onDoubleTap = { onDoubleTap() }) },
            ),
    ) {
        val cellW = maxWidth / GRID_COLUMNS
        val cellH = maxHeight / GRID_ROWS
        val scale = ctx.appearance.iconScale.coerceIn(0.7f, 1.3f)
        val iconSize = minOf(64.dp * scale, cellW * 0.82f, cellH * 0.72f)
        Column(Modifier.fillMaxSize()) {
            for (r in 0 until GRID_ROWS) {
                Row(Modifier.fillMaxWidth().weight(1f)) {
                    for (c in 0 until GRID_COLUMNS) {
                        val index = r * GRID_COLUMNS + c
                        if (blocked?.getOrNull(index) == true) {
                            SideEffect { drag.pageCells.remove(page to index) }
                            Spacer(Modifier.weight(1f).fillMaxHeight())
                        } else Box(Modifier.weight(1f).fillMaxHeight(), contentAlignment = Alignment.Center) {
                            HomeCell(
                                slot = Slot.Page(page, index),
                                item = items.getOrNull(index),
                                iconSize = iconSize,
                                ctx = ctx,
                                drag = drag,
                                callbacks = callbacks,
                                showLabel = ctx.showLabels,
                                register = { rect -> drag.pageCells[page to index] = rect },
                                hoverMatches = { t -> t is DropTarget.Cell && t.page == page && t.index == index },
                            )
                        }
                    }
                }
            }
        }
        widgetLayer?.invoke(this, cellW, cellH)
    }
}

/**
 * A home or dock cell: icon (app or folder), optional label, badges, edit-mode jiggle and
 * glass drop-target feedback while something is dragged over it.
 */
@Composable
fun HomeCell(
    slot: Slot,
    item: HomeItem?,
    iconSize: Dp,
    ctx: HomeContext,
    drag: DragController,
    callbacks: HomeCallbacks,
    showLabel: Boolean,
    register: (Rect) -> Unit,
    hoverMatches: (DropTarget?) -> Boolean,
) {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val coords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val hover = drag.hover
    val hovered = drag.info != null && hoverMatches(hover)
    val merge = hovered && when (hover) {
        is DropTarget.Cell -> hover.merge
        is DropTarget.DockCell -> hover.merge
        null -> false
    }
    val isSource = drag.isDragging(slot)
    val mergeScale by animateFloatAsState(if (merge) 1.14f else 1f, spring(dampingRatio = 0.5f, stiffness = 400f), label = "merge")
    val nudge by animateFloatAsState(if (hovered && !merge && item != null && !isSource) 1f else 0f, spring(dampingRatio = 0.7f), label = "nudge")
    val rotation = jiggleRotation(ctx.editMode && item != null && !isSource)

    fun bounds(): Rect? = coords[0]?.boundsInWindow()
    fun androidBounds() = bounds()?.let { android.graphics.Rect(it.left.toInt(), it.top.toInt(), it.right.toInt(), it.bottom.toInt()) }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.graphicsLayer { translationX = nudge * 10.dp.toPx() },
    ) {
        Box(
            Modifier
                .size(iconSize)
                .onGloballyPositioned {
                    coords[0] = it
                    register(it.boundsInWindow())
                }
                .then(
                    if (item == null) Modifier else Modifier.iconGestures(
                        key = item.id,
                        editMode = ctx.editMode,
                        coordinates = { coords[0] },
                        onTap = {
                            when (item) {
                                is AppItem -> ctx.apps[item.appKey]?.let { callbacks.launch(it, androidBounds()) }
                                is FolderItem -> bounds()?.let { callbacks.openFolder(item, it) }
                            }
                        },
                        onLongPress = {
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            val b = bounds() ?: return@iconGestures
                            callbacks.showMenu(
                                MenuTarget(
                                    entry = (item as? AppItem)?.let { ctx.apps[it.appKey] },
                                    folder = item as? FolderItem,
                                    slot = slot,
                                    anchor = b,
                                ),
                            )
                        },
                        onDragStart = { window, local ->
                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            callbacks.startDrag(
                                DragInfo(item, slot, (item as? AppItem)?.let { ctx.apps[it.appKey] }, local, with(density) { iconSize.toPx() }),
                                window,
                            )
                        },
                        onDrag = { callbacks.drag(it) },
                        onDragEnd = { callbacks.endDrag() },
                        onDragCancel = { callbacks.cancelDrag() },
                    ),
                ),
            contentAlignment = Alignment.Center,
        ) {
            when {
                item == null -> if (hovered) {
                    // Drop target: a Clear glass squircle where the icon will land.
                    GlassSurface(Modifier.size(iconSize), shape = GlassShape.Squircle, variant = GlassVariant.Clear, highlighted = true, role = null)
                }
                isSource -> Unit
                else -> {
                    if (merge) {
                        // Folder-forming feedback: a glass squircle swelling behind the target icon.
                        GlassSurface(
                            Modifier.size(iconSize * 1.28f),
                            shape = GlassShape.Squircle,
                            variant = GlassVariant.Clear,
                            highlighted = true,
                            role = null,
                        )
                    }
                    BadgedIcon(
                        iconSize = iconSize,
                        badgeCount = ctx.badge(item),
                        showRemove = ctx.editMode,
                        onRemove = { callbacks.remove(slot) },
                        rotation = rotation,
                        modifier = Modifier.graphicsLayer { scaleX = mergeScale; scaleY = mergeScale },
                    ) {
                        when (item) {
                            is AppItem -> ctx.apps[item.appKey]?.let { AppIconImage(it, iconSize) }
                            is FolderItem -> FolderIcon(item, ctx.apps, iconSize)
                        }
                    }
                }
            }
        }
        if (showLabel) {
            Spacer(Modifier.height(5.dp))
            val text = when (item) {
                is AppItem -> ctx.apps[item.appKey]?.let { ctx.label(it) } ?: ""
                is FolderItem -> item.name
                null -> ""
            }
            IconLabel(
                if (isSource) "" else text,
                false,
                Modifier.width(iconSize + 18.dp),
                sizeSp = ctx.appearance.labelSize,
                twoLines = ctx.appearance.twoLineLabels,
            )
        }
    }
}
