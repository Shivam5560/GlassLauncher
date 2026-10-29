package com.shivam.glasslauncher.settings

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
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shivam.glasslauncher.GlassApp
import com.shivam.glasslauncher.data.Appearance
import com.shivam.glasslauncher.data.DrawerCategorizer
import com.shivam.glasslauncher.data.DrawerLaunchView
import com.shivam.glasslauncher.data.DrawerSort
import com.shivam.glasslauncher.data.DrawerSwipe
import com.shivam.glasslauncher.data.DrawerTransition
import com.shivam.glasslauncher.data.HomeAnimation
import com.shivam.glasslauncher.data.LauncherSettings
import com.shivam.glasslauncher.data.DrawerOps
import com.shivam.glasslauncher.gestures.GlassGestureService
import com.shivam.glasslauncher.icons.IconShape
import com.shivam.glasslauncher.ui.drawer.DrawerGlyphs
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
import com.shivam.glasslauncher.ui.glass.GlassSwitchRow
import com.shivam.glasslauncher.ui.glass.GlassText
import com.shivam.glasslauncher.ui.glass.GlassType
import com.shivam.glasslauncher.ui.glass.GlassVariant
import com.shivam.glasslauncher.ui.glass.LocalBackdrop
import com.shivam.glasslauncher.ui.glass.LocalGlassContentColor
import com.shivam.glasslauncher.ui.glass.LocalGlassStyle
import com.shivam.glasslauncher.ui.glass.RoleStyle
import com.shivam.glasslauncher.ui.glass.SurfaceKind
import com.shivam.glasslauncher.ui.glass.ThemePreset
import com.shivam.glasslauncher.ui.glass.currentAccent
import com.shivam.glasslauncher.ui.glass.recordBackdrop
import com.shivam.glasslauncher.ui.home.AppIconImage
import kotlinx.coroutines.delay

/** Settings → Appearance and Settings → App drawer (Phase 5). */
fun isAppearanceScreen(screen: String) = screen == "appearance" || screen == "drawer"

@Composable
fun AppearanceScreen(nav: SettingsNav, settings: LauncherSettings) {
    when (nav.current) {
        "drawer" -> DrawerSettingsScreen(nav, settings)
        else -> GlobalAppearanceScreen(nav, settings)
    }
}

private val roleColors = listOf(
    0xFF000000, 0xFF16171D, 0xFF1C2541, 0xFF0F3A4A, 0xFF1D3A24, 0xFF3A1730, 0xFF4A2A10, 0xFF8E8E93, 0xFFE9ECF2, 0xFFFFFFFF,
).map { it.toInt() }

private val accentColors = listOf(
    0xFF0A84FF, 0xFF5E5CE6, 0xFFBF5AF2, 0xFFFF375F, 0xFFFF453A, 0xFFFF9F0A, 0xFFFFD60A, 0xFF30D158, 0xFF64D2FF,
).map { it.toInt() }

/**
 * Global appearance: one place for the whole look, with a live preview card (mini home row, dock
 * and drawer tab bar on the current wallpaper) at the top. Every value goes through
 * [GlassStyle] / [LocalGlassStyle] (material, per-role overrides, widget radius) or [Appearance]
 * (icons, labels, folders, home animation), so it applies everywhere.
 */
