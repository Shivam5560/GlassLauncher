package com.shivam.glasslauncher.data

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * Auto-categorisation for the app drawer (Phase 5). Order: the app's own
 * `ApplicationInfo.category`, then `FLAG_IS_GAME`, then a small built-in package map and keyword
 * table, then Other. Pure (category constants are copied as ints) so it is unit-tested.
 */
object DrawerCategorizer {
    const val INTERNET = "internet"
    const val MEDIA = "media"
    const val SOCIAL = "social"
    const val GAMES = "games"
    const val UTILITIES = "utilities"
    const val SETTINGS = "settings"
    const val OTHER = "other"

    /** Built-in categories in their default order. */
    val BUILT_IN = listOf(INTERNET, MEDIA, SOCIAL, GAMES, UTILITIES, SETTINGS, OTHER)

    // android.content.pm.ApplicationInfo.CATEGORY_* (API 26+).
    private const val CATEGORY_GAME = 0
    private const val CATEGORY_AUDIO = 1
    private const val CATEGORY_VIDEO = 2
    private const val CATEGORY_IMAGE = 3
    private const val CATEGORY_SOCIAL = 4
    private const val CATEGORY_NEWS = 5
    private const val CATEGORY_MAPS = 6
    private const val CATEGORY_PRODUCTIVITY = 7
    private const val CATEGORY_ACCESSIBILITY = 8

    fun fromSystemCategory(category: Int): String? = when (category) {
        CATEGORY_GAME -> GAMES
        CATEGORY_AUDIO, CATEGORY_VIDEO, CATEGORY_IMAGE -> MEDIA
        CATEGORY_SOCIAL -> SOCIAL
        CATEGORY_NEWS, CATEGORY_MAPS -> INTERNET
        CATEGORY_PRODUCTIVITY -> UTILITIES
        CATEGORY_ACCESSIBILITY -> SETTINGS
        else -> null
    }

    /** Exact packages whose names would otherwise match the wrong keyword. */
    private val PACKAGES = mapOf(
        "com.android.vending" to INTERNET,
        "com.google.android.gm" to INTERNET,
        "com.google.android.googlequicksearchbox" to INTERNET,
        "com.android.phone" to SOCIAL,
        "com.phonepe.app" to UTILITIES,
        "net.one97.paytm" to UTILITIES,
        "com.google.android.apps.nbu.paisa.user" to UTILITIES,
        "com.google.android.apps.wellbeing" to SETTINGS,
    )

    /** Checked in this order; the first list with a matching word wins. */
    private val KEYWORDS: List<Pair<String, List<String>>> = listOf(
        SETTINGS to listOf(
            "settings", "securitycenter", "security", "permission", "thememanager", "wallpaper", "launcher",
            "cleaner", "updater", "backup", "accessibility", "provision",
        ),
        GAMES to listOf("game", "supercell", "pubg", "miniclip", "mojang", "roblox", "gameloft", "king.", "tencent.ig"),
        SOCIAL to listOf(
            "whatsapp", "telegram", "instagram", "facebook", "messenger", "twitter", "snapchat", "discord", "signal",
            "reddit", "linkedin", "threads", "tinder", "bumble", "wechat", "viber", "mms", "messaging", "dialer",
            "contacts", "skype", "zoom", "teams", "slack", "sms", "incallui",
        ),
        MEDIA to listOf(
            "youtube", "netflix", "spotify", "music", "player", "video", "camera", "gallery", "photo", "podcast",
            "radio", "primevideo", "hotstar", "jiocinema", "disney", "twitch", "soundcloud", "gaana", "jiosaavn",
            "wynk", "vlc", "mxtech", "recorder", "snapseed", "lightroom", "capcut", "picsart",
        ),
        INTERNET to listOf(
            "browser", "chrome", "firefox", "opera", "brave", "duckduckgo", "mail", "outlook", "drive", "dropbox",
            "onedrive", "cloud", "maps", "news", "vending", "amazon", "flipkart", "shop", "uber", "booking",
        ),
        UTILITIES to listOf(
            "clock", "calculator", "calendar", "files", "filemanager", "fileexplorer", "notes", "keep", "compass",
            "torch", "flashlight", "vpn", "authenticator", "translate", "docs", "sheets", "office", "scanner", "pdf",
            "bank", "wallet", "weather", "health", "fit",
        ),
    )

