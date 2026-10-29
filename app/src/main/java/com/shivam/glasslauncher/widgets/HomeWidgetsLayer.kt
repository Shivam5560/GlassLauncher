package com.shivam.glasslauncher.widgets

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.shivam.glasslauncher.data.GRID_COLUMNS
import com.shivam.glasslauncher.data.GRID_ROWS
import com.shivam.glasslauncher.data.HomeWidget
import com.shivam.glasslauncher.ui.glass.GlassShape
import com.shivam.glasslauncher.ui.glass.GlassSurface
import com.shivam.glasslauncher.ui.glass.GlassVariant
import com.shivam.glasslauncher.ui.home.RemoveBadge
import com.shivam.glasslauncher.ui.home.jiggleRotation

/**
 * Widgets on one home page of the free-placement grid. Each widget covers its cells (icons
 * reflow around it). Long-press opens the widget menu; in edit mode a widget jiggles, drags to a
 * new spot (snapping to cells, never overlapping another widget) and resizes with glass handles.
 */
@Composable
fun BoxScope.HomeWidgetsLayer(
    page: Int,
    widgets: List<HomeWidget>,
    cellW: Dp,
    cellH: Dp,
    ui: WidgetUiState,
    editMode: Boolean,
) {
    if (widgets.isEmpty()) return
    ui.cellWidthDp = cellW.value
    ui.cellHeightDp = cellH.value
    val state by ui.repo.state.collectAsState()
    widgets.forEach { w ->
        key(w.id) {
            val inst = state?.instances?.get(w.id)
            HomeWidgetFrame(page, w, inst, widgets, cellW, cellH, ui, editMode)
        }
    }
}

@Composable
private fun HomeWidgetFrame(
    page: Int,
    w: HomeWidget,
    inst: WidgetInstance?,
    all: List<HomeWidget>,
    cellW: Dp,
    cellH: Dp,
    ui: WidgetUiState,
    editMode: Boolean,
) {
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    val cellWPx = with(density) { cellW.toPx() }
    val cellHPx = with(density) { cellH.toPx() }
    var drag by remember { mutableStateOf<Offset?>(null) }
    var resize by remember { mutableStateOf<GridRect?>(null) }
    val coords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    val rect = w.rect
    val others = remember(all, w.id) { all.filter { it.id != w.id }.map { it.rect } }
    val current by rememberUpdatedState(rect)

    // Where a drag would drop it (snapped, nearest free spot).
    val dragTarget: GridRect? = drag?.let { d ->
        val snapped = GridPlacement.snap(rect.col + d.x / cellWPx, rect.row + d.y / cellHPx, rect.w, rect.h, GRID_COLUMNS, GRID_ROWS)
        GridPlacement.nearestFree(snapped, others, GRID_COLUMNS, GRID_ROWS)
    }
    val shown = resize ?: rect
    val rotation = jiggleRotation(editMode && drag == null && resize == null)

    // Glass placeholder where the widget will land (drag) or its new size (resize).
    val ghost = dragTarget ?: resize
    if (ghost != null) {
        GlassSurface(
            Modifier
                .offset(cellW * ghost.col, cellH * ghost.row)
                .size(cellW * ghost.w, cellH * ghost.h)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            shape = WidgetShape,
            variant = GlassVariant.Clear,
            highlighted = true,
            role = null,
        )
    }

    Box(
        Modifier
            .zIndex(if (drag != null) 2f else 1f)
            .offset(cellW * rect.col, cellH * rect.row)
            .size(cellW * shown.w, cellH * shown.h)
            .graphicsLayer {
                val d = drag
                if (d != null) {
                    translationX = d.x
                    translationY = d.y
                    scaleX = 1.04f
                    scaleY = 1.04f
                    alpha = 0.92f
                }
                rotationZ = rotation * 0.5f
            }
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .onGloballyPositioned { coords[0] = it }
            .widgetLongPress(enabled = !editMode) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                val b = coords[0]?.boundsInWindow() ?: Rect.Zero
                ui.menu = WidgetMenu(w.id, b, onHome = true, page = page)
            },
    ) {
        if (inst != null) {
            WidgetContent(inst, Modifier.fillMaxSize(), size = WidgetSize.layoutFor(inst.kind, shown.w, shown.h))
        } else {
            WidgetPrompt(WidgetGlyphs.Widgets, "Widget missing", "Remove", { ui.remove(w.id) }, WidgetSize.SMALL, Modifier.fillMaxSize())
        }
        if (editMode) {
            // Edit mode: the widget itself is not interactive; tap edits, drag moves.
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(w.id) { detectTapGestures { if (inst != null && inst.kind != WidgetKind.SEARCH) ui.editing = w.id } }
                    .pointerInput(w.id, cellWPx, cellHPx) {
                        detectDragGestures(
                            onDragStart = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                drag = Offset.Zero
                            },
                            onDragEnd = {
                                val d = drag
                                drag = null
                                if (d != null) {
                                    val start = current
                                    val snapped = GridPlacement.snap(start.col + d.x / cellWPx, start.row + d.y / cellHPx, start.w, start.h, GRID_COLUMNS, GRID_ROWS)
                                    ui.moveWidget(w.id, page, snapped)
                                }
                            },
                            onDragCancel = { drag = null },
                        ) { change, amount ->
                            change.consume()
                            drag = (drag ?: Offset.Zero) + amount
                        }
                    },
            )
            RemoveBadge(onClick = { ui.remove(w.id) }, modifier = Modifier.align(Alignment.TopStart).offset((-8).dp, (-8).dp))
            if (drag == null && inst != null) {
                val spec = resizeSpec(inst, context, cellW.value, cellH.value)
                if (spec != null) {
                    // Each handle moves one or two edges (cells, fractional); the rect snaps like SL6's free rectangle.
                    fun dragEdges(dl: Float, dt: Float, dr: Float, db: Float) {
                        resize = GridPlacement.resizeEdges(
                            rect, dl, dt, dr, db, GRID_COLUMNS, GRID_ROWS,
                            minW = spec.minW, minH = spec.minH,
                            maxW = if (spec.horizontal) GRID_COLUMNS else rect.w,
                            maxH = if (spec.vertical) GRID_ROWS else rect.h,
                        ).let { r -> if (GridPlacement.collides(r, others)) resize ?: rect else r }
                    }
                    val endResize: () -> Unit = {
                        val r = resize
                        resize = null
                        if (r != null && r != rect) ui.resizeWidget(w.id, r.w, r.h, r.col, r.row)
                    }
                    ResizeHandle(Modifier.align(Alignment.BottomEnd).offset(6.dp, 6.dp), { t -> dragEdges(0f, 0f, t.x / cellWPx, t.y / cellHPx) }, endResize)
                    if (spec.horizontal) {
                        ResizeHandle(Modifier.align(Alignment.CenterEnd).offset(6.dp, 0.dp), { t -> dragEdges(0f, 0f, t.x / cellWPx, 0f) }, endResize, axisY = false, size = 24.dp)
                        ResizeHandle(Modifier.align(Alignment.CenterStart).offset((-6).dp, 0.dp), { t -> dragEdges(t.x / cellWPx, 0f, 0f, 0f) }, endResize, axisY = false, size = 24.dp)
                    }
                    if (spec.vertical) {
                        ResizeHandle(Modifier.align(Alignment.BottomCenter).offset(0.dp, 6.dp), { t -> dragEdges(0f, 0f, 0f, t.y / cellHPx) }, endResize, axisX = false, size = 24.dp)
                        ResizeHandle(Modifier.align(Alignment.TopCenter).offset(0.dp, (-6).dp), { t -> dragEdges(0f, t.y / cellHPx, 0f, 0f) }, endResize, axisX = false, size = 24.dp)
                    }
                }
            }
        }
    }
}

