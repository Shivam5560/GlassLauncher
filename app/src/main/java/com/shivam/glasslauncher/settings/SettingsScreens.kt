package com.shivam.glasslauncher.settings

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.shivam.glasslauncher.GlassApp
import com.shivam.glasslauncher.data.AppEntry
import com.shivam.glasslauncher.data.LauncherSettings
import com.shivam.glasslauncher.gestures.GlassGestureService
import com.shivam.glasslauncher.icons.IconPack
import com.shivam.glasslauncher.icons.IconPackInfo
import com.shivam.glasslauncher.overlay.OverlayIcons
import com.shivam.glasslauncher.overlay.OverlayScreens
import com.shivam.glasslauncher.overlay.OverlaySettingsScreen
import com.shivam.glasslauncher.overlay.overlaySummary
import com.shivam.glasslauncher.ui.glass.Backdrop
import com.shivam.glasslauncher.ui.glass.GlassButton
import com.shivam.glasslauncher.ui.glass.GlassColors
import com.shivam.glasslauncher.ui.glass.GlassIcon
import com.shivam.glasslauncher.ui.glass.GlassIcons
import com.shivam.glasslauncher.ui.glass.GlassListGroup
import com.shivam.glasslauncher.ui.glass.GlassRow
import com.shivam.glasslauncher.ui.glass.GlassSegmented
import com.shivam.glasslauncher.ui.glass.GlassShape
import com.shivam.glasslauncher.ui.glass.GlassSliderRow
import com.shivam.glasslauncher.ui.glass.GlassStyle
import com.shivam.glasslauncher.ui.glass.GlassSurface
import com.shivam.glasslauncher.ui.glass.GlassSwitch
import com.shivam.glasslauncher.ui.glass.GlassSwitchRow
import com.shivam.glasslauncher.ui.glass.GlassText
import com.shivam.glasslauncher.ui.glass.GlassTextField
import com.shivam.glasslauncher.ui.glass.GlassTone
import com.shivam.glasslauncher.ui.glass.GlassToastState
import com.shivam.glasslauncher.ui.glass.GlassType
import com.shivam.glasslauncher.ui.glass.GlassVariant
import com.shivam.glasslauncher.ui.glass.LiquidGlassShader
import com.shivam.glasslauncher.ui.glass.LocalBackdrop
import com.shivam.glasslauncher.ui.glass.LocalGlassStyle
import com.shivam.glasslauncher.ui.glass.WallpaperPath
import com.shivam.glasslauncher.ui.glass.WallpaperSource
import com.shivam.glasslauncher.ui.glass.recordBackdrop
import com.shivam.glasslauncher.ui.home.AppIconImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun SettingsScreen(
    nav: SettingsNav,
    settings: LauncherSettings,
    wallpaper: WallpaperSource.Snapshot,
    resumeTick: Int,
    toast: GlassToastState,
    onFinish: () -> Unit,
) {
    val app = GlassApp.from(LocalContext.current)
    if (isPolishScreen(nav.current)) {
        PolishScreen(nav, toast)
        return
    }
    if (isAppearanceScreen(nav.current)) {
        AppearanceScreen(nav, settings)
        return
    }
    if (isHomeGestureScreen(nav.current)) {
        HomeGestureScreen(nav, settings, resumeTick)
        return
    }
    when (nav.current) {
        "glass" -> GlassTuningScreen(nav, settings)
        "wallpaper" -> WallpaperScreen(nav, wallpaper, toast)
        "iconpack" -> IconPackScreen(nav, settings)
        "gestures" -> GesturesScreen(nav, settings, resumeTick)
        "excluded" -> AppToggleScreen(nav, "Gesture exclusions", "Back-swipe edges are switched off in these apps (the home swipe stays). Use it for games and apps with their own edge swipes.", byPackage = true, isOn = { it.packageName in settings.gestures.excludedApps }) { e, on ->
            val set = settings.gestures.excludedApps.let { if (on) it + e.packageName else it - e.packageName }
            app.settings.setGestures(settings.gestures.copy(excludedApps = set))
        }
        "hidden" -> AppToggleScreen(nav, "Hidden apps", "Hidden from the Home Screen, App Library and Spotlight only. They are still listed in system Settings and can still be opened from notifications.", byPackage = false, isOn = { it.key in settings.hidden }) { e, on ->
            app.settings.setHidden(e.key, on)
            if (on) app.layout.update { com.shivam.glasslauncher.data.LayoutOps.removeKey(it, e.key) }
            else app.layout.update { com.shivam.glasslauncher.data.LayoutOps.addToHome(it, e.key) }
        }
        "locked" -> AppToggleScreen(nav, "Locked apps", "Glass asks for your fingerprint, face or screen lock before opening these — only when opened from Glass. Opening them from notifications or Recents bypasses the lock.", byPackage = false, isOn = { it.key in settings.locked }) { e, on ->
            app.settings.setLocked(e.key, on)
        }
        "check" -> SystemCheckScreen(nav, settings, wallpaper, resumeTick, setupMode = false, onFinish = onFinish)
        "setup" -> SystemCheckScreen(nav, settings, wallpaper, resumeTick, setupMode = true, onFinish = onFinish)
        "about" -> AboutScreen(nav)
        "widgets" -> com.shivam.glasslauncher.widgets.WidgetsSettingsScreen(nav, resumeTick, toast)
        in OverlayScreens.ALL -> OverlaySettingsScreen(nav, nav.current, resumeTick, toast)
        else -> MainScreen(nav, settings, wallpaper, resumeTick)
    }
}

