package com.shivam.glasslauncher.settings

import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shivam.glasslauncher.GlassApp
import com.shivam.glasslauncher.backup.BackupCodec
import com.shivam.glasslauncher.backup.BackupData
import com.shivam.glasslauncher.backup.BackupManager
import com.shivam.glasslauncher.backup.BackupSummary
import com.shivam.glasslauncher.icons.IconOverride
import com.shivam.glasslauncher.icons.IconPack
import com.shivam.glasslauncher.icons.IconPackInfo
import com.shivam.glasslauncher.polish.Fonts
import com.shivam.glasslauncher.polish.GlassWallpaper
import com.shivam.glasslauncher.polish.IconStyle
import com.shivam.glasslauncher.polish.PaletteExtractor
import com.shivam.glasslauncher.polish.ThemeSource
import com.shivam.glasslauncher.shortcuts.PinnedShortcut
import com.shivam.glasslauncher.ui.glass.Backdrop
import com.shivam.glasslauncher.ui.glass.GlassButton
import com.shivam.glasslauncher.ui.glass.GlassColors
import com.shivam.glasslauncher.ui.glass.GlassIcon
import com.shivam.glasslauncher.ui.glass.GlassIconButton
import com.shivam.glasslauncher.ui.glass.GlassIcons
import com.shivam.glasslauncher.ui.glass.GlassListGroup
import com.shivam.glasslauncher.ui.glass.GlassRow
import com.shivam.glasslauncher.ui.glass.GlassSegmented
import com.shivam.glasslauncher.ui.glass.GlassShape
import com.shivam.glasslauncher.ui.glass.GlassSliderRow
import com.shivam.glasslauncher.ui.glass.GlassSurface
import com.shivam.glasslauncher.ui.glass.GlassSwitch
import com.shivam.glasslauncher.ui.glass.GlassSwitchRow
import com.shivam.glasslauncher.ui.glass.GlassText
import com.shivam.glasslauncher.ui.glass.GlassTextField
import com.shivam.glasslauncher.ui.glass.GlassToastState
import com.shivam.glasslauncher.ui.glass.GlassType
import com.shivam.glasslauncher.ui.glass.GlassVariant
import com.shivam.glasslauncher.ui.glass.LocalBackdrop
import com.shivam.glasslauncher.ui.glass.recordBackdrop
import com.shivam.glasslauncher.ui.home.AppIconImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.DateFormat
import java.util.Date

/** Screens owned by Phase 4 (wallpapers, theme, fonts, icons, pinned shortcuts, backup). */
fun isPolishScreen(screen: String) =
    screen in setOf("polish", "wallpapers", "theme", "fonts", "iconstyle", "appicons", "pinned", "backup") ||
        screen.startsWith("wallpaper:") || screen.startsWith("editicon:")

@Composable
fun PolishScreen(nav: SettingsNav, toast: GlassToastState) {
    val s = nav.current
    when {
        s == "polish" -> PolishHub(nav)
        s == "wallpapers" -> WallpaperGalleryScreen(nav)
        s.startsWith("wallpaper:") -> WallpaperDetailScreen(nav, s.removePrefix("wallpaper:"), toast)
        s == "theme" -> ThemeScreen(nav)
        s == "fonts" -> FontScreen(nav)
        s == "iconstyle" -> IconStyleScreen(nav)
        s == "appicons" -> AppIconsScreen(nav)
        s.startsWith("editicon:") -> IconEditScreen(nav, s.removePrefix("editicon:"), toast)
        s == "pinned" -> PinnedShortcutsScreen(nav, toast)
        s == "backup" -> BackupScreen(nav, toast)
    }
}

private val swatchTints = listOf(
    0xFF0A84FF, 0xFF5E5CE6, 0xFFBF5AF2, 0xFFFF375F, 0xFFFF453A, 0xFFFF9F0A, 0xFFFFD60A, 0xFF30D158, 0xFF64D2FF, 0xFFAC8E68,
).map { it.toInt() }

