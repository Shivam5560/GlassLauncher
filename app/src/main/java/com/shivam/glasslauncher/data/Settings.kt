package com.shivam.glasslauncher.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.shivam.glasslauncher.icons.IconShape
import com.shivam.glasslauncher.ui.home.GestureBinding
import com.shivam.glasslauncher.ui.home.HomeAction
import com.shivam.glasslauncher.ui.home.HomeGesture
import com.shivam.glasslauncher.ui.home.HomeGestureMap
import com.shivam.glasslauncher.ui.glass.GlassStyle
import com.shivam.glasslauncher.ui.glass.RoleStyle
import com.shivam.glasslauncher.ui.glass.ThemePreset
import com.shivam.glasslauncher.ui.glass.GlassTone
import com.shivam.glasslauncher.ui.glass.GlassVariant

val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

data class GestureSettings(
    val bottomEnabled: Boolean = true,
    val leftEnabled: Boolean = true,
    val rightEnabled: Boolean = true,
    val showPill: Boolean = true,
    /** Height of the bottom swipe zone in dp. */
    val bottomZoneHeight: Float = 22f,
    /** Fraction of the screen width used by the bottom zone (centred). */
    val bottomZoneWidth: Float = 0.8f,
    /** Width of the left/right back zones in dp. */
    val edgeZoneWidth: Float = 18f,
    /** Fraction of the screen height covered by the side zones (anchored above the bottom zone). */
    val edgeZoneHeight: Float = 0.7f,
    /** 0.5 = needs long swipes, 2.0 = very short swipes trigger. */
    val sensitivity: Float = 1f,
    val holdForRecentsMs: Int = 300,
    val haptics: Boolean = true,
    /** Packages where the edge zones are switched off (games, apps with their own back swipe). */
    val excludedApps: Set<String> = emptySet(),
    /** Hide the zones while the on-screen keyboard is showing. */
    val pauseWithKeyboard: Boolean = true,
)

/** Which drawer category opens first (Settings → App drawer). */
enum class DrawerLaunchView(val title: String) { LastCategory("Last opened"), First("First category") }

/** How drawer category pages change. */
enum class DrawerSwipe(val title: String) { Both("Both"), Screen("Screen"), Bar("Icons"), Off("Tap only") }
enum class DrawerTransition(val title: String) { Slide("Slide"), Fade("Fade"), Depth("Depth"), None("None") }

/** Home page transition (Settings → Appearance → Home animation). */
enum class HomeAnimation(val title: String) { Slide("Slide"), Fade("Fade"), Depth("Depth"), Cube("Cube") }

/** Phase 5 global appearance values that are not glass material (those live in [GlassStyle]). */
data class Appearance(
    /** Icon size multiplier on home and in the drawer. */
    val iconScale: Float = 1f,
    val iconShape: IconShape = IconShape.Squircle,
    /** Label text size (sp). */
    val labelSize: Float = 11.5f,
    val twoLineLabels: Boolean = false,
    /** Drawer labels (home labels are [LauncherSettings.showLabels]). */
    val drawerLabels: Boolean = true,
    /** Folders open full screen over a strong blur instead of a floating panel. */
    val immersiveFolders: Boolean = false,
    val folderLabels: Boolean = true,
    val homeAnimation: HomeAnimation = HomeAnimation.Slide,
)

