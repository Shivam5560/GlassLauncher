package com.shivam.glasslauncher.overlay

import android.content.Context
import android.content.Intent

/** What the Control Center and Notification Center panels can ask of the window that hosts them. */
interface PanelHost {
    val context: Context
    fun close()
    /** Starts [intent] as a new task and closes the panel; false if nothing handles it. */
    fun launch(intent: Intent?): Boolean
    /** The full HyperOS quick settings (GLOBAL_ACTION_QUICK_SETTINGS). */
    fun openSystemQuickSettings()
    /** Hands the panel to the other one (Control Center ⇄ Notification Center). */
    fun switchPanel()
}
