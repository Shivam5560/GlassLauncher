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
import com.shivam.glasslauncher.ui.glass.GlassStyle
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

data class LauncherSettings(
    val glass: GlassStyle = GlassStyle(),
    val gestures: GestureSettings = GestureSettings(),
    val iconPack: String = "",
    val hidden: Set<String> = emptySet(),
    val locked: Set<String> = emptySet(),
    val doubleTapToLock: Boolean = true,
    val swipeDownForSpotlight: Boolean = true,
    val showLabels: Boolean = true,
    val showBadges: Boolean = true,
    val onboardingDone: Boolean = false,
    /** HyperOS has no API to read Autostart reliably; the user confirms it in System check. */
    val autostartConfirmed: Boolean = false,
    val restrictedSettingsConfirmed: Boolean = false,
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
            doubleTapToLock = p[K.doubleTap] ?: d.doubleTapToLock,
            swipeDownForSpotlight = p[K.swipeDown] ?: d.swipeDownForSpotlight,
            showLabels = p[K.labels] ?: d.showLabels,
            showBadges = p[K.badges] ?: d.showBadges,
            onboardingDone = p[K.onboarding] ?: d.onboardingDone,
            autostartConfirmed = p[K.autostartConfirmed] ?: false,
            restrictedSettingsConfirmed = p[K.restrictedConfirmed] ?: false,
        )
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
    fun setHidden(key: String, hidden: Boolean) = edit {
        val cur = it[K.hidden] ?: emptySet()
        it[K.hidden] = if (hidden) cur + key else cur - key
    }
    fun setLocked(key: String, locked: Boolean) = edit {
        val cur = it[K.locked] ?: emptySet()
        it[K.locked] = if (locked) cur + key else cur - key
    }
    fun setDoubleTapToLock(v: Boolean) = edit { it[K.doubleTap] = v }
    fun setSwipeDownForSpotlight(v: Boolean) = edit { it[K.swipeDown] = v }
    fun setShowLabels(v: Boolean) = edit { it[K.labels] = v }
    fun setShowBadges(v: Boolean) = edit { it[K.badges] = v }
    fun setOnboardingDone(v: Boolean) = edit { it[K.onboarding] = v }
}