data class LauncherSettings(
    val glass: GlassStyle = GlassStyle(),
    val gestures: GestureSettings = GestureSettings(),
    val iconPack: String = "",
    val hidden: Set<String> = emptySet(),
    val locked: Set<String> = emptySet(),
    /** Labels under home-screen icons: white with a soft shadow, as on iOS. */
    val showLabels: Boolean = true,
    val showBadges: Boolean = true,
    val onboardingDone: Boolean = false,
    /** HyperOS has no API to read Autostart reliably; the user confirms it in System check. */
    val autostartConfirmed: Boolean = false,
    val restrictedSettingsConfirmed: Boolean = false,
    /** True once the user (or Glass Icons' Apply button) picked an icon pack, "System icons" included. */
    val iconPackChosen: Boolean = false,
    val appearance: Appearance = Appearance(),
    val drawerLaunchView: DrawerLaunchView = DrawerLaunchView.LastCategory,
    val drawerTransition: DrawerTransition = DrawerTransition.Slide,
    val drawerSwipe: DrawerSwipe = DrawerSwipe.Both,
    /** Category id the drawer showed last. */
    val drawerLastCategory: String? = null,
    /** Ultra-immersive: hide the status bar (and nav bar with Glass gestures) on home and drawer. */
    val immersive: Boolean = false,
    /** Phase 6: what each home gesture does (Settings → Home gestures). */
    val homeGestures: Map<HomeGesture, GestureBinding> = HomeGestureMap.defaults,
    /** Pages loop: after the App Library comes the Today view again. */
    val infiniteScroll: Boolean = false,
    /** The wallpaper copy shifts with device tilt (needs the wallpaper set through Glass). */
    val wallpaperParallax: Boolean = false,
    /** Home shows a softly blurred wallpaper (needs the wallpaper readable by Glass). */
    val homeBlur: Boolean = true,
    /** With "System icons": core system apps (Phone, Camera, Settings…) use Glass Icons' iOS-style art. */
    val systemAppIcons: Boolean = true,
)

class SettingsRepository(private val context: Context, private val scope: CoroutineScope) {

    private object K {
        val blur = floatPreferencesKey("glass_blur")
        val tint = intPreferencesKey("glass_tint")
        val tintOpacity = floatPreferencesKey("glass_tint_opacity")
        val edge = floatPreferencesKey("glass_edge")
        val wallpaperBackdrop = booleanPreferencesKey("glass_wallpaper_backdrop")
        val variant = stringPreferencesKey("glass_variant")
        val refraction = floatPreferencesKey("glass_refraction")
        val rim = floatPreferencesKey("glass_rim")
        val chroma = floatPreferencesKey("glass_chroma")
        val tone = stringPreferencesKey("glass_tone")
        val tilt = booleanPreferencesKey("glass_tilt")
        val liquid = booleanPreferencesKey("glass_liquid")
        val iconPack = stringPreferencesKey("icon_pack")
        val hidden = stringSetPreferencesKey("hidden_apps")
        val locked = stringSetPreferencesKey("locked_apps")
        val doubleTap = booleanPreferencesKey("double_tap_lock")
        val swipeDown = booleanPreferencesKey("swipe_down_spotlight")
        val labels = booleanPreferencesKey("show_labels")
        val badges = booleanPreferencesKey("show_badges")
        val onboarding = booleanPreferencesKey("onboarding_done")
        val gBottom = booleanPreferencesKey("g_bottom")
        val gLeft = booleanPreferencesKey("g_left")
        val gRight = booleanPreferencesKey("g_right")
        val gPill = booleanPreferencesKey("g_pill")
        val gZoneH = floatPreferencesKey("g_zone_h")
        val gZoneW = floatPreferencesKey("g_zone_w")
        val gEdgeW = floatPreferencesKey("g_edge_w")
        val gEdgeH = floatPreferencesKey("g_edge_h")
        val gSens = floatPreferencesKey("g_sens")
        val gHold = intPreferencesKey("g_hold")
        val gHaptics = booleanPreferencesKey("g_haptics")
        val gExcluded = stringSetPreferencesKey("g_excluded")
        val gKeyboard = booleanPreferencesKey("g_keyboard_pause")
        val autostartConfirmed = booleanPreferencesKey("autostart_confirmed")
        val restrictedConfirmed = booleanPreferencesKey("restricted_confirmed")
        // Phase 5
        val styleVersion = intPreferencesKey("glass_style_version")
        val roles = stringPreferencesKey("glass_roles")
        val widgetRadius = floatPreferencesKey("glass_widget_radius")
        val preset = stringPreferencesKey("glass_preset")
        val iconScale = floatPreferencesKey("ap_icon_scale")
        val iconShape = stringPreferencesKey("ap_icon_shape")
        val labelSize = floatPreferencesKey("ap_label_size")
        val twoLine = booleanPreferencesKey("ap_two_line")
        val drawerLabels = booleanPreferencesKey("ap_drawer_labels")
        val immersiveFolders = booleanPreferencesKey("ap_immersive_folders")
        val folderLabels = booleanPreferencesKey("ap_folder_labels")
        val homeAnim = stringPreferencesKey("ap_home_anim")
        val drawerLaunch = stringPreferencesKey("drawer_launch_view")
        val drawerTransition = stringPreferencesKey("drawer_transition")
        val drawerSwipe = stringPreferencesKey("drawer_swipe")
        val drawerLast = stringPreferencesKey("drawer_last_category")
        val immersive = booleanPreferencesKey("immersive")
        // Phase 6
        val homeGestures = stringPreferencesKey("home_gestures")
        val infinite = booleanPreferencesKey("infinite_scroll")
        val parallax = booleanPreferencesKey("wallpaper_parallax")
        val homeBlur = booleanPreferencesKey("home_blur")
        val systemAppIcons = booleanPreferencesKey("system_app_icons")
    }