@Composable
private fun PolishHub(nav: SettingsNav) {
    val app = GlassApp.from(LocalContext.current)
    val p = app.polish.settings.state.collectAsState().value ?: return
    val settings = app.settings.state.collectAsState().value ?: return
    val pinned by app.polish.shortcuts.shortcuts.collectAsState()
    ScreenScaffold("Customise", nav) {
        item {
            GlassListGroup(title = "Look") {
                GlassRow("Wallpapers", subtitle = app.polish.wallpapers.byId(p.wallpaperId)?.name ?: "12 Glass wallpapers, light and dark", icon = GlassIcons.Home, iconTint = Color(0xFF64D2FF), onClick = { nav.push("wallpapers") })
                GlassRow("Theme", subtitle = when (p.themeSource) { ThemeSource.Default -> "Default"; ThemeSource.Wallpaper -> "From wallpaper"; ThemeSource.Manual -> "Custom colours" }, icon = GlassIcons.Palette, iconTint = GlassColors.Accent, onClick = { nav.push("theme") })
                GlassRow("Font", subtitle = Fonts.entry(p.fontId).name, icon = GlassIcons.Pencil, iconTint = Color(0xFFFF9F0A), onClick = { nav.push("fonts") })
                GlassRow("Icon style", subtitle = "${p.iconStyle.title} · ${settings.iconPack.ifEmpty { "System icons" }}", icon = GlassIcons.Grid, iconTint = Color(0xFF30D158), onClick = { nav.push("iconstyle") })
            }
        }
        item {
            GlassListGroup(title = "Home", footer = "Long-press any app and choose Edit Icon to give it a different icon.") {
                GlassRow("Pinned shortcuts", subtitle = "${pinned.size} pinned", icon = GlassIcons.Plus, iconTint = Color(0xFF5E5CE6), onClick = { nav.push("pinned") })
            }
        }
        item {
            GlassListGroup(title = "Data") {
                GlassRow("Backup & restore", subtitle = "Layout, settings, hidden and locked apps, custom icons", icon = GlassIcons.Check, iconTint = Color(0xFF8E8E93), onClick = { nav.push("backup") })
            }
        }
    }
}

// ---------------------------------------------------------------------------------- wallpapers

@Composable
private fun WallpaperThumb(w: GlassWallpaper, mode: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    val app = GlassApp.from(LocalContext.current)
    val thumb by produceState<ImageBitmap?>(null, w.id, mode) {
        value = withContext(Dispatchers.IO) { app.polish.wallpapers.loadThumb(w.id, mode)?.asImageBitmap() }
    }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        GlassSurface(
            Modifier.fillMaxWidth().aspectRatio(0.45f),
            shape = GlassShape.rounded(18.dp),
            variant = GlassVariant.Clear,
            highlighted = selected,
            onClick = onClick,
        ) {
            thumb?.let { Image(it, w.name, Modifier.fillMaxSize().padding(3.dp).clip(GlassShape.rounded(15.dp)), contentScale = ContentScale.Crop) }
        }
        Spacer(Modifier.height(6.dp))
        GlassText(w.name, style = GlassType.Subhead, color = Color.White, maxLines = 1)
    }
}