    fun fromKeywords(packageName: String): String? {
        val p = packageName.lowercase()
        PACKAGES[p]?.let { return it }
        for ((cat, words) in KEYWORDS) if (words.any { p.contains(it) }) return cat
        return null
    }

    fun categorize(packageName: String, systemCategory: Int, isGame: Boolean): String =
        fromSystemCategory(systemCategory)
            ?: (if (isGame) GAMES else null)
            ?: fromKeywords(packageName)
            ?: OTHER

    fun defaultTitle(id: String): String = when (id) {
        INTERNET -> "Internet"
        MEDIA -> "Media"
        SOCIAL -> "Social"
        GAMES -> "Games"
        UTILITIES -> "Utilities"
        SETTINGS -> "Settings"
        else -> "Other"
    }
}

/** A drawer category page. [glyph] names one of the drawer glyphs; [inBar] shows it in the tab bar. */
data class DrawerCategory(val id: String, val title: String, val glyph: String, val inBar: Boolean = true) {
    val builtIn: Boolean get() = id in DrawerCategorizer.BUILT_IN
}

/** A folder inside a drawer category. */
data class DrawerFolder(val id: String, val name: String, val category: String, val apps: List<String>)

/** An app shortcut (LauncherApps deep shortcut) added to a drawer category. */
data class DrawerShortcut(
    val id: String,
    val category: String,
    val appKey: String,
    val packageName: String,
    val shortcutId: String,
    val label: String,
)

enum class DrawerSort(val title: String) { AZ("A–Z"), MostUsed("Most used"), InstallDate("Install date") }

/**
 * Everything the user customises in the drawer, persisted inside the home layout JSON
 * (`layout.json`, key `drawer`), so backup/restore carries it.
 */
data class DrawerConfig(
    val categories: List<DrawerCategory> = DEFAULT_CATEGORIES,
    /** Manual moves: app key -> category id (overrides the automatic category). */
    val assignments: Map<String, String> = emptyMap(),
    val folders: List<DrawerFolder> = emptyList(),
    val shortcuts: List<DrawerShortcut> = emptyList(),
    val sort: DrawerSort = DrawerSort.AZ,
) {
    fun category(id: String): DrawerCategory? = categories.firstOrNull { it.id == id }

    fun toJson(): JSONObject = JSONObject()
        .put("categories", JSONArray(categories.map { c ->
            JSONObject().put("id", c.id).put("t", c.title).put("g", c.glyph).put("bar", c.inBar)
        }))
        .put("assign", JSONObject(assignments))
        .put("folders", JSONArray(folders.map { f ->
            JSONObject().put("id", f.id).put("n", f.name).put("c", f.category).put("apps", JSONArray(f.apps))
        }))
        .put("shortcuts", JSONArray(shortcuts.map { s ->
            JSONObject().put("id", s.id).put("c", s.category).put("app", s.appKey).put("pkg", s.packageName)
                .put("sid", s.shortcutId).put("l", s.label)
        }))
        .put("sort", sort.name)

    companion object {
        val DEFAULT_CATEGORIES: List<DrawerCategory> =
            DrawerCategorizer.BUILT_IN.map { DrawerCategory(it, DrawerCategorizer.defaultTitle(it), it) }

        fun fromJson(o: JSONObject): DrawerConfig {
            val catArr = o.optJSONArray("categories")
            val categories = if (catArr == null) DEFAULT_CATEGORIES else (0 until catArr.length()).mapNotNull { i ->
                val c = catArr.optJSONObject(i) ?: return@mapNotNull null
                val id = c.optString("id").ifEmpty { return@mapNotNull null }
                DrawerCategory(id, c.optString("t", DrawerCategorizer.defaultTitle(id)), c.optString("g", id), c.optBoolean("bar", true))
            }.distinctBy { it.id }.ifEmpty { DEFAULT_CATEGORIES }
            val ids = categories.map { it.id }.toSet()
            val assignObj = o.optJSONObject("assign") ?: JSONObject()
            val assignments = assignObj.keys().asSequence().associateWith { assignObj.getString(it) }.filterValues { it in ids }
            val folderArr = o.optJSONArray("folders") ?: JSONArray()
            val folders = (0 until folderArr.length()).mapNotNull { i ->
                val f = folderArr.optJSONObject(i) ?: return@mapNotNull null
                val apps = f.optJSONArray("apps") ?: JSONArray()
                DrawerFolder(f.getString("id"), f.optString("n", "Folder"), f.optString("c"), List(apps.length()) { apps.getString(it) })
            }.filter { it.category in ids }
            val scArr = o.optJSONArray("shortcuts") ?: JSONArray()
            val shortcuts = (0 until scArr.length()).mapNotNull { i ->
                val s = scArr.optJSONObject(i) ?: return@mapNotNull null
                DrawerShortcut(s.getString("id"), s.optString("c"), s.optString("app"), s.optString("pkg"), s.optString("sid"), s.optString("l"))
            }.filter { it.category in ids }
            val sort = DrawerSort.entries.firstOrNull { it.name == o.optString("sort") } ?: DrawerSort.AZ
            return DrawerConfig(categories, assignments, folders, shortcuts, sort)
        }
    }
}

