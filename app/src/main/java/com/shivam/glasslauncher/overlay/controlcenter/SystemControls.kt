package com.shivam.glasslauncher.overlay.controlcenter

import android.app.NotificationManager
import android.bluetooth.BluetoothManager
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.database.ContentObserver
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.net.Uri
import android.net.wifi.WifiManager
import android.os.Handler
import android.os.Looper
import android.provider.AlarmClock
import android.provider.MediaStore
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.telephony.TelephonyManager
import android.util.Log
import com.shivam.glasslauncher.overlay.notifications.NotificationHub
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.pow
import kotlin.math.roundToInt

/** Live state of everything the Control Center shows. */
data class ControlState(
    val wifi: Boolean = false,
    val bluetooth: Boolean = false,
    val mobileData: Boolean = false,
    val airplane: Boolean = false,
    val torch: Boolean = false,
    val torchAvailable: Boolean = false,
    val dnd: Boolean = false,
    val rotationLocked: Boolean = false,
    val timeoutMs: Int = 30_000,
    /** 0..1, perceptual. */
    val brightness: Float = 0.5f,
    val autoBrightness: Boolean = false,
    /** 0..1 of the media stream. */
    val volume: Float = 0.5f,
    val canWriteSettings: Boolean = false,
    val dndAccess: Boolean = false,
)

/**
 * The system toggles behind Control Center modules (docs/CONSTRAINTS.md):
 *  - radios (Wi-Fi, Bluetooth, mobile data, airplane) can't be toggled by apps since Android 10,
 *    so they only show state and open the Settings.Panel pop-ups / settings pages;
 *  - torch via CameraManager.setTorchMode; volume via AudioManager;
 *  - brightness, rotation lock and screen timeout need WRITE_SETTINGS (granted once by the user);
 *  - Do Not Disturb needs DND access, falling back to the notification listener's
 *    requestInterruptionFilter when only notification access is on.
 */
class SystemControls(context: Context) {

    private val ctx = context.applicationContext
    private val resolver: ContentResolver = ctx.contentResolver
    private val main = Handler(Looper.getMainLooper())
    private val audio = ctx.getSystemService(AudioManager::class.java)
    private val cameras = ctx.getSystemService(CameraManager::class.java)
    private val notifications = ctx.getSystemService(NotificationManager::class.java)

    private val _state = MutableStateFlow(ControlState())
    val state: StateFlow<ControlState> = _state.asStateFlow()

    private val torchId: String? = try {
        cameras?.cameraIdList?.firstOrNull { id ->
            cameras.getCameraCharacteristics(id).get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
        }
    } catch (e: Exception) { null }
    private var torchOn = false

