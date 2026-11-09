package com.shivam.glasslauncher.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.shivam.glasslauncher.GlassApp
import com.shivam.glasslauncher.data.*
import com.shivam.glasslauncher.ui.glass.*
import com.shivam.glasslauncher.ui.home.*
import com.shivam.glasslauncher.widgets.*

/** Render the saved default page with the same grid, icons and dock as Home. Never interactive. */
@Composable
internal fun WallpaperHomePreview(luminance: Float) {
    val app = GlassApp.from(LocalContext.current)
    val layout = app.layout.layout.collectAsState().value ?: return
    val settings = app.settings.state.collectAsState().value ?: return
    val apps by app.apps.apps.collectAsState()
    val widgets by WidgetRepository.get(app).state.collectAsState()
    val density = LocalDensity.current
    val metrics = app.resources.displayMetrics
    val screenDp = minOf(metrics.widthPixels, metrics.heightPixels) / density.density
    val page = layout.defaultPage.coerceIn(0, (layout.pages.size - 1).coerceAtLeast(0))
    val drag = remember { DragController() }
    val ctx = HomeContext(apps.associateBy { it.key }, layout.labels, emptyMap(), settings.showLabels,
        false, false, luminance > 0.7f, settings.appearance)
    BoxWithConstraints(Modifier.fillMaxSize().clearAndSetSemantics { }) {
        val scaled = Density(density.density * maxWidth.value / screenDp, density.fontScale)
        CompositionLocalProvider(LocalDensity provides scaled, LocalWidgetEnv provides null) {
            Column(Modifier.fillMaxSize().padding(top = 32.dp)) {
                Box(Modifier.weight(1f)) {
                    HomeGridPage(page, layout.pages.getOrNull(page) ?: HomeLayout.emptyPage(), ctx, drag,
                        PreviewCallbacks, null, blocked = layout.blockedCells(page), widgetLayer = { cellW, cellH ->
                            layout.widgetsOn(page).forEach { placement ->
                                val inst = widgets?.instances?.get(placement.id)
                                val rect = placement.rect
                                Box(Modifier.offset(cellW * rect.col, cellH * rect.row).size(cellW * rect.w, cellH * rect.h)
                                    .padding(horizontal = 8.dp, vertical = 6.dp)) {
                                    if (inst != null && inst.kind != WidgetKind.APP_WIDGET && inst.kind != WidgetKind.SMART_STACK) {
                                        WidgetContent(inst, Modifier.fillMaxSize(), WidgetSize.layoutFor(inst.kind, rect.w, rect.h))
                                    } else {
                                        GlassSurface(Modifier.fillMaxSize(), shape = WidgetShape, role = null,
                                            contentAlignment = Alignment.Center) {
                                            GlassText(inst?.kind?.title ?: "Widget", Modifier.padding(12.dp), style = GlassType.Caption)
                                        }
                                    }
                                }
                            }
                        })
                }
                Dock(layout.dock, ctx, drag, PreviewCallbacks, {}, Modifier)
            }
        }
        // Intercept preview taps without consuming movement needed by the parent list.
        Box(Modifier.matchParentSize().pointerInput(Unit) { detectTapGestures(onTap = {}) })
    }
}

private object PreviewCallbacks : HomeCallbacks {
    override fun launch(entry: AppEntry, bounds: android.graphics.Rect?) = Unit
    override fun openFolder(folder: FolderItem, bounds: Rect) = Unit
    override fun showMenu(target: MenuTarget) = Unit
    override fun startDrag(info: DragInfo, at: Offset) = Unit
    override fun drag(at: Offset) = Unit
    override fun endDrag() = Unit
    override fun cancelDrag() = Unit
    override fun remove(slot: Slot) = Unit
    override fun lockScreen() = Unit
}
