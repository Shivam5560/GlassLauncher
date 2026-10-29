package com.shivam.glasslauncher.widgets

import com.shivam.glasslauncher.ui.glass.glassMenuPosition

import android.Manifest
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.shivam.glasslauncher.badges.BadgeStore
import com.shivam.glasslauncher.data.HomeLayout
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
import com.shivam.glasslauncher.ui.glass.GlassSegmented
import com.shivam.glasslauncher.ui.glass.GlassShape
import com.shivam.glasslauncher.ui.glass.GlassSurface
import com.shivam.glasslauncher.ui.glass.GlassSwitchRow
import com.shivam.glasslauncher.ui.glass.GlassText
import com.shivam.glasslauncher.ui.glass.GlassTextField
import com.shivam.glasslauncher.ui.glass.GlassType
import com.shivam.glasslauncher.ui.glass.GlassVariant
import com.shivam.glasslauncher.ui.glass.LocalGlassContentColor
import com.shivam.glasslauncher.ui.home.LauncherUiState
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/** All widget overlays, drawn in the launcher's overlay layer (they refract the whole home). */
@Composable
fun WidgetOverlays(widgets: WidgetUiState, launcher: LauncherUiState, layout: HomeLayout) {
    WidgetMenuOverlay(widgets, launcher)
    WidgetPickerSheet(widgets)
    PageManagerSheet(widgets, layout)
    WidgetEditSheet(widgets, layout)
}

/** Long-press menu on a widget: a Regular glass panel growing out of the widget. */
@Composable
private fun WidgetMenuOverlay(widgets: WidgetUiState, launcher: LauncherUiState) {
    val current = widgets.menu
    val last = com.shivam.glasslauncher.ui.home.rememberLast(current) ?: return
    var progress by remember { mutableFloatStateOf(0f) }
    if (current != null) BackHandler { widgets.menu = null }
    val inst = widgets.repo.instance(last.id)
    val close = { widgets.menu = null }

    Box(Modifier.fillMaxSize()) {
        GlassScrim(progress) { close() }
        Box(
            Modifier.glassMenuPosition(last.anchor, alignEnd = false),
        ) {
            GlassMorph(visible = current != null, origin = last.anchor, onProgress = { progress = it }) {
                com.shivam.glasslauncher.ui.glass.GlassMenuPanel {
                    if (inst != null && inst.kind != WidgetKind.SEARCH) {
                        MenuItem("Edit Widget", GlassIcons.Pencil) { close(); widgets.editing = inst.id }
                    }
                    if (inst != null && inst.kind != WidgetKind.APP_WIDGET && inst.kind.sizes.size > 1) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            inst.kind.sizes.forEach { s ->
                                GlassChip(s.title, selected = s == inst.size, onClick = { widgets.setSize(inst.id, s) })
                            }
                        }
                    }
                    if (last.onHome && inst != null) MenuItem("Resize & Move", GlassIcons.Grid) { close(); widgets.arranging = inst.id }
                    MenuItem("Add Widget", GlassIcons.Plus) { close(); widgets.picker = if (last.onHome) PickerTarget.Home(last.page) else PickerTarget.Today }
                    MenuItem(if (last.onHome) "Edit Home Screen" else "Edit Today View", GlassIcons.Grid) { close(); launcher.editMode = true }
                    com.shivam.glasslauncher.ui.glass.GlassMenuDivider()
                    MenuItem("Remove Widget", GlassIcons.Minus, destructive = true) { close(); widgets.remove(last.id) }
                }
            }
        }
    }
}

@Composable
internal fun MenuItem(text: String, icon: ImageVector?, destructive: Boolean = false, onClick: () -> Unit) =
    com.shivam.glasslauncher.ui.glass.GlassMenuRow(text, icon, destructive, onClick = onClick)