@Composable
private fun WallpaperGalleryScreen(nav: SettingsNav) {
    val app = GlassApp.from(LocalContext.current)
    val p = app.polish.settings.state.collectAsState().value ?: return
    val night by app.polish.night.collectAsState()
    val all = app.polish.wallpapers.all
    val categories = listOf("All") + app.polish.wallpapers.categories
    var category by remember { mutableStateOf(0) }
    var showDark by remember { mutableStateOf(night) }
    val list = if (category == 0) all else all.filter { it.category == categories[category] }
    ScreenScaffold("Wallpapers", nav) {
        item {
            GlassListGroup(footer = "Original Glass wallpapers. Setting one through Glass keeps a copy, so glass panels refract it (Path A). With Auto, Glass swaps the light and dark versions with the system theme.") {
                Column(Modifier.padding(12.dp)) {
                    GlassSegmented(categories, category, { category = it })
                    Spacer(Modifier.height(10.dp))
                    GlassSegmented(listOf("Light", "Dark"), if (showDark) 1 else 0, { showDark = it == 1 })
                }
            }
        }
        list.chunked(3).forEachIndexed { row, chunk ->
            item("wp-row-$row-$category") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    chunk.forEach { w ->
                        WallpaperThumb(w, if (showDark) "dark" else "light", w.id == p.wallpaperId, Modifier.weight(1f)) { nav.push("wallpaper:${w.id}") }
                    }
                    repeat(3 - chunk.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

@Composable
private fun WallpaperDetailScreen(nav: SettingsNav, id: String, toast: GlassToastState) {
    val context = LocalContext.current
    val app = GlassApp.from(context)
    val w = app.polish.wallpapers.byId(id) ?: return
    val p = app.polish.settings.state.collectAsState().value ?: return
    val night by app.polish.night.collectAsState()
    val scope = rememberCoroutineScope()
    var dark by remember { mutableStateOf(night) }
    var auto by remember { mutableStateOf(p.wallpaperAutoDark) }
    var lockToo by remember { mutableStateOf(p.wallpaperLockToo) }
    var busy by remember { mutableStateOf(false) }
    val mode = if (dark) "dark" else "light"
    val preview by produceState<ImageBitmap?>(null, id, mode) {
        value = withContext(Dispatchers.IO) {
            try {
                context.assets.open("wallpapers/${id}_$mode.webp").use { s ->
                    BitmapFactory.decodeStream(s, null, BitmapFactory.Options().apply { inSampleSize = 2 })?.asImageBitmap()
                }
            } catch (e: Exception) { null }
        }
    }
    ScreenScaffold(w.name, nav) {
        item { WallpaperPreview(preview) }
        item {
            GlassListGroup(title = w.category) {
                Column(Modifier.padding(12.dp)) {
                    GlassSegmented(listOf("Light", "Dark"), if (dark) 1 else 0, { dark = it == 1 })
                }
                GlassSwitchRow("Auto light/dark", auto, { auto = it }, subtitle = "Follow the system theme")
                GlassSwitchRow("Also set lock screen", lockToo, { lockToo = it })
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                GlassButton(if (busy) "Setting…" else "Set", enabled = !busy, prominent = true, variant = GlassVariant.Regular, onClick = {
                    busy = true
                    app.polish.settings.setWallpaperAutoDark(auto)
                    val target = if (auto) (if (night) "dark" else "light") else mode
                    scope.launch {
                        val r = app.polish.applyWallpaper(id, target, lockToo)
                        busy = false
                        toast.show(if (r.isSuccess) "${w.name} set — refraction enabled" else "Couldn't set wallpaper: ${r.exceptionOrNull()?.message}")
                    }
                })
            }
        }
    }
}

/** The wallpaper with a small glass panel on top, so the refraction can be judged before setting it. */
@Composable
private fun WallpaperPreview(image: ImageBitmap?) {
    val layer = rememberGraphicsLayer()
    val origin = remember { mutableStateOf(Offset.Zero) }
    val snapshot = GlassApp.from(LocalContext.current).wallpaper.snapshot.collectAsState().value
    val backdrop = remember(snapshot) { Backdrop(listOf(layer), snapshot) { origin.value } }
    Box(Modifier.fillMaxWidth().height(420.dp).onGloballyPositioned { origin.value = it.positionInWindow() }, contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxSize().clip(GlassShape.rounded(28.dp)).recordBackdrop(layer)) {
            image?.let { Image(it, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }
        }
        CompositionLocalProvider(LocalBackdrop provides backdrop) {
            GlassSurface(Modifier.fillMaxWidth(0.7f).height(120.dp), shape = GlassShape.Panel, role = null) {
                Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    GlassText("Liquid Glass", style = GlassType.Headline)
                    GlassText("Panels bend the wallpaper at their rim.", style = GlassType.Footnote, secondary = true)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------- theme

@Composable
private fun Swatch(color: Color, selected: Boolean, onClick: () -> Unit) {
    GlassSurface(Modifier.size(34.dp), shape = GlassShape.Circle, variant = GlassVariant.Clear, tintOverride = color, highlighted = selected, onClick = onClick, contentAlignment = Alignment.Center) {
        if (selected) GlassIcon(GlassIcons.Check, size = 16.dp, tint = Color.White)
    }
}

@Composable
private fun ThemeScreen(nav: SettingsNav) {
    val app = GlassApp.from(LocalContext.current)
    val p = app.polish.settings.state.collectAsState().value ?: return
    val palette by app.polish.wallpaperPalette.collectAsState()
    // Local drafts so sliders track the finger; every change is saved straight away.
    var accentHsl by remember { mutableStateOf(PaletteExtractor.toHsl(p.manualAccent)) }
    var tintHsl by remember { mutableStateOf(PaletteExtractor.toHsl(p.manualTint)) }
    fun saveAccent(h: FloatArray) { accentHsl = h; app.polish.settings.setManualAccent(PaletteExtractor.fromHsl(h[0], h[1], h[2])); app.polish.settings.setThemeSource(ThemeSource.Manual) }
    fun saveTint(h: FloatArray) { tintHsl = h; app.polish.settings.setManualTint(PaletteExtractor.fromHsl(h[0], h[1], h[2])); app.polish.settings.setThemeSource(ThemeSource.Manual) }
    ScreenScaffold("Theme", nav) {
        item {
            GlassListGroup(footer = "From wallpaper picks an accent and a glass tint from your wallpaper's colours, and updates them when the wallpaper changes.") {
                Column(Modifier.padding(12.dp)) {
                    GlassSegmented(ThemeSource.entries.map { it.title }, p.themeSource.ordinal, { app.polish.settings.setThemeSource(ThemeSource.entries[it]) })
                }
            }
        }
        item {
            GlassListGroup(title = "Wallpaper colours") {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Swatch(Color(palette.accent), p.themeSource == ThemeSource.Wallpaper) { app.polish.settings.setThemeSource(ThemeSource.Wallpaper) }
                    Spacer(Modifier.width(10.dp))
                    Swatch(Color(palette.glassTint), false) { app.polish.settings.setThemeSource(ThemeSource.Wallpaper) }
                    Spacer(Modifier.width(14.dp))
                    GlassText(if (palette.isDark) "Accent and tint from a dark wallpaper" else "Accent and tint from a light wallpaper", style = GlassType.Footnote, secondary = true)
                }
            }
        }
        item {
            GlassListGroup(title = "Theme editor", footer = "Changing a colour switches the theme to Custom.") {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                    GlassText("Accent", style = GlassType.Callout)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        swatchTints.take(8).forEach { c ->
                            Swatch(Color(c), p.themeSource == ThemeSource.Manual && p.manualAccent == c) { saveAccent(PaletteExtractor.toHsl(c)) }
                        }
                    }
                }
                GlassSliderRow("Accent hue", accentHsl[0], 0f..359f, { "${it.toInt()}°" }) { h ->
                    saveAccent(floatArrayOf(h, accentHsl[1].coerceAtLeast(0.6f), accentHsl[2].coerceIn(0.45f, 0.62f)))
                }
                GlassSliderRow("Glass tint hue", tintHsl[0], 0f..359f, { "${it.toInt()}°" }) { h ->
                    saveTint(floatArrayOf(h, tintHsl[1].coerceAtLeast(0.2f), tintHsl[2]))
                }
                GlassSliderRow("Glass tint lightness", tintHsl[2], 0.05f..0.92f, { "${(it * 100).toInt()}%" }) { l ->
                    saveTint(floatArrayOf(tintHsl[0], tintHsl[1], l))
                }
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    GlassButton("Preview", onClick = {}, prominent = true)
                    var on by remember { mutableStateOf(true) }
                    GlassSwitch(on, { on = it })
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------- fonts

@Composable
private fun FontScreen(nav: SettingsNav) {
    val context = LocalContext.current
    val app = GlassApp.from(context)
    val p = app.polish.settings.state.collectAsState().value ?: return
    val downloads by produceState<Boolean?>(null) { value = withContext(Dispatchers.IO) { Fonts.downloadsAvailable(context) } }
    ScreenScaffold("Font", nav) {
        item {
            GlassListGroup(title = "Bundled", footer = "Open-licensed fonts (SIL OFL 1.1) that ship with Glass and always work offline.") {
                Fonts.bundled.forEach { f ->
                    val family = remember(f.id) { Fonts.family(context, f.id) }
                    GlassRow(f.name, subtitle = f.licence, onClick = { app.polish.settings.setFont(f.id) }, showChevron = false) {
                        GlassText("Aa", style = TextStyle(fontFamily = family, fontSize = 22.sp))
                        Spacer(Modifier.width(10.dp))
                        if (p.fontId == f.id) GlassIcon(GlassIcons.Check, size = 18.dp, tint = GlassColors.Accent)
                    }
                }
            }
        }
        item {
            val ok = downloads == true
            GlassListGroup(
                title = "Google Fonts",
                footer = when (downloads) {
                    null -> "Checking Google Play services…"
                    true -> "Downloaded once by Google Play services and shared with other apps. Text shows in Inter until a font has downloaded, and stays in Inter if it can't."
                    false -> "Google Play services isn't available on this phone, so only the bundled fonts can be used."
                },
            ) {
                Fonts.downloadable.forEach { f ->
                    GlassRow(f.name, subtitle = if (ok) null else "Needs Google Play services", onClick = if (ok) ({ app.polish.settings.setFont(f.id) }) else null, showChevron = false) {
                        if (p.fontId == f.id) GlassIcon(GlassIcons.Check, size = 18.dp, tint = GlassColors.Accent)
                    }
                }
            }
        }
        item {
            GlassListGroup(title = "Preview") {
                Column(Modifier.padding(16.dp)) {
                    GlassText("The quick brown fox", style = GlassType.Title)
                    GlassText("jumps over the lazy dog. 0123456789", style = GlassType.Body, secondary = true)
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------- icons

@Composable
private fun IconStyleScreen(nav: SettingsNav) {
    val context = LocalContext.current
    val app = GlassApp.from(context)
    val p = app.polish.settings.state.collectAsState().value ?: return
    val settings = app.settings.state.collectAsState().value ?: return
    val apps by app.apps.apps.collectAsState()
    val packs by produceState<List<IconPackInfo>?>(null) { value = withContext(Dispatchers.IO) { IconPack.installed(context) } }
    ScreenScaffold("Icon style", nav) {
        item {
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                apps.filterNot { it.key in settings.hidden }.take(5).forEach { AppIconImage(it, 56.dp) }
            }
        }
        item {
            GlassListGroup(footer = "Light, Dark and Tinted use the matching artwork from packs that provide it. Auto follows the system dark mode. Other packs show their normal icons.") {
                Column(Modifier.padding(12.dp)) {
                    GlassSegmented(IconStyle.entries.map { it.title }, p.iconStyle.ordinal, { app.polish.settings.setIconStyle(IconStyle.entries[it]) })
                }
                if (p.iconStyle == IconStyle.Tinted) {
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        GlassText("Tint colour", style = GlassType.Callout)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Swatch(GlassColors.Accent, p.tintedIconColor == 0) { app.polish.settings.setTintedIconColor(0) }
                            listOf(0xFFFFD60A, 0xFFFFFFFF, 0xFF64D2FF, 0xFF30D158, 0xFFFF9F0A, 0xFFBF5AF2).map { it.toInt() }.forEach { c ->
                                Swatch(Color(c), p.tintedIconColor == c) { app.polish.settings.setTintedIconColor(c) }
                            }
                        }
                    }
                }
            }
        }
        item {
            GlassListGroup(title = "Icon pack") {
                GlassRow("Icon pack", subtitle = packs?.firstOrNull { it.packageName == settings.iconPack }?.label ?: settings.iconPack.ifEmpty { "System icons" }, onClick = { nav.push("iconpack") })
                GlassRow("Individual app icons", subtitle = "Fix a missing icon or choose different artwork for one app", icon = GlassIcons.Pencil, onClick = { nav.push("appicons") })
            }
        }
    }
}

/** Searchable entry point for repairing individual icons without changing the whole pack. */
@Composable
private fun AppIconsScreen(nav: SettingsNav) {
    val app = GlassApp.from(LocalContext.current)
    val apps by app.apps.apps.collectAsState()
    val overrides by app.polish.iconOverrides.overrides.collectAsState()
    var query by remember { mutableStateOf("") }
    val entries = remember(apps, query) {
        val needle = query.filter { it.isLetterOrDigit() }.lowercase()
        apps.filter {
            it.label.filter { c -> c.isLetterOrDigit() }.lowercase().contains(needle) ||
                it.packageName.filter { c -> c.isLetterOrDigit() }.lowercase().contains(needle)
        }.sortedBy { it.label.lowercase() }
    }
    ScreenScaffold("Individual app icons", nav) {
        item {
            GlassTextField(query, { query = it }, placeholder = "Search apps", variant = GlassVariant.Clear)
        }
        item {
            GlassText("Choose an app, then select artwork from an installed pack or Photos. Apps missing from a pack keep their own logo.",
                Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = GlassType.Footnote, color = com.shivam.glasslauncher.ui.glass.LocalScreenContentColor.current)
        }
        if (entries.isEmpty()) item { GlassText("No apps match", Modifier.padding(16.dp), color = com.shivam.glasslauncher.ui.glass.LocalScreenContentColor.current) }
        items(entries, key = { it.key }) { entry ->
            GlassListGroup {
                GlassRow(entry.label, subtitle = if (entry.key in overrides) "Custom icon" else entry.packageName,
                    onClick = { nav.push("editicon:${entry.key}") }) {
                    AppIconImage(entry, 36.dp)
                }
            }
        }
    }
}

/** Long-press → Edit Icon: pick any installed pack's icon, a photo, or go back to the default. */
@Composable
private fun IconEditScreen(nav: SettingsNav, appKey: String, toast: GlassToastState) {
    val context = LocalContext.current
    val app = GlassApp.from(context)
    val apps by app.apps.apps.collectAsState()
    val entry = apps.firstOrNull { it.key == appKey }
    val overrides by app.polish.iconOverrides.overrides.collectAsState()
    val scope = rememberCoroutineScope()
    val packs by produceState<List<IconPackInfo>?>(null) { value = withContext(Dispatchers.IO) { IconPack.installed(context) } }
    var openPack by remember { mutableStateOf<String?>(null) }
    var query by remember { mutableStateOf("") }
    val photo = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val name = withContext(Dispatchers.IO) {
                runCatching {
                    val bmp = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { d, info, _ ->
                        d.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                        val s = (512f / minOf(info.size.width, info.size.height)).coerceAtMost(1f)
                        d.setTargetSize((info.size.width * s).toInt().coerceAtLeast(1), (info.size.height * s).toInt().coerceAtLeast(1))
                    }
                    app.polish.iconOverrides.saveCustom(bmp)
                }.getOrNull()
            }
            if (name != null) {
                app.polish.iconOverrides.set(appKey, IconOverride.Custom(name)); toast.show("Icon updated")
            } else {
                toast.show("Couldn't read that picture")
            }
        }
    }
    val label = entry?.label ?: appKey
    ScreenScaffold("Edit icon", nav) {
        item {
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                if (entry != null) AppIconImage(entry, 84.dp)
                Spacer(Modifier.height(8.dp))
                GlassText(label, style = GlassType.Headline, color = Color.White)
            }
        }
        item {
            GlassListGroup {
                GlassRow("Default icon", subtitle = "Use the icon pack or the app's own icon", onClick = { app.polish.iconOverrides.set(appKey, null); toast.show("Icon reset") }, showChevron = false) {
                    if (overrides[appKey] == null) GlassIcon(GlassIcons.Check, size = 18.dp, tint = GlassColors.Accent)
                }
                GlassRow("Choose from Photos", icon = GlassIcons.Plus, onClick = { photo.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                    if (overrides[appKey] is IconOverride.Custom) GlassIcon(GlassIcons.Check, size = 18.dp, tint = GlassColors.Accent)
                }
            }
        }
        item {
            GlassListGroup(title = "From an icon pack", footer = if (packs?.isEmpty() == true) "No icon packs installed. Install an ADW/Nova-compatible pack, or choose a photo above." else null) {
                packs?.forEach { pack ->
                    GlassRow(pack.label, subtitle = if (openPack == pack.packageName) "Tap an icon to use it" else null, onClick = { openPack = if (openPack == pack.packageName) null else pack.packageName; query = "" }, showChevron = false) {
                        GlassIcon(if (openPack == pack.packageName) GlassIcons.Minus else GlassIcons.Plus, size = 18.dp)
                    }
                }
            }
        }
        val pkg = openPack
        if (pkg != null && entry != null) {
            item("pack-search") {
                GlassTextField(query, { query = it }, placeholder = "Search icons", variant = GlassVariant.Clear)
            }
            item("pack-grid-$pkg") {
                val names by produceState<List<String>?>(null, pkg) {
                    value = withContext(Dispatchers.IO) {
                        val pack = app.icons.loadPack(pkg)
                        val all = pack?.allDrawableNames().orEmpty()
                        // The pack's own icon for this app first.
                        val mapped = pack?.drawableNameFor(entry.component)
                        if (mapped != null) listOf(mapped) + (all - mapped) else all
                    }
                }
                val list = names
                if (list == null) {
                    GlassText("Loading icons…", Modifier.padding(16.dp), color = Color.White)
                } else {
                    val filtered = if (query.isBlank()) list else list.filter { it.contains(query.trim().replace(' ', '_'), ignoreCase = true) }
                    PackIconGrid(pkg, filtered.take(400), (overrides[appKey] as? IconOverride.Pack)?.takeIf { it.packageName == pkg }?.drawable) { name ->
                        app.polish.iconOverrides.set(appKey, IconOverride.Pack(pkg, name))
                        toast.show("Icon updated")
                    }
                }
            }
        }
    }
}

@Composable
private fun PackIconGrid(pkg: String, names: List<String>, selected: String?, onPick: (String) -> Unit) {
    val app = GlassApp.from(LocalContext.current)
    GlassSurface(Modifier.fillMaxWidth(), shape = GlassShape.Card, role = null) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (names.isEmpty()) GlassText("No icons match", Modifier.padding(8.dp), style = GlassType.Callout, secondary = true)
            names.chunked(5).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { name ->
                        val bmp by produceState<ImageBitmap?>(null, pkg, name) { value = withContext(Dispatchers.IO) { app.icons.renderPackIcon(pkg, name, 144) } }
                        GlassSurface(Modifier.weight(1f).aspectRatio(1f), shape = GlassShape.rounded(14.dp), variant = GlassVariant.Clear, highlighted = name == selected, onClick = { onPick(name) }, contentAlignment = Alignment.Center) {
                            bmp?.let { Image(it, name, Modifier.fillMaxSize().padding(6.dp)) }
                        }
                    }
                    repeat(5 - row.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------------- pinned shortcuts

@Composable
private fun PinnedShortcutsScreen(nav: SettingsNav, toast: GlassToastState) {
    val app = GlassApp.from(LocalContext.current)
    val list by app.polish.shortcuts.shortcuts.collectAsState()
    ScreenScaffold("Pinned shortcuts", nav) {
        item {
            GlassListGroup(footer = "Shortcuts other apps pinned to Glass (for example a website from your browser or a contact). Pinning, opening and removing only work while Glass is your default Home app.") {
                if (list.isEmpty()) GlassRow("No pinned shortcuts yet", subtitle = "Use “Add to Home screen” in another app")
            }
        }
        items(list, key = { "${it.packageName}/${it.id}/${it.userSerial}" }) { item -> PinnedRow(item, toast) }
    }
}

@Composable
private fun PinnedRow(item: PinnedShortcut, toast: GlassToastState) {
    val context = LocalContext.current
    val app = GlassApp.from(context)
    val icon by produceState<ImageBitmap?>(null, item) {
        value = withContext(Dispatchers.IO) {
            app.polish.shortcuts.info(item)?.let { info ->
                val la = context.getSystemService(android.content.pm.LauncherApps::class.java)
                app.icons.renderShortcut(la.getShortcutIconDrawable(info, context.resources.displayMetrics.densityDpi), 108)
            }
        }
    }
    GlassSurface(Modifier.fillMaxWidth(), shape = GlassShape.Row, role = null, onClick = {
        if (!app.polish.shortcuts.start(item, null)) toast.show("Couldn't open it — is Glass your default Home app?")
    }) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(36.dp)) { icon?.let { Image(it, null, Modifier.fillMaxSize()) } }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                GlassText(item.label, style = GlassType.Body, maxLines = 1)
                GlassText(item.packageName, style = GlassType.Footnote, secondary = true, maxLines = 1)
            }
            GlassIconButton(GlassIcons.Trash, onClick = { app.polish.shortcuts.remove(item); toast.show("Removed") }, size = 34.dp)
        }
    }
}

// ---------------------------------------------------------------------------------- backup

@Composable
private fun BackupScreen(nav: SettingsNav, toast: GlassToastState) {
    val context = LocalContext.current
    val app = GlassApp.from(context)
    val manager = remember { BackupManager(app) }
    val scope = rememberCoroutineScope()
    var busy by remember { mutableStateOf(false) }
    var pending by remember { mutableStateOf<Pair<BackupData, BackupSummary>?>(null) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val r = runCatching { manager.write(uri, manager.create()) }
            busy = false
            toast.show(if (r.isSuccess) "Backup saved" else "Couldn't save: ${r.exceptionOrNull()?.message}")
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val r = runCatching { manager.read(uri) }
            busy = false
            r.onSuccess { pending = it to BackupCodec.summarize(it) }
                .onFailure { toast.show(it.message ?: "Not a Glass backup") }
        }
    }
    ScreenScaffold("Backup & restore", nav) {
        item {
            GlassListGroup(footer = "One file with your Home Screen layout, all Glass settings, hidden and locked apps, per-app icons and pinned shortcuts. Saved wherever you choose (Files, Drive, a USB stick). It isn't encrypted, so keep it somewhere private. Wallpaper pictures aren't included.") {
                GlassRow(if (busy) "Working…" else "Export backup", icon = GlassIcons.Check, iconTint = GlassColors.Green, onClick = if (busy) null else {
                    { export.launch("glass-backup-${java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.US).format(Date())}.json") }
                })
                GlassRow("Restore from file", icon = GlassIcons.Plus, iconTint = GlassColors.Orange, onClick = if (busy) null else {
                    { importer.launch(arrayOf("application/json", "text/plain", "application/octet-stream")) }
                })
            }
        }
        val p = pending
        if (p != null) {
            item("preview") {
                val s = p.second
                GlassSurface(Modifier.fillMaxWidth(), shape = GlassShape.Card, role = null) {
                    Column(Modifier.padding(16.dp)) {
                        GlassText("Restore this backup?", style = GlassType.Headline)
                        GlassText("Made ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(s.createdAt))} · Glass ${s.appVersion}", style = GlassType.Footnote, secondary = true)
                        Spacer(Modifier.height(10.dp))
                        listOf(
                            "Home Screen" to "${s.homeApps} apps, ${s.folders} folders",
                            "Settings" to "${s.settingsCount} values",
                            "Icon pack" to (s.iconPack ?: "System icons"),
                            "Hidden apps" to "${s.hiddenApps}",
                            "Locked apps" to "${s.lockedApps}",
                            "Per-app icons" to "${s.iconOverrides} (${s.customIcons} pictures)",
                            "Pinned shortcuts" to "${s.pinnedShortcuts}",
                        ).forEach { (k, v) ->
                            Row(Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                                GlassText(k, Modifier.weight(1f), style = GlassType.Callout)
                                GlassText(v, style = GlassType.Callout, secondary = true)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        GlassText("This replaces your current layout and settings.", style = GlassType.Footnote, color = GlassColors.Orange)
                        Spacer(Modifier.height(12.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            GlassButton("Cancel", onClick = { pending = null })
                            GlassButton("Restore", destructive = true, enabled = !busy, onClick = {
                                busy = true
                                scope.launch {
                                    val r = runCatching { manager.restore(p.first) }
                                    busy = false
                                    pending = null
                                    toast.show(if (r.isSuccess) "Restored" else "Restore failed: ${r.exceptionOrNull()?.message}")
                                }
                            })
                        }
                    }
                }
            }
        }
    }
}
