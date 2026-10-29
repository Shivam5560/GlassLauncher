package com.shivam.glasslauncher.backup

import org.json.JSONArray
import org.json.JSONObject
import java.util.Base64

/** One DataStore preference, type-tagged so it can be written back with the right key type. */
data class PrefEntry(val name: String, val value: Any) {
    val type: String get() = when (value) {
        is Boolean -> "b"
        is Int -> "i"
        is Long -> "l"
        is Float -> "f"
        is Double -> "d"
        is String -> "s"
        is Set<*> -> "ss"
        else -> error("Unsupported preference type ${value::class.java}")
    }
}

/**
 * Everything a Glass backup holds. Kept free of Android types so it round-trips in JVM tests.
 *
 * @param files small JSON files from the app's files dir, by name (home layout, icon overrides,
 *   pinned shortcuts and any `*.json` state other phases keep there), stored as text.
 * @param prefs DataStore preferences by store name ("settings", "polish").
 * @param customIcons PNGs picked for per-app icons, by file name.
 */
data class BackupData(
    val createdAt: Long,
    val appVersion: String,
    val files: Map<String, String>,
    val prefs: Map<String, List<PrefEntry>>,
    val customIcons: Map<String, ByteArray>,
) {
    override fun equals(other: Any?): Boolean = other is BackupData &&
        createdAt == other.createdAt && appVersion == other.appVersion && files == other.files && prefs == other.prefs &&
        customIcons.keys == other.customIcons.keys && customIcons.all { (k, v) -> v.contentEquals(other.customIcons[k]) }

    override fun hashCode(): Int = files.hashCode() * 31 + prefs.hashCode()
}

/** What the restore preview shows before anything is overwritten. */
data class BackupSummary(
    val createdAt: Long,
    val appVersion: String,
    val homeApps: Int,
    val folders: Int,
    val hiddenApps: Int,
    val lockedApps: Int,
    val settingsCount: Int,
    val customIcons: Int,
    val iconOverrides: Int,
    val pinnedShortcuts: Int,
    val iconPack: String?,
)

class BackupFormatException(message: String) : Exception(message)

object BackupCodec {
    const val FORMAT = "glass-launcher-backup"
    const val VERSION = 1
    /** Refuse absurd files rather than run out of memory. */
    const val MAX_BYTES = 32L * 1024 * 1024

    fun encode(data: BackupData): String {
        val root = JSONObject()
        root.put("format", FORMAT)
        root.put("version", VERSION)
        root.put("createdAt", data.createdAt)
        root.put("appVersion", data.appVersion)
        root.put("files", JSONObject().apply { data.files.forEach { (k, v) -> put(k, v) } })
        root.put("prefs", JSONObject().apply {
            data.prefs.forEach { (store, entries) ->
                put(store, JSONArray(entries.map { e ->
                    val value: Any = when (val v = e.value) {
                        is Set<*> -> JSONArray(v.map { it.toString() }.sorted())
                        is Float -> v.toDouble()
                        else -> v
                    }
                    JSONObject().put("k", e.name).put("t", e.type).put("v", value)
                }))
            }
        })
        root.put("customIcons", JSONObject().apply {
            data.customIcons.forEach { (k, v) -> put(k, Base64.getEncoder().encodeToString(v)) }
        })
        return root.toString(2)
    }

    fun decode(text: String): BackupData {
        val root = try { JSONObject(text) } catch (e: Exception) { throw BackupFormatException("Not a Glass backup (unreadable JSON)") }
        if (root.optString("format") != FORMAT) throw BackupFormatException("Not a Glass backup")
        val version = root.optInt("version", -1)
        if (version < 1 || version > VERSION) throw BackupFormatException("Backup version $version is not supported by this Glass")
        val files = LinkedHashMap<String, String>()
        root.optJSONObject("files")?.let { o -> for (k in o.keys()) if (safeName(k)) files[k] = o.getString(k) }
        val prefs = LinkedHashMap<String, List<PrefEntry>>()
        root.optJSONObject("prefs")?.let { o ->
            for (store in o.keys()) {
                val arr = o.getJSONArray(store)
                prefs[store] = (0 until arr.length()).mapNotNull { i ->
                    val e = arr.getJSONObject(i)
                    val name = e.getString("k")
                    val value: Any = when (e.getString("t")) {
                        "b" -> e.getBoolean("v")
                        "i" -> e.getInt("v")
                        "l" -> e.getLong("v")
                        "f" -> e.getDouble("v").toFloat()
                        "d" -> e.getDouble("v")
                        "s" -> e.getString("v")
                        "ss" -> e.getJSONArray("v").let { a -> (0 until a.length()).map { a.getString(it) }.toSet() }
                        else -> return@mapNotNull null
                    }
                    PrefEntry(name, value)
                }
            }
        }
        val icons = LinkedHashMap<String, ByteArray>()
        root.optJSONObject("customIcons")?.let { o ->
            for (k in o.keys()) if (safeName(k)) icons[k] = Base64.getDecoder().decode(o.getString(k))
        }
        return BackupData(root.optLong("createdAt"), root.optString("appVersion"), files, prefs, icons)
    }

    /** File names must be plain names in the target directory (no paths). */
    fun safeName(name: String) = name.isNotEmpty() && !name.contains('/') && !name.contains('\\') && name != "." && name != ".."

    fun summarize(data: BackupData): BackupSummary {
        var apps = 0
        var folders = 0
        data.files["layout.json"]?.let { text ->
            try {
                val root = JSONObject(text)
                val items = ArrayList<Any?>()
                root.optJSONArray("pages")?.let { pages -> for (i in 0 until pages.length()) pages.optJSONArray(i)?.let { p -> for (j in 0 until p.length()) items += p.opt(j) } }
                root.optJSONArray("dock")?.let { d -> for (j in 0 until d.length()) items += d.opt(j) }
                items.filterIsInstance<JSONObject>().forEach { o ->
                    when (o.optString("t")) {
                        "app" -> apps++
                        "folder" -> { folders++; apps += o.optJSONArray("apps")?.length() ?: 0 }
                    }
                }
            } catch (e: Exception) {
                // Unknown layout format (e.g. a newer phase): show counts as zero, restore still copies it.
            }
        }
        val settings = data.prefs["settings"].orEmpty()
        fun set(name: String) = (settings.firstOrNull { it.name == name }?.value as? Set<*>)?.size ?: 0
        val overrides = data.files["icon_overrides.json"]?.let { try { JSONObject(it).length() } catch (e: Exception) { 0 } } ?: 0
        val pinned = data.files["pinned_shortcuts.json"]?.let { try { JSONArray(it).length() } catch (e: Exception) { 0 } } ?: 0
        return BackupSummary(
            createdAt = data.createdAt,
            appVersion = data.appVersion,
            homeApps = apps,
            folders = folders,
            hiddenApps = set("hidden_apps"),
            lockedApps = set("locked_apps"),
            settingsCount = data.prefs.values.sumOf { it.size },
            customIcons = data.customIcons.size,
            iconOverrides = overrides,
            pinnedShortcuts = pinned,
            iconPack = (settings.firstOrNull { it.name == "icon_pack" }?.value as? String)?.takeIf { it.isNotEmpty() },
        )
    }
}
