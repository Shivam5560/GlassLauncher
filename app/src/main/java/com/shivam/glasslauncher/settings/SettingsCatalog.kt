package com.shivam.glasslauncher.settings

/** Searchable destinations, including controls that live inside a settings page. */
internal data class SettingsDestination(
    val route: String,
    val title: String,
    val summary: String,
    val section: String,
    val keywords: String = "",
)

internal object SettingsCatalog {
    val entries = listOf(
        SettingsDestination("appearance", "Appearance", "Material, colours, icons & layout", "Personalise", "theme solid frosted liquid glass amoled size dock folders animation corner radius"),
        SettingsDestination("wallpapers", "Wallpapers", "Find a new backdrop for your day", "Personalise", "background gallery light dark"),
        SettingsDestination("wallpaper", "Your wallpaper", "Choose a photo or manage wallpaper access", "Personalise", "image photos refraction files"),
        SettingsDestination("iconpack", "Icon packs", "Give your apps a consistent look", "Personalise", "system icons artwork"),
        SettingsDestination("iconstyle", "Icon style", "Light, dark or tinted icons", "Personalise"),
        SettingsDestination("appicons", "Individual app icons", "Choose artwork for a single app", "Personalise", "edit photo custom"),
        SettingsDestination("fonts", "Fonts", "Find your type", "Personalise", "text typography inter manrope size"),
        SettingsDestination("theme", "Theme colours", "An accent that feels like you", "Personalise", "wallpaper palette custom"),
        SettingsDestination("glass", "Glass tuning", "Fine-tune blur, depth & lighting", "Personalise", "tint opacity refraction rim chromatic lens"),
        SettingsDestination("home", "Home screen", "Labels, badges & wallpaper effects", "Everyday", "notifications infinite scroll blur dim true tone strength 3d parallax immersive"),
        SettingsDestination("drawer", "App drawer", "Categories, sorting & transitions", "Everyday", "apps alphabetical launch view"),
        SettingsDestination("widgets", "Widgets & Today", "Weather, calendar & widget access", "Everyday", "location units music battery photos"),
        SettingsDestination("homegestures", "Home gestures", "Shortcuts at your fingertips", "Everyday", "swipe double tap pinch rotate two fingers"),
        SettingsDestination("gestures", "System navigation", "Home, back & recent apps", "Everyday", "accessibility gestures exclusions"),
        SettingsDestination("overlays", "Island & Centers", "Music, notifications & quick controls", "Everyday", "dynamic island control center"),
        SettingsDestination("pinned", "Pinned shortcuts", "Manage shortcuts added by apps", "Everyday"),
        SettingsDestination("hidden", "Hidden apps", "Choose what stays out of sight", "Privacy & care", "hide private"),
        SettingsDestination("locked", "Locked apps", "Require authentication when opened from Glass", "Privacy & care", "lock fingerprint face biometric security"),
        SettingsDestination("backup", "Backup & restore", "Keep your setup safe", "Privacy & care", "export import save layout data"),
        SettingsDestination("check", "System check", "Permissions & launcher health", "Privacy & care", "default home battery notification access"),
        SettingsDestination("setup", "Setup guide", "Get the most from Glass", "Privacy & care", "hyperos autostart restrictions permissions"),
        SettingsDestination("about", "About Glass", "A little more about your launcher", "Privacy & care", "version license"),
    )

    fun search(query: String): List<SettingsDestination> {
        val terms = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (terms.isEmpty()) return emptyList()
        return entries.filter { entry ->
            val text = "${entry.title} ${entry.summary} ${entry.section} ${entry.keywords}".lowercase()
            terms.all { it in text }
        }.sortedByDescending { entry ->
            val title = entry.title.lowercase()
            when {
                title == query.trim().lowercase() -> 3
                title.startsWith(query.trim().lowercase()) -> 2
                terms.all { it in title } -> 1
                else -> 0
            }
        }
    }
}
