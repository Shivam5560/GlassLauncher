package com.shivam.glasslauncher.overlay.island

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.AudioRecordingConfiguration
import android.os.BatteryManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class BatteryInfo(val level: Int, val charging: Boolean, val watts: Float?, val wattsSupported: Boolean)

/**
 * System signals for the island that do not come from notifications: battery and charging
 * (ACTION_POWER_CONNECTED + BatteryManager), ringer mode changes, camera and microphone use
 * (CameraManager.AvailabilityCallback / AudioManager recording callbacks: best effort), and the
 * keyguard, so the island drops message previews on the lock screen.
 */
class IslandSignals(context: Context) {

    private val ctx = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val battery = ctx.getSystemService(BatteryManager::class.java)
    private val audio = ctx.getSystemService(AudioManager::class.java)
    private val cameras = ctx.getSystemService(CameraManager::class.java)
    private val power = ctx.getSystemService(android.os.PowerManager::class.java)
    private val keyguard = ctx.getSystemService(KeyguardManager::class.java)

    private val _battery = MutableStateFlow(BatteryInfo(0, false, null, false))
    val batteryInfo: StateFlow<BatteryInfo> = _battery.asStateFlow()

    /** elapsedRealtime when the charger was last plugged in (0 = never this session). */
    private val _pluggedAt = MutableStateFlow(0L)
    val pluggedAt: StateFlow<Long> = _pluggedAt.asStateFlow()

    private val _ringer = MutableStateFlow<Pair<RingerMode, Long>?>(null)
    /** Last ringer-mode change and when it happened (elapsedRealtime). */
    val ringer: StateFlow<Pair<RingerMode, Long>?> = _ringer.asStateFlow()

    private val _cameraInUse = MutableStateFlow(false)
    val cameraInUse: StateFlow<Boolean> = _cameraInUse.asStateFlow()
    private val _micInUse = MutableStateFlow(false)
    val micInUse: StateFlow<Boolean> = _micInUse.asStateFlow()

    private val _interactive = MutableStateFlow(power?.isInteractive == true)
    val interactive: StateFlow<Boolean> = _interactive.asStateFlow()

    private val _locked = MutableStateFlow(false)
    val locked: StateFlow<Boolean> = _locked.asStateFlow()

    private val busyCameras = HashSet<String>()
    private var started = false
    private var lastVoltage = 0
    private var lastStatusCharging = false

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, intent: Intent) {
            when (intent.action) {
                Intent.ACTION_BATTERY_CHANGED -> onBattery(intent)
                Intent.ACTION_POWER_CONNECTED -> {
                    _pluggedAt.value = SystemClock.elapsedRealtime()
                    refreshBattery(charging = true)
                }
                Intent.ACTION_POWER_DISCONNECTED -> {
                    _pluggedAt.value = 0L
                    refreshBattery(charging = false)
                }
                AudioManager.RINGER_MODE_CHANGED_ACTION -> if (!isInitialStickyBroadcast) {
                    _ringer.value = ringerMode() to SystemClock.elapsedRealtime()
                }
                Intent.ACTION_SCREEN_OFF, Intent.ACTION_SCREEN_ON, Intent.ACTION_USER_PRESENT -> updateLocked()
            }
        }
    }

    private val cameraCallback = object : CameraManager.AvailabilityCallback() {
        override fun onCameraAvailable(cameraId: String) {
            busyCameras.remove(cameraId)
            _cameraInUse.value = busyCameras.isNotEmpty()
        }
        override fun onCameraUnavailable(cameraId: String) {
            busyCameras.add(cameraId)
            _cameraInUse.value = busyCameras.isNotEmpty()
        }
    }

    private val recordingCallback = object : AudioManager.AudioRecordingCallback() {
        override fun onRecordingConfigChanged(configs: MutableList<AudioRecordingConfiguration>?) {
            _micInUse.value = !configs.isNullOrEmpty()
        }
    }

    fun start() {
        if (started) return
        started = true
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED)
            addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
            addAction(Intent.ACTION_SCREEN_OFF)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        ContextCompat.registerReceiver(ctx, receiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
        try { cameras?.registerAvailabilityCallback(cameraCallback, main) } catch (_: Exception) { }
        try {
            audio?.registerAudioRecordingCallback(recordingCallback, main)
            _micInUse.value = audio?.activeRecordingConfigurations?.isNotEmpty() == true
        } catch (_: Exception) { }
        updateLocked()
    }

    fun stop() {
        if (!started) return
        started = false
        try { ctx.unregisterReceiver(receiver) } catch (_: Exception) { }
        try { cameras?.unregisterAvailabilityCallback(cameraCallback) } catch (_: Exception) { }
        try { audio?.unregisterAudioRecordingCallback(recordingCallback) } catch (_: Exception) { }
    }

    private fun ringerMode(): RingerMode = when (audio?.ringerMode) {
        AudioManager.RINGER_MODE_SILENT -> RingerMode.Silent
        AudioManager.RINGER_MODE_VIBRATE -> RingerMode.Vibrate
        else -> RingerMode.Normal
    }

    fun updateLocked() {
        _interactive.value = power?.isInteractive == true
        _locked.value = keyguard?.isKeyguardLocked == true
    }

    private fun onBattery(intent: Intent) {
        lastVoltage = intent.getIntExtra(BatteryManager.EXTRA_VOLTAGE, 0)
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        lastStatusCharging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL
        refreshBattery(lastStatusCharging || intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0)
    }

    /** Re-reads level and current; called on battery broadcasts and every second while charging is shown. */
    fun refreshBattery(charging: Boolean = _battery.value.charging) {
        val bm = battery ?: return
        val level = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY).takeIf { it in 0..100 } ?: _battery.value.level
        val current = try { bm.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) } catch (_: Exception) { Long.MIN_VALUE }
        val supported = current != Long.MIN_VALUE && current != 0L
        val watts = if (charging) ChargingEstimate.watts(current, lastVoltage, level) else null
        _battery.value = BatteryInfo(level, charging, watts, supported || _battery.value.wattsSupported)
    }

    /** For System check: does this phone report a charging current at all? */
    fun currentSensorAvailable(): Boolean {
        val v = try { battery?.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) } catch (_: Exception) { null }
        return v != null && v != Long.MIN_VALUE && v != 0L
    }
}
