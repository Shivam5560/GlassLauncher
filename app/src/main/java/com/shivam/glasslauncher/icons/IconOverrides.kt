package com.shivam.glasslauncher.icons

import android.content.Context
import android.graphics.Bitmap
import android.util.AtomicFile
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest

/** A per-app icon chosen with long-press → Edit Icon. */
sealed interface IconOverride {
    /** A drawable from any installed icon pack. */
    data class Pack(val packageName: String, val drawable: String) : IconOverride
    /** A picture the user picked, saved as `files/custom_icons/<file>`. */
    data class Custom(val file: String) : IconOverride
}

/** Persists icon overrides (app key → override) in `files/icon_overrides.json`. */
class IconOverrideStore(private val context: Context, private val scope: CoroutineScope) {

    val file = AtomicFile(File(context.filesDir, FILE_NAME))
    val customDir: File get() = File(context.filesDir, CUSTOM_DIR).apply { mkdirs() }

    private val _overrides = MutableStateFlow<Map<String, IconOverride>>(emptyMap())
    val overrides: StateFlow<Map<String, IconOverride>> = _overrides.asStateFlow()

    fun load() {
        scope.launch(Dispatchers.IO) { _overrides.value = read() }
    }

    private fun read(): Map<String, IconOverride> = try {
        if (file.baseFile.exists()) decode(String(file.readFully())) else emptyMap()
    } catch (e: Exception) {
        Log.w("GlassIcons", "Bad icon overrides, ignoring", e)
        emptyMap()
    }

    fun set(appKey: String, override: IconOverride?) {
        scope.launch(Dispatchers.IO) {
            val next = _overrides.value.toMutableMap()
            if (override == null) next.remove(appKey) else next[appKey] = override
            _overrides.value = next
            write(next)
            cleanUnusedCustom(next)
        }
    }

    /** Saves a square, centre-cropped copy of [bitmap] and returns its file name. Blocking. */
    fun saveCustom(bitmap: Bitmap): String {
        val side = minOf(bitmap.width, bitmap.height)
        val cropped = Bitmap.createBitmap(bitmap, (bitmap.width - side) / 2, (bitmap.height - side) / 2, side, side)
        val scaled = Bitmap.createScaledBitmap(cropped, CUSTOM_SIZE, CUSTOM_SIZE, true)
        val bytes = java.io.ByteArrayOutputStream().use { scaled.compress(Bitmap.CompressFormat.PNG, 100, it); it.toByteArray() }
        val name = sha1(bytes).take(16) + ".png"
        File(customDir, name).writeBytes(bytes)
        return name
    }

    private fun write(map: Map<String, IconOverride>) {
        val out = file.startWrite()
        try {
            out.write(encode(map).toByteArray())
            file.finishWrite(out)
        } catch (e: Exception) {
            file.failWrite(out)
            Log.e("GlassIcons", "Failed to save icon overrides", e)
        }
    }

    private fun cleanUnusedCustom(map: Map<String, IconOverride>) {
        val used = map.values.filterIsInstance<IconOverride.Custom>().map { it.file }.toSet()
        customDir.listFiles()?.filter { it.name !in used }?.forEach { it.delete() }
    }

    companion object {
        const val FILE_NAME = "icon_overrides.json"
        const val CUSTOM_DIR = "custom_icons"
        private const val CUSTOM_SIZE = 256

        fun encode(map: Map<String, IconOverride>): String {
            val root = JSONObject()
            map.forEach { (key, o) ->
                root.put(key, when (o) {
                    is IconOverride.Pack -> JSONObject().put("t", "pack").put("pkg", o.packageName).put("d", o.drawable)
                    is IconOverride.Custom -> JSONObject().put("t", "custom").put("f", o.file)
                })
            }
            return root.toString()
        }

        fun decode(text: String): Map<String, IconOverride> {
            val root = JSONObject(text)
            val out = LinkedHashMap<String, IconOverride>()
            for (key in root.keys()) {
                val o = root.optJSONObject(key) ?: continue
                when (o.optString("t")) {
                    "pack" -> out[key] = IconOverride.Pack(o.getString("pkg"), o.getString("d"))
                    "custom" -> out[key] = IconOverride.Custom(File(o.getString("f")).name)
                }
            }
            return out
        }

        private fun sha1(bytes: ByteArray) = MessageDigest.getInstance("SHA-1").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
