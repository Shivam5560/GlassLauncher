package com.shivam.glasslauncher.polish

import android.app.Activity
import android.os.Bundle
import com.shivam.glasslauncher.GlassApp
import com.shivam.glasslauncher.icons.IconPack
import com.shivam.glasslauncher.settings.SettingsActivity
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull

/**
 * `com.shivam.glasslauncher.action.APPLY_ICON_PACK` with the pack's package name in
 * [EXTRA_ICON_PACK]: sent by Glass Icons' "Apply to Glass Launcher" button (any installed icon
 * pack may use it). Only real, installed icon packs are accepted. Opens the icon style screen so
 * the change is visible and can be undone.
 */
class ApplyIconPackActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val pkg = intent.getStringExtra(EXTRA_ICON_PACK)
        val ok = pkg != null && IconPack.installed(this).any { it.packageName == pkg }
        if (ok) {
            // Write synchronously: this activity finishes at once and HyperOS may kill a process
            // that was only started for it, which used to lose an asynchronous DataStore write.
            runBlocking {
                withTimeoutOrNull(3000) { GlassApp.from(this@ApplyIconPackActivity).settings.setIconPackNow(pkg!!) }
            }
        }
        setResult(if (ok) RESULT_OK else RESULT_CANCELED)
        startActivity(SettingsActivity.intent(this, if (ok) "iconstyle" else "iconpack"))
        finish()
    }

    companion object {
        const val ACTION_APPLY_ICON_PACK = "com.shivam.glasslauncher.action.APPLY_ICON_PACK"
        const val EXTRA_ICON_PACK = "com.shivam.glasslauncher.extra.ICON_PACK"
    }
}