/** How a widget may be resized: our widgets snap to their iOS sizes, app widgets follow their provider. */
private data class ResizeSpec(
    val allowed: List<Pair<Int, Int>>?,
    val minW: Int,
    val minH: Int,
    val horizontal: Boolean,
    val vertical: Boolean,
)

private fun resizeSpec(inst: WidgetInstance, context: android.content.Context, cellWDp: Float, cellHDp: Float): ResizeSpec? {
    if (inst.kind == WidgetKind.APP_WIDGET) {
        val info = GlassWidgetHost.get(context).info(inst.appWidgetId) ?: return null
        val h = GlassWidgetHost.canResize(info, horizontal = true)
        val v = GlassWidgetHost.canResize(info, horizontal = false)
        if (!h && !v) return null
        val (mw, mh) = GlassWidgetHost.minSpan(info, cellWDp, cellHDp, context.resources.displayMetrics.density)
        return ResizeSpec(null, mw, mh, h, v)
    }
    val sizes = inst.kind.sizes
    if (sizes.size < 2) return null
    // Free resize on the grid (SL6-style); Small, Medium and Large stay one tap away in the widget menu.
    return ResizeSpec(null, 2, 2, horizontal = true, vertical = true)
}

/** A Clear glass handle at the widget's corner; reports the total drag offset. */
@Composable
private fun ResizeHandle(
    modifier: Modifier,
    onDrag: (Offset) -> Unit,
    onEnd: () -> Unit,
    axisX: Boolean = true,
    axisY: Boolean = true,
    size: androidx.compose.ui.unit.Dp = 30.dp,
) {
    var total by remember { mutableStateOf(Offset.Zero) }
    var active by remember { mutableStateOf(false) }
    GlassSurface(
        modifier
            .size(size)
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragStart = { total = Offset.Zero; active = true },
                    onDragEnd = { active = false; onEnd() },
                    onDragCancel = { active = false; onEnd() },
                ) { change, amount ->
                    change.consume()
                    total += Offset(if (axisX) amount.x else 0f, if (axisY) amount.y else 0f)
                    onDrag(total)
                }
            },
        shape = GlassShape.Circle,
        variant = GlassVariant.Clear,
        highlighted = active,
        role = null,
        contentAlignment = Alignment.Center,
    ) {
        com.shivam.glasslauncher.ui.glass.GlassIcon(WidgetGlyphs.Resize, size = 16.dp)
    }
}
