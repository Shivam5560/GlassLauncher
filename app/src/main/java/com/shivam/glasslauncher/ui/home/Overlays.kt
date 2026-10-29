@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.shivam.glasslauncher.ui.home

import com.shivam.glasslauncher.ui.glass.glassMenuPosition

import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.WindowInsets
import android.app.Activity
import android.content.pm.ShortcutInfo
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.shivam.glasslauncher.data.AppEntry
import com.shivam.glasslauncher.data.FolderItem
import com.shivam.glasslauncher.data.HomeLayout
import com.shivam.glasslauncher.data.Slot
import com.shivam.glasslauncher.ui.LauncherViewModel
import com.shivam.glasslauncher.ui.LibraryGroup
import com.shivam.glasslauncher.ui.glass.GlassAlert
import com.shivam.glasslauncher.ui.glass.GlassAlertAction
import com.shivam.glasslauncher.ui.glass.GlassButton
import com.shivam.glasslauncher.ui.glass.GlassColors
import com.shivam.glasslauncher.ui.glass.GlassIcon
import com.shivam.glasslauncher.ui.glass.GlassIcons
import com.shivam.glasslauncher.ui.glass.GlassMorph
import com.shivam.glasslauncher.ui.glass.GlassScrim
import com.shivam.glasslauncher.ui.glass.GlassShape
import com.shivam.glasslauncher.ui.glass.GlassSurface
import com.shivam.glasslauncher.ui.glass.GlassText
import com.shivam.glasslauncher.ui.glass.GlassTextField
import com.shivam.glasslauncher.ui.glass.GlassType
import com.shivam.glasslauncher.ui.glass.GlassVariant
import com.shivam.glasslauncher.ui.glass.LocalGlassContentColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

private fun Rect.toAndroid() = android.graphics.Rect(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())

