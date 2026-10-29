package com.shivam.glasslauncher.ui.drawer

import android.content.pm.ShortcutInfo
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.shivam.glasslauncher.data.AppEntry
import com.shivam.glasslauncher.data.DrawerCategorizer
import com.shivam.glasslauncher.data.DrawerItem
import com.shivam.glasslauncher.data.DrawerOps
import com.shivam.glasslauncher.data.DrawerSort
import com.shivam.glasslauncher.data.HomeLayout
import com.shivam.glasslauncher.ui.LauncherViewModel
import com.shivam.glasslauncher.ui.glass.GlassAlert
import com.shivam.glasslauncher.ui.glass.GlassAlertAction
import com.shivam.glasslauncher.ui.glass.GlassButton
import com.shivam.glasslauncher.ui.glass.GlassChip
import com.shivam.glasslauncher.ui.glass.GlassColors
import com.shivam.glasslauncher.ui.glass.GlassIcon
import com.shivam.glasslauncher.ui.glass.GlassIconButton
import com.shivam.glasslauncher.ui.glass.GlassIcons
import com.shivam.glasslauncher.ui.glass.GlassListGroup
import com.shivam.glasslauncher.ui.glass.GlassMorph
import com.shivam.glasslauncher.ui.glass.GlassRow
import com.shivam.glasslauncher.ui.glass.GlassScrim
import com.shivam.glasslauncher.ui.glass.GlassShape
import com.shivam.glasslauncher.ui.glass.GlassSurface
import com.shivam.glasslauncher.ui.glass.GlassSwitch
import com.shivam.glasslauncher.ui.glass.GlassText
import com.shivam.glasslauncher.ui.glass.GlassTextField
import com.shivam.glasslauncher.ui.glass.GlassType
import com.shivam.glasslauncher.ui.glass.GlassVariant
import com.shivam.glasslauncher.ui.glass.currentAccent
import com.shivam.glasslauncher.ui.home.AppIconImage
import com.shivam.glasslauncher.ui.home.HomeContext
import com.shivam.glasslauncher.ui.home.rememberLast
import com.shivam.glasslauncher.widgets.GlassSheet
import com.shivam.glasslauncher.widgets.WidgetGlyphs
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Sheets opened from the drawer's ⋮ menu and from long-press → Move to category. */
@Composable
fun DrawerSheets(
    state: DrawerUiState,
    vm: LauncherViewModel,
    layout: HomeLayout,
    ctx: HomeContext,
    pages: Map<String, List<DrawerItem>>,
) {
    CategoriesSheet(state, vm, layout)
    SortingSheet(state, vm, layout)
    AddFolderSheet(state, vm, ctx, pages)
    AddShortcutSheet(state, vm, ctx, pages)
    MoveToCategorySheet(state, vm, layout, ctx)
    ShortcutRemoveAlert(state, vm)
}

/** Category bar: show/hide, order, rename, delete. */
@Composable
private fun CategoriesSheet(state: DrawerUiState, vm: LauncherViewModel, layout: HomeLayout) {
    val cats = layout.drawer.categories
    GlassSheet(visible = state.sheet == DrawerSheet.Categories, title = "Category bar", onDismiss = { state.sheet = null }) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item("list") {
                GlassListGroup(footer = "Switch a category off to take it out of the bar. Deleting a category sends its apps back to their automatic category; Other can't be deleted.") {
                    cats.forEachIndexed { i, c ->
                        Row(Modifier.fillMaxWidth().padding(start = 14.dp, end = 8.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            GlassIcon(DrawerGlyphs.of(c.glyph), size = 22.dp)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                GlassText(c.title, style = GlassType.Body, maxLines = 1)
                                GlassText(if (c.builtIn) "Built-in" else "Yours", style = GlassType.Caption, secondary = true)
                            }
                            GlassIconButton(WidgetGlyphs.ArrowUp, onClick = { vm.updateDrawer { DrawerOps.moveCategory(it, i, i - 1) } }, size = 32.dp)
                            Spacer(Modifier.width(4.dp))
                            GlassIconButton(WidgetGlyphs.ArrowDown, onClick = { vm.updateDrawer { DrawerOps.moveCategory(it, i, i + 1) } }, size = 32.dp)
                            Spacer(Modifier.width(4.dp))
                            GlassIconButton(GlassIcons.Pencil, onClick = { state.prompt = DrawerPrompt.RenameCategory(c.id, c.title) }, size = 32.dp)
                            if (c.id != DrawerCategorizer.OTHER) {
                                Spacer(Modifier.width(4.dp))
                                GlassIconButton(GlassIcons.Trash, onClick = { vm.updateDrawer { DrawerOps.deleteCategory(it, c.id) } }, size = 32.dp, tint = GlassColors.Red)
                            }
                            Spacer(Modifier.width(8.dp))
                            GlassSwitch(c.inBar, { on -> vm.updateDrawer { DrawerOps.setInBar(it, c.id, on) } })
                        }
                    }
                }
            }
            item("add") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    GlassButton("Add category", onClick = { state.prompt = DrawerPrompt.AddCategory }, icon = GlassIcons.Plus, prominent = true)
                }
            }
        }
    }
}

