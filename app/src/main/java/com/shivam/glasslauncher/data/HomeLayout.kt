package com.shivam.glasslauncher.data

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
 */
data class HomeLayout(
    val pages: List<List<HomeItem?>>,
    val dock: List<HomeItem?>,
    val labels: Map<String, String>,
    val known: Set<String>,
) {
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
        root.put("version", 1)
        root.put("pages", JSONArray(pages.map { page -> JSONArray(page.map { itemJson(it) }) }))
        root.put("dock", JSONArray(dock.map { itemJson(it) }))
        root.put("labels", JSONObject(labels))
        root.put("known", JSONArray(known.toList()))
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
            return HomeLayout(pages, dock, labels, known)
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

        fun build() = HomeLayout(trimPages(pages), dock.toList(), labels, known)

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
                val i = pages[p].indexOf(null)
                if (i >= 0) { pages[p][i] = item; return }
            }
            pages += MutableList<HomeItem?>(PAGE_SIZE) { null }.also { it[0] = item }
        }

        /** Inserts at [index] on [page], shifting items right and overflowing onto later pages. */
        fun insertShifting(page: Int, index: Int, item: HomeItem) {
            while (pages.size <= page) pages += MutableList<HomeItem?>(PAGE_SIZE) { null }
            val row = pages[page]
            val empty = (index until PAGE_SIZE).firstOrNull { row[it] == null }
            if (empty != null) {
                for (k in empty downTo index + 1) row[k] = row[k - 1]
                row[index] = item
                return
            }
            val overflow = row[PAGE_SIZE - 1]!!
            for (k in PAGE_SIZE - 1 downTo index + 1) row[k] = row[k - 1]
            row[index] = item
            insertShifting(page + 1, 0, overflow)
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

    private fun trimPages(pages: List<List<HomeItem?>>): List<List<HomeItem?>> {
        // Drop empty pages except the first.
        val kept = pages.filterIndexed { i, p -> i == 0 || p.any { it != null } }
        return kept.map { it.toList() }
    }

    /**
     * Brings the layout in sync with [installed] app keys. Uninstalled apps are removed,
     * newly seen apps are appended to the first free cell, and [hidden] apps are removed.
     */
    fun reconcile(layout: HomeLayout, installed: List<String>, hidden: Set<String>): HomeLayout {
        val installedSet = installed.toSet()
        val w = Working(layout)
        fun keep(key: String) = key in installedSet && key !in hidden
        for (page in w.pages) for (i in page.indices) page[i] = filterItem(page[i], ::keep)
        for (i in w.dock.indices) w.dock[i] = filterItem(w.dock[i], ::keep)
        val placed = w.build().placedKeys()
        val newKeys = installed.filter { it !in layout.known && it !in placed && it !in hidden }
        newKeys.forEach { w.placeInFirstFree(AppItem(it)) }
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
}
