package com.shivam.glasslauncher.widgets

import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetHostView
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.ComponentName
import android.content.Context
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.os.UserHandle
import android.os.UserManager
import android.util.Log
import android.util.SizeF
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.ConcurrentHashMap

/** A third-party widget provider as shown in the widget picker. */
data class ProviderEntry(
    val info: AppWidgetProviderInfo,
    val label: String,
    val appLabel: String,
    val packageName: String,
    val user: UserHandle,
    val isWorkProfile: Boolean,
)

/**
 * Hosts third-party Android widgets (docs/CONSTRAINTS.md: AppWidgetHost works; some widgets need
 * the bind-permission dialog the first time). One process-wide host; it listens for updates while
 * the launcher is started.
 */
class GlassWidgetHost private constructor(private val context: Context) {

    val manager: AppWidgetManager = AppWidgetManager.getInstance(context)
    private val host = object : AppWidgetHost(context, HOST_ID) {
        override fun onCreateView(context: Context, appWidgetId: Int, appWidget: AppWidgetProviderInfo): AppWidgetHostView = GlassAppWidgetHostView(context)
    }
    private var listeners = 0

    fun startListening() {
        if (listeners++ == 0) try { host.startListening() } catch (e: Exception) { Log.w(TAG, "startListening failed", e) }
    }

    fun stopListening() {
        if (--listeners <= 0) {
            listeners = 0
            try { host.stopListening() } catch (e: Exception) { Log.w(TAG, "stopListening failed", e) }
        }
    }

    fun allocateId(): Int = host.allocateAppWidgetId()

    fun deleteId(id: Int) {
        try { host.deleteAppWidgetId(id) } catch (e: Exception) { Log.w(TAG, "deleteAppWidgetId failed", e) }
    }

    /** Frees ids the system still has bound to this host but no widget instance uses. */
    fun deleteUnused(live: Set<Int>) {
        val ids = try { host.appWidgetIds } catch (e: Exception) { return }
        ids.filter { it !in live }.forEach { deleteId(it) }
    }

    fun info(id: Int): AppWidgetProviderInfo? = try { manager.getAppWidgetInfo(id) } catch (e: Exception) { null }

    fun createView(viewContext: Context, id: Int, info: AppWidgetProviderInfo): AppWidgetHostView =
        host.createView(viewContext, id, info)

    /** Tries to bind silently; false means the user must allow it in the system bind dialog. */
    fun bindIfAllowed(id: Int, info: AppWidgetProviderInfo): Boolean = try {
        manager.bindAppWidgetIdIfAllowed(id, info.profile, info.provider, null)
    } catch (e: Exception) {
        Log.w(TAG, "bindAppWidgetIdIfAllowed failed", e)
        false
    }

    fun startConfigure(activity: android.app.Activity, id: Int, requestCode: Int): Boolean = try {
        host.startAppWidgetConfigureActivityForResult(activity, id, 0, requestCode, null)
        true
    } catch (e: Exception) {
        Log.w(TAG, "configure activity failed", e)
        false
    }

    /** Every widget provider across the user's profiles (work profile included), by app then label. */
    fun providers(): List<ProviderEntry> {
        val um = context.getSystemService(UserManager::class.java)
        val pm = context.packageManager
        val me = android.os.Process.myUserHandle()
        val profiles = try { um.userProfiles } catch (e: Exception) { listOf(me) }
        return profiles.flatMap { user ->
            val list = try { manager.getInstalledProvidersForProfile(user) } catch (e: Exception) { emptyList() }
            list.map { info ->
                val pkg = info.provider.packageName
                val appLabel = try { pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString() } catch (e: Exception) { pkg }
                ProviderEntry(
                    info = info,
                    label = try { info.loadLabel(pm) } catch (e: Exception) { null } ?: info.provider.shortClassName,
                    appLabel = appLabel,
                    packageName = pkg,
                    user = user,
                    isWorkProfile = user != me,
                )
            }
        }.sortedWith(compareBy({ it.appLabel.lowercase() }, { it.label.lowercase() }))
    }