@Composable
private fun MainScreen(nav: SettingsNav, settings: LauncherSettings, wallpaper: WallpaperSource.Snapshot, resumeTick: Int) {
    val context = LocalContext.current
    val app = GlassApp.from(context)
    val issues = remember(resumeTick, settings, wallpaper) {
        SystemChecks.all(context, settings, wallpaper).count { it.status == CheckStatus.Off }
    }
    val gesturesOn = remember(resumeTick) { GlassGestureService.isEnabled(context) }
    ScreenScaffold("Glass", nav) {
        item {
            GlassListGroup(title = "Setup") {
                GlassRow(
                    "System check",
                    subtitle = if (issues == 0) "Everything Phase 1 needs is set up" else "$issues setting${if (issues > 1) "s" else ""} need attention",
                    icon = GlassIcons.Check,
                    iconTint = if (issues == 0) GlassColors.Green else GlassColors.Red,
                    onClick = { nav.push("check") },
                )
                GlassRow("HyperOS setup guide", icon = GlassIcons.Sparkle, iconTint = GlassColors.Orange, onClick = { nav.push("setup") })
            }
        }
        item {
            GlassListGroup(title = "Appearance") {
                GlassRow("Appearance", subtitle = "Theme, colours per surface, icons, labels, widgets, folders, animation", icon = GlassIcons.Sparkle, iconTint = Color(0xFFBF5AF2), onClick = { nav.push("appearance") })
                GlassRow("App drawer", subtitle = "Categories, launch view, transition, sorting", icon = GlassIcons.Grid, iconTint = Color(0xFF30B0C7), onClick = { nav.push("drawer") })
                GlassRow("Glass tuning", subtitle = "Variant, blur, tint, refraction, rim, lighting", icon = GlassIcons.Palette, iconTint = Color(0xFF5E5CE6), onClick = { nav.push("glass") })
                GlassRow("Wallpaper", subtitle = wallpaper.path.title, icon = GlassIcons.Home, iconTint = Color(0xFF64D2FF), onClick = { nav.push("wallpaper") })
                GlassRow("Icon pack", subtitle = settings.iconPack.ifEmpty { "System icons" }, icon = GlassIcons.Grid, iconTint = GlassColors.Orange, onClick = { nav.push("iconpack") })
                GlassRow("Customise", subtitle = "Glass wallpapers, theme, font, icon style, backup", icon = GlassIcons.Sparkle, iconTint = Color(0xFFFF375F), onClick = { nav.push("polish") })
            }
        }
        item {
            GlassListGroup(title = "Home & Search", footer = "Badges count notifications, not each app's own unread number, and need notification access.") {
                GlassSwitchRow("Show app labels", settings.showLabels, { app.settings.setShowLabels(it) })
                GlassSwitchRow("Notification badges", settings.showBadges, { app.settings.setShowBadges(it) })
                GlassRow("Home gestures", subtitle = "Swipes, double tap, two fingers, pinch, rotate", icon = GlassIcons.Hand, iconTint = GlassColors.Accent, onClick = { nav.push("homegestures") })
                GlassSwitchRow("Infinite scroll", settings.infiniteScroll, { app.settings.setInfiniteScroll(it) }, subtitle = "Pages loop: after the App Library comes the Today view")
                GlassSwitchRow("Blur wallpaper on Home", settings.homeBlur, { app.settings.setHomeBlur(it) }, subtitle = if (wallpaper.image != null) "A soft blur behind the home pages" else "Needs the wallpaper set through Glass (Wallpaper)")
                GlassSwitchRow("Balanced dim on all pages", settings.darkerPages, { app.settings.setDarkerPages(it) }, subtitle = "Every home page gets the same premium dim; Today always has it")
                GlassSwitchRow("True Tone", settings.trueTone, { app.settings.setTrueTone(it) }, subtitle = "Warms Glass's screens in indoor light, using the light sensor. Daylight: no change.")
                if (settings.trueTone) GlassSliderRow("True Tone strength", settings.trueToneStrength, 0.2f..1f, { "${(it * 100).toInt()}%" }) { app.settings.setTrueToneStrength(it) }
                GlassSwitchRow("Wallpaper 3D effect", settings.wallpaperParallax, { app.settings.setWallpaperParallax(it) }, subtitle = if (wallpaper.image != null) "The wallpaper moves as you tilt the phone" else "Needs the wallpaper set through Glass (Wallpaper)")
                GlassSwitchRow("Ultra-immersive", settings.immersive, { app.settings.setImmersive(it) }, subtitle = if (gesturesOn) "Hide the navigation bar on Home and in the drawer" else "Needs Glass Gestures to hide the navigation bar")
            }
        }
        item {
            GlassListGroup(title = "Widgets") {
                GlassRow("Widgets & Today view", subtitle = "Weather location, units and widget access", icon = com.shivam.glasslauncher.widgets.WidgetGlyphs.Widgets, iconTint = Color(0xFF30B0C7), onClick = { nav.push("widgets") })
            }
        }
        item {
            GlassListGroup(title = "Navigation") {
                GlassRow("Gestures", subtitle = if (gesturesOn) "On" else "Off", icon = GlassIcons.Hand, iconTint = GlassColors.Accent, onClick = { nav.push("gestures") })
                GlassRow("Island & Centers", subtitle = overlaySummary(context), icon = OverlayIcons.Island, iconTint = Color(0xFF1C1C1E), onClick = { nav.push(OverlayScreens.ROOT) })
            }
        }
        item {
            GlassListGroup(title = "Privacy") {
                GlassRow("Hidden apps", subtitle = "${settings.hidden.size} hidden", icon = GlassIcons.EyeOff, iconTint = Color(0xFF8E8E93), onClick = { nav.push("hidden") })
                GlassRow("Locked apps", subtitle = "${settings.locked.size} locked", icon = GlassIcons.Lock, iconTint = GlassColors.Red, onClick = { nav.push("locked") })
            }
        }
        item {
            GlassListGroup {
                GlassRow("About", icon = GlassIcons.Info, iconTint = Color(0xFF8E8E93), onClick = { nav.push("about") })
            }
        }
    }
}

