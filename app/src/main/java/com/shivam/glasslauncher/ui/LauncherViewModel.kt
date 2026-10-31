package com.shivam.glasslauncher.ui

import android.app.Activity
import android.app.Application
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Rect
import android.net.Uri
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.shivam.glasslauncher.GlassApp
import com.shivam.glasslauncher.badges.BadgeStore
import com.shivam.glasslauncher.data.AppCategory
import com.shivam.glasslauncher.data.AppEntry
import com.shivam.glasslauncher.data.DrawerApp
import com.shivam.glasslauncher.data.DrawerConfig
import com.shivam.glasslauncher.data.DrawerItem
import com.shivam.glasslauncher.data.DrawerOps
import com.shivam.glasslauncher.data.DrawerShortcut
import com.shivam.glasslauncher.data.DropTarget
import com.shivam.glasslauncher.data.HomeLayout
import com.shivam.glasslauncher.data.LauncherSettings
import com.shivam.glasslauncher.data.LayoutOps
import com.shivam.glasslauncher.data.Slot
import com.shivam.glasslauncher.gestures.GlassGestureService
import com.shivam.glasslauncher.search.SpotlightResults
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** A labelled group of apps in the App Library. */
data class LibraryGroup(val id: String, val title: String, val apps: List<AppEntry>)

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class LauncherViewModel(application: Application) : AndroidViewModel(application) {

    private val g = application as GlassApp

    val settings: StateFlow<LauncherSettings?> = g.settings.state
    val layout: StateFlow<HomeLayout?> = g.layout.layout
    val apps: StateFlow<List<AppEntry>> = g.apps.apps
    val badges: StateFlow<Map<String, Int>> = BadgeStore.counts
    val wallpaper = g.wallpaper.snapshot
    val iconGeneration = g.icons.generation
    val icons get() = g.icons
    val actions get() = g.actions

    val appsByKey: StateFlow<Map<String, AppEntry>> = apps.map { list -> list.associateBy { it.key } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    private val hidden = settings.filterNotNull().map { it.hidden }.distinctUntilChanged()

    /** Apps that are not hidden, alphabetical. */
    val visibleApps: StateFlow<List<AppEntry>> = combine(apps, hidden) { list, h -> list.filter { it.key !in h } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** "Siri Suggestions": the 4 most recently/frequently launched apps. */
    val suggestions: StateFlow<List<AppEntry>> = combine(visibleApps, g.usage.changes) { list, _ ->
        val byKey = list.associateBy { it.key }
        g.usage.ranked().mapNotNull { byKey[it] }.take(4)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val libraryGroups: StateFlow<List<LibraryGroup>> = combine(visibleApps, suggestions) { list, sugg ->
        val groups = ArrayList<LibraryGroup>()
        if (sugg.isNotEmpty()) groups += LibraryGroup("suggestions", "Suggestions", sugg)
        val recent = list.sortedByDescending { it.info.firstInstallTime }.take(8)
        if (recent.isNotEmpty()) groups += LibraryGroup("recent", "Recently Added", recent)
        AppCategory.entries.forEach { cat ->
            val inCat = list.filter { it.category == cat }
            if (inCat.isNotEmpty()) groups += LibraryGroup(cat.name, cat.title, inCat)
        }
        groups
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    // --- App drawer (Phase 5) ----------------------------------------------------------------

    /** Drawer pages: category id -> folders, shortcuts and apps, sorted. */
    val drawerPages: StateFlow<Map<String, List<DrawerItem>>> = combine(visibleApps, layout.filterNotNull(), g.usage.changes) { list, lay, _ ->
        Triple(list, lay, g.usage.ranked())
    }.mapLatest { (list, lay, ranked) ->
        kotlinx.coroutines.withContext(Dispatchers.Default) {
            val rank = ranked.withIndex().associate { it.value to it.index }
            val apps = list.map { DrawerApp(it.key, lay.labels[it.key] ?: it.label, it.drawerCategory, it.info.firstInstallTime) }
            DrawerOps.pages(lay.drawer, apps, rank)
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    fun updateDrawer(transform: (DrawerConfig) -> DrawerConfig) = g.layout.update { it.copy(drawer = transform(it.drawer)) }

    fun setDrawerLastCategory(id: String) = g.settings.setDrawerLastCategory(id)

    fun moveToCategory(entry: AppEntry, categoryId: String) =
        updateDrawer { DrawerOps.assign(it, entry.key, categoryId, entry.drawerCategory) }

    fun startDrawerShortcut(activity: Activity, s: DrawerShortcut, bounds: Rect?): Boolean {
        val user = appsByKey.value[s.appKey]?.user ?: android.os.Process.myUserHandle()
        return g.actions.startShortcut(s.packageName, s.shortcutId, user, bounds)
    }

    // --- Spotlight -------------------------------------------------------------------------

    val query = MutableStateFlow("")
    private val contactsPermissionTick = MutableStateFlow(0)

    // Declared before [results]: its eager collector may run during construction.
    @Volatile private var lastResults = SpotlightResults()

    /**
     * Two passes per query: apps, shortcuts, contacts and settings land at once; files and calendar
     * events (disk-backed) follow once typing pauses, and a newer query cancels the older one's queries.
     */
    val results: StateFlow<SpotlightResults> = combine(query, contactsPermissionTick) { q, _ -> q }
        .debounce { if (it.isEmpty()) 0L else 45L }
        .transformLatest { q ->
            val fast = kotlinx.coroutines.withContext(Dispatchers.Default) {
                g.search.search(q, visibleApps.value, settings.value?.locked.orEmpty()) { key -> g.usage.scoreBoost(key) }
            }
            val previous = lastResults
            emit(g.search.carryOver(previous, fast))
            if (fast.query.length < 2 || fast.calculation != null || fast.conversion != null) return@transformLatest
            kotlinx.coroutines.delay(110)
            val cancel = android.os.CancellationSignal()
            val job = currentCoroutineContext()[kotlinx.coroutines.Job]
            val handle = job?.invokeOnCompletion { cancel.cancel() }
            try {
                emit(kotlinx.coroutines.withContext(Dispatchers.IO) { g.search.searchStorage(fast, cancel) })
            } finally {
                handle?.dispose()
            }
        }
        .onEach { lastResults = it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SpotlightResults())

    val recentSearches: StateFlow<List<String>> = g.searchHistory.recent

    fun recordSearch(q: String) = g.searchHistory.record(q)
    fun forgetSearch(q: String) = g.searchHistory.remove(q)
    fun clearSearches() = g.searchHistory.clear()

    /** Re-reads app shortcuts as Spotlight opens, so new or changed shortcuts are searchable right away. */
    fun onSpotlightOpened() {
        viewModelScope.launch(Dispatchers.IO) { g.search.refreshShortcuts(visibleApps.value) }
    }

    fun onContactsPermissionChanged() { contactsPermissionTick.value++ }

    // --- Layout sync -----------------------------------------------------------------------

    init {
        viewModelScope.launch {
            layout.filterNotNull().first()
            combine(apps.filter { it.isNotEmpty() }, hidden) { list, h -> list to h }.collect { (list, h) ->
                val keys = list.map { it.key }
                g.layout.update { current ->
                    val synced = if (current.known.isEmpty() && current.placedKeys().isEmpty()) {
                        LayoutOps.initial(keys, defaultDockKeys(list), h)
                    } else {
                        LayoutOps.reconcile(current, keys, h, autoAdd = false)
                    }
                    // Once (fresh or old layouts): home keeps only SL6's apps, the rest live in the drawer.
                    if (synced.version < 2) LayoutOps.arrange(synced, defaultDockKeys(list), homePicks(list, h)) else synced
                }
            }
        }
    }

    /** SL6's dock: Phone, Browser, Gallery, Camera. */
    private fun defaultDockKeys(list: List<AppEntry>): List<String> {
        val pm = getApplication<Application>().packageManager
        fun keyOf(pkg: String?) = list.firstOrNull { it.packageName == pkg && !it.isWorkProfile }?.key
        fun resolve(intent: Intent) = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
            ?.takeIf { it != "android" }?.let(::keyOf)
        return listOfNotNull(
            resolve(Intent(Intent.ACTION_DIAL)),
            resolve(Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")).addCategory(Intent.CATEGORY_BROWSABLE)),
            GALLERIES.firstNotNullOfOrNull(::keyOf),
            resolve(Intent(MediaStore.ACTION_IMAGE_CAPTURE)),
        )
    }

    private fun homePicks(list: List<AppEntry>, hidden: Set<String>): List<List<String>> = SL6_PAGES.map { page ->
        page.mapNotNull { pkg -> list.firstOrNull { it.packageName == pkg && !it.isWorkProfile && it.key !in hidden }?.key }
    }

    private companion object {
        val GALLERIES = listOf("com.miui.gallery", "com.google.android.apps.photos")
        /** SL6's home pages on the owner's phone (observed 29 Oct 2026); missing apps are skipped. */
        val SL6_PAGES = listOf(
            listOf("com.instagram.android", "com.whatsapp", "com.google.android.gm", "com.google.android.youtube"),
            listOf(
                "com.application.zomato", "com.anthropic.claude", "com.flipkart.android", "com.linkedin.android",
                "in.gokiwi.kiwitpap", "net.one97.paytm", "com.netflix.mediaclient", "com.phonepe.app",
            ),
        )
    }

    // --- Actions ---------------------------------------------------------------------------

    fun label(entry: AppEntry): String = layout.value?.labels?.get(entry.key) ?: entry.label

    fun isLocked(entry: AppEntry) = settings.value?.locked?.contains(entry.key) == true

    /** Launches without the lock check. */
    fun launchNow(activity: Activity, entry: AppEntry, bounds: Rect?) {
        // Off before the app starts: payment apps check for accessibility services at launch.
        if (settings.value?.gestures?.pauseForApps?.contains(entry.packageName) == true) {
            com.shivam.glasslauncher.gestures.GlassGestureService.pauseForPaymentApp(activity)
        }
        if (g.actions.launch(activity, entry, bounds)) g.usage.recordLaunch(entry.key)
    }

    fun move(from: Slot, target: DropTarget) {
        g.layout.update { current ->
            val moving = current.itemAt(from)
            val targetItem = when (target) {
                is DropTarget.Cell -> current.pages.getOrNull(target.page)?.getOrNull(target.index)
                is DropTarget.DockCell -> current.dock.getOrNull(target.index)
            }
            LayoutOps.move(current, from, target, folderName(moving, targetItem))
        }
    }

    /** iOS-style folder naming: the shared category of the two apps, else "Folder". */
    private fun folderName(a: com.shivam.glasslauncher.data.HomeItem?, b: com.shivam.glasslauncher.data.HomeItem?): String {
        val byKey = appsByKey.value
        val ca = (a as? com.shivam.glasslauncher.data.AppItem)?.let { byKey[it.appKey]?.category }
        val cb = (b as? com.shivam.glasslauncher.data.AppItem)?.let { byKey[it.appKey]?.category }
        return if (ca != null && ca == cb && ca != AppCategory.OTHER) ca.title.substringBefore(" &") else "Folder"
    }

    fun removeFromFolder(slot: Slot.InFolder, key: String) =
        g.layout.update { LayoutOps.addToHome(LayoutOps.removeFromHome(it, slot), key) }

    fun removeFromHome(slot: Slot) = g.layout.update { LayoutOps.removeFromHome(it, slot) }
    fun addToHome(key: String) = g.layout.update { LayoutOps.addToHome(it, key) }
    fun renameFolder(id: String, name: String) = g.layout.update { LayoutOps.renameFolder(it, id, name) }
    fun setLabel(key: String, label: String?) = g.layout.update { LayoutOps.setLabel(it, key, label) }
    fun moveInFolder(id: String, from: Int, to: Int) = g.layout.update { LayoutOps.moveInFolder(it, id, from, to) }

    fun hide(key: String) {
        g.settings.setHidden(key, true)
        g.layout.update { LayoutOps.removeKey(it, key) }
    }

    fun setLocked(key: String, locked: Boolean) = g.settings.setLocked(key, locked)

    /** Locks the screen through the accessibility service; false if the service is off. */
    fun lockScreen(): Boolean = GlassGestureService.instance?.lockScreen() ?: false

    fun refreshWallpaper() = g.wallpaper.refresh()
}