/** Per-widget settings on a glass sheet. */
@Composable
private fun WidgetEditSheet(widgets: WidgetUiState, layout: HomeLayout) {
    val state by widgets.repo.state.collectAsState()
    val id = widgets.editing
    val lastId = com.shivam.glasslauncher.ui.home.rememberLast(id)
    val inst = lastId?.let { state?.instances?.get(it) }
    GlassSheet(visible = id != null && inst != null, title = inst?.kind?.title ?: "Widget", onDismiss = { widgets.editing = null }, heightFraction = 0.75f) {
        if (inst == null) return@GlassSheet
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 14.dp, end = 14.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (inst.kind != WidgetKind.APP_WIDGET && inst.kind.sizes.size > 1) {
                item("size") {
                    GlassListGroup(title = "Size", footer = if (widgets.isOnHome(layout, inst.id)) "On the Home Screen the widget needs free cells to grow; icons move out of its way." else null) {
                        Box(Modifier.padding(12.dp)) {
                            GlassSegmented(inst.kind.sizes.map { it.title }, inst.kind.sizes.indexOf(inst.size).coerceAtLeast(0), { i -> widgets.setSize(inst.id, inst.kind.sizes[i]) })
                        }
                    }
                }
            }
            when (inst.kind) {
                WidgetKind.CHECKLIST -> item("checklist") { ChecklistSettings(widgets, inst) }
                WidgetKind.FOCUS, WidgetKind.QUICK_ACTIONS -> item("actions") {
                    GlassListGroup { GlassRow(inst.kind.description) }
                }
                WidgetKind.CLOCK -> clockSettings(widgets, inst)
                WidgetKind.WEATHER -> item("weather") { WeatherLocationSettings() }
                WidgetKind.PHOTOS -> item("photos") { PhotoSettings(widgets, inst) }
                WidgetKind.SMART_STACK -> item("stack") { StackSettings(widgets, inst) }
                WidgetKind.NOTES -> item("notes") {
                    GlassListGroup {
                        GlassRow("Clear note", icon = GlassIcons.Trash, iconTint = GlassColors.Red, onClick = { widgets.repo.setConfig(inst.id, WidgetKeys.NOTE_TEXT, "") })
                    }
                }
                WidgetKind.MUSIC -> item("music") { AccessRow() }
                WidgetKind.CALENDAR -> item("calendar") { CalendarSettings() }
                WidgetKind.BATTERY -> item("battery") { BluetoothSettings() }
                WidgetKind.APP_WIDGET -> item("app") { AppWidgetSettings(widgets, inst, layout) }
                WidgetKind.SEARCH, WidgetKind.GLANCE, WidgetKind.COMPASS -> Unit
                WidgetKind.ICON_GROUP -> item("group") { IconGroupSettings(widgets, inst) }
            }
            item("remove") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    GlassButton("Remove Widget", onClick = { widgets.remove(inst.id) }, destructive = true, icon = GlassIcons.Trash)
                }
            }
        }
    }
}

private fun LazyListScope.clockSettings(widgets: WidgetUiState, inst: WidgetInstance) {
    val styles = listOf(ClockStyles.DIGITAL, ClockStyles.ANALOG, ClockStyles.WORLD)
    val style = inst[WidgetKeys.CLOCK_STYLE] ?: ClockStyles.DIGITAL
    item("clockStyle") {
        GlassListGroup(title = "Style") {
            Box(Modifier.padding(12.dp)) {
                GlassSegmented(listOf("Digital", "Analog", "World"), styles.indexOf(style).coerceAtLeast(0), { i ->
                    widgets.repo.setConfig(inst.id, WidgetKeys.CLOCK_STYLE, styles[i])
                })
            }
        }
    }
    if (style == ClockStyles.WORLD) {
        item("cities") { WorldCities(widgets, inst) }
    }
}

@Composable
private fun WorldCities(widgets: WidgetUiState, inst: WidgetInstance) {
    val zones = inst.list(WidgetKeys.CLOCK_ZONES).ifEmpty { ClockStyles.defaultZones }
    var query by remember { mutableStateOf("") }
    fun save(list: List<String>) = widgets.repo.setConfig(inst.id, WidgetKeys.CLOCK_ZONES, list.joinToString(","))
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassListGroup(title = "Cities", footer = "Small shows the first two, Medium the first four.") {
            zones.forEachIndexed { i, z ->
                GlassRow(ClockStyles.cityName(z), subtitle = z.substringBefore('/').replace('_', ' '), showChevron = false) {
                    if (i > 0) GlassIconButton(WidgetGlyphs.ArrowUp, onClick = { save(zones.toMutableList().also { it.add(i - 1, it.removeAt(i)) }) }, size = 30.dp)
                    Spacer(Modifier.width(6.dp))
                    GlassIconButton(GlassIcons.Minus, onClick = { save(zones - z) }, size = 30.dp)
                }
            }
        }
        GlassTextField(query, { query = it }, placeholder = "Add a city", variant = GlassVariant.Clear)
        if (query.isNotBlank()) {
            val matches = remember(query) { ClockStyles.pickableZones.filter { ClockStyles.cityName(it).contains(query, true) || it.contains(query, true) }.take(8) }
            GlassListGroup {
                if (matches.isEmpty()) GlassRow("No matching city")
                matches.forEach { z ->
                    GlassRow(ClockStyles.cityName(z), subtitle = z, icon = GlassIcons.Plus, onClick = {
                        if (z !in zones) save(zones + z)
                        query = ""
                    })
                }
            }
        }
    }
}

