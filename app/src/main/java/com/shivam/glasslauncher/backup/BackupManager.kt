package com.shivam.glasslauncher.backup

import android.net.Uri
import android.util.AtomicFile
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.shivam.glasslauncher.GlassApp
import com.shivam.glasslauncher.icons.IconOverrideStore
import com.shivam.glasslauncher.polish.polishStore
import com.shivam.glasslauncher.data.settingsStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Backup / restore through the Storage Access Framework: one JSON file holding the home layout,
 * every setting (including the hidden and locked lists), per-app icons and pinned shortcuts.
 * The wallpaper bitmap itself is not included; the chosen Glass wallpaper id is (in "polish").
 */
class BackupManager(private val app: GlassApp) {

    private val stores: Map<String, DataStore<Preferences>> get() = mapOf("settings" to app.settingsStore, "polish" to app.polishStore)

    suspend fun create(): BackupData = withContext(Dispatchers.IO) {
        val files = app.filesDir.listFiles { f -> f.isFile && f.name.endsWith(".json") && f.length() < 4 * 1024 * 1024 }
            .orEmpty().associate { it.name to it.readText() }
        val prefs = stores.mapValues { (_, store) ->
            store.data.first().asMap().mapNotNull { (k, v) -> if (v is Boolean || v is Int || v is Long || v is Float || v is Double || v is String || v is Set<*>) PrefEntry(k.name, v) else null }
        }
        val icons = File(app.filesDir, IconOverrideStore.CUSTOM_DIR).listFiles().orEmpty()
            .filter { it.isFile && it.name.endsWith(".png") }.associate { it.name to it.readBytes() }
        val version = try { app.packageManager.getPackageInfo(app.packageName, 0).versionName } catch (e: Exception) { null } ?: "?"
        BackupData(System.currentTimeMillis(), version, files, prefs, icons)
    }

    suspend fun write(uri: Uri, data: BackupData) = withContext(Dispatchers.IO) {
        val out = app.contentResolver.openOutputStream(uri, "wt") ?: error("Can't write to the chosen file")
        out.use { it.write(BackupCodec.encode(data).toByteArray()) }
    }

    suspend fun read(uri: Uri): BackupData = withContext(Dispatchers.IO) {
        val input = app.contentResolver.openInputStream(uri) ?: throw BackupFormatException("Can't open the chosen file")
        val bytes = input.use { s ->
            val buf = java.io.ByteArrayOutputStream()
            val chunk = ByteArray(64 * 1024)
            var total = 0L
            while (true) {
                val n = s.read(chunk)
                if (n < 0) break
                total += n
                if (total > BackupCodec.MAX_BYTES) throw BackupFormatException("File is too large to be a Glass backup")
                buf.write(chunk, 0, n)
            }
            buf.toByteArray()
        }
        BackupCodec.decode(String(bytes))
    }

    /** Replaces the current setup with [data], then reloads everything that caches it. */
    suspend fun restore(data: BackupData) = withContext(Dispatchers.IO) {
        for ((name, store) in stores) {
            val entries = data.prefs[name] ?: continue
            store.edit { p ->
                p.clear()
                entries.forEach { e ->
                    when (val v = e.value) {
                        is Boolean -> p[booleanPreferencesKey(e.name)] = v
                        is Int -> p[intPreferencesKey(e.name)] = v
                        is Long -> p[longPreferencesKey(e.name)] = v
                        is Float -> p[floatPreferencesKey(e.name)] = v
                        is Double -> p[doublePreferencesKey(e.name)] = v
                        is String -> p[stringPreferencesKey(e.name)] = v
                        is Set<*> -> p[stringSetPreferencesKey(e.name)] = v.map { it.toString() }.toSet()
                    }
                }
            }
        }
        data.files.forEach { (name, text) ->
            if (!BackupCodec.safeName(name) || !name.endsWith(".json")) return@forEach
            val f = AtomicFile(File(app.filesDir, name))
            val out = f.startWrite()
            try { out.write(text.toByteArray()); f.finishWrite(out) } catch (e: Exception) { f.failWrite(out); throw e }
        }
        val dir = File(app.filesDir, IconOverrideStore.CUSTOM_DIR).apply { mkdirs() }
        data.customIcons.forEach { (name, bytes) -> if (BackupCodec.safeName(name)) File(dir, name).writeBytes(bytes) }

        app.layout.load()
        app.polish.iconOverrides.load()
        app.polish.shortcuts.load()
        app.icons.invalidatePackage(null)
    }
}
