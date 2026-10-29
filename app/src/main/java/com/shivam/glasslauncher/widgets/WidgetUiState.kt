package com.shivam.glasslauncher.widgets

import android.app.Activity
import android.appwidget.AppWidgetProviderInfo
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Rect
import com.shivam.glasslauncher.GlassApp
import com.shivam.glasslauncher.data.HomeLayout
import com.shivam.glasslauncher.data.LayoutOps
import com.shivam.glasslauncher.ui.glass.GlassToastState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/** Where the widget picker adds to. */
sealed interface PickerTarget {
    data class Home(val page: Int) : PickerTarget
    data object Today : PickerTarget
}

/** Long-press menu on a widget, anchored at its bounds. */
data class WidgetMenu(val id: String, val anchor: Rect, val onHome: Boolean, val page: Int = 0)

/**
 * Widget-related launcher state: picker, long-press menu, edit sheet and page manager, plus the
 * actions that change the home layout or the widget store. Created by the launcher screen.
 */
@Stable
class WidgetUiState(
    private val activity: Activity,
    private val scope: CoroutineScope,
    val toast: GlassToastState,
) {
    val repo = WidgetRepository.get(activity)
    private val layoutRepo = GlassApp.from(activity).layout
    private val host = GlassWidgetHost.get(activity)

    var picker by mutableStateOf<PickerTarget?>(null)
    var menu by mutableStateOf<WidgetMenu?>(null)
    /** Instance whose edit sheet is open. */
    var editing by mutableStateOf<String?>(null)
    var pageManager by mutableStateOf(false)
    /** A third-party widget is being bound/configured. */
    private var bindJob: kotlinx.coroutines.Job? = null

    /** Home grid cell size in dp, reported by the grid (used for third-party default spans). */
    var cellWidthDp = 84f
    var cellHeightDp = 104f

    val open: Boolean get() = picker != null || menu != null || editing != null || pageManager

    fun closeTop(): Boolean = when {
        editing != null -> { editing = null; true }
        menu != null -> { menu = null; true }
        picker != null -> { picker = null; true }
        pageManager -> { pageManager = false; true }
        else -> false
    }

    fun closeAll() {
        editing = null
        menu = null
        picker = null
        pageManager = false
    }

    fun isOnHome(layout: HomeLayout?, id: String) = layout?.widgets?.any { it.id == id } == true

    // --- Adding ----------------------------------------------------------------------------

    /**
     * Phase 5 default home (once): a large clock/date/weather/next-event header across the top of
     * the first page, then rounded photo and calendar widgets; icons reflow below them.
     */
    fun seedHomeIfNeeded(layout: HomeLayout) {
        val s = repo.state.value ?: return
        if (s.homeSeeded) return
        repo.markHomeSeeded()
        if (layout.widgetsOn(0).isNotEmpty()) return
        val glance = repo.create(WidgetKind.GLANCE, WidgetSize.MEDIUM)
        val photos = repo.create(WidgetKind.PHOTOS, WidgetSize.SMALL)
        val calendar = repo.create(WidgetKind.CALENDAR, WidgetSize.SMALL)
        layoutRepo.update { l ->
            var next = LayoutOps.addWidget(l, glance.id, 0, 4, 2, GridRect(0, 0, 4, 2))
            next = LayoutOps.addWidget(next, photos.id, 0, 2, 2, GridRect(0, 2, 2, 2))
            LayoutOps.addWidget(next, calendar.id, 0, 2, 2, GridRect(2, 2, 2, 2))
        }
    }

    fun addOurs(kind: WidgetKind, size: WidgetSize, target: PickerTarget) {
        val inst = repo.create(kind, size, defaultConfig(kind))
        when (target) {
            is PickerTarget.Home -> layoutRepo.update { LayoutOps.addWidget(it, inst.id, target.page, size.w, size.h) }
            PickerTarget.Today -> repo.addToToday(inst.id)
        }
        picker = null
        toast.show("${kind.title} widget added")
    }

    private fun defaultConfig(kind: WidgetKind): Map<String, String> = when (kind) {
        WidgetKind.CLOCK -> mapOf(WidgetKeys.CLOCK_STYLE to ClockStyles.DIGITAL)
        else -> emptyMap()
    }

    /**
     * Third-party widget: allocate an id, let [WidgetBindActivity] show the bind dialog and the
     * configure activity if needed, then place it. Cancelling anywhere frees the id.
     */
    fun addAppWidget(entry: ProviderEntry, target: PickerTarget) {
        // A previous add that was interrupted (Home, clear-all, process death) never reported back; drop it.
        bindJob?.cancel()
        picker = null
        bindJob = scope.launch {
            val id = host.allocateId()
            val result = WidgetBinding.register(WidgetBinding.Request(id, WidgetBinding.Mode.BIND_AND_CONFIGURE))
            try {
                activity.startActivity(WidgetBindActivity.intent(activity, id, entry.info))
            } catch (e: Exception) {
                WidgetBinding.finish(id, false)
            }
            val ok = try {
                result.await()
            } catch (e: kotlinx.coroutines.CancellationException) {
                WidgetBinding.finish(id, false)
                host.deleteId(id)
                throw e
            }
            if (!ok) {
                host.deleteId(id)
                toast.show("Widget not added")
                return@launch
            }
            val info: AppWidgetProviderInfo = host.info(id) ?: entry.info
            val density = activity.resources.displayMetrics.density
            val (w, h) = GlassWidgetHost.defaultSpan(info, cellWidthDp, cellHeightDp, density)
            val inst = repo.create(
                WidgetKind.APP_WIDGET,
                WidgetSize.forSpan(w, h) ?: WidgetSize.SMALL,
                mapOf(
                    WidgetKeys.APP_WIDGET_ID to id.toString(),
                    WidgetKeys.APP_WIDGET_PROVIDER to GlassWidgetHost.providerKey(info.provider),
                    WidgetKeys.TODAY_ROWS to h.toString(),
                ),
            )
            when (target) {
                is PickerTarget.Home -> layoutRepo.update { LayoutOps.addWidget(it, inst.id, target.page, w, h) }
                PickerTarget.Today -> repo.addToToday(inst.id)
            }
            toast.show("${entry.label} added")
        }
    }

    /** Re-opens a third-party widget's configure activity (when it declares it reconfigurable). */
    fun reconfigure(inst: WidgetInstance) {
        val id = inst.appWidgetId
        if (id < 0) return
        WidgetBinding.register(WidgetBinding.Request(id, WidgetBinding.Mode.CONFIGURE))
        try {
            activity.startActivity(WidgetBindActivity.intent(activity, id, null))
        } catch (e: Exception) {
            WidgetBinding.finish(id, false)
        }
    }

    // --- Home grid -------------------------------------------------------------------------

    fun moveWidget(id: String, page: Int, rect: GridRect) = layoutRepo.update { LayoutOps.moveWidget(it, id, page, rect) }

    /** Resizes on the home grid; our widgets also switch to the matching iOS size. */
    fun resizeWidget(id: String, w: Int, h: Int) {
        val current = layoutRepo.layout.value
        val placed = current?.widgets?.firstOrNull { it.id == id }
        if (placed != null && current != null) {
            val next = LayoutOps.resizeWidget(current, id, w, h)
            if (next.widgets.firstOrNull { it.id == id }?.rect == placed.rect && (placed.rect.w != w || placed.rect.h != h)) {
                toast.show("Not enough room here — move other widgets first")
                return
            }
        }
        layoutRepo.update { LayoutOps.resizeWidget(it, id, w, h) }
        val inst = repo.instance(id) ?: return
        if (inst.kind == WidgetKind.APP_WIDGET) {
            repo.setConfig(id, WidgetKeys.TODAY_ROWS, h.toString())
        } else {
            WidgetSize.forSpan(w, h)?.let { repo.setSize(id, it) }
        }
    }

    /** Changes the size of one of our widgets wherever it lives. */
    fun setSize(id: String, size: WidgetSize) {
        if (isOnHome(layoutRepo.layout.value, id)) resizeWidget(id, size.w, size.h) else repo.setSize(id, size)
    }

    fun remove(id: String) {
        layoutRepo.update { LayoutOps.removeWidget(it, id) }
        repo.delete(id)
        if (editing == id) editing = null
        if (menu?.id == id) menu = null
    }

    // --- Pages -----------------------------------------------------------------------------

    fun addPage() = layoutRepo.update { LayoutOps.addPage(it) }

    fun removePage(page: Int) {
        val layout = layoutRepo.layout.value ?: return
        val widgetIds = layout.widgetsOn(page).map { it.id }
        layoutRepo.update { LayoutOps.removePage(it, page) }
        widgetIds.forEach { repo.delete(it) }
    }

    fun movePage(from: Int, to: Int) = layoutRepo.update { LayoutOps.movePage(it, from, to) }
    fun setDefaultPage(page: Int) = layoutRepo.update { LayoutOps.setDefaultPage(it, page) }
}