/** Folder popup: a Regular glass panel morphing out of the folder icon (or library tile). */
@Composable
fun FolderOverlay(
    ui: LauncherUiState,
    layout: HomeLayout,
    libraryGroups: List<LibraryGroup>,
    ctx: HomeContext,
    callbacks: HomeCallbacks,
) {
    val current = ui.folder
    val last = rememberLast(current) ?: return
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val homeFolder: FolderItem? = last.folderId?.let { layout.findFolder(it) }
    val group = last.libraryGroupId?.let { id -> libraryGroups.firstOrNull { it.id == id } }
    val title = homeFolder?.name ?: group?.title ?: ""
    val entries: List<AppEntry> = homeFolder?.apps?.mapNotNull { ctx.apps[it] } ?: group?.apps ?: emptyList()
    val draggingOut = ui.drag.info?.from is Slot.InFolder
    var progress by remember { mutableFloatStateOf(0f) }

    // A home folder that lost its last app collapses: close the popup.
    LaunchedEffect(current, homeFolder) {
        if (current?.folderId != null && homeFolder == null) ui.folder = null
    }
    if (current != null && !draggingOut) BackHandler { ui.folder = null }

    val immersive = ctx.appearance.immersiveFolders
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = if (draggingOut) 0f else 1f }) {
        if (!draggingOut) GlassScrim(progress) { ui.folder = null }
        // Immersive folders: a deeper dim and no floating panel (Settings → Appearance → Folder).
        if (!draggingOut && immersive) GlassScrim(progress * 1.6f, null)
        GlassMorph(
            visible = current != null,
            origin = last.origin,
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility),
            onProgress = { progress = it },
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(horizontal = 28.dp)) {
                // Folder title on its own glass capsule; tap to rename home folders.
                GlassSurface(
                    shape = GlassShape.Capsule,
                    variant = GlassVariant.Clear,
                    onClick = if (homeFolder != null) {
                        { ui.rename = RenameTarget(null, homeFolder.id, homeFolder.name, last.origin) }
                    } else null,
                ) {
                    GlassText(title, Modifier.padding(horizontal = 20.dp, vertical = 8.dp), style = GlassType.Title, maxLines = 1)
                }
                Spacer(Modifier.height(18.dp))
                val panel: @Composable (@Composable () -> Unit) -> Unit = if (immersive) {
                    { inner -> Box(Modifier.fillMaxWidth()) { inner() } }
                } else {
                    { inner -> GlassSurface(Modifier.fillMaxWidth(), shape = GlassShape.rounded(38.dp), role = null) { inner() } }
                }
                panel {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(if (entries.size > 9) 4 else 3),
                        modifier = Modifier.fillMaxWidth().heightIn(max = 460.dp).padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        items(entries, key = { it.key }) { entry ->
                            val index = homeFolder?.apps?.indexOf(entry.key) ?: -1
                            val slot: Slot? = if (homeFolder != null && index >= 0) Slot.InFolder(homeFolder.id, index) else null
                            val coords = remember { arrayOfNulls<LayoutCoordinates>(1) }
                            val hiddenWhileDragging = slot != null && ui.drag.isDragging(slot)
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier
                                    .onGloballyPositioned { coords[0] = it }
                                    .graphicsLayer { alpha = if (hiddenWhileDragging) 0f else 1f }
                                    .iconGestures(
                                        key = entry.key,
                                        editMode = false,
                                        coordinates = { coords[0] },
                                        onTap = {
                                            val b = coords[0]?.boundsInWindow()?.toAndroid()
                                            callbacks.launch(entry, b)
                                        },
                                        onLongPress = {
                                            haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                            val b = coords[0]?.boundsInWindow() ?: return@iconGestures
                                            callbacks.showMenu(MenuTarget(entry, null, slot, b))
                                        },
                                        onDragStart = { window, local ->
                                            if (slot != null) {
                                                ui.menu = null
                                                callbacks.startDrag(DragInfo(com.shivam.glasslauncher.data.AppItem(entry.key), slot, entry, local, with(density) { 60.dp.toPx() }), window)
                                            }
                                        },
                                        onDrag = { if (slot != null) callbacks.drag(it) },
                                        onDragEnd = { if (slot != null) { callbacks.endDrag(); ui.folder = null } },
                                        onDragCancel = { if (slot != null) callbacks.cancelDrag() },
                                    ),
                            ) {
                                BadgedIcon(60.dp, ctx.badges[entry.packageName]?.takeIf { ctx.showBadges } ?: 0, false, {}, 0f) {
                                    AppIconImage(entry, 60.dp)
                                }
                                if (ctx.appearance.folderLabels) {
                                    Spacer(Modifier.height(5.dp))
                                    GlassText(ctx.label(entry), style = GlassType.IconLabel, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** Long-press menu: shortcuts and actions on a Regular glass panel growing out of the icon. */
@Composable
fun ContextMenuOverlay(
    ui: LauncherUiState,
    vm: LauncherViewModel,
    layout: HomeLayout,
    ctx: HomeContext,
    callbacks: HomeCallbacks,
) {
    val current = ui.menu
    val target = rememberLast(current) ?: return
    val activity = androidx.activity.compose.LocalActivity.current ?: return
    var progress by remember { mutableFloatStateOf(0f) }
    if (current != null) BackHandler { ui.menu = null }
    val entry = target.entry
    val shortcuts by produceState<List<Pair<ShortcutInfo, androidx.compose.ui.graphics.ImageBitmap?>>>(emptyList(), entry?.key) {
        value = if (entry == null) emptyList() else withContext(Dispatchers.IO) {
            vm.actions.shortcuts(entry).map { it to vm.icons.renderShortcut(vm.actions.shortcutIcon(it), 72) }
        }
    }
    val close = { ui.menu = null }
    val placed = entry != null && entry.key in layout.placedKeys()

    Box(Modifier.fillMaxSize()) {
        GlassScrim(progress) { close() }
        // The pressed icon, lifted above the blur.
        val d = LocalDensity.current
        if (current != null) {
            Box(
                Modifier
                    .offset { IntOffset(target.anchor.left.roundToInt(), target.anchor.top.roundToInt()) }
                    .size(with(d) { target.anchor.width.toDp() }, with(d) { target.anchor.height.toDp() })
                    .graphicsLayer { val s = 1f + 0.06f * progress; scaleX = s; scaleY = s },
            ) {
                when {
                    entry != null -> AppIconImage(entry, with(d) { target.anchor.width.toDp() })
                    target.folder != null -> FolderIcon(target.folder, ctx.apps, with(d) { target.anchor.width.toDp() })
                }
            }
        }
        Box(
            Modifier.glassMenuPosition(target.anchor, alignEnd = false),
        ) {
            GlassMorph(visible = current != null, origin = target.anchor, onProgress = { progress = it }) {
                com.shivam.glasslauncher.ui.glass.GlassMenuPanel {
                    shortcuts.forEach { (info, icon) ->
                        MenuRow(info.shortLabel?.toString() ?: "", null, bitmap = icon) {
                            close()
                            vm.actions.startShortcut(info, target.anchor.toAndroid())
                        }
                    }
                    if (entry != null && !vm.actions.hasShortcutAccess()) {
                        // docs/CONSTRAINTS.md: shortcuts only work while Glass is the default Home app.
                        GlassText(
                            "App shortcuts appear when Glass is your default Home app.",
                            Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            style = GlassType.Footnote,
                            secondary = true,
                        )
                    }
                    if (shortcuts.isNotEmpty()) com.shivam.glasslauncher.ui.glass.GlassMenuDivider()
                    val slot = target.slot
                    if (target.folder != null) {
                        MenuRow("Rename Folder", GlassIcons.Pencil) {
                            close(); ui.rename = RenameTarget(null, target.folder.id, target.folder.name, target.anchor)
                        }
                        MenuRow("Edit Home Screen", GlassIcons.Grid) { close(); ui.editMode = true }
                        if (slot != null) MenuRow("Remove Folder", GlassIcons.Minus, destructive = true) { close(); callbacks.remove(slot) }
                    } else if (entry != null) {
                        if (slot is Slot.Page || slot is Slot.Dock) MenuRow("Edit Home Screen", GlassIcons.Grid) { close(); ui.editMode = true }
                        if (target.fromDrawer) {
                            MenuRow("Move to category", GlassIcons.Grid) { close(); ui.drawer.moveApp = entry }
                        }
                        MenuRow("Rename", GlassIcons.Pencil) {
                            close(); ui.rename = RenameTarget(entry.key, null, ctx.label(entry), target.anchor)
                        }
                        MenuRow("Edit Icon", GlassIcons.Palette) {
                            close(); activity.startActivity(com.shivam.glasslauncher.settings.SettingsActivity.intent(activity, "editicon:${entry.key}"))
                        }
                        when {
                            slot is Slot.InFolder -> MenuRow("Remove from Folder", GlassIcons.Minus) { close(); vm.removeFromFolder(slot, entry.key) }
                            slot != null -> MenuRow("Remove from Home", GlassIcons.Minus) { close(); callbacks.remove(slot) }
                            !placed -> MenuRow("Add to Home Screen", GlassIcons.Plus) { close(); vm.addToHome(entry.key); ui.toast.show("Added to Home Screen") }
                        }
                        MenuRow("Hide App", GlassIcons.EyeOff) { close(); vm.hide(entry.key); ui.toast.show("Hidden from Home, Library and Search. Still listed in system Settings.") }
                        val locked = vm.isLocked(entry)
                        MenuRow(if (locked) "Unlock App" else "Lock App", GlassIcons.Lock) {
                            close(); vm.setLocked(entry.key, !locked)
                            ui.toast.show(if (locked) "Unlocked" else "Locked when opened from Glass. Notifications and Recents can still open it.")
                        }
                        MenuRow("App Info", GlassIcons.Info) { close(); vm.actions.appInfo(entry) }
                        com.shivam.glasslauncher.ui.glass.GlassMenuDivider()
                        MenuRow("Uninstall", GlassIcons.Trash, destructive = true) { close(); vm.actions.uninstall(activity, entry) }
                    }
                }
            }
        }
    }
}

@Composable
private fun MenuRow(
    text: String,
    icon: ImageVector?,
    destructive: Boolean = false,
    bitmap: androidx.compose.ui.graphics.ImageBitmap? = null,
    onClick: () -> Unit,
) {
    com.shivam.glasslauncher.ui.glass.GlassMenuRow(
        text,
        icon,
        destructive,
        trailing = if (bitmap != null) ({ Image(bitmap, null, Modifier.size(24.dp)) }) else null,
        onClick = onClick,
    )
}

/** Rename an app label or a folder on a glass panel with a glass text field. */
@Composable
fun RenameOverlay(ui: LauncherUiState, vm: LauncherViewModel) {
    val current = ui.rename
    val target = rememberLast(current) ?: return
    var text by remember(target) { mutableStateOf(target.current) }
    var progress by remember { mutableFloatStateOf(0f) }
    val focus = remember { FocusRequester() }
    if (current != null) BackHandler { ui.rename = null }
    fun save() {
        if (target.folderId != null) vm.renameFolder(target.folderId, text)
        if (target.appKey != null) vm.setLabel(target.appKey, text)
        ui.rename = null
    }
    Box(Modifier.fillMaxSize()) {
        GlassScrim(progress) { ui.rename = null }
        GlassMorph(
            visible = current != null,
            origin = target.origin,
            modifier = Modifier.fillMaxSize().imePadding(),
            onProgress = { progress = it },
        ) {
            GlassSurface(Modifier.widthIn(max = 340.dp).padding(24.dp), shape = GlassShape.Panel, role = null) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    GlassText(if (target.folderId != null) "Rename Folder" else "Rename App", style = GlassType.Headline)
                    if (target.appKey != null) {
                        GlassText("Leave empty to restore the original name.", style = GlassType.Footnote, secondary = true)
                    }
                    Spacer(Modifier.height(14.dp))
                    GlassTextField(
                        value = text,
                        onValueChange = { text = it },
                        leadingIcon = GlassIcons.Pencil,
                        focusRequester = focus,
                        variant = GlassVariant.Clear,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { save() }),
                    )
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GlassButton("Cancel", onClick = { ui.rename = null }, modifier = Modifier.weight(1f))
                        GlassButton("Save", onClick = { save() }, modifier = Modifier.weight(1f), prominent = true)
                    }
                }
            }
        }
    }
    LaunchedEffect(current) { if (current != null) try { focus.requestFocus() } catch (_: Exception) { } }
}

/** Glass pre-screen before the system BiometricPrompt for locked apps. */
@Composable
fun LockPromptOverlay(ui: LauncherUiState, vm: LauncherViewModel, ctx: HomeContext) {
    val current = ui.lockPrompt
    val last = rememberLast(current) ?: return
    val activity = androidx.activity.compose.LocalActivity.current ?: return
    GlassAlert(
        visible = current != null,
        title = "“${ctx.label(last.entry)}” is locked",
        message = "Use your fingerprint, face or screen lock to open it.",
        icon = GlassIcons.Lock,
        origin = last.bounds?.let { Rect(it.left.toFloat(), it.top.toFloat(), it.right.toFloat(), it.bottom.toFloat()) },
        onDismiss = { ui.lockPrompt = null },
        actions = listOf(
            GlassAlertAction("Cancel") { ui.lockPrompt = null },
            GlassAlertAction("Unlock", prominent = true) {
                ui.lockPrompt = null
                vm.actions.authenticate(
                    activity,
                    "Unlock “${ctx.label(last.entry)}”",
                    onSuccess = { vm.launchNow(activity, last.entry, last.bounds) },
                    onFail = { ui.toast.show(it) },
                )
            },
        ),
    )
}

