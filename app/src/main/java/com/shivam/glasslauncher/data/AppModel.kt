package com.shivam.glasslauncher.data

import android.content.ComponentName
import android.content.pm.ApplicationInfo
import android.content.pm.LauncherActivityInfo
import android.os.UserHandle
import com.shivam.glasslauncher.search.FuzzyMatcher

/** A launchable activity, possibly in a work profile. [key] is stable across restarts. */
data class AppEntry(
    val key: String,
    val label: String,
    val component: ComponentName,
    val user: UserHandle,
    val isWorkProfile: Boolean,
    val category: AppCategory,
    val info: LauncherActivityInfo,
) {
    val packageName: String get() = component.packageName
    val searchTarget: FuzzyMatcher.Target by lazy { FuzzyMatcher.Target(label) }

    companion object {
        fun keyOf(component: ComponentName, userSerial: Long): String =
            if (userSerial == 0L) component.flattenToShortString() else "${component.flattenToShortString()}@$userSerial"
    }
}

enum class AppCategory(val title: String) {
    SOCIAL("Social"),
    ENTERTAINMENT("Entertainment"),
    GAMES("Games"),
    PRODUCTIVITY("Productivity & Finance"),
    CREATIVITY("Creativity"),
    INFO("Information & Reading"),
    TRAVEL("Travel"),
    SHOPPING("Shopping & Food"),
    HEALTH("Health & Fitness"),
    UTILITIES("Utilities"),
    OTHER("Other");

    companion object {
        /**
         * Category from [ApplicationInfo.category] first, then a small package-name keyword table,
         * falling back to Utilities for system apps and Other for the rest.
         */
        fun of(appInfo: ApplicationInfo): AppCategory {
            fromSystemCategory(appInfo.category)?.let { return it }
            if (appInfo.flags and ApplicationInfo.FLAG_IS_GAME != 0) return GAMES
            fromKeywords(appInfo.packageName)?.let { return it }
            val system = appInfo.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0
            return if (system) UTILITIES else OTHER
        }

        private fun fromSystemCategory(category: Int): AppCategory? = when (category) {
            ApplicationInfo.CATEGORY_GAME -> GAMES
            ApplicationInfo.CATEGORY_AUDIO, ApplicationInfo.CATEGORY_VIDEO -> ENTERTAINMENT
            ApplicationInfo.CATEGORY_IMAGE -> CREATIVITY
            ApplicationInfo.CATEGORY_SOCIAL -> SOCIAL
            ApplicationInfo.CATEGORY_NEWS -> INFO
            ApplicationInfo.CATEGORY_MAPS -> TRAVEL
            ApplicationInfo.CATEGORY_PRODUCTIVITY -> PRODUCTIVITY
            ApplicationInfo.CATEGORY_ACCESSIBILITY -> UTILITIES
            else -> null
        }

        private val KEYWORDS: List<Pair<AppCategory, List<String>>> = listOf(
            SOCIAL to listOf(
                "whatsapp", "telegram", "instagram", "facebook", "messenger", "twitter", "snapchat", "discord",
                "signal", "reddit", "linkedin", "threads", "tinder", "bumble", "wechat", "viber", "mms", "messaging",
                "dialer", "contacts", "skype", "zoom", "teams", "slack",
            ),
            ENTERTAINMENT to listOf(
                "youtube", "netflix", "spotify", "music", "primevideo", "hotstar", "jiocinema", "disney", "twitch",
                "player", "video", "podcast", "soundcloud", "gaana", "jiosaavn", "wynk", "mxtech", "vlc", "tv",
            ),
            GAMES to listOf("game", "games", "supercell", "tencent.ig", "pubg", "miniclip", "king.", "mojang", "roblox", "ea.", "gameloft"),
            PRODUCTIVITY to listOf(
                "docs", "sheets", "slides", "office", "notion", "drive", "calendar", "mail", "gmail", "outlook", "keep",
                "notes", "todo", "tasks", "bank", "pay", "paytm", "phonepe", "upi", "wallet", "finance", "money",
                "trading", "zerodha", "groww", "invest", "pdf", "scanner",
            ),
            CREATIVITY to listOf("camera", "photo", "gallery", "editor", "lightroom", "snapseed", "canva", "picsart", "capcut", "draw", "paint", "studio"),
            INFO to listOf("news", "kindle", "books", "reader", "medium", "weather", "wikipedia", "quora", "inshorts", "dailyhunt"),
            TRAVEL to listOf("maps", "uber", "ola", "rapido", "irctc", "booking", "airbnb", "makemytrip", "goibibo", "travel", "flight", "trip", "transit", "metro"),
            SHOPPING to listOf("amazon", "flipkart", "myntra", "meesho", "shop", "store", "swiggy", "zomato", "blinkit", "zepto", "bigbasket", "instamart", "food", "dominos"),
            HEALTH to listOf("health", "fit", "fitness", "workout", "yoga", "steps", "sleep", "meditation", "calm", "headspace", "strava", "pharm"),
            UTILITIES to listOf(
                "settings", "clock", "calculator", "files", "filemanager", "security", "cleaner", "browser", "chrome",
                "firefox", "compass", "recorder", "torch", "vpn", "launcher", "backup", "authenticator", "translate",
            ),
        )

        private fun fromKeywords(pkg: String): AppCategory? {
            val p = pkg.lowercase()
            for ((cat, words) in KEYWORDS) {
                if (words.any { p.contains(it) }) return cat
            }
            return null
        }
    }
}
