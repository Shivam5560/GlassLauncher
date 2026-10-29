package com.shivam.glasslauncher.data

import com.shivam.glasslauncher.widgets.GridPlacement
import com.shivam.glasslauncher.widgets.GridRect
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

const val GRID_COLUMNS = 4
const val GRID_ROWS = 6
const val PAGE_SIZE = GRID_COLUMNS * GRID_ROWS
const val DOCK_SIZE = 4

sealed interface HomeItem {
    val id: String
}

data class AppItem(val appKey: String) : HomeItem {
    override val id: String get() = "app:$appKey"
}

data class FolderItem(override val id: String, val name: String, val apps: List<String>) : HomeItem

/**
 * A widget on a home page. It owns [rect] (cells) on [page]; icons never sit in those cells and
 * reflow around it. [id] refers to the widget instance in the widget store.
 */
data class HomeWidget(val id: String, val page: Int, val rect: GridRect)

/** Where an item lives on the home screen. */
sealed interface Slot {
    data class Page(val page: Int, val index: Int) : Slot
    data class Dock(val index: Int) : Slot
    data class InFolder(val folderId: String, val index: Int) : Slot
}

/**
 * The home screen layout: pages of a fixed 4x6 grid (null = empty cell), a 4-slot dock,
 * custom labels, and the set of app keys the launcher has already seen (so apps the user
 * removed from the home screen are not re-added, while newly installed apps are).
 * Phase 2 adds [widgets] placed freely on pages, [keptPages] (pages added in the page manager,
 * kept while empty) and the [defaultPage] the Home button returns to.
 */