/** Live Glass tuning with a preview that refracts a colourful test pattern. */
@Composable
private fun GlassTuningScreen(nav: SettingsNav, settings: LauncherSettings) {
    val app = GlassApp.from(LocalContext.current)
    var draft by remember { mutableStateOf(settings.glass) }
    LaunchedEffect(draft) {
        delay(120)
        if (draft != settings.glass) app.settings.setGlass(draft)
    }
    fun update(f: (GlassStyle) -> GlassStyle) { draft = f(draft) }
    val shaderOk = remember { LiquidGlassShader.createEffectShader() != null }

    CompositionLocalProvider(LocalGlassStyle provides draft) {
        ScreenScaffold("Glass tuning", nav) {
            item { GlassPreview() }
            item {
                GlassListGroup(title = "Material", footer = if (shaderOk) "Regular is frosted. Clear is more transparent with stronger lensing; small controls always use Clear." else "This phone can't run the lens shader, so glass is frosted only (blur, tint and rim).") {
                    Column(Modifier.padding(12.dp)) {
                        GlassSegmented(listOf("Regular", "Clear"), draft.variant.ordinal, { i -> update { it.copy(variant = GlassVariant.entries[i]) } })
                        Spacer(Modifier.height(12.dp))
                        GlassText("Tone", style = GlassType.Subhead, secondary = true)
                        Spacer(Modifier.height(6.dp))
                        GlassSegmented(listOf("Auto", "Light", "Dark"), draft.tone.ordinal, { i -> update { it.copy(tone = GlassTone.entries[i]) } })
                    }
                }
            }
            item {
                GlassListGroup(title = "Frost") {
                    GlassSliderRow("Blur radius", draft.blurRadius, 0f..60f, { "${it.toInt()} dp" }) { v -> update { it.copy(blurRadius = v) } }
                    GlassSliderRow("Tint opacity", draft.tintOpacity, 0f..0.85f, { "${(it * 100).toInt()}%" }) { v -> update { it.copy(tintOpacity = v) } }
                    TintPicker(draft.tintColor) { c -> update { it.copy(tintColor = c) } }
                }
            }
            item {
                GlassListGroup(title = "Liquid lens") {
                    GlassSliderRow("Refraction", draft.refraction, 0f..1f, { "${(it * 100).toInt()}%" }) { v -> update { it.copy(refraction = v) } }
                    GlassSliderRow("Rim width", draft.rimWidth, 4f..28f, { "${it.toInt()} dp" }) { v -> update { it.copy(rimWidth = v) } }
                    GlassSliderRow("Chromatic aberration", draft.chromaticAberration, 0f..1f, { "${(it * 100).toInt()}%" }) { v -> update { it.copy(chromaticAberration = v) } }
                    GlassSliderRow("Edge highlight", draft.specular, 0f..1f, { "${(it * 100).toInt()}%" }) { v -> update { it.copy(specular = v) } }
                }
            }
            item {
                GlassListGroup(title = "Effects", footer = "Tilt lighting uses the motion sensors only while Glass is on screen.") {
                    GlassSwitchRow("Tilt-following highlights", draft.tiltLighting, { v -> update { it.copy(tiltLighting = v) } })
                    GlassSwitchRow("Refract the wallpaper", draft.wallpaperBackdrop, { v -> update { it.copy(wallpaperBackdrop = v) } }, subtitle = "Needs a wallpaper set through Glass or All files access")
                    GlassSwitchRow("Liquid lens shader", draft.liquidEffects, { v -> update { it.copy(liquidEffects = v) } }, subtitle = "Off = frosted glass only (blur + tint + rim). Applies after reopening.")
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    GlassButton("Reset to defaults", onClick = { draft = GlassStyle() }, variant = GlassVariant.Regular)
                }
            }
        }
    }
}

