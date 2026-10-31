@file:OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)

package com.shivam.glasslauncher.ui.spotlight

import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.statusBarsIgnoringVisibility
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.imeAnimationTarget
import android.Manifest
import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.shivam.glasslauncher.data.AppEntry
import com.shivam.glasslauncher.search.ContactResult
import com.shivam.glasslauncher.search.FileResult
import com.shivam.glasslauncher.search.FileSearch
import com.shivam.glasslauncher.search.ShortcutResult
import com.shivam.glasslauncher.widgets.CalendarEvent
import com.shivam.glasslauncher.search.SpotlightResults
import com.shivam.glasslauncher.ui.LauncherViewModel
import com.shivam.glasslauncher.ui.glass.GlassColors
import com.shivam.glasslauncher.ui.glass.GlassIconButton
import com.shivam.glasslauncher.ui.glass.GlassIcons
import com.shivam.glasslauncher.ui.glass.GlassListGroup
import com.shivam.glasslauncher.ui.glass.GlassMorph
import com.shivam.glasslauncher.ui.glass.GlassRow
import com.shivam.glasslauncher.ui.glass.PremiumDim
import com.shivam.glasslauncher.ui.glass.premiumDim
import com.shivam.glasslauncher.ui.glass.GlassShape
import com.shivam.glasslauncher.ui.glass.GlassSurface
import com.shivam.glasslauncher.ui.glass.GlassText
import com.shivam.glasslauncher.ui.glass.GlassTextField
import com.shivam.glasslauncher.ui.glass.GlassType
import com.shivam.glasslauncher.ui.home.AppIconImage
import com.shivam.glasslauncher.ui.home.LauncherUiState
import com.shivam.glasslauncher.ui.home.rememberLast
import kotlinx.coroutines.delay

private var askedContacts = false

/**
 * Spotlight: a full-screen glass sheet that grows out of where it was summoned (swipe-down
 * point or the search pill). The search field is focused and the keyboard raised immediately.
 */
