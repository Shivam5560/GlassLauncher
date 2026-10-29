package com.shivam.glasslauncher.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import com.shivam.glasslauncher.data.AppEntry
import com.shivam.glasslauncher.data.DropTarget
import com.shivam.glasslauncher.data.FolderItem
import com.shivam.glasslauncher.data.HomeItem
import com.shivam.glasslauncher.data.Slot
import com.shivam.glasslauncher.icons.IconRenderer
import com.shivam.glasslauncher.ui.glass.GlassToastState

/** An open folder popup: a home folder ([folderId]) or an App Library group ([libraryGroupId]). */
data class OpenFolder(val folderId: String?, val libraryGroupId: String?, val origin: Rect)

/** Long-press context menu target. */
data class MenuTarget(
    val entry: AppEntry?,
    val folder: FolderItem?,
    val slot: Slot?,
    val anchor: Rect,
    /** Opened from the app drawer: adds "Move to category". */
    val fromDrawer: Boolean = false,
)

data class LockPrompt(val entry: AppEntry, val bounds: android.graphics.Rect?)

/** Rename an app label ([appKey]) or a folder ([folderId]). */
data class RenameTarget(val appKey: String?, val folderId: String?, val current: String, val origin: Rect?)

data class DragInfo(
    val item: HomeItem,
    val from: Slot,
    val entry: AppEntry?,
    /** Finger position relative to the icon's top-left, in px. */
    val touchOffset: Offset,
    val iconSizePx: Float,
)

/** Drag and drop state plus the window-space geometry of every drop target. */
@Stable
class DragController {
    var info by mutableStateOf<DragInfo?>(null)
        private set
    var pointer by mutableStateOf(Offset.Zero)
    var hover by mutableStateOf<DropTarget?>(null)

    /** (page, index) -> icon bounds in window coordinates. */
    val pageCells = HashMap<Pair<Int, Int>, Rect>()
    val dockCells = HashMap<Int, Rect>()

    fun start(info: DragInfo, at: Offset) {
        this.info = info
        pointer = at
        hover = null
    }

    fun end(): Pair<DragInfo, DropTarget?>? {
        val i = info ?: return null
        val h = hover
        info = null
        hover = null
        return i to h
    }

    fun isDragging(slot: Slot) = info?.from == slot
}

@Stable
class LauncherUiState {
    var spotlight by mutableStateOf<Rect?>(null)
    var spotlightOpen by mutableStateOf(false)
    var folder by mutableStateOf<OpenFolder?>(null)
    var menu by mutableStateOf<MenuTarget?>(null)
    var editMode by mutableStateOf(false)
    var lockPrompt by mutableStateOf<LockPrompt?>(null)
    var rename by mutableStateOf<RenameTarget?>(null)
    val drag = DragController()
    val toast = GlassToastState()
    /** Phase 5 app drawer (swipe up from home). */
    val drawer = com.shivam.glasslauncher.ui.drawer.DrawerUiState()

    val overlayOpen: Boolean
        get() = spotlightOpen || folder != null || menu != null || lockPrompt != null || rename != null || drawer.open

    fun openSpotlight(origin: Rect?) {
        menu = null
        folder = null
        spotlight = origin
        spotlightOpen = true
    }

    /** Closes the top-most overlay; returns false if nothing was open. */
    fun closeTop(): Boolean = when {
        rename != null -> { rename = null; true }
        lockPrompt != null -> { lockPrompt = null; true }
        menu != null -> { menu = null; true }
        spotlightOpen -> { spotlightOpen = false; true }
        folder != null -> { folder = null; true }
        drawer.closeTop() -> true
        editMode -> { editMode = false; true }
        else -> false
    }

    fun closeAll() {
        rename = null
        lockPrompt = null
        menu = null
        spotlightOpen = false
        folder = null
        editMode = false
        drawer.closeAll()
    }
}

val LocalIconRenderer = staticCompositionLocalOf<IconRenderer> { error("No IconRenderer") }
val LocalIconGeneration = staticCompositionLocalOf { 0 }

/** Keeps the last non-null value so content can animate out after its state is cleared. */
@Composable
fun <T : Any> rememberLast(value: T?): T? {
    val holder = remember { mutableStateOf(value) }
    if (value != null) holder.value = value
    return holder.value
}