@Composable
private fun TintPicker(selected: Int, onSelect: (Int) -> Unit) {
    val swatches = listOf(
        0xFF1E2028.toInt() to "Graphite",
        0xFF101A33.toInt() to "Midnight",
        0xFF0F3A4A.toInt() to "Ocean",
        0xFF3A1730.toInt() to "Plum",
        0xFF1D3A24.toInt() to "Forest",
        0xFFE9ECF2.toInt() to "Snow",
    )
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        GlassText("Tint colour", style = GlassType.Callout)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            swatches.forEach { (c, _) ->
                GlassSurface(
                    Modifier.size(36.dp),
                    shape = GlassShape.Circle,
                    variant = GlassVariant.Clear,
                    tintOverride = Color(c),
                    highlighted = c == selected,
                    onClick = { onSelect(c) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (c == selected) GlassIcon(GlassIcons.Check, size = 16.dp, tint = if (c == 0xFFE9ECF2.toInt()) Color.Black else Color.White)
                }
            }
        }
    }
}

/** A test card: stripes behind a Regular panel, a Clear capsule and a switch, so lensing is visible. */
@Composable
private fun GlassPreview() {
    val layer = rememberGraphicsLayer()
    val origin = remember { mutableStateOf(Offset.Zero) }
    val wallpaper = GlassApp.from(LocalContext.current).wallpaper.snapshot.collectAsState().value
    val backdrop = remember(wallpaper) { Backdrop(listOf(layer), wallpaper) { origin.value } }
    var on by remember { mutableStateOf(true) }
    Box(
        Modifier
            .fillMaxWidth()
            .height(220.dp)
            .onGloballyPositioned { origin.value = it.positionInWindow() },
    ) {
        Canvas(Modifier.fillMaxSize().recordBackdrop(layer)) {
            val stripe = 26.dp.toPx()
            val colors = listOf(Color(0xFFFF453A), Color(0xFFFF9F0A), Color(0xFFFFD60A), Color(0xFF30D158), Color(0xFF64D2FF), Color(0xFF0A84FF), Color(0xFFBF5AF2))
            var x = -size.height
            var i = 0
            while (x < size.width + size.height) {
                drawLine(colors[i % colors.size], Offset(x, size.height), Offset(x + size.height, 0f), strokeWidth = stripe * 0.55f)
                x += stripe
                i++
            }
            drawRect(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f))))
        }
        CompositionLocalProvider(LocalBackdrop provides backdrop) {
            GlassSurface(Modifier.align(Alignment.Center).fillMaxWidth(0.8f).height(130.dp), shape = GlassShape.Panel, role = null) {
                Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.SpaceBetween) {
                    GlassText("Liquid Glass", style = GlassType.Headline)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        GlassButton("Button", onClick = {})
                        Spacer(Modifier.weight(1f))
                        GlassSwitch(on, { on = it })
                    }
                }
            }
        }
    }
}

