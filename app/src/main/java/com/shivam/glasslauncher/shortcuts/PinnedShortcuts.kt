package com.shivam.glasslauncher.shortcuts

import android.content.Context
import android.content.pm.LauncherApps
import android.content.pm.ShortcutInfo
import android.graphics.Rect
import android.os.Process
import android.os.UserManager
import android.util.AtomicFile
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** A shortcut another app asked us to pin (LauncherApps.PinItemRequest). */
data class PinnedShortcut(val packageName: String, val id: String, val label: String, val userSerial: Long)

/**
 * Pinned shortcuts accepted through [PinShortcutActivity], persisted in `files/pinned_shortcuts.json`.
 * The list is exposed as [shortcuts] so the home grid can place them; Settings → Customise →
 * Pinned shortcuts lists, opens and removes them.
 */
class PinnedShortcutStore(private val context: Context, private val scope: CoroutineScope) {

    private val file = AtomicFile(File(context.filesDir, FILE_NAME))
    private val launcherApps = context.getSystemService(LauncherApps::class.java)
    private val users = context.getSystemService(UserManager::class.java)

    private val _shortcuts = MutableStateFlow<List<PinnedShortcut>>(emptyList())
    val shortcuts: StateFlow<List<PinnedShortcut>> = _shortcuts.asStateFlow()

    fun load() {
        scope.launch(Dispatchers.IO) {
            _shortcuts.value = try {
                if (file.baseFile.exists()) decode(String(file.readFully())) else emptyList()
            } catch (e: Exception) {
                Log.w(TAG, "Bad pinned shortcuts file", e)
                emptyList()
            }
        }
    }

    fun add(info: ShortcutInfo) {
        val item = PinnedShortcut(info.`package`, info.id, (info.shortLabel ?: info.longLabel ?: info.id).toString(), users.getSerialNumberForUser(info.userHandle))
        update { list -> list.filterNot { it.packageName == item.packageName && it.id == item.id && it.userSerial == item.userSerial } + item }
    }

    fun remove(item: PinnedShortcut) {
        val remaining = _shortcuts.value.filter { it.packageName == item.packageName && it.userSerial == item.userSerial && it.id != item.id }.map { it.id }
        update { list -> list - item }
        // Tell the system we no longer pin it (only possible while we are the default home app).
        try {
            val user = users.getUserForSerialNumber(item.userSerial) ?: Process.myUserHandle()
            launcherApps.pinShortcuts(item.packageName, remaining, user)
        } catch (e: Exception) {
            Log.w(TAG, "Could not unpin ${item.id}", e)
        }
    }

    fun start(item: PinnedShortcut, bounds: Rect?): Boolean = try {
        val user = users.getUserForSerialNumber(item.userSerial) ?: Process.myUserHandle()
        launcherApps.startShortcut(item.packageName, item.id, bounds, null, user)
        true
    } catch (e: Exception) {
        Log.w(TAG, "Could not start ${item.id}", e)
        false
    }

    /** The live ShortcutInfo (for its icon); null when we are not the default home app. */
    fun info(item: PinnedShortcut): ShortcutInfo? = try {
        val user = users.getUserForSerialNumber(item.userSerial) ?: Process.myUserHandle()
        val q = LauncherApps.ShortcutQuery().setPackage(item.packageName).setShortcutIds(listOf(item.id))
            .setQueryFlags(LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED)
        launcherApps.getShortcuts(q, user)?.firstOrNull()
    } catch (e: Exception) {
        null
    }

    private fun update(transform: (List<PinnedShortcut>) -> List<PinnedShortcut>) {
        scope.launch(Dispatchers.IO) {
            val next = transform(_shortcuts.value)
            _shortcuts.value = next
            val out = file.startWrite()
            try {
                out.write(encode(next).toByteArray())
                file.finishWrite(out)
            } catch (e: Exception) {
                file.failWrite(out)
            }
        }
    }

    companion object {
        const val FILE_NAME = "pinned_shortcuts.json"
        private const val TAG = "GlassShortcuts"

        fun encode(list: List<PinnedShortcut>): String = JSONArray(list.map {
            JSONObject().put("pkg", it.packageName).put("id", it.id).put("label", it.label).put("user", it.userSerial)
        }).toString()

        fun decode(text: String): List<PinnedShortcut> {
            val arr = JSONArray(text)
            return (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                PinnedShortcut(o.getString("pkg"), o.getString("id"), o.optString("label"), o.optLong("user"))
            }
        }
    }
}