    private val torchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            if (cameraId == torchId) { torchOn = enabled; refresh() }
        }
    }

    private val observer = object : ContentObserver(main) {
        override fun onChange(selfChange: Boolean) = refresh()
    }

    /** Maximum of Settings.System.SCREEN_BRIGHTNESS; HyperOS builds often use more than 255. */
    private val brightnessMax: Int = try {
        val id = Resources.getSystem().getIdentifier("config_screenBrightnessSettingMaximum", "integer", "android")
        if (id != 0) Resources.getSystem().getInteger(id).coerceAtLeast(1) else 255
    } catch (e: Exception) { 255 }

    private var started = false

    fun start() {
        if (started) return
        started = true
        listOf(
            Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS),
            Settings.System.getUriFor(Settings.System.SCREEN_BRIGHTNESS_MODE),
            Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION),
            Settings.System.getUriFor(Settings.System.SCREEN_OFF_TIMEOUT),
            Settings.Global.getUriFor(Settings.Global.AIRPLANE_MODE_ON),
            Settings.Global.getUriFor("mobile_data"),
            Settings.Global.getUriFor(Settings.Global.WIFI_ON),
            Settings.Global.getUriFor(Settings.Global.BLUETOOTH_ON),
            Settings.Global.getUriFor("zen_mode"),
        ).forEach { uri -> registerSafely(uri) }
        try { cameras?.registerTorchCallback(torchCallback, main) } catch (_: Exception) { }
        refresh()
    }

    private fun registerSafely(uri: Uri?) {
        if (uri == null) return
        try { resolver.registerContentObserver(uri, false, observer) } catch (_: Exception) { }
    }

    fun stop() {
        if (!started) return
        started = false
        try { resolver.unregisterContentObserver(observer) } catch (_: Exception) { }
        try { cameras?.unregisterTorchCallback(torchCallback) } catch (_: Exception) { }
    }

    fun canWriteSettings(): Boolean = Settings.System.canWrite(ctx)
    fun hasDndAccess(): Boolean = notifications?.isNotificationPolicyAccessGranted == true

    fun refresh() {
        val wifi = try { ctx.getSystemService(WifiManager::class.java)?.isWifiEnabled == true } catch (_: Exception) { false }
        val bt = try { ctx.getSystemService(BluetoothManager::class.java)?.adapter?.isEnabled == true } catch (_: Exception) { false }
        val data = try {
            @Suppress("MissingPermission")
            ctx.getSystemService(TelephonyManager::class.java)?.isDataEnabled == true
        } catch (_: Exception) { Settings.Global.getInt(resolver, "mobile_data", 0) == 1 }
        val raw = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS, brightnessMax / 2)
        val musicMax = audio?.getStreamMaxVolume(AudioManager.STREAM_MUSIC)?.coerceAtLeast(1) ?: 1
        val filter = notifications?.currentInterruptionFilter ?: NotificationManager.INTERRUPTION_FILTER_ALL
        _state.value = ControlState(
            wifi = wifi,
            bluetooth = bt,
            mobileData = data,
            airplane = Settings.Global.getInt(resolver, Settings.Global.AIRPLANE_MODE_ON, 0) == 1,
            torch = torchOn,
            torchAvailable = torchId != null,
            dnd = filter != NotificationManager.INTERRUPTION_FILTER_ALL && filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN,
            rotationLocked = Settings.System.getInt(resolver, Settings.System.ACCELEROMETER_ROTATION, 1) == 0,
            timeoutMs = Settings.System.getInt(resolver, Settings.System.SCREEN_OFF_TIMEOUT, 30_000),
            brightness = toPerceptual(raw.toFloat() / brightnessMax),
            autoBrightness = Settings.System.getInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, 0) == Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC,
            volume = (audio?.getStreamVolume(AudioManager.STREAM_MUSIC) ?: 0).toFloat() / musicMax,
            canWriteSettings = canWriteSettings(),
            dndAccess = hasDndAccess(),
        )
    }

    // Brightness sliders feel linear to the eye when the setting follows a power curve.
    private fun toPerceptual(linear: Float) = linear.coerceIn(0f, 1f).pow(1f / 2.2f)
    private fun toLinear(perceptual: Float) = perceptual.coerceIn(0f, 1f).pow(2.2f)

    /** False when WRITE_SETTINGS is missing (the caller prompts once). */
    fun setBrightness(perceptual: Float): Boolean {
        if (!canWriteSettings()) return false
        return try {
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
            val v = (toLinear(perceptual) * brightnessMax).roundToInt().coerceIn(1, brightnessMax)
            Settings.System.putInt(resolver, Settings.System.SCREEN_BRIGHTNESS, v)
            _state.value = _state.value.copy(brightness = perceptual, autoBrightness = false)
            true
        } catch (e: Exception) {
            Log.w(TAG, "brightness", e)
            false
        }
    }

    fun setVolume(fraction: Float) {
        val a = audio ?: return
        val max = a.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        try {
            a.setStreamVolume(AudioManager.STREAM_MUSIC, (fraction.coerceIn(0f, 1f) * max).roundToInt(), 0)
            _state.value = _state.value.copy(volume = fraction.coerceIn(0f, 1f))
        } catch (e: Exception) { Log.w(TAG, "volume", e) }
    }

    fun toggleTorch(): Boolean {
        val id = torchId ?: return false
        return try {
            cameras?.setTorchMode(id, !torchOn)
            true
        } catch (e: Exception) {
            Log.w(TAG, "torch", e)
            false
        }
    }

    /** Toggles DND; returns false when neither DND access nor notification access allows it. */
    fun toggleDnd(): Boolean {
        val on = _state.value.dnd
        val target = if (on) NotificationManager.INTERRUPTION_FILTER_ALL else NotificationManager.INTERRUPTION_FILTER_PRIORITY
        if (hasDndAccess()) {
            return try {
                notifications?.setInterruptionFilter(target)
                main.postDelayed({ refresh() }, 150)
                true
            } catch (e: Exception) { false }
        }
        val listenerTarget = if (on) NotificationListenerService.INTERRUPTION_FILTER_ALL else NotificationListenerService.INTERRUPTION_FILTER_PRIORITY
        val ok = NotificationHub.requestInterruptionFilter(listenerTarget)
        if (ok) main.postDelayed({ refresh() }, 300)
        return ok
    }

    fun toggleRotationLock(): Boolean {
        if (!canWriteSettings()) return false
        return try {
            val locked = _state.value.rotationLocked
            Settings.System.putInt(resolver, Settings.System.ACCELEROMETER_ROTATION, if (locked) 1 else 0)
            true
        } catch (e: Exception) { false }
    }

    /** Steps through 15 s → 30 s → 1 → 2 → 5 → 10 min. */
    fun cycleTimeout(): Boolean {
        if (!canWriteSettings()) return false
        val steps = TIMEOUTS
        val cur = _state.value.timeoutMs
        val next = steps.firstOrNull { it > cur } ?: steps.first()
        return try {
            Settings.System.putInt(resolver, Settings.System.SCREEN_OFF_TIMEOUT, next)
            true
        } catch (e: Exception) { false }
    }

    // --- intents ---

    fun wifiPanel() = Intent(Settings.Panel.ACTION_WIFI)
    fun internetPanel() = Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)
    fun bluetoothSettings() = Intent(Settings.ACTION_BLUETOOTH_SETTINGS)
    fun airplaneSettings() = Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS)
    fun writeSettingsIntent() = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${ctx.packageName}"))
    fun dndAccessIntent() = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
    fun cameraIntent() = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA)
    fun timerIntent() = Intent(AlarmClock.ACTION_SHOW_TIMERS)

    fun calculatorIntent(): Intent? {
        val pm = ctx.packageManager
        val selector = Intent.makeMainSelectorActivity(Intent.ACTION_MAIN, Intent.CATEGORY_APP_CALCULATOR)
        if (selector.resolveActivity(pm) != null) return selector
        return CALCULATORS.firstNotNullOfOrNull { pm.getLaunchIntentForPackage(it) }
    }

    /** Google Lens if installed (it reads QR codes), else the camera app, which scans QR codes on HyperOS. */
    fun qrIntent(): Intent {
        val pm = ctx.packageManager
        pm.getLaunchIntentForPackage("com.google.ar.lens")?.let { return it }
        val lens = Intent(Intent.ACTION_VIEW, Uri.parse("google://lens")).setPackage("com.google.android.googlequicksearchbox")
        if (lens.resolveActivity(pm) != null) return lens
        pm.getLaunchIntentForPackage("com.xiaomi.scanner")?.let { return it }
        return cameraIntent()
    }

    companion object {
        private const val TAG = "GlassControls"
        val TIMEOUTS = listOf(15_000, 30_000, 60_000, 120_000, 300_000, 600_000)
        private val CALCULATORS = listOf("com.miui.calculator", "com.google.android.calculator", "com.android.calculator2", "com.sec.android.app.popupcalculator")

        fun timeoutLabel(ms: Int): String = when {
            ms < 60_000 -> "${ms / 1000} s"
            ms < 3_600_000 -> "${ms / 60_000} min"
            else -> "Never"
        }
    }
}