data class HomeLayout(
    val pages: List<List<HomeItem?>>,
    val dock: List<HomeItem?>,
    val labels: Map<String, String>,
    val known: Set<String>,
    val widgets: List<HomeWidget> = emptyList(),
    val keptPages: Set<Int> = emptySet(),
    val defaultPage: Int = 0,
    /** Phase 5: app drawer categories, manual moves, folders, shortcuts and sorting. */
    val drawer: DrawerConfig = DrawerConfig(),
    /** 2 = home holds only picked apps ([LayoutOps.arrange]); 1 = old "every app on home" layouts. */
    val version: Int = 1,
) {
    fun widgetsOn(page: Int): List<HomeWidget> = widgets.filter { it.page == page }

    /** Row-major mask of the cells on [page] covered by widgets. */
    fun blockedCells(page: Int): BooleanArray =
        GridPlacement.blockedMask(widgetsOn(page).map { it.rect }, GRID_COLUMNS, GRID_ROWS)

    fun itemAt(slot: Slot): HomeItem? = when (slot) {
        is Slot.Page -> pages.getOrNull(slot.page)?.getOrNull(slot.index)
        is Slot.Dock -> dock.getOrNull(slot.index)
        is Slot.InFolder -> findFolder(slot.folderId)?.apps?.getOrNull(slot.index)?.let { AppItem(it) }
    }

    fun findFolder(id: String): FolderItem? =
        (pages.asSequence().flatten() + dock.asSequence()).filterIsInstance<FolderItem>().firstOrNull { it.id == id }

    fun placedKeys(): Set<String> = buildSet {
        (pages.flatten() + dock).forEach { item ->
            when (item) {
                is AppItem -> add(item.appKey)
                is FolderItem -> addAll(item.apps)
                null -> Unit
            }
        }
    }

    fun toJson(): String {
        fun itemJson(item: HomeItem?): Any = when (item) {
            null -> JSONObject.NULL
            is AppItem -> JSONObject().put("t", "app").put("k", item.appKey)
            is FolderItem -> JSONObject().put("t", "folder").put("id", item.id).put("n", item.name)
                .put("apps", JSONArray(item.apps))
        }
        val root = JSONObject()
        root.put("version", version)
        root.put("pages", JSONArray(pages.map { page -> JSONArray(page.map { itemJson(it) }) }))
        root.put("dock", JSONArray(dock.map { itemJson(it) }))
        root.put("labels", JSONObject(labels))
        root.put("known", JSONArray(known.toList()))
        root.put("widgets", JSONArray(widgets.map { w ->
            JSONObject().put("id", w.id).put("p", w.page).put("c", w.rect.col).put("r", w.rect.row).put("w", w.rect.w).put("h", w.rect.h)
        }))
        root.put("keptPages", JSONArray(keptPages.toList()))
        root.put("defaultPage", defaultPage)
        root.put("drawer", drawer.toJson())
        return root.toString()
    }

    companion object {
        val EMPTY = HomeLayout(listOf(emptyPage()), List(DOCK_SIZE) { null }, emptyMap(), emptySet())

        fun emptyPage(): List<HomeItem?> = List(PAGE_SIZE) { null }

        fun fromJson(text: String): HomeLayout {
            val root = JSONObject(text)
            fun parseItem(o: Any?): HomeItem? {
                if (o !is JSONObject) return null
                return when (o.optString("t")) {
                    "app" -> AppItem(o.getString("k"))
                    "folder" -> {
                        val arr = o.getJSONArray("apps")
                        FolderItem(o.getString("id"), o.optString("n", "Folder"), List(arr.length()) { arr.getString(it) })
                    }
                    else -> null
                }
            }
            val pagesArr = root.getJSONArray("pages")
            val pages = List(pagesArr.length()) { p ->
                val arr = pagesArr.getJSONArray(p)
                List(PAGE_SIZE) { i -> if (i < arr.length()) parseItem(arr.opt(i)) else null }
            }.ifEmpty { listOf(emptyPage()) }
            val dockArr = root.optJSONArray("dock") ?: JSONArray()
            val dock = List(DOCK_SIZE) { i -> if (i < dockArr.length()) parseItem(dockArr.opt(i)) else null }
            val labelsObj = root.optJSONObject("labels") ?: JSONObject()
            val labels = labelsObj.keys().asSequence().associateWith { labelsObj.getString(it) }
            val knownArr = root.optJSONArray("known") ?: JSONArray()
            val known = List(knownArr.length()) { knownArr.getString(it) }.toSet()
            val widgetsArr = root.optJSONArray("widgets") ?: JSONArray()
            val widgets = ArrayList<HomeWidget>()
            for (i in 0 until widgetsArr.length()) {
                val o = widgetsArr.optJSONObject(i) ?: continue
                val page = o.optInt("p", 0)
                if (page !in pages.indices) continue
                val rect = GridPlacement.clamp(GridRect(o.optInt("c"), o.optInt("r"), o.optInt("w", 2), o.optInt("h", 2)), GRID_COLUMNS, GRID_ROWS)
                // Drop corrupt overlaps instead of rendering widgets on top of each other.
                if (widgets.any { it.page == page && it.rect.overlaps(rect) }) continue
                widgets += HomeWidget(o.getString("id"), page, rect)
            }
            val keptArr = root.optJSONArray("keptPages") ?: JSONArray()
            val kept = List(keptArr.length()) { keptArr.getInt(it) }.filter { it in pages.indices }.toSet()
            val defaultPage = root.optInt("defaultPage", 0).coerceIn(0, pages.size - 1)
            val drawer = root.optJSONObject("drawer")?.let { DrawerConfig.fromJson(it) } ?: DrawerConfig()
            return HomeLayout(pages, dock, labels, known, widgets, kept, defaultPage, drawer, root.optInt("version", 1))
        }
    }
}

/** Describes where a dragged item was released. */
sealed interface DropTarget {
    /** Drop into a home cell. [merge] = released on top of the icon there (folder creation). */
    data class Cell(val page: Int, val index: Int, val merge: Boolean) : DropTarget
    data class DockCell(val index: Int, val merge: Boolean) : DropTarget
}

/** Pure layout transformations, kept free of Android types so they are unit-testable. */
object LayoutOps {

    private class Working(layout: HomeLayout) {
        val pages: MutableList<MutableList<HomeItem?>> = layout.pages.map { it.toMutableList() }.toMutableList()
        val dock: MutableList<HomeItem?> = layout.dock.toMutableList()
        var labels = layout.labels
        var known = layout.known
        val widgets: MutableList<HomeWidget> = layout.widgets.toMutableList()
        val kept: MutableSet<Int> = layout.keptPages.toMutableSet()
        var defaultPage = layout.defaultPage
        val drawer = layout.drawer
        var version = layout.version

        fun blocked(page: Int, index: Int): Boolean =
            widgets.any { it.page == page && it.rect.contains(index % GRID_COLUMNS, index / GRID_COLUMNS) }

        /** Cell indices of [page] not covered by a widget, in reading order. */
        fun openCells(page: Int): List<Int> = (0 until PAGE_SIZE).filter { !blocked(page, it) }