@Composable
private fun GlobalAppearanceScreen(nav: SettingsNav, settings: LauncherSettings) {
    val app = GlassApp.from(LocalContext.current)
    var draft by remember { mutableStateOf(settings.glass) }
    var look by remember { mutableStateOf(settings.appearance) }
    var role by remember { mutableStateOf(SurfaceKind.Panel) }
    LaunchedEffect(draft) {
        delay(150)
        if (draft != settings.glass) app.settings.setGlass(draft)
    }
    LaunchedEffect(look) {
        delay(150)
        if (look != settings.appearance) app.settings.setAppearance(look)
    }
    val gesturesOn = remember { GlassGestureService.isEnabled(app) }

    CompositionLocalProvider(LocalGlassStyle provides draft) {
        ScreenScaffold("Appearance", nav) {
            item("preview") { AppearancePreview(look, settings.showLabels) }
            item("presets") {
                GlassListGroup(title = "Launcher theme", footer = "Solid: opaque panels, no blur. Frosted: blur and tint, no lens. Liquid Glass: blur, tint and a gentle lens. Editing anything below switches to Custom.") {
                    Column(Modifier.padding(12.dp)) {
                        val presets = listOf(ThemePreset.Solid, ThemePreset.Frosted, ThemePreset.Liquid)
                        GlassSegmented(presets.map { it.title }, presets.indexOf(draft.preset), { i -> draft = GlassStyle.applyPreset(draft, presets[i]) })
                        if (draft.preset == ThemePreset.Custom) {
                            Spacer(Modifier.height(6.dp))
                            GlassText("Custom", style = GlassType.Footnote, secondary = true)
                        }
                    }
                }
            }
            item("edit") {
                val r = draft.role(role)
                fun set(next: RoleStyle) { draft = draft.withRole(role, next) }
                GlassListGroup(
                    title = "Edit theme",
                    footer = when (role) {
                        SurfaceKind.Background -> "The dim behind Settings and the app drawer."
                        SurfaceKind.Cards -> "Buttons, chips, switches, settings cards and small controls."
                        SurfaceKind.Widget -> "Every widget panel."
                        SurfaceKind.Panel -> "Dock, folders, menus, sheets and the drawer's category bar."
                    } + " Automatic text colour keeps WCAG AA contrast.",
                ) {
                    Column(Modifier.padding(12.dp)) {
                        GlassSegmented(listOf("Background", "Cards", "Widget", "Panel"), role.ordinal, { role = SurfaceKind.entries[it] })
                    }
                    ColorRow("Colour", r.color, roleColors) { set(r.copy(color = it)) }
                    GlassSliderRow("Opacity", r.opacity ?: draft.defaultOpacity(role), 0f..1f, { "${(it * 100).toInt()}%" }) { set(r.copy(opacity = it)) }
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        GlassText("Text & icon colour", style = GlassType.Callout)
                        Spacer(Modifier.height(8.dp))
                        val sel = when (r.contentColor) {
                            null -> 0
                            0xFFFFFFFF.toInt() -> 1
                            else -> 2
                        }
                        GlassSegmented(listOf("Automatic", "White", "Black"), sel, { i ->
                            set(r.copy(contentColor = when (i) { 1 -> 0xFFFFFFFF.toInt(); 2 -> 0xFF050507.toInt(); else -> null }))
                        })
                    }
                    GlassSliderRow("Blur radius", r.blur ?: draft.blurRadius, 0f..60f, { "${it.toInt()} dp" }) { set(r.copy(blur = it)) }
                    GlassSliderRow("Stroke", r.stroke, 0f..3f, { if (it < 0.05f) "None" else "%.1f dp".format(it) }) { set(r.copy(stroke = if (it < 0.05f) 0f else it)) }
                    ColorRow("Accent colour", r.accent, accentColors) { set(r.copy(accent = it)) }
                    GlassRow("Reset ${role.title.lowercase()}", icon = GlassIcons.Close, iconTint = Color(0xFF8E8E93), onClick = { draft = draft.withRole(role, RoleStyle()) }, showChevron = false)
                }
            }
            item("icons") {
                GlassListGroup(title = "Icon appearance", footer = "Shape applies to icons Glass draws itself; icon pack artwork keeps its own shape.") {
                    GlassRow("Icon pack", subtitle = settings.iconPack.ifEmpty { "System icons" }, icon = GlassIcons.Grid, iconTint = GlassColors.Orange, onClick = { nav.push("iconpack") })
                    GlassRow("Icon style", subtitle = "Light, dark or tinted pack artwork", icon = GlassIcons.Palette, iconTint = Color(0xFF30D158), onClick = { nav.push("iconstyle") })
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
                        GlassText("Adaptive shape", style = GlassType.Callout)
                        Spacer(Modifier.height(8.dp))
                        GlassSegmented(IconShape.entries.map { it.title.substringBefore(' ') }, look.iconShape.ordinal, { look = look.copy(iconShape = IconShape.entries[it]) })
                    }
                    GlassSliderRow("Icon size", look.iconScale, 0.7f..1.3f, { "${(it * 100).toInt()}%" }) { look = look.copy(iconScale = it) }
                    GlassSwitchRow("Labels on Home", settings.showLabels, { app.settings.setShowLabels(it) })
                    GlassSwitchRow("Labels in the drawer", look.drawerLabels, { look = look.copy(drawerLabels = it) })
                    GlassSliderRow("Label size", look.labelSize, 9f..15f, { "%.1f sp".format(it) }) { look = look.copy(labelSize = it) }
                    GlassSwitchRow("Two-line labels", look.twoLineLabels, { look = look.copy(twoLineLabels = it) })
                }
            }
            item("widget") {
                GlassListGroup(title = "Widget") {
                    GlassSliderRow("Corner radius", draft.widgetCornerRadius, 0f..40f, { "${it.toInt()} dp" }) { draft = draft.copy(widgetCornerRadius = it) }
                }
            }
            item("font") {
                GlassListGroup(title = "Font") {
                    GlassRow("Font", subtitle = "Bundled and Google Fonts", icon = GlassIcons.Pencil, iconTint = Color(0xFFFF9F0A), onClick = { nav.push("fonts") })
                }
            }
            item("folder") {
                GlassListGroup(title = "Folder") {
                    GlassSwitchRow("Immersive folders", look.immersiveFolders, { look = look.copy(immersiveFolders = it) }, subtitle = "Open over a deeper dim without a floating panel")
                    GlassSwitchRow("Labels in folders", look.folderLabels, { look = look.copy(folderLabels = it) })
                }
            }
            item("home") {
                GlassListGroup(title = "Home animation", footer = "How home pages move when you swipe between them.") {
                    Column(Modifier.padding(12.dp)) {
                        GlassSegmented(HomeAnimation.entries.map { it.title }, look.homeAnimation.ordinal, { look = look.copy(homeAnimation = HomeAnimation.entries[it]) })
                    }
                }
            }
            item("immersive") {
                GlassListGroup(
                    title = "Ultra-immersive",
                    footer = if (gesturesOn) "Hides the status and navigation bars on Home and in the drawer; swipe from an edge to show them for a moment."
                    else "Hides the status bar on Home and in the drawer. The navigation bar stays because HyperOS forces 3-button navigation for other launchers; turn on Glass Gestures to hide it too.",
                ) {
                    GlassSwitchRow("Ultra-immersive mode", settings.immersive, { app.settings.setImmersive(it) })
                }
            }
            item("reset") {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    GlassButton("Reset appearance", onClick = { draft = GlassStyle(); look = Appearance() }, variant = GlassVariant.Regular)
                }
            }
        }
    }
}

