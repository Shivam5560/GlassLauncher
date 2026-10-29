package com.shivam.glasslauncher.widgets

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle

/**
 * Invisible trampoline for adding a third-party widget: shows the system "allow Glass to create
 * widgets" dialog when silent binding is refused, then the widget's configure activity (through
 * the host, so non-exported configure activities work too), and reports the outcome to
 * [WidgetBinding]. Keeps the launcher activity free of activity-result plumbing.
 */
class WidgetBindActivity : Activity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var reported = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appWidgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        if (savedInstanceState != null) return // results arrive in onActivityResult
        val request = WidgetBinding.request(appWidgetId)
        if (request == null) { finish(); return }
        val host = GlassWidgetHost.get(this)
        when (request.mode) {
            WidgetBinding.Mode.CONFIGURE -> configure()
            WidgetBinding.Mode.BIND_AND_CONFIGURE -> {
                val info = androidx.core.content.IntentCompat.getParcelableExtra(intent, EXTRA_INFO, android.appwidget.AppWidgetProviderInfo::class.java)
                if (info == null) { done(false); return }
                if (host.bindIfAllowed(appWidgetId, info)) {
                    afterBind(info)
                } else {
                    val bind = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, info.provider)
                        .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER_PROFILE, info.profile)
                    try {
                        startActivityForResult(bind, REQUEST_BIND)
                    } catch (e: Exception) {
                        done(false)
                    }
                }
            }
        }
    }

    private fun afterBind(info: android.appwidget.AppWidgetProviderInfo) {
        if (GlassWidgetHost.needsConfigure(info)) configure() else done(true)
    }

    private fun configure() {
        if (!GlassWidgetHost.get(this).startConfigure(this, appWidgetId, REQUEST_CONFIGURE)) done(false)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            REQUEST_BIND -> {
                val info = GlassWidgetHost.get(this).info(appWidgetId)
                if (resultCode == RESULT_OK && info != null) afterBind(info) else done(false)
            }
            REQUEST_CONFIGURE -> {
                val mode = WidgetBinding.request(appWidgetId)?.mode
                // Reconfiguring never removes the widget; cancelling the first configure does.
                done(resultCode == RESULT_OK || mode == WidgetBinding.Mode.CONFIGURE)
            }
        }
    }

    private fun done(ok: Boolean) {
        if (!reported) {
            reported = true
            WidgetBinding.finish(appWidgetId, ok)
        }
        finish()
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isFinishing && !reported) WidgetBinding.finish(appWidgetId, false)
    }

    companion object {
        private const val REQUEST_BIND = 1
        private const val REQUEST_CONFIGURE = 2
        private const val EXTRA_INFO = "glass.widget.info"

        fun intent(context: Context, appWidgetId: Int, info: android.appwidget.AppWidgetProviderInfo?): Intent =
            Intent(context, WidgetBindActivity::class.java)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                .putExtra(EXTRA_INFO, info)
                .addFlags(Intent.FLAG_ACTIVITY_NO_ANIMATION)
    }
}
