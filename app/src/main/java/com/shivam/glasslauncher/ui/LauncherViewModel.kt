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

    // --- Spotlight -------------------------------------------------------------------------

    val query = MutableStateFlow("")
    private val contactsPermissionTick = MutableStateFlow(0)

    val results: StateFlow<SpotlightResults> = combine(query, contactsPermissionTick) { q, _ -> q }
        .debounce { if (it.isEmpty()) 0L else 45L }
        .mapLatest { q ->
            kotlinx.coroutines.withContext(Dispatchers.Default) {
                g.search.search(q, visibleApps.value) { key -> g.usage.scoreBoost(key) }
            }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SpotlightResults())

    fun onContactsPermissionChanged() { contactsPermissionTick.value++ }

    // --- Layout sync -----------------------------------------------------------------------

    init {
        viewModelScope.launch {
            layout.filterNotNull().first()
            combine(apps.filter { it.isNotEmpty() }, hidden) { list, h -> list to h }.collect { (list, h) ->
                val keys = list.map { it.key }
                g.layout.update { current ->
                    if (current.known.isEmpty() && current.placedKeys().isEmpty()) {
                        LayoutOps.initial(keys, defaultDockKeys(list), h)
                    } else {
                        LayoutOps.reconcile(current, keys, h)
                    }
                }
            }
        }
    }

    private fun defaultDockKeys(list: List<AppEntry>): List<String> {
        val pm = getApplication<Application>().packageManager
        val intents = listOf(
            Intent(Intent.ACTION_DIAL),
            Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")),
            Intent(Intent.ACTION_VIEW, Uri.parse("https://example.com")).addCategory(Intent.CATEGORY_BROWSABLE),
            Intent(MediaStore.ACTION_IMAGE_CAPTURE),
        )
        return intents.mapNotNull { intent ->
            val pkg = pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
            if (pkg == null || pkg == "android") null else list.firstOrNull { it.packageName == pkg && !it.isWorkProfile }?.key
        }
    }

    // --- Actions ---------------------------------------------------------------------------

    fun label(entry: AppEntry): String = layout.value?.labels?.get(entry.key) ?: entry.label

    fun isLocked(entry: AppEntry) = settings.value?.locked?.contains(entry.key) == true

    /** Launches without the lock check. */
    fun launchNow(activity: Activity, entry: AppEntry, bounds: Rect?) {
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
