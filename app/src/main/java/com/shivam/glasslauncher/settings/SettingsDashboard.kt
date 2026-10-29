package com.shivam.glasslauncher.settings

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shivam.glasslauncher.GlassApp
import com.shivam.glasslauncher.data.LauncherSettings
import com.shivam.glasslauncher.gestures.GlassGestureService
import com.shivam.glasslauncher.ui.glass.*
import com.shivam.glasslauncher.ui.home.AppIconImage

@Composable
internal fun SettingsDashboard(nav: SettingsNav, settings: LauncherSettings, wallpaper: WallpaperSource.Snapshot, resumeTick: Int) {
    val context = LocalContext.current
    val app = GlassApp.from(context)
    val focus = LocalFocusManager.current
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember(query) { SettingsCatalog.search(query) }
    val defaultHome = remember(resumeTick) { SystemChecks.isDefaultHome(context) }
    fun open(route: String) { focus.clearFocus(); nav.push(route) }

    ScreenScaffold("Settings", nav, subtitle = "A little more you. A lot more Glass.") {
        item("search") {
            GlassTextField(query, { query = it }, Modifier.fillMaxWidth(), placeholder = "Search settings",
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { results.firstOrNull()?.let { open(it.route) } }))
        }
        if (query.isNotBlank()) {
            item("results") {
                GlassListGroup(title = if (results.size == 1) "1 result" else "${results.size} results") {
                    if (results.isEmpty()) {
                        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            GlassIcon(GlassIcons.Search, size = 28.dp)
                            Spacer(Modifier.height(12.dp))
                            GlassText("No settings found", style = GlassType.Headline)
                            Spacer(Modifier.height(6.dp))
                            GlassText("Try “wallpaper”, “icons” or “gestures”.", style = GlassType.Callout, secondary = true)
                        }
                    }
                    results.forEach { entry ->
                        GlassRow(entry.title, subtitle = "${entry.section} · ${entry.summary}", icon = destinationIcon(entry.route), onClick = { open(entry.route) })
                    }
                }
            }
        } else {
            item("appearance") { AppearanceFeature(settings, wallpaper) { open("appearance") } }
            item("quick-actions") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    QuickTile("Wallpapers", "Set the mood", GlassIcons.Home, Modifier.weight(1f)) { open("wallpapers") }
                    QuickTile("Icon packs", "Make it yours", GlassIcons.Grid, Modifier.weight(1f)) { open("iconpack") }
                }
            }
            if (!defaultHome) {
                item("setup") {
                    GlassListGroup {
                        GlassRow("Make Glass your home", subtitle = "Finish setup for a seamless experience", icon = GlassIcons.Check,
                            iconTint = GlassColors.Green, onClick = { open("check") })
                    }
                }
            }
            item("everyday") {
                GlassListGroup(title = "Everyday") {
                    listOf("home", "drawer", "widgets", "homegestures").forEach { route -> DestinationRow(route, ::open) }
                }
            }
            item("personalise") {
                GlassListGroup(title = "The finer details") {
                    listOf("glass", "theme", "fonts", "iconstyle").forEach { route -> DestinationRow(route, ::open) }
                }
            }
            item("navigation") {
                GlassListGroup(title = "Navigation & shortcuts") {
                    listOf("gestures", "overlays", "pinned").forEach { route -> DestinationRow(route, ::open) }
                }
            }
            item("privacy") {
                GlassListGroup(title = "Privacy & care") {
                    GlassRow("Hidden apps", subtitle = if (settings.hidden.isEmpty()) "Choose what stays out of sight" else "${settings.hidden.size} hidden from Glass",
                        icon = GlassIcons.EyeOff, onClick = { open("hidden") })
                    GlassRow("Locked apps", subtitle = if (settings.locked.isEmpty()) "Extra protection when opening from Glass" else "${settings.locked.size} protected in Glass",
                        icon = GlassIcons.Lock, onClick = { open("locked") })
                    DestinationRow("backup", ::open)
                    DestinationRow("check", ::open)
                    DestinationRow("setup", ::open)
                    DestinationRow("about", ::open)
                }
            }
            item("signature") {
                Column(Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    val ink = LocalScreenContentColor.current
                    GlassIcon(GlassIcons.Sparkle, size = 22.dp, tint = ink.copy(alpha = 0.65f))
                    Spacer(Modifier.height(8.dp))
                    GlassText("Made for your everyday.", style = GlassType.Footnote, color = ink.copy(alpha = 0.7f))
                    GlassText("No ads. No analytics. Just Glass.", Modifier.padding(top = 4.dp), style = GlassType.Caption, color = ink.copy(alpha = 0.55f))
                }
            }
        }
    }
}