        fun newPage() { pages += MutableList<HomeItem?>(PAGE_SIZE) { null } }

        fun build(): HomeLayout {
            reflowAll()
            // Drop empty pages, except the first, the default page, pages holding widgets and
            // pages the user added in the page manager. Indices of widgets etc. follow.
            val keep = pages.indices.filter { i ->
                i == 0 || i == defaultPage || i in kept || pages[i].any { it != null } || widgets.any { it.page == i }
            }
            val remap = keep.withIndex().associate { (newIndex, old) -> old to newIndex }
            return HomeLayout(
                pages = keep.map { pages[it].toList() },
                dock = dock.toList(),
                labels = labels,
                known = known,
                widgets = widgets.mapNotNull { w -> remap[w.page]?.let { w.copy(page = it) } },
                keptPages = kept.mapNotNull { remap[it] }.toSet(),
                defaultPage = remap[defaultPage] ?: 0,
                drawer = drawer,
                version = version,
            )
        }

        /** Moves icons out of cells that a widget now covers, shifting them forward (reflow). */
        fun reflowAll() {
            var p = 0
            while (p < pages.size) {
                val row = pages[p]
                val displaced = (0 until PAGE_SIZE).filter { row[it] != null && blocked(p, it) }.map { it to row[it]!! }
                displaced.forEach { (i, _) -> row[i] = null }
                // Last first, so each earlier icon is inserted in front of the later ones: order is kept.
                for ((i, item) in displaced.asReversed()) insertShifting(p, i, item)
                p++
            }
        }

        fun replaceFolder(id: String, transform: (FolderItem) -> HomeItem?) {
            for (page in pages) for (i in page.indices) {
                val it = page[i]
                if (it is FolderItem && it.id == id) page[i] = transform(it)
            }
            for (i in dock.indices) {
                val it = dock[i]
                if (it is FolderItem && it.id == id) dock[i] = transform(it)
            }
        }

        fun placeInFirstFree(item: HomeItem, startPage: Int = 0) {
            for (p in startPage until pages.size) {
                val i = openCells(p).firstOrNull { pages[p][it] == null }
                if (i != null) { pages[p][i] = item; return }
            }
            newPage()
            pages[pages.size - 1][0] = item
        }

        /**
         * Inserts at [index] on [page] (or the next cell a widget does not cover), shifting items
         * right through the open cells and overflowing onto later pages.
         */
        fun insertShifting(page: Int, index: Int, item: HomeItem) {
            var p = page
            var from = index
            var carry = item
            while (true) {
                while (pages.size <= p) newPage()
                val row = pages[p]
                val open = openCells(p).filter { it >= from }
                if (open.isEmpty()) { p++; from = 0; continue }
                val emptyAt = open.indexOfFirst { row[it] == null }
                if (emptyAt >= 0) {
                    for (k in emptyAt downTo 1) row[open[k]] = row[open[k - 1]]
                    row[open[0]] = carry
                    return
                }
                val overflow = row[open.last()]!!
                for (k in open.size - 1 downTo 1) row[open[k]] = row[open[k - 1]]
                row[open[0]] = carry
                carry = overflow
                p++
                from = 0
            }
        }

        fun removeAt(slot: Slot): HomeItem? = when (slot) {
            is Slot.Page -> pages.getOrNull(slot.page)?.let { row -> row[slot.index].also { row[slot.index] = null } }
            is Slot.Dock -> dock[slot.index].also { dock[slot.index] = null }
            is Slot.InFolder -> {
                var removed: HomeItem? = null
                replaceFolder(slot.folderId) { f ->
                    val key = f.apps.getOrNull(slot.index) ?: return@replaceFolder f
                    removed = AppItem(key)
                    collapse(f.copy(apps = f.apps.toMutableList().also { it.removeAt(slot.index) }))
                }
                removed
            }
        }
    }

    /** A folder with one app becomes that app; an empty folder disappears. */
    private fun collapse(f: FolderItem): HomeItem? = when (f.apps.size) {
        0 -> null
        1 -> AppItem(f.apps[0])
        else -> f
    }