@Composable
fun SpotlightOverlay(
    ui: LauncherUiState,
    vm: LauncherViewModel,
    label: (AppEntry) -> String,
    onLaunch: (AppEntry, android.graphics.Rect?) -> Unit,
    onOpenSettings: () -> Unit,
) {
    val visible = ui.spotlightOpen
    val origin = rememberLast(ui.spotlight)
    val query by vm.query.collectAsState()
    val results by vm.results.collectAsState()
    val wallpaper by vm.wallpaper.collectAsState()
    val spotlightDim = remember(wallpaper) {
        PremiumDim.alphaFor(wallpaper.luminance(0f, 0f, wallpaper.screenWidth.toFloat(), wallpaper.screenHeight.toFloat()))
    }
    val suggestions by vm.suggestions.collectAsState()
    val recent by vm.recentSearches.collectAsState()
    val focus = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    val activity = androidx.activity.compose.LocalActivity.current ?: return
    var progress by remember { mutableFloatStateOf(0f) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
        vm.onContactsPermissionChanged()
    }

    fun close() {
        keyboard?.hide()
        ui.spotlightOpen = false
    }

    /** A result was chosen: remember the query, close, then act. */
    fun pick(action: () -> Unit) {
        vm.recordSearch(query)
        close()
        action()
    }

    LaunchedEffect(visible) {
        if (visible) {
            vm.query.value = ""
            vm.onSpotlightOpened()
            // the field only exists once GlassMorph has composed it, so retry until focus sticks
            var focused = false
            repeat(20) {
                if (!focused) {
                    try { focus.requestFocus(); focused = true } catch (_: Exception) { delay(40) }
                }
            }
            keyboard?.show()
            if (activity.checkSelfPermission(Manifest.permission.READ_CONTACTS) != android.content.pm.PackageManager.PERMISSION_GRANTED &&
                !askedContacts
            ) {
                askedContacts = true
                permissionLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE))
            }
        }
    }
    if (visible) BackHandler { close() }
    if (!visible && progress <= 0.01f) {
        // keep composition cheap when closed
    }

    Box(Modifier.fillMaxSize()) {
        // Balanced dim (PremiumDim) so the results read over the blurred home and the wallpaper stays visible.
        if (visible || progress > 0.01f) Box(
            Modifier.fillMaxSize().premiumDim(spotlightDim * progress.coerceIn(0f, 1f))
                .pointerInput(Unit) { detectTapGestures { close() } },
        )
        GlassMorph(
            visible = visible,
            origin = origin,
            // The keyboard's final height, applied once: following it frame by frame re-measured the
            // whole sheet (~20 ms) and resized its glass layers on every frame of the slide-in.
            modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.statusBarsIgnoringVisibility).windowInsetsPadding(WindowInsets.imeAnimationTarget),
            contentAlignment = Alignment.TopCenter,
            onProgress = { progress = it },
        ) {
            // A tap on empty space (not on a result, field or button) goes back to Home.
            Column(Modifier.fillMaxSize().pointerInput(Unit) { detectTapGestures { close() } }.padding(horizontal = 14.dp)) {
                Spacer(Modifier.height(10.dp))
                // Search stays within thumb reach and exposes an explicit way back home.
                SpotlightSearchField(
                    query = query,
                    onQuery = { vm.query.value = it },
                    focus = focus,
                    onGo = {
                        val top = results.apps.firstOrNull()
                        if (top != null) pick { onLaunch(top, null) }
                        else if (query.isNotBlank()) pick { vm.actions.webSearch(activity, query) }
                    },
                    onClose = { close() },
                    onVoice = {
                        close()
                        try {
                            activity.startActivity(android.content.Intent(android.speech.RecognizerIntent.ACTION_WEB_SEARCH).addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK))
                        } catch (_: Exception) { ui.toast.show("Voice search isn't available") }
                    },
                )
                Spacer(Modifier.height(12.dp))
                SpotlightResultsList(
                    query = query,
                    results = results,
                    suggestions = suggestions,
                    recent = recent,
                    label = label,
                    onLaunch = { e, b -> pick { onLaunch(e, b) } },
                    onShortcut = { r, b ->
                        pick { if (!vm.actions.startShortcut(r.info.`package`, r.info.id, r.info.userHandle, b)) ui.toast.show("This shortcut isn't available") }
                    },
                    loadShortcutIcon = { r -> vm.icons.renderShortcut(vm.actions.shortcutIcon(r.info), 96) },
                    onCall = { c -> c.number?.let { pick { vm.actions.call(activity, it) } } },
                    onWhatsApp = { c -> c.number?.let { pick { vm.actions.whatsApp(activity, it) } } },
                    onSetting = { s -> pick { if (!vm.actions.startIntent(activity, s.intent())) ui.toast.show("Not available on this phone") } },
                    onEvent = { e -> pick { com.shivam.glasslauncher.widgets.CalendarRepository.openEvent(activity, e) } },
                    onFile = { f ->
                        pick {
                            // No viewer for the exact type: let any app that opens files try.
                            if (!vm.actions.startIntent(activity, FileSearch.openIntent(f)) &&
                                !vm.actions.startIntent(activity, FileSearch.openIntent(f, anyType = true))
                            ) ui.toast.show("No app can open this file")
                        }
                    },
                    onWeb = { pick { vm.actions.webSearch(activity, query) } },
                    onPlayStore = { pick { vm.actions.playStoreSearch(activity, query) } },
                    onRecent = { vm.query.value = it },
                    onForgetRecent = { vm.forgetSearch(it) },
                    onAllowContacts = {
                        permissionLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS, Manifest.permission.CALL_PHONE))
                    },
                    onAllowFiles = { close(); vm.actions.startIntent(activity, FileSearch.accessIntent(activity)) },
                    onOpenSettings = { close(); onOpenSettings() },
                )
            }
        }
    }
}

/** Spotlight sections use the Notification Centre's card glass: frosted widget material, 28 dp corners. */
private val SectionShape = GlassShape.rounded(28.dp)
private val SectionKind = com.shivam.glasslauncher.ui.glass.SurfaceKind.Widget

@Composable
private fun Section(title: String? = null, footer: String? = null, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) =
    GlassListGroup(title = title, footer = footer, shape = SectionShape, kind = SectionKind, content = content)