    init {
        // Phase 5: move users still on the old, too-strong lens defaults to the toned-down ones.
        scope.launch {
            context.settingsStore.edit { p ->
                if ((p[K.styleVersion] ?: 0) >= 5) return@edit
                if (p[K.refraction] == 0.6f) p[K.refraction] = 0.3f
                if (p[K.chroma] == 0.35f) p[K.chroma] = 0.15f
                if (p[K.edge] == 0.65f) p[K.edge] = 0.45f
                if (p[K.tintOpacity] == 0.30f) p[K.tintOpacity] = 0.42f
                if (p[K.tint] == 0xFF1E2028.toInt()) p[K.tint] = 0xFF16171D.toInt()
                p[K.styleVersion] = 5
            }
        }
    }

    val flow: Flow<LauncherSettings> = context.settingsStore.data.map { p ->
        val d = LauncherSettings()
        LauncherSettings(
            glass = GlassStyle(
                variant = p[K.variant]?.let { v -> GlassVariant.entries.firstOrNull { it.name == v } } ?: d.glass.variant,
                blurRadius = p[K.blur] ?: d.glass.blurRadius,
                tintColor = p[K.tint] ?: d.glass.tintColor,
                tintOpacity = p[K.tintOpacity] ?: d.glass.tintOpacity,
                specular = p[K.edge] ?: d.glass.specular,
                refraction = p[K.refraction] ?: d.glass.refraction,
                rimWidth = p[K.rim] ?: d.glass.rimWidth,
                chromaticAberration = p[K.chroma] ?: d.glass.chromaticAberration,
                tone = p[K.tone]?.let { v -> GlassTone.entries.firstOrNull { it.name == v } } ?: d.glass.tone,
                tiltLighting = p[K.tilt] ?: d.glass.tiltLighting,
                wallpaperBackdrop = p[K.wallpaperBackdrop] ?: d.glass.wallpaperBackdrop,
                liquidEffects = p[K.liquid] ?: d.glass.liquidEffects,
                roles = RoleStyle.decode(p[K.roles]),
                widgetCornerRadius = p[K.widgetRadius] ?: d.glass.widgetCornerRadius,
                preset = p[K.preset]?.let { v -> ThemePreset.entries.firstOrNull { it.name == v } } ?: d.glass.preset,
            ),
            gestures = GestureSettings(
                bottomEnabled = p[K.gBottom] ?: d.gestures.bottomEnabled,
                leftEnabled = p[K.gLeft] ?: d.gestures.leftEnabled,
                rightEnabled = p[K.gRight] ?: d.gestures.rightEnabled,
                showPill = p[K.gPill] ?: d.gestures.showPill,
                bottomZoneHeight = p[K.gZoneH] ?: d.gestures.bottomZoneHeight,
                bottomZoneWidth = p[K.gZoneW] ?: d.gestures.bottomZoneWidth,
                edgeZoneWidth = p[K.gEdgeW] ?: d.gestures.edgeZoneWidth,
                edgeZoneHeight = p[K.gEdgeH] ?: d.gestures.edgeZoneHeight,
                sensitivity = p[K.gSens] ?: d.gestures.sensitivity,
                holdForRecentsMs = p[K.gHold] ?: d.gestures.holdForRecentsMs,
                haptics = p[K.gHaptics] ?: d.gestures.haptics,
                excludedApps = p[K.gExcluded] ?: d.gestures.excludedApps,
                pauseWithKeyboard = p[K.gKeyboard] ?: d.gestures.pauseWithKeyboard,
            ),
            iconPack = p[K.iconPack] ?: d.iconPack,
            hidden = p[K.hidden] ?: d.hidden,
            locked = p[K.locked] ?: d.locked,
            showLabels = p[K.labels] ?: d.showLabels,
            showBadges = p[K.badges] ?: d.showBadges,
            onboardingDone = p[K.onboarding] ?: d.onboardingDone,
            autostartConfirmed = p[K.autostartConfirmed] ?: false,
            restrictedSettingsConfirmed = p[K.restrictedConfirmed] ?: false,
            iconPackChosen = p[K.iconPack] != null,
            appearance = Appearance(
                iconScale = p[K.iconScale] ?: d.appearance.iconScale,
                iconShape = p[K.iconShape]?.let { v -> IconShape.entries.firstOrNull { it.name == v } } ?: d.appearance.iconShape,
                labelSize = p[K.labelSize] ?: d.appearance.labelSize,
                twoLineLabels = p[K.twoLine] ?: d.appearance.twoLineLabels,
                drawerLabels = p[K.drawerLabels] ?: d.appearance.drawerLabels,
                immersiveFolders = p[K.immersiveFolders] ?: d.appearance.immersiveFolders,
                folderLabels = p[K.folderLabels] ?: d.appearance.folderLabels,
                homeAnimation = p[K.homeAnim]?.let { v -> HomeAnimation.entries.firstOrNull { it.name == v } } ?: d.appearance.homeAnimation,
            ),
            drawerLaunchView = p[K.drawerLaunch]?.let { v -> DrawerLaunchView.entries.firstOrNull { it.name == v } } ?: d.drawerLaunchView,
            drawerSwipe = p[K.drawerSwipe]?.let { v -> DrawerSwipe.entries.firstOrNull { it.name == v } } ?: d.drawerSwipe,
            drawerTransition = p[K.drawerTransition]?.let { v -> DrawerTransition.entries.firstOrNull { it.name == v } } ?: d.drawerTransition,
            drawerLastCategory = p[K.drawerLast],
            immersive = p[K.immersive] ?: d.immersive,
            homeGestures = HomeGestureMap.decode(p[K.homeGestures]) ?: legacyGestures(p),
            infiniteScroll = p[K.infinite] ?: d.infiniteScroll,
            wallpaperParallax = p[K.parallax] ?: d.wallpaperParallax,
            homeBlur = p[K.homeBlur] ?: d.homeBlur,
            systemAppIcons = p[K.systemAppIcons] ?: d.systemAppIcons,
        )
    }