@Composable
private fun SortingSheet(state: DrawerUiState, vm: LauncherViewModel, layout: HomeLayout) {
    GlassSheet(visible = state.sheet == DrawerSheet.Sorting, title = "Sorting", onDismiss = { state.sheet = null }, heightFraction = 0.45f) {
        Column(Modifier.padding(horizontal = 14.dp)) {
            GlassListGroup(footer = "Folders and shortcuts stay first. Most used learns from launches through Glass only.") {
                DrawerSort.entries.forEach { s ->
                    GlassRow(s.title, onClick = { vm.updateDrawer { DrawerOps.setSort(it, s) }; state.sheet = null }, showChevron = false) {
                        if (layout.drawer.sort == s) GlassIcon(GlassIcons.Check, size = 18.dp, tint = currentAccent())
                    }
                }
            }
        }
    }
}

/** The apps of a category page (no folders or shortcuts). */
private fun appsOf(pages: Map<String, List<DrawerItem>>, categoryId: String, ctx: HomeContext): List<AppEntry> =
    (pages[categoryId] ?: emptyList()).filterIsInstance<DrawerItem.App>().mapNotNull { ctx.apps[it.key] }

@Composable
private fun AddFolderSheet(state: DrawerUiState, vm: LauncherViewModel, ctx: HomeContext, pages: Map<String, List<DrawerItem>>) {
    val sheet = rememberLast(state.sheet as? DrawerSheet.AddFolder)
    val visible = state.sheet is DrawerSheet.AddFolder
    var name by remember(sheet) { mutableStateOf("") }
    var chosen by remember(sheet) { mutableStateOf(listOf<String>()) }
    GlassSheet(visible = visible, title = "New folder", onDismiss = { state.sheet = null }) {
        val catId = sheet?.categoryId ?: return@GlassSheet
        val apps = appsOf(pages, catId, ctx)
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item("name") {
                GlassListGroup(footer = "Pick apps from this category. An app in a folder shows only in the folder.") {
                    Box(Modifier.padding(10.dp)) {
                        GlassTextField(name, { name = it }, placeholder = "Folder name", leadingIcon = DrawerGlyphs.Folder, variant = GlassVariant.Clear)
                    }
                }
            }
            items(apps.chunked(12), key = { it.first().key }) { chunk ->
                GlassListGroup {
                    chunk.forEach { e ->
                        Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            AppIconImage(e, 32.dp)
                            Spacer(Modifier.width(12.dp))
                            GlassText(ctx.label(e), Modifier.weight(1f), style = GlassType.Body, maxLines = 1)
                            GlassSwitch(e.key in chosen, { on -> chosen = if (on) chosen + e.key else chosen - e.key })
                        }
                    }
                }
            }
            item("create") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    GlassButton(
                        if (chosen.isEmpty()) "Pick some apps" else "Create folder (${chosen.size})",
                        onClick = {
                            vm.updateDrawer { DrawerOps.addFolder(it, catId, name, chosen) }
                            state.sheet = null
                        },
                        enabled = chosen.isNotEmpty(),
                        prominent = true,
                    )
                }
            }
        }
    }
}