@Composable
private fun SpotlightResultsList(
    query: String,
    results: SpotlightResults,
    suggestions: List<AppEntry>,
    recent: List<String>,
    label: (AppEntry) -> String,
    onLaunch: (AppEntry, android.graphics.Rect?) -> Unit,
    onShortcut: (ShortcutResult, android.graphics.Rect?) -> Unit,
    loadShortcutIcon: (ShortcutResult) -> androidx.compose.ui.graphics.ImageBitmap?,
    onCall: (ContactResult) -> Unit,
    onWhatsApp: (ContactResult) -> Unit,
    onSetting: (com.shivam.glasslauncher.search.SettingsShortcut) -> Unit,
    onEvent: (CalendarEvent) -> Unit,
    onFile: (FileResult) -> Unit,
    onWeb: () -> Unit,
    onPlayStore: () -> Unit,
    onRecent: (String) -> Unit,
    onForgetRecent: (String) -> Unit,
    onAllowContacts: () -> Unit,
    onAllowFiles: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    // The query the results were computed for; stale rows never pair with a newer query's text.
    val shown = results.query
    LazyColumn(
        Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        if (suggestions.isNotEmpty() && query.isBlank()) {
            item("suggestions") {
                Section(title = "Suggested for you") {
                    AppIconRow(suggestions, label, onLaunch)
                }
            }
        }
        if (recent.isNotEmpty() && query.isBlank()) {
            item("recent") {
                Section(title = "Recent") {
                    recent.forEach { q ->
                        GlassRow(q, icon = GlassIcons.Clock, iconTint = Color(0xFF8E8E93), onClick = { onRecent(q) }, showChevron = false) {
                            GlassIconButton(GlassIcons.Close, onClick = { onForgetRecent(q) }, size = 32.dp, contentDescription = "Remove “$q” from recent searches")
                        }
                    }
                }
            }
        }
        if (query.isBlank()) {
            item("hint") {
                Section(footer = "Find apps, shortcuts, contacts, events, files and settings. You can also try 12 × 7 or 5 km to mi.") {
                    GlassRow("Glass Settings", icon = GlassIcons.Gear, iconTint = Color(0xFF8E8E93), onClick = onOpenSettings)
                }
            }
        }
        if (query.isBlank()) return@LazyColumn
        results.calculation?.let { value ->
            item("calc") {
                GlassSurface(Modifier.fillMaxWidth(), shape = SectionShape, role = null, kind = SectionKind) {
                    Column(Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            com.shivam.glasslauncher.ui.glass.GlassIcon(GlassIcons.Calculator, size = 18.dp)
                            Spacer(Modifier.width(8.dp))
                            GlassText(results.calculationExpression ?: "", style = GlassType.Callout, secondary = true, maxLines = 2)
                        }
                        Spacer(Modifier.height(4.dp))
                        GlassText("= $value", style = GlassType.LargeTitle, maxLines = 2)
                    }
                }
            }
        }
        results.conversion?.let { c ->
            item("convert") {
                GlassSurface(Modifier.fillMaxWidth(), shape = SectionShape, role = null, kind = SectionKind) {
                    Column(Modifier.padding(18.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            com.shivam.glasslauncher.ui.glass.GlassIcon(GlassIcons.Ruler, size = 18.dp)
                            Spacer(Modifier.width(8.dp))
                            GlassText("${c.from.name} → ${c.to.name}", style = GlassType.Callout, secondary = true)
                        }
                        Spacer(Modifier.height(4.dp))
                        GlassText(c.text, style = GlassType.Title, maxLines = 2)
                    }
                }
            }
        }
        if (results.apps.isNotEmpty()) {
            item("apps") {
                Section(title = "Apps") {
                    AppIconRow(results.apps.take(4), label, onLaunch)
                    results.apps.drop(4).forEach { e ->
                        AppResultRow(e, label(e), onLaunch)
                    }
                }
            }
        }
        if (results.shortcuts.isNotEmpty()) {
            item("shortcuts") {
                Section(title = "Shortcuts") {
                    results.shortcuts.forEach { r -> ShortcutResultRow(r, label(r.app), loadShortcutIcon, onShortcut) }
                }
            }
        }
        if (results.contacts.isNotEmpty()) {
            item("contacts") {
                Section(title = "Contacts") {
                    results.contacts.forEach { c ->
                        GlassRow(
                            title = c.name,
                            subtitle = c.number,
                            icon = GlassIcons.Person,
                            iconTint = Color(0xFF8E8E93),
                            showChevron = false,
                        ) {
                            if (c.number != null) {
                                GlassIconButton(GlassIcons.Phone, onClick = { onCall(c) }, size = 38.dp, tint = GlassColors.Green)
                                if (results.whatsAppInstalled) {
                                    Spacer(Modifier.width(8.dp))
                                    GlassIconButton(GlassIcons.Chat, onClick = { onWhatsApp(c) }, size = 38.dp, tint = Color(0xFF25D366))
                                }
                            }
                        }
                    }
                }
            }
        }
        if (results.needsContactsPermission) {
            item("contactsPerm") {
                Section(footer = "Contacts stay on your phone; Glass only reads them to show matches here.") {
                    GlassRow("Search your contacts too", icon = GlassIcons.Person, onClick = onAllowContacts)
                }
            }
        }
        if (results.settings.isNotEmpty()) {
            item("settings") {
                Section(title = "Settings") {
                    results.settings.forEach { s ->
                        GlassRow(s.title, icon = GlassIcons.Gear, iconTint = Color(0xFF8E8E93), onClick = { onSetting(s) })
                    }
                }
            }
        }
        if (results.events.isNotEmpty()) {
            item("events") {
                val context = LocalContext.current
                Section(title = "Calendar") {
                    results.events.forEach { e ->
                        GlassRow(
                            e.title,
                            subtitle = listOfNotNull(eventWhen(context, e), e.location).joinToString(" · "),
                            icon = GlassIcons.Calendar,
                            iconTint = Color(e.color or 0xFF000000.toInt()),
                            onClick = { onEvent(e) },
                        )
                    }
                }
            }
        }
        if (results.files.isNotEmpty()) {
            item("files") {
                val context = LocalContext.current
                Section(title = "Files") {
                    results.files.forEach { f ->
                        GlassRow(
                            f.name,
                            subtitle = fileDetails(context, f),
                            icon = fileIcon(f.mime),
                            iconTint = Color(0xFF0A84FF),
                            onClick = { onFile(f) },
                        )
                    }
                }
            }
        }
        if (results.needsFilesPermission) {
            item("filesPerm") {
                Section(footer = "Turn on All files access for Glass. Files are searched on your phone; nothing leaves it.") {
                    GlassRow("Search your files too", icon = GlassIcons.Doc, onClick = onAllowFiles)
                }
            }
        }
        if (shown.isNotBlank()) {
            item("web") {
                Section {
                    GlassRow("Search Google for “$shown”", icon = GlassIcons.Globe, onClick = onWeb)
                    GlassRow("Search Play Store for “$shown”", icon = GlassIcons.Bag, iconTint = GlassColors.Green, onClick = onPlayStore)
                }
            }
        }
    }
}