/** Path A wallpaper picker (Photo Picker: no storage permission) plus Path B / C status. */
@Composable
private fun WallpaperScreen(nav: SettingsNav, wallpaper: WallpaperSource.Snapshot, toast: GlassToastState) {
    val context = LocalContext.current
    val activity = context as Activity
    val app = GlassApp.from(context)
    val scope = rememberCoroutineScope()
    var lockToo by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            val r = withContext(Dispatchers.IO) { app.wallpaper.setWallpaper(uri, lockToo) }
            busy = false
            toast.show(if (r.isSuccess) "Wallpaper set — refraction enabled" else "Couldn't set wallpaper: ${r.exceptionOrNull()?.message}")
        }
    }
    ScreenScaffold("Wallpaper", nav) {
        item {
            GlassListGroup(title = "Refraction source", footer = wallpaper.path.detail + if (wallpaper.isLive) " Live wallpapers can't be refracted." else "") {
                GlassRow(wallpaper.path.title, icon = if (wallpaper.path == WallpaperPath.None) GlassIcons.Info else GlassIcons.Check, iconTint = if (wallpaper.path == WallpaperPath.None) GlassColors.Orange else GlassColors.Green)
            }
        }
        item {
            GlassListGroup(title = "Path A · recommended", footer = "Pick any photo. Glass sets it as your wallpaper and keeps a copy so panels can bend it like real glass. Glass never captures your screen.") {
                GlassSwitchRow("Also set lock screen", lockToo, { lockToo = it })
                GlassRow(if (busy) "Setting wallpaper…" else "Choose from Photos", icon = GlassIcons.Plus, onClick = if (busy) null else {
                    { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }
                })
            }
        }
        item {
            GlassListGroup(title = "Path B", footer = "Android 14+ only lets apps read the current wallpaper with All files access. Glass uses it for nothing else.") {
                GlassRow("Grant All files access", subtitle = if (app.wallpaper.canReadWallpaper()) "Granted" else "Not granted", icon = GlassIcons.Lock, onClick = {
                    if (!SystemChecks.launchFirst(activity, SystemChecks.allFilesIntents(context))) toast.show("Not available on this phone")
                })
            }
        }
    }
}