/** Add an app shortcut (deep shortcut) to a category; works only while Glass is the default Home app. */
@Composable
private fun AddShortcutSheet(state: DrawerUiState, vm: LauncherViewModel, ctx: HomeContext, pages: Map<String, List<DrawerItem>>) {
    val sheet = rememberLast(state.sheet as? DrawerSheet.AddShortcut)
    val visible = state.sheet is DrawerSheet.AddShortcut
    var expanded by remember(sheet) { mutableStateOf<String?>(null) }
    GlassSheet(visible = visible, title = "Add shortcut", onDismiss = { state.sheet = null }) {
        val catId = sheet?.categoryId ?: return@GlassSheet
        val access = remember(visible) { vm.actions.hasShortcutAccess() }
        // Shortcuts may come from any app, not just this category's.
        val apps = ctx.apps.values.sortedBy { ctx.label(it).lowercase() }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (!access) {
                item("noaccess") {
                    GlassListGroup(footer = "Android shares app shortcuts only with the default Home app (docs/CONSTRAINTS.md).") {
                        GlassRow("Set Glass as your Home app first", icon = GlassIcons.Home, iconTint = GlassColors.Orange)
                    }
                }
            } else {
                item("hint") {
                    GlassText(
                        "Tap an app to see its shortcuts, then tap one to add it to this category.",
                        Modifier.padding(horizontal = 8.dp),
                        style = GlassType.Footnote,
                        color = com.shivam.glasslauncher.ui.glass.LocalScreenContentColor.current.copy(alpha = 0.8f),
                    )
                }
                items(apps, key = { it.key }) { e ->
                    GlassListGroup {
                        GlassRow(ctx.label(e), onClick = { expanded = if (expanded == e.key) null else e.key }, showChevron = false) {
                            AppIconImage(e, 28.dp)
                        }
                        if (expanded == e.key) {
                            val shortcuts by produceState<List<ShortcutInfo>?>(null, e.key) {
                                value = withContext(Dispatchers.IO) { vm.actions.shortcuts(e) }
                            }
                            when {
                                shortcuts == null -> GlassRow("Loading…")
                                shortcuts!!.isEmpty() -> GlassRow("This app has no shortcuts")
                                else -> shortcuts!!.forEach { info ->
                                    val label = info.shortLabel?.toString() ?: info.id
                                    GlassRow(label, icon = DrawerGlyphs.Link, onClick = {
                                        vm.updateDrawer { DrawerOps.addShortcut(it, catId, e.key, e.packageName, info.id, label) }
                                        state.sheet = null
                                    })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MoveToCategorySheet(state: DrawerUiState, vm: LauncherViewModel, layout: HomeLayout, ctx: HomeContext) {
    val entry = rememberLast(state.moveApp)
    GlassSheet(visible = state.moveApp != null, title = "Move to category", onDismiss = { state.moveApp = null }, heightFraction = 0.7f) {
        val e = entry ?: return@GlassSheet
        val current = layout.drawer.assignments[e.key] ?: e.drawerCategory
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 14.dp, end = 14.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item("app") {
                Row(Modifier.padding(horizontal = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    AppIconImage(e, 40.dp)
                    Spacer(Modifier.width(12.dp))
                    GlassText(ctx.label(e), style = GlassType.Headline, color = com.shivam.glasslauncher.ui.glass.LocalScreenContentColor.current)
                }
            }
            item("cats") {
                GlassListGroup(footer = "Automatic: ${DrawerCategorizer.defaultTitle(e.drawerCategory)}. Choosing it again removes the manual move.") {
                    layout.drawer.categories.forEach { c ->
                        GlassRow(c.title, icon = DrawerGlyphs.of(c.glyph), iconTint = com.shivam.glasslauncher.ui.glass.GlassColors.Accent, showChevron = false, onClick = {
                            vm.moveToCategory(e, c.id)
                            state.moveApp = null
                        }) {
                            if (c.id == current) GlassIcon(GlassIcons.Check, size = 18.dp, tint = currentAccent())
                        }
                    }
                }
            }
            item("new") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    GlassButton("New category", onClick = { state.prompt = DrawerPrompt.AddCategory }, icon = GlassIcons.Plus)
                }
            }
        }
    }
}

@Composable
private fun ShortcutRemoveAlert(state: DrawerUiState, vm: LauncherViewModel) {
    val s = rememberLast(state.shortcutMenu)
    GlassAlert(
        visible = state.shortcutMenu != null,
        title = s?.label ?: "",
        message = "Remove this shortcut from the drawer?",
        actions = listOf(
            GlassAlertAction("Cancel") { state.shortcutMenu = null },
            GlassAlertAction("Remove", destructive = true) {
                s?.let { sc -> vm.updateDrawer { DrawerOps.removeShortcut(it, sc.id) } }
                state.shortcutMenu = null
            },
        ),
        onDismiss = { state.shortcutMenu = null },
        icon = DrawerGlyphs.Link,
    )
}

/** Name prompts: new category (with a glyph), rename category, rename folder. */
@Composable
fun DrawerPrompts(state: DrawerUiState, vm: LauncherViewModel) {
    val current = state.prompt
    val target = rememberLast(current) ?: return
    var text by remember(target) {
        mutableStateOf(
            when (target) {
                is DrawerPrompt.RenameCategory -> target.current
                is DrawerPrompt.RenameFolder -> target.current
                DrawerPrompt.AddCategory -> ""
            },
        )
    }
    var glyph by remember(target) { mutableStateOf("star") }
    var progress by remember { mutableFloatStateOf(0f) }
    val focus = remember { FocusRequester() }
    fun save() {
        when (target) {
            DrawerPrompt.AddCategory -> vm.updateDrawer { DrawerOps.addCategory(it, text, glyph) }
            is DrawerPrompt.RenameCategory -> vm.updateDrawer { DrawerOps.renameCategory(it, target.id, text) }
            is DrawerPrompt.RenameFolder -> {
                vm.updateDrawer { DrawerOps.renameFolder(it, target.id, text) }
                state.folder = state.folder?.let { if (it.id == target.id && text.isNotBlank()) it.copy(name = text.trim()) else it }
            }
        }
        state.prompt = null
    }
    Box(Modifier.fillMaxSize()) {
        if (current != null || progress > 0.01f) GlassScrim(progress) { state.prompt = null }
        GlassMorph(visible = current != null, origin = null, modifier = Modifier.fillMaxSize().imePadding(), onProgress = { progress = it }) {
            GlassSurface(Modifier.widthIn(max = 340.dp).padding(24.dp), shape = GlassShape.Panel, role = null) {
                Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    GlassText(
                        when (target) {
                            DrawerPrompt.AddCategory -> "New category"
                            is DrawerPrompt.RenameCategory -> "Rename category"
                            is DrawerPrompt.RenameFolder -> "Rename folder"
                        },
                        style = GlassType.Headline,
                    )
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
                    if (target == DrawerPrompt.AddCategory) {
                        Spacer(Modifier.height(12.dp))
                        GlassText("Icon", style = GlassType.Subhead, secondary = true)
                        Spacer(Modifier.height(6.dp))
                        DrawerGlyphs.pickable.chunked(6).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(vertical = 3.dp)) {
                                row.forEach { g ->
                                    GlassSurface(
                                        Modifier.size(38.dp),
                                        shape = GlassShape.Circle,
                                        variant = GlassVariant.Clear,
                                        tintOverride = if (g == glyph) currentAccent() else null,
                                        onClick = { glyph = g },
                                        contentAlignment = Alignment.Center,
                                    ) { GlassIcon(DrawerGlyphs.of(g), size = 20.dp) }
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        GlassButton("Cancel", onClick = { state.prompt = null }, modifier = Modifier.weight(1f))
                        GlassButton("Save", onClick = { save() }, modifier = Modifier.weight(1f), prominent = true, enabled = text.isNotBlank())
                    }
                }
            }
        }
    }
    LaunchedEffect(current) { if (current != null) try { focus.requestFocus() } catch (_: Exception) { } }
}
