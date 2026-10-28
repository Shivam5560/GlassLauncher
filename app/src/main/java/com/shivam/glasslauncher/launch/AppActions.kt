package com.shivam.glasslauncher.launch

import android.app.Activity
import android.app.ActivityOptions
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Rect
import android.hardware.biometrics.BiometricManager
import android.hardware.biometrics.BiometricPrompt
import android.net.Uri
import android.os.CancellationSignal
import android.util.Log
import com.shivam.glasslauncher.data.AppEntry

/** Launching apps and shortcuts, app info/uninstall, and the biometric gate for locked apps. */
class AppActions(private val context: Context) {

    private val launcherApps = context.getSystemService(LauncherApps::class.java)

    /** Launches [entry] with a zoom-out-of-the-icon animation from [bounds] (window coordinates). */
    fun launch(activity: Activity, entry: AppEntry, bounds: Rect?): Boolean {
        val opts = bounds?.let {
            ActivityOptions.makeScaleUpAnimation(activity.window.decorView, it.left, it.top, it.width(), it.height())
        }
        return try {
            launcherApps.startMainActivity(entry.component, entry.user, bounds, opts?.toBundle())
            true
        } catch (e: Exception) {
            Log.w(TAG, "Launch failed for ${entry.key}", e)
            false
        }
    }

    /**
     * Shows the system BiometricPrompt (fingerprint/face, or the device PIN as a fallback) and
     * calls [onSuccess] when the user authenticates.
     */
    fun authenticate(activity: Activity, title: String, onSuccess: () -> Unit, onFail: (String) -> Unit) {
        val bm = activity.getSystemService(BiometricManager::class.java)
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        if (bm != null && bm.canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            // No lock screen configured: nothing to check against, so allow the launch.
            onSuccess()
            return
        }
        val prompt = BiometricPrompt.Builder(activity)
            .setTitle(title)
            .setSubtitle("Glass Launcher")
            .setAllowedAuthenticators(authenticators)
            .setConfirmationRequired(false)
            .build()
        prompt.authenticate(CancellationSignal(), activity.mainExecutor, object : BiometricPrompt.AuthenticationCallback() {
            override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult?) = onSuccess()
            override fun onAuthenticationError(errorCode: Int, errString: CharSequence?) = onFail(errString?.toString() ?: "Cancelled")
        })
    }

    fun hasShortcutAccess(): Boolean = try { launcherApps.hasShortcutHostPermission() } catch (e: Exception) { false }

    /** App shortcuts (manifest, dynamic, pinned) - only available while this is the default home app. */
    fun shortcuts(entry: AppEntry): List<ShortcutInfo> {
        if (!hasShortcutAccess()) return emptyList()
        val query = LauncherApps.ShortcutQuery()
            .setPackage(entry.packageName)
            .setActivity(entry.component)
            .setQueryFlags(
                LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                    LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED,
            )
        return try {
            (launcherApps.getShortcuts(query, entry.user) ?: emptyList())
                .filter { it.isEnabled }
                .sortedWith(compareBy<ShortcutInfo>({ if (it.isDeclaredInManifest) 0 else 1 }, { it.rank }))
                .take(4)
        } catch (e: Exception) {
            Log.w(TAG, "getShortcuts failed", e)
            emptyList()
        }
    }

    fun shortcutIcon(info: ShortcutInfo) = try {
        launcherApps.getShortcutIconDrawable(info, context.resources.displayMetrics.densityDpi)
    } catch (e: Exception) { null }

    fun startShortcut(info: ShortcutInfo, bounds: Rect?) {
        try {
            launcherApps.startShortcut(info, bounds, null)
        } catch (e: Exception) {
            Log.w(TAG, "startShortcut failed", e)
        }
    }

    fun appInfo(entry: AppEntry) {
        try {
            launcherApps.startAppDetailsActivity(entry.component, entry.user, null, null)
        } catch (e: Exception) {
            Log.w(TAG, "App info failed", e)
        }
    }

    fun uninstall(activity: Activity, entry: AppEntry) {
        val intent = Intent(Intent.ACTION_DELETE, Uri.parse("package:${entry.packageName}"))
            .putExtra(Intent.EXTRA_USER, entry.user)
        try { activity.startActivity(intent) } catch (e: ActivityNotFoundException) { appInfo(entry) }
    }

    fun call(activity: Activity, number: String) {
        val granted = activity.checkSelfPermission(android.Manifest.permission.CALL_PHONE) == android.content.pm.PackageManager.PERMISSION_GRANTED
        val intent = Intent(if (granted) Intent.ACTION_CALL else Intent.ACTION_DIAL, Uri.parse("tel:" + Uri.encode(number)))
        try { activity.startActivity(intent) } catch (e: Exception) { Log.w(TAG, "call failed", e) }
    }

    fun whatsApp(activity: Activity, number: String) {
        val digits = number.filter { it.isDigit() || it == '+' }.removePrefix("+")
        val uri = Uri.parse("https://wa.me/$digits")
        val pm = activity.packageManager
        val pkg = listOf("com.whatsapp", "com.whatsapp.w4b").firstOrNull { pm.getLaunchIntentForPackage(it) != null }
        val intent = Intent(Intent.ACTION_VIEW, uri).apply { if (pkg != null) setPackage(pkg) }
        try { activity.startActivity(intent) } catch (e: Exception) { activity.startActivity(Intent(Intent.ACTION_VIEW, uri)) }
    }

    fun webSearch(activity: Activity, query: String) {
        val uri = Uri.parse("https://www.google.com/search?q=" + Uri.encode(query))
        try { activity.startActivity(Intent(Intent.ACTION_VIEW, uri)) } catch (e: Exception) { Log.w(TAG, "No browser", e) }
    }

    fun startIntent(activity: Activity, intent: Intent): Boolean = try {
        activity.startActivity(intent)
        true
    } catch (e: Exception) {
        false
    }

    companion object { private const val TAG = "GlassActions" }
}