@Composable
private fun IconPackScreen(nav: SettingsNav, settings: LauncherSettings) {
    val context = LocalContext.current
    val app = GlassApp.from(context)
    val packs by produceState<List<IconPackInfo>?>(null) { value = withContext(Dispatchers.IO) { IconPack.installed(context) } }
    ScreenScaffold("Icon pack", nav) {
        item {
            GlassListGroup(footer = "Removes the colour from every app icon.") {
                GlassSwitchRow("Greyscale icons", settings.greyscaleIcons, { app.settings.setGreyscaleIcons(it) })
            }
        }
        item {
            GlassListGroup(footer = "Packs using the standard ADW/Nova format are detected. Icons a pack doesn't cover are masked to the squircle with the pack's back/mask.") {
                GlassRow("System icons", onClick = { app.settings.setIconPack("") }, showChevron = false) {
                    if (settings.iconPack.isEmpty()) GlassIcon(GlassIcons.Check, size = 18.dp, tint = GlassColors.Accent)
                }
                val list = packs
                when {
                    list == null -> GlassRow("Looking for icon packs…")
                    list.isEmpty() -> GlassRow("No icon packs installed", subtitle = "Install one from the Play Store, then come back.")
                    else -> list.forEach { p ->
                        GlassRow(p.label, subtitle = p.packageName, onClick = { app.settings.setIconPack(p.packageName) }, showChevron = false) {
                            if (settings.iconPack == p.packageName) GlassIcon(GlassIcons.Check, size = 18.dp, tint = GlassColors.Accent)
                        }
                    }
                }
            }
        }
        item {
            GlassListGroup(footer = "For an app a pack doesn't cover, choose its icon from a pack or Photos without changing the other apps.") {
                GlassRow("Individual app icons", icon = GlassIcons.Pencil, onClick = { nav.push("appicons") })
            }
        }
    }
}

@Composable
private fun GesturesScreen(nav: SettingsNav, settings: LauncherSettings, resumeTick: Int) {
    val context = LocalContext.current
    val activity = context as Activity
    val app = GlassApp.from(context)
    val enabled = remember(resumeTick) { GlassGestureService.isEnabled(context) }
    var g by remember { mutableStateOf(settings.gestures) }
    LaunchedEffect(g) {
        delay(150)
        if (g != settings.gestures) app.settings.setGestures(g)
    }
    ScreenScaffold("Gestures", nav) {
        item {
            GlassListGroup(footer = "Swipe up from the bottom for Home, swipe up and hold for Recents (opens the HyperOS recents screen), swipe in from a side for Back. There is no app-shrinks-into-icon animation: Android doesn't allow it for third-party launchers.") {
                GlassRow(
                    "Glass Gestures service",
                    subtitle = if (enabled) "On" else "Off — allow restricted settings first, then turn it on",
                    icon = GlassIcons.Hand,
                    iconTint = if (enabled) GlassColors.Green else GlassColors.Red,
                    onClick = { SystemChecks.launchFirst(activity, SystemChecks.accessibilityIntents(context)) },
                )
            }
        }
        item {
            GlassListGroup(title = "Zones") {
                GlassSwitchRow("Swipe up from bottom", g.bottomEnabled, { g = g.copy(bottomEnabled = it) })
                GlassSwitchRow("Back from left edge", g.leftEnabled, { g = g.copy(leftEnabled = it) })
                GlassSwitchRow("Back from right edge", g.rightEnabled, { g = g.copy(rightEnabled = it) })
                GlassSwitchRow("Home indicator pill", g.showPill, { g = g.copy(showPill = it) })
                GlassSwitchRow("Pause while keyboard is up", g.pauseWithKeyboard, { g = g.copy(pauseWithKeyboard = it) })
                GlassSwitchRow("Haptics", g.haptics, { g = g.copy(haptics = it) })
                GlassRow("Excluded apps", subtitle = "${g.excludedApps.size} apps", onClick = { nav.push("excluded") })
            }
        }
        item {
            GlassListGroup(title = "Size & sensitivity") {
                GlassSliderRow("Bottom zone height", g.bottomZoneHeight, 10f..48f, { "${it.toInt()} dp" }) { g = g.copy(bottomZoneHeight = it) }
                GlassSliderRow("Bottom zone width", g.bottomZoneWidth, 0.3f..1f, { "${(it * 100).toInt()}%" }) { g = g.copy(bottomZoneWidth = it) }
                GlassSliderRow("Edge zone width", g.edgeZoneWidth, 8f..40f, { "${it.toInt()} dp" }) { g = g.copy(edgeZoneWidth = it) }
                GlassSliderRow("Edge zone height", g.edgeZoneHeight, 0.3f..0.95f, { "${(it * 100).toInt()}%" }) { g = g.copy(edgeZoneHeight = it) }
                GlassSliderRow("Sensitivity", g.sensitivity, 0.5f..2f, { "%.1fx".format(it) }) { g = g.copy(sensitivity = it) }
                GlassSliderRow("Hold for Recents", g.holdForRecentsMs.toFloat(), 150f..800f, { "${it.toInt()} ms" }) { g = g.copy(holdForRecentsMs = it.toInt()) }
            }
        }
    }
}

