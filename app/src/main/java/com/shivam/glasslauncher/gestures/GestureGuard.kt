package com.shivam.glasslauncher.gestures

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Process
import android.provider.Settings
import com.shivam.glasslauncher.GlassApp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Keeps Glass Gestures alive, since without it there is no Home or Back:
 *  - a service the user left on but that isn't bound (crashed, or dropped from Android's list by a
 *    force-stop) is re-registered on unlock and when Home resumes;
 *  - after a payment-app pause it comes back as soon as another app is in front (usage access),
 *    or when the screen turns off if Glass can't see the foreground app.
 * Needs WRITE_SECURE_SETTINGS; a service the user switched off (a clean onDestroy) is left alone.
 */
object GestureGuard {

    private var watch: Job? = null

    /** Registered once per process (GlassApp.onCreate). */
    fun register(app: GlassApp) {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_USER_PRESENT)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        androidx.core.content.ContextCompat.registerReceiver(app, object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) {
                when (intent.action) {
                    Intent.ACTION_SCREEN_OFF -> if (!canSeeForeground(context)) GlassGestureService.resumeAfterPaymentApp(context)
                    Intent.ACTION_USER_PRESENT -> ensureRunning(context)
                }
            }
        }, filter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        if (isPaused(app)) watchPaymentExit(app)
    }

    /** Re-registers the service when the setting says on but nothing is bound. */
    /** Whether the user wants the service on. A force-stop removes it from Android's list without telling us. */
    fun setWanted(context: Context, on: Boolean) {
        val p = prefs(context)
        if (!on && p.getBoolean("paused", false)) return
        p.edit().putBoolean("wanted", on).apply()
    }

    fun ensureRunning(context: Context) {
        if (GlassGestureService.instance != null || isPaused(context)) return
        if (!GlassGestureService.isEnabled(context) && !prefs(context).getBoolean("wanted", false)) return
        if (context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) != PackageManager.PERMISSION_GRANTED) return
        try {
            val cr = context.contentResolver
            val me = ComponentName(context, GlassGestureService::class.java)
            val current = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES).orEmpty()
            val others = current.split(':').filter { it.isNotBlank() && ComponentName.unflattenFromString(it) != me }
            // Writing the list without and then with us makes Android drop the "crashed" mark and bind again.
            Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, others.joinToString(":"))
            Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES, (others + me.flattenToString()).joinToString(":"))
            Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1)
        } catch (_: RuntimeException) { }
    }

    /** While paused, polls the foreground app and resumes once it's no longer a payment app. */
    fun watchPaymentExit(context: Context) {
        val app = GlassApp.from(context)
        if (!canSeeForeground(app)) return
        watch?.cancel()
        watch = app.scope.launch {
            val payment = app.settings.state.filterNotNull().first().gestures.pauseForApps
            // ponytail: 2 s poll only while paused; a usage-events callback would need a bound service.
            while (isPaused(app)) {
                delay(2_000)
                val front = foregroundPackage(app) ?: continue
                if (front !in payment && front != app.packageName) GlassGestureService.resumeAfterPaymentApp(app)
            }
        }
    }

    private fun prefs(context: Context) = context.getSharedPreferences("gesture_pause", Context.MODE_PRIVATE)
    private fun isPaused(context: Context) = prefs(context).getBoolean("paused", false)

    private fun canSeeForeground(context: Context): Boolean {
        val ops = context.getSystemService(AppOpsManager::class.java) ?: return false
        return ops.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName) == AppOpsManager.MODE_ALLOWED
    }

    /** The app most recently brought to front in the last minute, from usage events. */
    private fun foregroundPackage(context: Context): String? {
        val usm = context.getSystemService(UsageStatsManager::class.java) ?: return null
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(now - 60_000, now)
        val e = UsageEvents.Event()
        var last: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.ACTIVITY_RESUMED) last = e.packageName
        }
        return last
    }
}