    fun preview(info: AppWidgetProviderInfo): Drawable? {
        val density = context.resources.displayMetrics.densityDpi
        return try { info.loadPreviewImage(context, density) } catch (e: Exception) { null }
            ?: try { info.loadIcon(context, density) } catch (e: Exception) { null }
    }

    /** Tells the widget how big it is, in dp, so it can pick a layout. */
    fun updateSize(view: AppWidgetHostView, widthDp: Float, heightDp: Float) {
        try {
            val options = Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp.toInt())
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, widthDp.toInt())
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp.toInt())
                putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, heightDp.toInt())
            }
            view.updateAppWidgetSize(options, listOf(SizeF(widthDp, heightDp)))
        } catch (e: Exception) {
            Log.w(TAG, "updateAppWidgetSize failed", e)
        }
    }

    companion object {
        private const val TAG = "GlassWidgetHost"
        const val HOST_ID = 0x61A55

        @Volatile private var instance: GlassWidgetHost? = null

        fun get(context: Context): GlassWidgetHost = instance ?: synchronized(this) {
            instance ?: GlassWidgetHost(context.applicationContext).also { instance = it }
        }

        /** Grid span for a provider's default size on a grid with cells of [cellWDp] x [cellHDp]. */
        fun defaultSpan(info: AppWidgetProviderInfo, cellWDp: Float, cellHDp: Float, density: Float): Pair<Int, Int> {
            if (android.os.Build.VERSION.SDK_INT >= 31 && info.targetCellWidth > 0 && info.targetCellHeight > 0) {
                return info.targetCellWidth.coerceIn(1, 4) to info.targetCellHeight.coerceIn(1, 6)
            }
            val wDp = info.minWidth / density
            val hDp = info.minHeight / density
            val w = kotlin.math.ceil(wDp / cellWDp).toInt().coerceIn(1, 4)
            val h = kotlin.math.ceil(hDp / cellHDp).toInt().coerceIn(1, 6)
            return w to h
        }

        /** Smallest span a provider allows when resizing (falls back to its default minimum). */
        fun minSpan(info: AppWidgetProviderInfo, cellWDp: Float, cellHDp: Float, density: Float): Pair<Int, Int> {
            val mw = (if (info.minResizeWidth > 0) info.minResizeWidth else info.minWidth) / density
            val mh = (if (info.minResizeHeight > 0) info.minResizeHeight else info.minHeight) / density
            return kotlin.math.ceil(mw / cellWDp).toInt().coerceIn(1, 4) to kotlin.math.ceil(mh / cellHDp).toInt().coerceIn(1, 6)
        }

        fun canResize(info: AppWidgetProviderInfo, horizontal: Boolean): Boolean {
            val mode = info.resizeMode
            return if (horizontal) mode and AppWidgetProviderInfo.RESIZE_HORIZONTAL != 0 else mode and AppWidgetProviderInfo.RESIZE_VERTICAL != 0
        }

        fun isReconfigurable(info: AppWidgetProviderInfo): Boolean =
            info.configure != null && info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE != 0

        fun needsConfigure(info: AppWidgetProviderInfo): Boolean {
            if (info.configure == null) return false
            if (android.os.Build.VERSION.SDK_INT >= 31 &&
                info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_CONFIGURATION_OPTIONAL != 0
            ) return false
            return true
        }

        fun providerKey(component: ComponentName) = component.flattenToShortString()
    }
}

/**
 * Hand-off between the launcher and [WidgetBindActivity]: the launcher registers a pending request
 * and awaits its result while the trampoline shows the system bind dialog and the widget's
 * configure activity.
 */
object WidgetBinding {
    enum class Mode { BIND_AND_CONFIGURE, CONFIGURE }

    data class Request(val appWidgetId: Int, val mode: Mode)

    private val pending = ConcurrentHashMap<Int, Pair<Request, CompletableDeferred<Boolean>>>()

    fun register(request: Request): CompletableDeferred<Boolean> {
        val d = CompletableDeferred<Boolean>()
        pending[request.appWidgetId]?.second?.complete(false)
        pending[request.appWidgetId] = request to d
        return d
    }

    fun request(id: Int): Request? = pending[id]?.first

    fun finish(id: Int, ok: Boolean) {
        pending.remove(id)?.second?.complete(ok)
    }
}