/** What the drawer needs to know about one app (no Android types, for tests). */
data class DrawerApp(val key: String, val label: String, val autoCategory: String, val installTime: Long)

/** One cell of a drawer page. */
sealed interface DrawerItem {
    val key: String
    data class App(override val key: String) : DrawerItem
    data class Folder(val folder: DrawerFolder) : DrawerItem { override val key: String get() = "folder:${folder.id}" }
    data class Shortcut(val shortcut: DrawerShortcut) : DrawerItem { override val key: String get() = "shortcut:${shortcut.id}" }
}

/** Pure drawer transformations (unit-tested). */
object DrawerOps {

    private fun newId(prefix: String) = prefix + "-" + UUID.randomUUID().toString().take(8)

    fun addCategory(c: DrawerConfig, title: String, glyph: String = "star"): DrawerConfig {
        val t = title.trim()
        if (t.isEmpty()) return c
        return c.copy(categories = c.categories + DrawerCategory(newId("cat"), t, glyph))
    }

    fun renameCategory(c: DrawerConfig, id: String, title: String): DrawerConfig {
        val t = title.trim()
        if (t.isEmpty()) return c
        return c.copy(categories = c.categories.map { if (it.id == id) it.copy(title = t) else it })
    }

    fun setGlyph(c: DrawerConfig, id: String, glyph: String): DrawerConfig =
        c.copy(categories = c.categories.map { if (it.id == id) it.copy(glyph = glyph) else it })

    fun moveCategory(c: DrawerConfig, from: Int, to: Int): DrawerConfig {
        if (from !in c.categories.indices || to !in c.categories.indices || from == to) return c
        val list = c.categories.toMutableList()
        list.add(to, list.removeAt(from))
        return c.copy(categories = list)
    }

    fun setInBar(c: DrawerConfig, id: String, inBar: Boolean): DrawerConfig {
        val next = c.categories.map { if (it.id == id) it.copy(inBar = inBar) else it }
        // At least one category stays in the bar.
        if (next.none { it.inBar }) return c
        return c.copy(categories = next)
    }

    /**
     * Deletes a category. Apps moved there go back to their automatic category, its folders and
     * shortcuts are removed (folder apps stay in the drawer). Other cannot be deleted: it is
     * where apps without a category live.
     */
    fun deleteCategory(c: DrawerConfig, id: String): DrawerConfig {
        if (id == DrawerCategorizer.OTHER || c.category(id) == null) return c
        return c.copy(
            categories = c.categories.filter { it.id != id },
            assignments = c.assignments.filterValues { it != id },
            folders = c.folders.filter { it.category != id },
            shortcuts = c.shortcuts.filter { it.category != id },
        )
    }

    /** Moves an app to a category (long-press → Move to category); also takes it out of drawer folders. */
    fun assign(c: DrawerConfig, appKey: String, categoryId: String, autoCategory: String? = null): DrawerConfig {
        if (c.category(categoryId) == null) return c
        val assignments = if (categoryId == autoCategory) c.assignments - appKey else c.assignments + (appKey to categoryId)
        return c.copy(
            assignments = assignments,
            folders = c.folders.mapNotNull { f -> if (appKey in f.apps) f.copy(apps = f.apps - appKey).takeIf { it.apps.isNotEmpty() } else f },
        )
    }