    /** Before Phase 6 only swipe down and double tap could be switched off. */
    private fun legacyGestures(p: Preferences): Map<HomeGesture, GestureBinding> {
        val m = HomeGestureMap.defaults.toMutableMap()
        if (p[K.swipeDown] == false) m[HomeGesture.SwipeDown] = GestureBinding(HomeAction.None)
        if (p[K.doubleTap] == false) m[HomeGesture.DoubleTap] = GestureBinding(HomeAction.None)
        return m
    }

    val state: StateFlow<LauncherSettings?> = flow.stateIn(scope, SharingStarted.Eagerly, null)

    private fun edit(block: (MutablePreferences) -> Unit) {
        scope.launch { context.settingsStore.edit { block(it) } }
    }

    fun setGlass(style: GlassStyle) = edit {
        it[K.variant] = style.variant.name
        it[K.blur] = style.blurRadius
        it[K.tint] = style.tintColor
        it[K.tintOpacity] = style.tintOpacity
        it[K.edge] = style.specular
        it[K.refraction] = style.refraction
        it[K.rim] = style.rimWidth
        it[K.chroma] = style.chromaticAberration
        it[K.tone] = style.tone.name
        it[K.tilt] = style.tiltLighting
        it[K.wallpaperBackdrop] = style.wallpaperBackdrop
        it[K.liquid] = style.liquidEffects
        it[K.roles] = RoleStyle.encode(style.roles)
        it[K.widgetRadius] = style.widgetCornerRadius
        it[K.preset] = style.preset.name
    }