@Composable
private fun DestinationRow(route: String, open: (String) -> Unit) {
    val entry = SettingsCatalog.entries.first { it.route == route }
    GlassRow(entry.title, subtitle = entry.summary, icon = destinationIcon(route), onClick = { open(route) })
}

private fun destinationIcon(route: String): ImageVector = when (route) {
    "home", "wallpaper", "wallpapers" -> GlassIcons.Home
    "appearance", "iconstyle" -> GlassIcons.Sparkle
    "drawer", "widgets", "iconpack", "appicons" -> GlassIcons.Grid
    "glass", "theme" -> GlassIcons.Palette
    "fonts" -> GlassIcons.Pencil
    "homegestures", "gestures" -> GlassIcons.Hand
    "overlays" -> GlassIcons.Bell
    "hidden" -> GlassIcons.EyeOff
    "locked" -> GlassIcons.Lock
    "pinned" -> GlassIcons.Plus
    "check", "backup", "setup" -> GlassIcons.Check
    else -> GlassIcons.Info
}

@Composable
private fun QuickTile(title: String, subtitle: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    GlassSurface(modifier, shape = GlassShape.Card, variant = GlassVariant.Regular, kind = SurfaceKind.Cards, onClick = onClick) {
        Column(Modifier.padding(18.dp)) {
            GlassIcon(icon, size = 24.dp, tint = LocalGlassContentColor.current.copy(alpha = 0.85f))
            Spacer(Modifier.height(18.dp))
            GlassText(title, style = GlassType.Headline)
            GlassText(subtitle, Modifier.padding(top = 4.dp), style = GlassType.Footnote, secondary = true)
        }
    }
}

/** A compact, real wallpaper and icon preview; decorative content has no nested actions. */
@Composable
private fun AppearanceFeature(settings: LauncherSettings, wallpaper: WallpaperSource.Snapshot, onClick: () -> Unit) {
    val app = GlassApp.from(LocalContext.current)
    val apps by app.apps.apps.collectAsState()
    GlassSurface(Modifier.fillMaxWidth(), shape = GlassShape.rounded(28.dp), variant = GlassVariant.Regular,
        kind = SurfaceKind.Cards, onClick = onClick) {
        Row(Modifier.fillMaxWidth().padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            Column(Modifier.weight(1f)) {
                GlassText("YOUR SPACE", style = GlassType.Caption.copy(letterSpacing = 1.6.sp), secondary = true)
                Spacer(Modifier.height(12.dp))
                GlassText("Your home,\nyour way.", style = GlassType.Title.copy(fontSize = 27.sp, lineHeight = 32.sp), modifier = Modifier.semantics { heading() })
                Spacer(Modifier.height(10.dp))
                GlassText(settings.glass.preset.title, style = GlassType.Footnote, secondary = true)
                Spacer(Modifier.height(20.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GlassText("Appearance", style = GlassType.Subhead)
                    Spacer(Modifier.width(6.dp))
                    GlassIcon(GlassIcons.ChevronRight, size = 14.dp)
                }
            }
            Box(Modifier.width(108.dp).height(190.dp).clip(GlassShape.rounded(22.dp))) {
                val img = wallpaper.image
                if (img != null) Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Canvas(Modifier.fillMaxSize()) {
                    drawRect(Brush.linearGradient(listOf(Color(0xFF447D88), Color(0xFF636095), Color(0xFF252D46)), Offset.Zero, Offset(size.width, size.height)))
                }
                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.2f), Color.Transparent, Color.Black.copy(alpha = 0.35f)))))
                Column(Modifier.fillMaxSize().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Spacer(Modifier.height(16.dp))
                    GlassText("9:41", style = GlassType.Title.copy(fontWeight = FontWeight.Light, fontSize = 28.sp), color = Color.White)
                    GlassText("Your everyday, refined", style = GlassType.Caption.copy(fontSize = 6.sp), color = Color.White.copy(alpha = 0.85f))
                    Spacer(Modifier.weight(1f))
                    apps.take(6).chunked(3).forEach { row ->
                        Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
                            row.forEach { AppIconImage(it, 21.dp) }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Box(Modifier.width(30.dp).height(2.dp).background(Color.White.copy(alpha = 0.7f), GlassShape.Capsule))
                }
            }
        }
    }
}