    fun setSort(c: DrawerConfig, sort: DrawerSort): DrawerConfig = c.copy(sort = sort)

    fun addFolder(c: DrawerConfig, categoryId: String, name: String, apps: List<String>): DrawerConfig {
        if (c.category(categoryId) == null || apps.isEmpty()) return c
        val others = c.folders.map { it.copy(apps = it.apps - apps.toSet()) }.filter { it.apps.isNotEmpty() }
        return c.copy(folders = others + DrawerFolder(newId("dfolder"), name.trim().ifEmpty { "Folder" }, categoryId, apps.distinct()))
    }

    fun removeFolder(c: DrawerConfig, id: String): DrawerConfig = c.copy(folders = c.folders.filter { it.id != id })

    fun renameFolder(c: DrawerConfig, id: String, name: String): DrawerConfig =
        c.copy(folders = c.folders.map { if (it.id == id && name.isNotBlank()) it.copy(name = name.trim()) else it })

    fun addShortcut(c: DrawerConfig, categoryId: String, appKey: String, packageName: String, shortcutId: String, label: String): DrawerConfig {
        if (c.category(categoryId) == null) return c
        if (c.shortcuts.any { it.packageName == packageName && it.shortcutId == shortcutId && it.category == categoryId }) return c
        return c.copy(shortcuts = c.shortcuts + DrawerShortcut(newId("dsc"), categoryId, appKey, packageName, shortcutId, label))
    }

    fun removeShortcut(c: DrawerConfig, id: String): DrawerConfig = c.copy(shortcuts = c.shortcuts.filter { it.id != id })

    /** The category an app shows in: a manual move, else its automatic category, else Other (or the first). */
    fun categoryOf(c: DrawerConfig, app: DrawerApp): String {
        val ids = c.categories.map { it.id }
        c.assignments[app.key]?.let { if (it in ids) return it }
        if (app.autoCategory in ids) return app.autoCategory
        return if (DrawerCategorizer.OTHER in ids) DrawerCategorizer.OTHER else ids.first()
    }

    /** Sorted app keys: A–Z by label, by usage rank (unranked last, then A–Z) or newest install first. */
    fun sort(apps: List<DrawerApp>, sort: DrawerSort, usageRank: Map<String, Int>): List<DrawerApp> {
        val az = compareBy<DrawerApp>({ it.label.lowercase() }, { it.key })
        return when (sort) {
            DrawerSort.AZ -> apps.sortedWith(az)
            DrawerSort.MostUsed -> apps.sortedWith(compareBy<DrawerApp> { usageRank[it.key] ?: Int.MAX_VALUE }.then(az))
            DrawerSort.InstallDate -> apps.sortedWith(compareByDescending<DrawerApp> { it.installTime }.then(az))
        }
    }

    /**
     * Each category's page: folders first, then shortcuts, then the remaining apps (apps inside any
     * drawer folder are not repeated), sorted by [DrawerConfig.sort]. Empty categories
     * are kept so users can fill them.
     */
    fun pages(c: DrawerConfig, apps: List<DrawerApp>, usageRank: Map<String, Int>): Map<String, List<DrawerItem>> {
        val known = apps.map { it.key }.toSet()
        val byCat = apps.groupBy { categoryOf(c, it) }
        val liveFolders = c.folders.map { it.copy(apps = it.apps.filter { k -> k in known }) }.filter { it.apps.isNotEmpty() }
        // An app inside a drawer folder shows only in that folder.
        val inFolders = liveFolders.flatMap { it.apps }.toSet()
        return c.categories.associate { cat ->
            val folders = liveFolders.filter { it.category == cat.id }
            val loose = sort((byCat[cat.id] ?: emptyList()).filter { it.key !in inFolders }, c.sort, usageRank)
            cat.id to (
                folders.map { DrawerItem.Folder(it) } +
                    c.shortcuts.filter { it.category == cat.id && it.appKey in known }.map { DrawerItem.Shortcut(it) } +
                    loose.map { DrawerItem.App(it.key) }
                )
        }
    }
}