    fun resetGlass() = setGlass(GlassStyle())

    fun setGestures(g: GestureSettings) = edit {
        it[K.gBottom] = g.bottomEnabled
        it[K.gLeft] = g.leftEnabled
        it[K.gRight] = g.rightEnabled
        it[K.gPill] = g.showPill
        it[K.gZoneH] = g.bottomZoneHeight
        it[K.gZoneW] = g.bottomZoneWidth
        it[K.gEdgeW] = g.edgeZoneWidth
        it[K.gEdgeH] = g.edgeZoneHeight
        it[K.gSens] = g.sensitivity
        it[K.gHold] = g.holdForRecentsMs
        it[K.gHaptics] = g.haptics
        it[K.gExcluded] = g.excludedApps
        it[K.gKeyboard] = g.pauseWithKeyboard
    }

    fun setAutostartConfirmed(v: Boolean) = edit { it[K.autostartConfirmed] = v }
    fun setRestrictedConfirmed(v: Boolean) = edit { it[K.restrictedConfirmed] = v }

    fun setIconPack(pkg: String) = edit { it[K.iconPack] = pkg }

    /** Writes the icon pack and waits until it is on disk (used by the cross-app Apply intent). */
    suspend fun setIconPackNow(pkg: String) { context.settingsStore.edit { it[K.iconPack] = pkg } }

    fun setAppearance(a: Appearance) = edit {
        it[K.iconScale] = a.iconScale
        it[K.iconShape] = a.iconShape.name
        it[K.labelSize] = a.labelSize
        it[K.twoLine] = a.twoLineLabels
        it[K.drawerLabels] = a.drawerLabels
        it[K.immersiveFolders] = a.immersiveFolders
        it[K.folderLabels] = a.folderLabels
        it[K.homeAnim] = a.homeAnimation.name
    }
    fun setDrawerLaunchView(v: DrawerLaunchView) = edit { it[K.drawerLaunch] = v.name }
    fun setDrawerSwipe(v: DrawerSwipe) = edit { it[K.drawerSwipe] = v.name }
    fun setDrawerTransition(v: DrawerTransition) = edit { it[K.drawerTransition] = v.name }
    fun setDrawerLastCategory(id: String) = edit { it[K.drawerLast] = id }
    fun setImmersive(v: Boolean) = edit { it[K.immersive] = v }
    fun setHidden(key: String, hidden: Boolean) = edit {
        val cur = it[K.hidden] ?: emptySet()
        it[K.hidden] = if (hidden) cur + key else cur - key
    }
    fun setLocked(key: String, locked: Boolean) = edit {
        val cur = it[K.locked] ?: emptySet()
        it[K.locked] = if (locked) cur + key else cur - key
    }
    fun setShowLabels(v: Boolean) = edit { it[K.labels] = v }
    fun setShowBadges(v: Boolean) = edit { it[K.badges] = v }
    fun setHomeGestures(map: Map<HomeGesture, GestureBinding>) = edit { it[K.homeGestures] = HomeGestureMap.encode(map) }
    fun setHomeGesture(map: Map<HomeGesture, GestureBinding>, g: HomeGesture, b: GestureBinding) = setHomeGestures(map + (g to b))
    fun setInfiniteScroll(v: Boolean) = edit { it[K.infinite] = v }
    fun setWallpaperParallax(v: Boolean) = edit { it[K.parallax] = v }
    fun setHomeBlur(v: Boolean) = edit { it[K.homeBlur] = v }
    fun setSystemAppIcons(v: Boolean) = edit { it[K.systemAppIcons] = v }
    fun setOnboardingDone(v: Boolean) = edit { it[K.onboarding] = v }
}