@Composable
private fun ShortcutResultRow(
    r: ShortcutResult,
    appLabel: String,
    loadIcon: (ShortcutResult) -> androidx.compose.ui.graphics.ImageBitmap?,
    onShortcut: (ShortcutResult, android.graphics.Rect?) -> Unit,
) {
    val icon by androidx.compose.runtime.produceState<androidx.compose.ui.graphics.ImageBitmap?>(null, r.info.id, r.info.`package`) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { loadIcon(r) }
    }
    val coords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    Box(
        Modifier.fillMaxWidth().onGloballyPositioned { coords[0] = it }
            .clickable { onShortcut(r, coords[0]?.boundsInWindow()?.toAndroid()) },
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 11.dp), verticalAlignment = Alignment.CenterVertically) {
            val bmp = icon
            if (bmp != null) androidx.compose.foundation.Image(bmp, null, Modifier.size(34.dp))
            else AppIconImage(r.app, 34.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                GlassText(r.label, style = GlassType.Body, maxLines = 1)
                GlassText(appLabel, style = GlassType.Footnote, secondary = true, maxLines = 1)
            }
        }
    }
}

private fun fileIcon(mime: String?) = when {
    mime == null -> GlassIcons.Doc
    mime.startsWith("image/") -> GlassIcons.Image
    mime.startsWith("video/") -> GlassIcons.Play
    mime.startsWith("audio/") -> GlassIcons.Music
    else -> GlassIcons.Doc
}