/** A searchable list of apps with a glass switch each (hidden, locked, gesture exclusions). */
@Composable
private fun AppToggleScreen(
    nav: SettingsNav,
    title: String,
    footer: String,
    byPackage: Boolean,
    isOn: (AppEntry) -> Boolean,
    onToggle: (AppEntry, Boolean) -> Unit,
) {
    val context = LocalContext.current
    val apps by GlassApp.from(context).apps.apps.collectAsState()
    var query by remember { mutableStateOf("") }
    val list = remember(apps, query, byPackage) {
        val base = if (byPackage) apps.distinctBy { it.packageName } else apps
        if (query.isBlank()) base else base.filter { it.label.contains(query, ignoreCase = true) }
    }
    ScreenScaffold(title, nav) {
        item {
            GlassListGroup(footer = footer) {
                Box(Modifier.padding(10.dp)) {
                    GlassTextField(query, { query = it }, placeholder = "Search apps", variant = GlassVariant.Clear)
                }
            }
        }
        // One glass card per block of apps (plain rows with dividers inside), not a slab per app.
        items(list.chunked(12), key = { chunk -> chunk.first().key }) { chunk ->
            GlassListGroup {
                chunk.forEach { e ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        AppIconImage(e, 36.dp)
                        Spacer(Modifier.width(12.dp))
                        GlassText(e.label, Modifier.weight(1f), style = GlassType.Body, maxLines = 1)
                        GlassSwitch(isOn(e), { onToggle(e, it) })
                    }
                }
            }
        }
    }
}

/**
 * Settings → System check (and, with [setupMode], the first-run HyperOS guide): every permission
 * or HyperOS setting Phase 1 needs, its status, and a button that fixes it.
 */