/** Weather units and location: the phone's coarse location, or a city found with Open-Meteo geocoding. */
@Composable
fun WeatherLocationSettings() {
    val context = LocalContext.current
    val repo = remember { WeatherRepository.get(context) }
    val prefs by repo.prefs.weather.collectAsState()
    val weather by repo.state.collectAsState()
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<GeoPlace>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }
    val askLocation = rememberPermissionRequest(Manifest.permission.ACCESS_COARSE_LOCATION) { repo.useDeviceLocation() }
    LaunchedEffect(query) {
        if (query.isBlank()) { results = emptyList(); searchError = null; return@LaunchedEffect }
        delay(350)
        searching = true
        val r = repo.searchCities(query)
        searching = false
        results = r.getOrDefault(emptyList())
        searchError = if (r.isFailure) "Couldn't search — check your connection" else null
    }
    val p = prefs ?: return
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassListGroup(title = "Units") {
            Box(Modifier.padding(12.dp)) {
                GlassSegmented(listOf("°C", "°F"), if (p.fahrenheit) 1 else 0, { i -> repo.prefs.setFahrenheit(i == 1) })
            }
        }
        val updated = if (weather.fetchedAt > 0) "Updated " + android.text.format.DateUtils.getRelativeTimeSpanString(weather.fetchedAt) else "Not updated yet"
        GlassListGroup(
            title = "Location",
            footer = "Approximate (coarse) location is enough. Without it, search for a city. Weather refreshes every 45 minutes in the background, or when you tap the widget.",
        ) {
            GlassRow(
                "Use my location",
                subtitle = when {
                    !repo.hasLocationPermission() -> "Allow approximate location"
                    p.mode == WeatherLocationMode.DEVICE -> "On · ${p.placeName ?: "locating…"}"
                    else -> "Off"
                },
                icon = WidgetGlyphs.Pin,
                iconTint = if (p.mode == WeatherLocationMode.DEVICE && repo.hasLocationPermission()) GlassColors.Green else GlassColors.Accent,
                onClick = { if (repo.hasLocationPermission()) repo.useDeviceLocation() else askLocation() },
            )
            if (p.mode == WeatherLocationMode.CITY) {
                GlassRow("City: ${p.placeName ?: "—"}", icon = GlassIcons.Globe, iconTint = GlassColors.Accent)
            }
            GlassRow("Refresh now", subtitle = updated, icon = WidgetGlyphs.Refresh, iconTint = GlassColors.Orange, onClick = { repo.refreshIfStale(0) })
        }
        GlassTextField(query, { query = it }, placeholder = "Search for a city", variant = GlassVariant.Clear)
        if (query.isNotBlank()) {
            GlassListGroup(footer = searchError) {
                when {
                    searching && results.isEmpty() -> GlassRow("Searching…")
                    results.isEmpty() && searchError == null -> GlassRow("No matching city")
                }
                results.forEach { place ->
                    GlassRow(place.name, subtitle = place.subtitle, icon = WidgetGlyphs.Pin, onClick = {
                        repo.useCity(place)
                        query = ""
                    })
                }
            }
        }
    }
}

@Composable
private fun PhotoSettings(widgets: WidgetUiState, inst: WidgetInstance) {
    val context = LocalContext.current
    val pick = rememberPhotoPicker(inst.id) { n -> widgets.toast.show("$n photo${if (n == 1) "" else "s"} added") }
    val count = remember(inst[WidgetKeys.PHOTO_COUNT]) { PhotoStore.files(context, inst.id).size }
    GlassListGroup(title = "Photos", footer = "Photos are picked with the system Photo Picker; Glass gets no access to the rest of your library. Copies are kept inside Glass.") {
        GlassRow(if (count == 0) "Choose Photos" else "Replace Photos", subtitle = if (count == 0) null else "$count photo${if (count == 1) "" else "s"}", icon = WidgetGlyphs.Photo, onClick = pick)
    }
}