/** "Download · 2.4 MB · 12 Oct" */
private fun fileDetails(context: android.content.Context, f: FileResult): String = listOf(
    f.folder.trimEnd('/').ifEmpty { "Internal storage" },
    android.text.format.Formatter.formatShortFileSize(context, f.size),
    android.text.format.DateUtils.formatDateTime(context, f.modified, android.text.format.DateUtils.FORMAT_SHOW_DATE or android.text.format.DateUtils.FORMAT_ABBREV_MONTH),
).joinToString(" · ")

/** "Sat, 2 Nov, 10:00–11:00", or just the day(s) for all-day events. */
private fun eventWhen(context: android.content.Context, e: CalendarEvent): String {
    val base = android.text.format.DateUtils.FORMAT_SHOW_DATE or android.text.format.DateUtils.FORMAT_SHOW_WEEKDAY or
        android.text.format.DateUtils.FORMAT_ABBREV_ALL
    return if (e.allDay) {
        // All-day ends are exclusive midnights: show the last day the event covers.
        android.text.format.DateUtils.formatDateRange(context, e.begin, maxOf(e.begin, e.end - 1), base)
    } else {
        android.text.format.DateUtils.formatDateRange(context, e.begin, e.end, base or android.text.format.DateUtils.FORMAT_SHOW_TIME)
    }
}

@Composable
private fun AppIconRow(apps: List<AppEntry>, label: (AppEntry) -> String, onLaunch: (AppEntry, android.graphics.Rect?) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
        apps.forEach { e ->
            val coords = remember { arrayOfNulls<LayoutCoordinates>(1) }
            // Plain icons inside the frosted section card: glass inside glass mirrored the icons below.
            Box(
                Modifier.width(78.dp).onGloballyPositioned { coords[0] = it }
                    .clickable { onLaunch(e, coords[0]?.boundsInWindow()?.toAndroid()) },
            ) {
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    AppIconImage(e, 54.dp)
                    Spacer(Modifier.height(5.dp))
                    GlassText(label(e), style = GlassType.IconLabel, maxLines = 1)
                }
            }
        }
    }
}

@Composable
private fun AppResultRow(e: AppEntry, label: String, onLaunch: (AppEntry, android.graphics.Rect?) -> Unit) {
    val coords = remember { arrayOfNulls<LayoutCoordinates>(1) }
    Box(
        Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 2.dp).onGloballyPositioned { coords[0] = it }
            .clickable { onLaunch(e, coords[0]?.boundsInWindow()?.toAndroid()) },
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            AppIconImage(e, 36.dp)
            Spacer(Modifier.width(12.dp))
            GlassText(label, style = GlassType.Body, maxLines = 1)
        }
    }
}

private fun Rect.toAndroid() = android.graphics.Rect(left.toInt(), top.toInt(), right.toInt(), bottom.toInt())

/** One shared glass search control with clear, voice and close actions. */
@Composable
private fun SpotlightSearchField(
    query: String,
    onQuery: (String) -> Unit,
    focus: FocusRequester,
    onGo: () -> Unit,
    onVoice: () -> Unit,
    onClose: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        GlassTextField(query, onQuery, Modifier.weight(1f), placeholder = "Apps, files, contacts & more", focusRequester = focus,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go), keyboardActions = KeyboardActions(onGo = { onGo() }))
        if (query.isBlank()) GlassIconButton(com.shivam.glasslauncher.overlay.OverlayIcons.Mic,
            onClick = onVoice, size = 44.dp, contentDescription = "Voice search")
        GlassIconButton(GlassIcons.Close, onClick = onClose, size = 44.dp, contentDescription = "Close search")
    }
}
