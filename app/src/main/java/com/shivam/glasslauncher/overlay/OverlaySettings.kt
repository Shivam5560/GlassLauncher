package com.shivam.glasslauncher.overlay

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.shivam.glasslauncher.overlay.controlcenter.CcModule
import com.shivam.glasslauncher.overlay.controlcenter.ControlCenterLayout
import com.shivam.glasslauncher.overlay.island.IslandKind
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Phase 3 keeps its own preferences file so it never touches the launcher's settings store. */
private val Context.overlayStore: DataStore<Preferences> by preferencesDataStore(name = "overlays")

data class IslandSettings(
    val enabled: Boolean = true,
    /** Activity kinds the user switched off. */
    val disabledKinds: Set<IslandKind> = emptySet(),
    val privacyDots: Boolean = true,
    /** Black pill around the camera even when nothing is running. */
    val showIdlePill: Boolean = true,
    /** Manual calibration on top of the DisplayCutout position, in dp. */
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    /** Size multiplier for the pill. */
    val scale: Float = 1f,
) {
    fun isOn(kind: IslandKind) = kind !in disabledKinds
}

data class OverlaySettings(
    val island: IslandSettings = IslandSettings(),
    val controlCenter: Boolean = true,
    val notificationCenter: Boolean = true,
    val ccLayout: List<CcModule> = ControlCenterLayout.DEFAULT,
    /** Width of each top-corner swipe zone as a fraction of the screen width. */
    val cornerWidth: Float = 0.3f,
    /** Height of the top-corner swipe zones in dp. */
    val cornerHeight: Float = 28f,
    /** WRITE_SETTINGS is prompted for once (from the brightness slider); afterwards only System check asks. */
    val writeSettingsAsked: Boolean = false,
)

object OverlayPrefs {

    private object K {
        val islandOn = booleanPreferencesKey("island_on")
        val islandOff = stringSetPreferencesKey("island_disabled_kinds")
        val dots = booleanPreferencesKey("island_dots")
        val idle = booleanPreferencesKey("island_idle")
        val dx = floatPreferencesKey("island_dx")
        val dy = floatPreferencesKey("island_dy")
        val scale = floatPreferencesKey("island_scale")
        val cc = booleanPreferencesKey("cc_on")
        val nc = booleanPreferencesKey("nc_on")
        val layout = stringPreferencesKey("cc_layout")
        val cornerW = floatPreferencesKey("corner_w")
        val cornerH = floatPreferencesKey("corner_h")
        val asked = booleanPreferencesKey("write_settings_asked")
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    @Volatile private var flow: StateFlow<OverlaySettings>? = null
    private lateinit var appContext: Context

    /** Process-wide settings state; starts with defaults until DataStore has loaded. */
    fun state(context: Context): StateFlow<OverlaySettings> {
        flow?.let { return it }
        synchronized(this) {
            flow?.let { return it }
            appContext = context.applicationContext
            val d = OverlaySettings()
            val f = appContext.overlayStore.data.map { p ->
                OverlaySettings(
                    island = IslandSettings(
                        enabled = p[K.islandOn] ?: d.island.enabled,
                        disabledKinds = p[K.islandOff]?.mapNotNull { n -> IslandKind.entries.firstOrNull { it.name == n } }?.toSet() ?: emptySet(),
                        privacyDots = p[K.dots] ?: d.island.privacyDots,
                        showIdlePill = p[K.idle] ?: d.island.showIdlePill,
                        offsetX = p[K.dx] ?: d.island.offsetX,
                        offsetY = p[K.dy] ?: d.island.offsetY,
                        scale = p[K.scale] ?: d.island.scale,
                    ),
                    controlCenter = p[K.cc] ?: d.controlCenter,
                    notificationCenter = p[K.nc] ?: d.notificationCenter,
                    ccLayout = ControlCenterLayout.parse(p[K.layout]),
                    cornerWidth = p[K.cornerW] ?: d.cornerWidth,
                    cornerHeight = p[K.cornerH] ?: d.cornerHeight,
                    writeSettingsAsked = p[K.asked] ?: false,
                )
            }.stateIn(scope, SharingStarted.Eagerly, d)
            flow = f
            return f
        }
    }

    private fun edit(context: Context, block: (MutablePreferences) -> Unit) {
        val c = context.applicationContext
        scope.launch { c.overlayStore.edit { block(it) } }
    }

    fun setIsland(context: Context, s: IslandSettings) = edit(context) {
        it[K.islandOn] = s.enabled
        it[K.islandOff] = s.disabledKinds.mapTo(HashSet()) { k -> k.name }
        it[K.dots] = s.privacyDots
        it[K.idle] = s.showIdlePill
        it[K.dx] = s.offsetX
        it[K.dy] = s.offsetY
        it[K.scale] = s.scale
    }

    fun setControlCenter(context: Context, on: Boolean) = edit(context) { it[K.cc] = on }
    fun setNotificationCenter(context: Context, on: Boolean) = edit(context) { it[K.nc] = on }
    fun setLayout(context: Context, modules: List<CcModule>) = edit(context) { it[K.layout] = ControlCenterLayout.serialize(modules) }
    fun setCorners(context: Context, width: Float, height: Float) = edit(context) {
        it[K.cornerW] = width
        it[K.cornerH] = height
    }
    fun setWriteSettingsAsked(context: Context) = edit(context) { it[K.asked] = true }
}