@Composable
private fun StackSettings(widgets: WidgetUiState, inst: WidgetInstance) {
    val state by widgets.repo.state.collectAsState()
    val children = inst.list(WidgetKeys.STACK_CHILDREN).mapNotNull { state?.instances?.get(it) }
    val kinds = children.map { it.kind }
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        GlassListGroup(footer = "Turns to the calendar in the morning, weather during the day, music in the evening and batteries at night. A swipe stays until the next part of the day.") {
            GlassSwitchRow("Smart Rotate", inst[WidgetKeys.STACK_ROTATE] != "0", { on -> widgets.repo.setConfig(inst.id, WidgetKeys.STACK_ROTATE, if (on) "1" else "0") })
        }
        GlassListGroup(title = "Widgets in this stack") {
            WidgetKind.stackable.forEach { k ->
                GlassSwitchRow(k.title, k in kinds, { on ->
                    val next = if (on) kinds + k else kinds - k
                    widgets.repo.setStackKinds(inst.id, WidgetKind.stackable.filter { it in next })
                }, icon = WidgetGlyphs.kind(k), iconTint = GlassColors.Accent)
            }
        }
    }
}

@Composable
private fun AccessRow() {
    val context = LocalContext.current
    val granted = remember { BadgeStore.isAccessGranted(context) }
    GlassListGroup(footer = "Music reads the active media session. Android only allows that with notification access. Album art and seeking appear only if the player app provides them.") {
        GlassRow(
            "Notification access",
            subtitle = if (granted) "On" else "Off — tap to allow",
            icon = GlassIcons.Bell,
            iconTint = if (granted) GlassColors.Green else GlassColors.Red,
            onClick = { openNotificationAccess(context) },
        )
    }
}

@Composable
private fun CalendarSettings() {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(CalendarRepository.hasPermission(context)) }
    val ask = rememberPermissionRequest(Manifest.permission.READ_CALENDAR) { granted = true }
    GlassListGroup(footer = "Shows events from every calendar synced to this phone. Tap an event to open it.") {
        GlassRow("Calendar access", subtitle = if (granted) "Allowed" else "Tap to allow", icon = WidgetGlyphs.Calendar, iconTint = if (granted) GlassColors.Green else GlassColors.Red, onClick = if (granted) null else ask)
        GlassRow("Open Calendar", icon = GlassIcons.ChevronRight, onClick = { CalendarRepository.openCalendar(context) })
    }
}

@Composable
private fun BluetoothSettings() {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(BatteryRepository.hasBluetoothPermission(context)) }
    val ask = rememberPermissionRequest(Manifest.permission.BLUETOOTH_CONNECT) { granted = true }
    GlassListGroup(footer = "Bluetooth levels need Nearby devices permission. Many earbuds don't report a level; those are hidden.") {
        GlassRow("Nearby devices", subtitle = if (granted) "Allowed" else "Tap to allow", icon = WidgetGlyphs.Earbuds, iconTint = if (granted) GlassColors.Green else GlassColors.Red, onClick = if (granted) null else ask)
    }
}

@Composable
private fun AppWidgetSettings(widgets: WidgetUiState, inst: WidgetInstance, layout: HomeLayout) {
    val context = LocalContext.current
    val info = remember(inst.appWidgetId) { GlassWidgetHost.get(context).info(inst.appWidgetId) }
    val onHome = widgets.isOnHome(layout, inst.id)
    val rows = inst[WidgetKeys.TODAY_ROWS]?.toIntOrNull() ?: 2
    GlassListGroup(footer = if (onHome) "Resize on the Home Screen with the handles in edit mode." else "Height in the Today view.") {
        GlassRow(info?.loadLabel(context.packageManager) ?: "Widget", subtitle = info?.provider?.packageName)
        if (info != null && GlassWidgetHost.isReconfigurable(info)) {
            GlassRow("Reconfigure", icon = WidgetGlyphs.Sliders, onClick = { widgets.reconfigure(inst) })
        }
        if (!onHome) {
            GlassRow("Height", subtitle = "$rows row${if (rows == 1) "" else "s"}", showChevron = false) {
                GlassIconButton(GlassIcons.Minus, onClick = { widgets.repo.setConfig(inst.id, WidgetKeys.TODAY_ROWS, (rows - 1).coerceAtLeast(1).toString()) }, size = 30.dp)
                Spacer(Modifier.width(6.dp))
                GlassIconButton(GlassIcons.Plus, onClick = { widgets.repo.setConfig(inst.id, WidgetKeys.TODAY_ROWS, (rows + 1).coerceAtMost(6).toString()) }, size = 30.dp)
            }
        }
    }
}