/** A row of colour swatches with an "Auto" (null) choice first. */
@Composable
private fun ColorRow(title: String, selected: Int?, colors: List<Int>, onPick: (Int?) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        GlassText(title, style = GlassType.Callout)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            GlassSurface(
                Modifier.size(34.dp),
                shape = GlassShape.Circle,
                variant = GlassVariant.Clear,
                highlighted = selected == null,
                onClick = { onPick(null) },
                contentAlignment = Alignment.Center,
            ) { GlassText("A", style = GlassType.Subhead) }
            colors.forEach { c ->
                GlassSurface(
                    Modifier.size(34.dp),
                    shape = GlassShape.Circle,
                    variant = GlassVariant.Clear,
                    tintOverride = Color(c),
                    highlighted = c == selected,
                    onClick = { onPick(c) },
                    contentAlignment = Alignment.Center,
                ) {
                    if (c == selected) GlassIcon(GlassIcons.Check, size = 16.dp, tint = LocalGlassContentColor.current)
                }
            }
        }
    }
}

/**
 * Live preview: the current wallpaper (Glass's copy, or a gradient when it can't be read) with a
 * clock, a home row of icons, the dock and the drawer tab bar, all drawn with the draft style.
 */
@Composable
private fun AppearancePreview(look: Appearance, homeLabels: Boolean) {
    val app = GlassApp.from(LocalContext.current)
    val wallpaper by app.wallpaper.snapshot.collectAsState()
    val apps by app.apps.apps.collectAsState()
    val layer = rememberGraphicsLayer()
    val origin = remember { mutableStateOf(Offset.Zero) }
    val backdrop = remember(wallpaper) { Backdrop(listOf(layer), wallpaper) { origin.value } }
    val sample = remember(apps) { apps.take(8) }
    val style = LocalGlassStyle.current
    Box(
        Modifier
            .fillMaxWidth()
            .height(300.dp)
            .clip(GlassShape.rounded(28.dp))
            .onGloballyPositioned { origin.value = it.positionInWindow() },
    ) {
        val img = wallpaper.image
        Box(Modifier.fillMaxSize().recordBackdrop(layer)) {
            if (img != null) {
                androidx.compose.foundation.Image(img, null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            } else {
                Canvas(Modifier.fillMaxSize()) { drawRect(Brush.verticalGradient(listOf(Color(0xFF3A5A8C), Color(0xFF151A2C)))) }
            }
        }
        CompositionLocalProvider(LocalBackdrop provides backdrop) {
            Column(Modifier.fillMaxSize().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                GlassText("9:41", style = GlassType.LargeTitle.copy(fontSize = 40.sp, fontWeight = FontWeight.Light), color = Color.White)
                Spacer(Modifier.height(6.dp))
                val icon = 40.dp * look.iconScale.coerceIn(0.7f, 1.3f)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    sample.take(4).forEach { e ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            AppIconImage(e, icon)
                            if (homeLabels) {
                                com.shivam.glasslauncher.ui.home.IconLabel(e.label, false, Modifier.width(icon + 14.dp), sizeSp = look.labelSize * 0.85f, twoLines = look.twoLineLabels)
                            }
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                // Mini drawer tab bar (Panel role).
                GlassSurface(shape = GlassShape.Capsule, kind = SurfaceKind.Panel, role = null) {
                    Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        DrawerCategorizer.BUILT_IN.take(5).forEachIndexed { i, id ->
                            GlassIcon(DrawerGlyphs.of(id), size = 16.dp, tint = LocalGlassContentColor.current.copy(alpha = if (i == 0) 1f else 0.55f))
                        }
                    }
                }
                Spacer(Modifier.height(8.dp))
                // Mini dock (Panel role) with a Cards-role button next to it.
                GlassSurface(Modifier.fillMaxWidth(), shape = GlassShape.rounded(26.dp), kind = SurfaceKind.Panel, role = null) {
                    Row(Modifier.fillMaxWidth().padding(10.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        sample.drop(4).take(4).forEach { AppIconImage(it, icon) }
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    GlassSurface(Modifier.size(width = 96.dp, height = 40.dp), shape = GlassShape.rounded(style.widgetCornerRadius.coerceAtMost(20f).dp), kind = SurfaceKind.Widget, role = null, contentAlignment = Alignment.Center) {
                        GlassText("Widget", style = GlassType.Subhead)
                    }
                    GlassButton("Button", onClick = {})
                    GlassText("Accent", style = GlassType.Subhead, color = currentAccent())
                }
            }
        }
    }
}

/** Settings → App drawer: launch view, category transition, sorting and labels. */
@Composable
private fun DrawerSettingsScreen(nav: SettingsNav, settings: LauncherSettings) {
    val app = GlassApp.from(LocalContext.current)
    val layout by app.layout.layout.collectAsState()
    val drawer = layout?.drawer ?: return
    ScreenScaffold("App drawer", nav) {
        item("how") {
            GlassListGroup(footer = "Swipe up on Home to open the drawer. Apps are sorted into categories automatically; long-press an app → Move to category to change it. The ⋮ button in the drawer adds shortcuts, folders and categories.") {
                GlassRow("${drawer.categories.size} categories, ${drawer.categories.count { it.inBar }} in the bar", icon = GlassIcons.Grid, iconTint = currentAccent())
            }
        }
        item("launch") {
            GlassListGroup(title = "Launch view") {
                DrawerLaunchView.entries.forEach { v ->
                    GlassRow(v.title, onClick = { app.settings.setDrawerLaunchView(v) }, showChevron = false) {
                        if (settings.drawerLaunchView == v) GlassIcon(GlassIcons.Check, size = 18.dp, tint = currentAccent())
                    }
                }
            }
        }
        item("swipe") {
            GlassListGroup(title = "Swipe to switch category", footer = "Swipe on the screen, on the category icons, or both. No need to tap.") {
                Column(Modifier.padding(12.dp)) {
                    GlassSegmented(DrawerSwipe.entries.map { it.title }, settings.drawerSwipe.ordinal, { app.settings.setDrawerSwipe(DrawerSwipe.entries[it]) })
                }
            }
        }
        item("transition") {
            GlassListGroup(title = "Category transition") {
                Column(Modifier.padding(12.dp)) {
                    GlassSegmented(DrawerTransition.entries.map { it.title }, settings.drawerTransition.ordinal, { app.settings.setDrawerTransition(DrawerTransition.entries[it]) })
                }
            }
        }
        item("sort") {
            GlassListGroup(title = "Sorting") {
                DrawerSort.entries.forEach { s ->
                    GlassRow(s.title, onClick = { app.layout.update { it.copy(drawer = DrawerOps.setSort(it.drawer, s)) } }, showChevron = false) {
                        if (drawer.sort == s) GlassIcon(GlassIcons.Check, size = 18.dp, tint = currentAccent())
                    }
                }
            }
        }
        item("labels") {
            GlassListGroup(title = "Labels") {
                GlassSwitchRow("Labels in the drawer", settings.appearance.drawerLabels, { app.settings.setAppearance(settings.appearance.copy(drawerLabels = it)) })
            }
        }
        item("reset") {
            GlassListGroup(footer = "Puts the seven built-in categories back in their default order and clears your moves, folders and shortcuts.") {
                GlassRow("Reset categories", icon = GlassIcons.Trash, iconTint = GlassColors.Red, onClick = {
                    app.layout.update { it.copy(drawer = com.shivam.glasslauncher.data.DrawerConfig()) }
                }, showChevron = false)
            }
        }
    }
}