    /**
     * Brings the layout in sync with [installed] app keys. Uninstalled apps are removed,
     * newly seen apps are appended to the first free cell, and [hidden] apps are removed.
     */
    fun reconcile(layout: HomeLayout, installed: List<String>, hidden: Set<String>, autoAdd: Boolean = true): HomeLayout {
        val installedSet = installed.toSet()
        val w = Working(layout)
        fun keep(key: String) = key in installedSet && key !in hidden
        for (page in w.pages) for (i in page.indices) page[i] = filterItem(page[i], ::keep)
        for (i in w.dock.indices) w.dock[i] = filterItem(w.dock[i], ::keep)
        val placed = w.build().placedKeys()
        val newKeys = installed.filter { it !in layout.known && it !in placed && it !in hidden }
        if (autoAdd) newKeys.forEach { w.placeInFirstFree(AppItem(it)) }
        w.known = installedSet
        w.labels = layout.labels.filterKeys { it in installedSet }
        return w.build()
    }

    private fun filterItem(item: HomeItem?, keep: (String) -> Boolean): HomeItem? = when (item) {
        null -> null
        is AppItem -> if (keep(item.appKey)) item else null
        is FolderItem -> collapse(item.copy(apps = item.apps.filter(keep)))
    }

    /** Fresh layout: [dockKeys] go in the dock, everything else fills pages in order. */
    fun initial(installed: List<String>, dockKeys: List<String>, hidden: Set<String>): HomeLayout {
        val dock = MutableList<HomeItem?>(DOCK_SIZE) { null }
        dockKeys.filter { it in installed && it !in hidden }.distinct().take(DOCK_SIZE).forEachIndexed { i, k -> dock[i] = AppItem(k) }
        val inDock = dock.filterIsInstance<AppItem>().map { it.appKey }.toSet()
        val w = Working(HomeLayout(listOf(HomeLayout.emptyPage()), dock, emptyMap(), emptySet()))
        installed.filter { it !in inDock && it !in hidden }.forEach { w.placeInFirstFree(AppItem(it)) }
        w.known = installed.toSet()
        return w.build()
    }

    /**
     * Home shows only [pageKeys] (one list per page, in order) and [dockKeys]; every other app
     * lives in the drawer. Widgets, folders and labels stay.
     */
    fun arrange(layout: HomeLayout, dockKeys: List<String>, pageKeys: List<List<String>>): HomeLayout {
        val w = Working(layout)
        for (page in w.pages) for (i in page.indices) if (page[i] is AppItem) page[i] = null
        if (dockKeys.isNotEmpty()) for (i in w.dock.indices) w.dock[i] = dockKeys.getOrNull(i)?.let { AppItem(it) }
        pageKeys.forEachIndexed { p, keys ->
            while (w.pages.size <= p) w.newPage()
            keys.forEach { w.placeInFirstFree(AppItem(it), p) }
        }
        w.version = 2
        return w.build()
    }

    /** Moves the item at [from] to [target]; merging creates or extends folders. */
    fun move(layout: HomeLayout, from: Slot, target: DropTarget, folderName: String = "Folder"): HomeLayout {
        val moving = layout.itemAt(from) ?: return layout
        // Dropping onto itself is a no-op.
        if (target is DropTarget.Cell && from == Slot.Page(target.page, target.index)) return layout
        if (target is DropTarget.DockCell && from == Slot.Dock(target.index)) return layout

        val w = Working(layout)
        val targetItem = when (target) {
            is DropTarget.Cell -> layout.pages.getOrNull(target.page)?.getOrNull(target.index)
            is DropTarget.DockCell -> layout.dock[target.index]
        }
        val merge = when (target) {
            is DropTarget.Cell -> target.merge
            is DropTarget.DockCell -> target.merge
        }

        if (merge && targetItem != null && moving is AppItem) {
            w.removeAt(from)
            // The source removal can collapse a folder and shift nothing else; re-read the target.
            val current = when (target) {
                is DropTarget.Cell -> w.pages.getOrNull(target.page)?.getOrNull(target.index)
                is DropTarget.DockCell -> w.dock[target.index]
            }
            val merged: HomeItem = when (current) {
                is FolderItem -> if (moving.appKey in current.apps) current else current.copy(apps = current.apps + moving.appKey)
                is AppItem -> if (current.appKey == moving.appKey) current else
                    FolderItem("folder-" + UUID.randomUUID().toString().take(8), folderName, listOf(current.appKey, moving.appKey))
                null -> moving
            }
            when (target) {
                is DropTarget.Cell -> w.pages[target.page][target.index] = merged
                is DropTarget.DockCell -> w.dock[target.index] = merged
            }
            return w.build()
        }

        when (target) {
            is DropTarget.Cell -> {
                w.removeAt(from)
                while (w.pages.size <= target.page) w.pages += MutableList<HomeItem?>(PAGE_SIZE) { null }
                if (w.pages[target.page][target.index] == null) {
                    w.pages[target.page][target.index] = moving
                } else {
                    w.insertShifting(target.page, target.index, moving)
                }
            }
            is DropTarget.DockCell -> {
                val displaced = w.dock[target.index]
                w.removeAt(from)
                if (displaced == null) {
                    w.dock[target.index] = moving
                } else {
                    val free = w.dock.indexOf(null)
                    if (free >= 0 && from !is Slot.Dock) {
                        // shift dock items to make room at the target index
                        val list = w.dock.toMutableList()
                        list.removeAt(free)
                        list.add(target.index, moving)
                        for (i in 0 until DOCK_SIZE) w.dock[i] = list[i]
                    } else {
                        w.dock[target.index] = moving
                        // swap the displaced item back to where the dragged item came from
                        when (from) {
                            is Slot.Page -> w.pages[from.page][from.index] = displaced
                            is Slot.Dock -> w.dock[from.index] = displaced
                            is Slot.InFolder -> w.placeInFirstFree(displaced)
                        }
                    }
                }
            }
        }
        return w.build()
    }