@Composable
private fun SystemCheckScreen(
    nav: SettingsNav,
    settings: LauncherSettings,
    wallpaper: WallpaperSource.Snapshot,
    resumeTick: Int,
    setupMode: Boolean,
    onFinish: () -> Unit,
) {
    val context = LocalContext.current
    val activity = context as Activity
    val app = GlassApp.from(context)
    var permTick by remember { mutableStateOf(0) }
    val permissions = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permTick++ }
    val checks = remember(resumeTick, settings, wallpaper, permTick) { SystemChecks.all(context, settings, wallpaper) }
    val order = if (setupMode) listOf("home", "restricted", "gestures", "badges", "autostart", "battery", "wallpaper") else null
    val shown = if (order != null) order.mapNotNull { id -> checks.firstOrNull { it.id == id } } else checks

    ScreenScaffold(if (setupMode) "Set up Glass" else "System check", nav) {
        if (setupMode) {
            item {
                GlassListGroup(footer = "HyperOS forces 3-button navigation whenever ANY third-party launcher is the default Home app (Smart Launcher and every other launcher too) — that is HyperOS, not Glass, and there is no setting to undo it. Glass Gestures (step 3) is the fix: swipe up for Home, swipe up and hold for Recents, swipe in from a side for Back. The other steps keep Glass running. Do them in order.") {
                    GlassRow("Welcome to Glass", subtitle = "About two minutes of setup", icon = GlassIcons.Sparkle, iconTint = GlassColors.Accent)
                }
            }
        } else {
            val off = checks.count { it.status == CheckStatus.Off }
            val warn = checks.count { it.status == CheckStatus.Warning }
            item {
                GlassListGroup {
                    GlassRow(
                        when {
                            off > 0 -> "$off required setting${if (off > 1) "s are" else " is"} off"
                            warn > 0 -> "Working, with $warn caveat${if (warn > 1) "s" else ""}"
                            else -> "All set"
                        },
                        icon = if (off > 0) GlassIcons.Info else GlassIcons.Check,
                        iconTint = when {
                            off > 0 -> GlassColors.Red
                            warn > 0 -> GlassColors.Orange
                            else -> GlassColors.Green
                        },
                    )
                }
            }
        }
        shown.forEachIndexed { index, c ->
            item(c.id) {
                CheckCard(
                    c,
                    step = if (setupMode) index + 1 else null,
                    onFix = {
                        when (val f = c.fix) {
                            is CheckFix.OpenIntents -> if (!SystemChecks.launchFirst(activity, f.intents)) {
                                SystemChecks.launchFirst(activity, listOf(android.content.Intent(android.provider.Settings.ACTION_SETTINGS)))
                            }
                            is CheckFix.RequestPermissions -> permissions.launch(f.permissions)
                            is CheckFix.InApp -> nav.push(f.screen)
                            CheckFix.None -> Unit
                        }
                    },
                    onConfirm = when (c.id) {
                        "autostart" -> ({ app.settings.setAutostartConfirmed(true) })
                        "restricted" -> ({ app.settings.setRestrictedConfirmed(true) })
                        else -> null
                    },
                )
            }
        }
        if (setupMode) {
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    GlassButton("Done", onClick = { app.settings.setOnboardingDone(true); onFinish() }, prominent = true, variant = GlassVariant.Regular)
                }
            }
        }
    }
}

@Composable
private fun CheckCard(c: CheckResult, step: Int?, onFix: () -> Unit, onConfirm: (() -> Unit)?) {
    val tint = when (c.status) {
        CheckStatus.Ok -> GlassColors.Green
        CheckStatus.Warning -> GlassColors.Orange
        CheckStatus.Off -> GlassColors.Red
        CheckStatus.Info -> Color(0xFF8E8E93)
    }
    GlassSurface(Modifier.fillMaxWidth(), shape = GlassShape.Card, role = null) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                GlassSurface(Modifier.size(28.dp), shape = GlassShape.Circle, variant = GlassVariant.Clear, tintOverride = tint, contentAlignment = Alignment.Center, role = null) {
                    if (step != null) GlassText("$step", style = GlassType.Subhead, color = Color.White)
                    else GlassIcon(if (c.status == CheckStatus.Ok) GlassIcons.Check else GlassIcons.Info, size = 16.dp, tint = Color.White)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    GlassText(c.title, style = GlassType.Headline)
                    GlassText(c.statusText, style = GlassType.Footnote, color = tint)
                }
            }
            Spacer(Modifier.height(8.dp))
            GlassText(c.reason, style = GlassType.Callout, secondary = true)
            if (c.fixLabel != null || (c.confirmable && onConfirm != null)) {
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (c.fixLabel != null) GlassButton(c.fixLabel, onClick = onFix, prominent = c.status == CheckStatus.Off)
                    if (c.confirmable && onConfirm != null) GlassButton("I've done this", onClick = onConfirm)
                }
            }
        }
    }
}

@Composable
private fun AboutScreen(nav: SettingsNav) {
    val context = LocalContext.current
    val version = remember { try { context.packageManager.getPackageInfo(context.packageName, 0).versionName } catch (e: Exception) { null } ?: "?" }
    ScreenScaffold("About", nav) {
        item {
            GlassListGroup(footer = "An original launcher inspired by iOS design. Not affiliated with Apple; no Apple artwork is used. No analytics or ads. The only network use is the weather widget (Open-Meteo) and Google search intents.") {
                GlassRow("Glass Launcher", subtitle = "Version $version")
                GlassRow("Inter typeface", subtitle = "© The Inter Project Authors, SIL Open Font License 1.1")
            }
        }
    }
}