@Composable
internal fun HomePreferencesScreen(nav: SettingsNav, settings: LauncherSettings, wallpaper: WallpaperSource.Snapshot, resumeTick: Int) {
    val context = LocalContext.current
    val app = GlassApp.from(context)
    val gesturesOn = remember(resumeTick) { GlassGestureService.isEnabled(context) }
    ScreenScaffold("Home screen", nav, subtitle = "Keep the things you love within reach.") {
        item {
            GlassListGroup(title = "Apps & notifications", footer = "Badges count notifications and need notification access. They may differ from an app’s unread count.") {
                GlassSwitchRow("App labels", settings.showLabels, { app.settings.setShowLabels(it) }, subtitle = "Show names below your home screen icons")
                GlassSwitchRow("Notification badges", settings.showBadges, { app.settings.setShowBadges(it) })
                GlassRow("Pinned shortcuts", icon = GlassIcons.Plus, onClick = { nav.push("pinned") })
            }
        }
        item {
            GlassListGroup(title = "Wallpaper & atmosphere") {
                GlassRow("Your wallpaper", subtitle = "Choose a photo or manage wallpaper access", icon = GlassIcons.Home, onClick = { nav.push("wallpaper") })
                GlassSwitchRow("Soft wallpaper blur", settings.homeBlur, { app.settings.setHomeBlur(it) }, subtitle = if (wallpaper.image != null) "Let your icons and widgets stand out" else "Set your wallpaper through Glass to enable the effect")
                GlassSwitchRow("Consistent dimming", settings.darkerPages, { app.settings.setDarkerPages(it) }, subtitle = "Keep every home page equally calm")
                GlassSwitchRow("True Tone", settings.trueTone, { app.settings.setTrueTone(it) }, subtitle = "Gently warm the screen in indoor light")
                if (settings.trueTone) GlassSliderRow("Warmth", settings.trueToneStrength, 0.2f..1f, { "${(it * 100).toInt()}%" }) { app.settings.setTrueToneStrength(it) }
                GlassSwitchRow("Wallpaper depth", settings.wallpaperParallax, { app.settings.setWallpaperParallax(it) }, subtitle = if (wallpaper.image != null) "A subtle shift as you tilt your phone" else "Set your wallpaper through Glass to enable the effect")
            }
        }
        item {
            GlassListGroup(title = "Movement & navigation") {
                GlassRow("Home gestures", subtitle = "Swipes, double tap & more", icon = GlassIcons.Hand, onClick = { nav.push("homegestures") })
                GlassSwitchRow("Loop home pages", settings.infiniteScroll, { app.settings.setInfiniteScroll(it) }, subtitle = "Continue from App Library to Today view")
                GlassSwitchRow("Immersive mode", settings.immersive, { app.settings.setImmersive(it) }, subtitle = if (gesturesOn) "Hide system bars for more room" else "Hides the status bar; enable Glass Gestures to also hide navigation")
            }
        }
    }
}