    fun removeFromHome(layout: HomeLayout, slot: Slot): HomeLayout {
        val w = Working(layout)
        w.removeAt(slot)
        return w.build()
    }

    fun removeKey(layout: HomeLayout, key: String): HomeLayout {
        val w = Working(layout)
        for (page in w.pages) for (i in page.indices) page[i] = filterItem(page[i]) { it != key }
        for (i in w.dock.indices) w.dock[i] = filterItem(w.dock[i]) { it != key }
        return w.build()
    }

    fun addToHome(layout: HomeLayout, key: String): HomeLayout {
        if (key in layout.placedKeys()) return layout
        val w = Working(layout)
        w.placeInFirstFree(AppItem(key))
        w.known = layout.known + key
        return w.build()
    }

    fun renameFolder(layout: HomeLayout, folderId: String, name: String): HomeLayout {
        val w = Working(layout)
        w.replaceFolder(folderId) { it.copy(name = name.ifBlank { it.name }) }
        return w.build()
    }

    fun setLabel(layout: HomeLayout, key: String, label: String?): HomeLayout =
        layout.copy(labels = if (label.isNullOrBlank()) layout.labels - key else layout.labels + (key to label.trim()))

    /** Reorders apps within a folder. */
    fun moveInFolder(layout: HomeLayout, folderId: String, from: Int, to: Int): HomeLayout {
        val w = Working(layout)
        w.replaceFolder(folderId) { f ->
            val list = f.apps.toMutableList()
            if (from !in list.indices) return@replaceFolder f
            val k = list.removeAt(from)
            list.add(to.coerceIn(0, list.size), k)
            f.copy(apps = list)
        }
        return w.build()
    }

    // --- Phase 2: widgets on the free-placement grid and the page manager ------------------

    /**
     * Places widget [id] with a [w] x [h] span on [page], at [at] if given and free of other
     * widgets, else at the free spot that displaces the fewest icons; if the page is full of
     * widgets it goes to the next page with room (or a new page). Icons reflow around it.
     */
    fun addWidget(layout: HomeLayout, id: String, page: Int, w: Int, h: Int, at: GridRect? = null): HomeLayout {
        val wk = Working(layout)
        val span = GridPlacement.clamp(GridRect(0, 0, w, h), GRID_COLUMNS, GRID_ROWS)
        var p = page.coerceAtLeast(0)
        while (true) {
            while (wk.pages.size <= p) wk.newPage()
            val others = wk.widgets.filter { it.page == p }.map { it.rect }
            val wanted = at?.let { GridPlacement.clamp(it.copy(w = span.w, h = span.h), GRID_COLUMNS, GRID_ROWS) }
            val occupied = BooleanArray(PAGE_SIZE) { wk.pages[p][it] != null }
            val spot = if (wanted != null && p == page && !GridPlacement.collides(wanted, others)) wanted
            else GridPlacement.firstFree(span.w, span.h, others, GRID_COLUMNS, GRID_ROWS, occupied)
            if (spot != null) {
                wk.widgets.removeAll { it.id == id }
                wk.widgets += HomeWidget(id, p, spot)
                return wk.build()
            }
            p++
        }
    }

    /** Moves a widget to [rect] on [page] (nearest free spot if it would overlap another widget). */
    fun moveWidget(layout: HomeLayout, id: String, page: Int, rect: GridRect): HomeLayout {
        val current = layout.widgets.firstOrNull { it.id == id } ?: return layout
        if (page !in layout.pages.indices) return layout
        val others = layout.widgets.filter { it.page == page && it.id != id }.map { it.rect }
        val target = GridPlacement.nearestFree(rect, others, GRID_COLUMNS, GRID_ROWS) ?: return layout
        if (current.page == page && current.rect == target) return layout
        val wk = Working(layout)
        wk.widgets.replaceAll { if (it.id == id) HomeWidget(id, page, target) else it }
        return wk.build()
    }

    /** Resizes a widget in place; refused (unchanged) if the new span would overlap another widget. */
    fun resizeWidget(layout: HomeLayout, id: String, w: Int, h: Int): HomeLayout {
        val current = layout.widgets.firstOrNull { it.id == id } ?: return layout
        val rect = GridPlacement.clamp(current.rect.copy(w = w, h = h), GRID_COLUMNS, GRID_ROWS)
        val others = layout.widgets.filter { it.page == current.page && it.id != id }.map { it.rect }
        if (GridPlacement.collides(rect, others) || rect == current.rect) return layout
        val wk = Working(layout)
        wk.widgets.replaceAll { if (it.id == id) it.copy(rect = rect) else it }
        return wk.build()
    }

    fun removeWidget(layout: HomeLayout, id: String): HomeLayout {
        if (layout.widgets.none { it.id == id }) return layout
        val wk = Working(layout)
        wk.widgets.removeAll { it.id == id }
        return wk.build()
    }

    /** Appends an empty page that is kept while empty. */
    fun addPage(layout: HomeLayout): HomeLayout {
        val wk = Working(layout)
        wk.newPage()
        wk.kept += wk.pages.size - 1
        return wk.build()
    }

    /**
     * Removes a page. Its icons leave the Home Screen but stay in the App Library (they remain
     * known, so they are not re-added); its widgets are removed. The last page cannot be removed.
     */
    fun removePage(layout: HomeLayout, page: Int): HomeLayout {
        if (layout.pages.size <= 1 || page !in layout.pages.indices) return layout
        val wk = Working(layout)
        wk.pages.removeAt(page)
        wk.widgets.removeAll { it.page == page }
        wk.widgets.replaceAll { if (it.page > page) it.copy(page = it.page - 1) else it }
        val kept = wk.kept.filter { it != page }.map { if (it > page) it - 1 else it }
        wk.kept.clear()
        wk.kept += kept
        wk.defaultPage = when {
            wk.defaultPage == page -> 0
            wk.defaultPage > page -> wk.defaultPage - 1
            else -> wk.defaultPage
        }
        // Removing page 0 makes the next page the first one, which is always kept.
        return wk.build()
    }

    /** Moves page [from] to position [to], carrying its widgets, kept flag and default flag. */
    fun movePage(layout: HomeLayout, from: Int, to: Int): HomeLayout {
        val n = layout.pages.size
        if (from !in 0 until n || to !in 0 until n || from == to) return layout
        val order = (0 until n).toMutableList()
        order.add(to, order.removeAt(from))
        val newIndexOf = order.withIndex().associate { (newIndex, old) -> old to newIndex }
        val wk = Working(layout)
        val pages = order.map { wk.pages[it] }
        wk.pages.clear()
        wk.pages += pages
        wk.widgets.replaceAll { it.copy(page = newIndexOf.getValue(it.page)) }
        val kept = wk.kept.map { newIndexOf.getValue(it) }
        wk.kept.clear()
        wk.kept += kept
        wk.defaultPage = newIndexOf.getValue(wk.defaultPage)
        // Keep the old first page, so moving pages never deletes an empty first page silently.
        if (layout.pages[0].all { it == null } && layout.widgetsOn(0).isEmpty()) wk.kept += newIndexOf.getValue(0)
        return wk.build()
    }

    fun setDefaultPage(layout: HomeLayout, page: Int): HomeLayout =
        if (page !in layout.pages.indices) layout else layout.copy(defaultPage = page)
}
