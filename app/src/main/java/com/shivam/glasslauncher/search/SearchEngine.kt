package com.shivam.glasslauncher.search

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.LauncherApps
import android.content.pm.PackageManager
import android.content.pm.ShortcutInfo
import android.net.Uri
import android.provider.ContactsContract
import android.provider.Settings
import com.shivam.glasslauncher.data.AppEntry
import com.shivam.glasslauncher.widgets.CalendarEvent
import com.shivam.glasslauncher.widgets.CalendarRepository

data class ContactResult(val id: Long, val name: String, val number: String?, val photo: Uri?)

data class SettingsShortcut(val title: String, val keywords: List<String>, val action: String) {
    val target = FuzzyMatcher.Target(title)
    fun intent(): Intent = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

/** An app shortcut ("New chat", "Scan") offered by an app that is visible and not locked. */
data class ShortcutResult(val app: AppEntry, val info: ShortcutInfo) {
    val label: String = (info.shortLabel ?: info.longLabel ?: "").toString()
    internal val target = FuzzyMatcher.Target(label)
    internal val combined = FuzzyMatcher.Target(app.label + " " + label)
}

data class SpotlightResults(
    val query: String = "",
    val calculation: String? = null,
    val calculationExpression: String? = null,
    val conversion: UnitConverter.Conversion? = null,
    val apps: List<AppEntry> = emptyList(),
    val contacts: List<ContactResult> = emptyList(),
    val settings: List<SettingsShortcut> = emptyList(),
    val shortcuts: List<ShortcutResult> = emptyList(),
    /** Filled by the second, storage-backed pass ([SearchEngine.searchStorage]). */
    val events: List<CalendarEvent> = emptyList(),
    val files: List<FileResult> = emptyList(),
    val needsContactsPermission: Boolean = false,
    val needsFilesPermission: Boolean = false,
    /** The WhatsApp button is only offered when WhatsApp (or WhatsApp Business) is installed. */
    val whatsAppInstalled: Boolean = false,
)

/** Spotlight backend. All methods are blocking and must run off the main thread. */
class SearchEngine(private val context: Context) {

    /**
     * Every visible app's shortcuts with their search targets, built once per Spotlight opening
     * ([refreshShortcuts]) so typing never waits on LauncherApps or label parsing.
     */
    @Volatile private var shortcutIndex: List<ShortcutResult> = emptyList()

    /**
     * One request per profile for every app's manifest, dynamic and pinned shortcuts. Only works while
     * Glass is the default Home app; otherwise the index is empty. [apps] are the visible (not hidden) apps.
     */
    fun refreshShortcuts(apps: List<AppEntry>) {
        val la = context.getSystemService(LauncherApps::class.java) ?: return
        shortcutIndex = try {
            if (!la.hasShortcutHostPermission()) emptyList() else {
                val query = LauncherApps.ShortcutQuery().setQueryFlags(
                    LauncherApps.ShortcutQuery.FLAG_MATCH_DYNAMIC or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_MANIFEST or
                        LauncherApps.ShortcutQuery.FLAG_MATCH_PINNED,
                )
                val byComponent = apps.associateBy { it.component to it.user }
                val byPackage = apps.groupBy { it.packageName to it.user }
                la.profiles.asSequence()
                    .flatMap { la.getShortcuts(query, it).orEmpty() }
                    .filter { it.isEnabled }
                    .distinctBy { Triple(it.`package`, it.id, it.userHandle) }
                    .mapNotNull { info ->
                        val app = info.activity?.let { byComponent[it to info.userHandle] }
                            ?: byPackage[info.`package` to info.userHandle]?.firstOrNull()
                        app?.let { ShortcutResult(it, info) }?.takeIf { it.label.isNotBlank() }
                    }
                    .toList()
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Shortcuts whose own label matches, or "app + label" for multi-word queries ("whatsapp new").
     * Shortcuts of locked apps never show: they would open the app around its lock.
     */
    fun matchShortcuts(q: String, locked: Set<String>, boost: (String) -> Int): List<ShortcutResult> {
        if (q.length < 2) return emptyList()
        val multiWord = q.contains(' ')
        return shortcutIndex.asSequence()
            .filter { it.app.key !in locked }
            .mapNotNull { r ->
                val score = maxOf(
                    FuzzyMatcher.score(q, r.target).takeIf { it >= 500 } ?: 0,
                    if (multiWord) FuzzyMatcher.score(q, r.combined).takeIf { it >= 600 } ?: 0 else 0,
                )
                if (score == 0) null else r to score + boost(r.app.key)
            }
            .sortedByDescending { it.second }
            .map { it.first }
            .take(4)
            .toList()
    }

    /**
     * Second pass, run once typing pauses: calendar events and files. Skipped for sums and unit
     * conversions, and for single characters, where nearly every file name would match.
     */
    fun searchStorage(fast: SpotlightResults, cancel: android.os.CancellationSignal): SpotlightResults {
        val q = fast.query
        if (q.length < 2 || fast.calculation != null || fast.conversion != null) return fast
        val files = FileSearch.query(context, q, cancel = cancel)
        return fast.copy(
            events = CalendarRepository.search(context, q),
            files = files,
            needsFilesPermission = !FileSearch.hasAccess() && q.any { it.isLetter() },
        )
    }

    /**
     * While the storage pass for a new query runs, keep the previous files and events that still
     * match every word, so rows don't blink out and back on each keystroke.
     */
    fun carryOver(previous: SpotlightResults, fast: SpotlightResults): SpotlightResults {
        if (previous.files.isEmpty() && previous.events.isEmpty()) return fast
        if (fast.query.length < 2 || fast.calculation != null || fast.conversion != null) return fast
        val words = fast.query.split(Regex("\\s+")).filter { it.isNotEmpty() }.map(FuzzyMatcher::normalize)
        fun keeps(text: String) = FuzzyMatcher.normalize(text).let { t -> words.all { it in t } }
        return fast.copy(
            files = previous.files.filter { keeps(it.name) },
            events = previous.events.filter { keeps(it.title + " " + it.location.orEmpty()) },
            needsFilesPermission = previous.needsFilesPermission,
        )
    }

    fun search(query: String, apps: List<AppEntry>, locked: Set<String>, boost: (String) -> Int): SpotlightResults {
        val q = query.trim()
        if (q.isEmpty()) return SpotlightResults()

        val calc = Calculator.evaluateOrNull(q)?.let { Calculator.format(it) }
        val conversion = if (calc == null) UnitConverter.convert(q) else null

        val scoredApps = apps.asSequence()
            .map { it to FuzzyMatcher.score(q, it.searchTarget) }
            .filter { it.second > 0 }
            .sortedByDescending { it.second + boost(it.first.key) }
            .map { it.first }
            .distinctBy { it.key }
            .take(8)
            .toList()

        val settings = SETTINGS.asSequence()
            .map { s -> s to maxOf(FuzzyMatcher.score(q, s.target), if (s.keywords.any { it.startsWith(q.lowercase()) }) 650 else 0) }
            .filter { it.second >= 300 }
            .sortedByDescending { it.second }
            .map { it.first }
            .take(4)
            .toList()

        val hasContacts = context.checkSelfPermission(Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED
        val contacts = if (hasContacts && calc == null && conversion == null) queryContacts(q) else emptyList()

        return SpotlightResults(
            query = q,
            calculation = calc,
            calculationExpression = if (calc != null) q else null,
            conversion = conversion,
            apps = scoredApps,
            contacts = contacts,
            settings = settings,
            shortcuts = if (calc == null && conversion == null) matchShortcuts(q, locked, boost) else emptyList(),
            needsContactsPermission = !hasContacts && q.any { it.isLetter() },
            whatsAppInstalled = contacts.isNotEmpty() && whatsAppInstalled(),
        )
    }

    private fun whatsAppInstalled(): Boolean =
        listOf("com.whatsapp", "com.whatsapp.w4b").any { context.packageManager.getLaunchIntentForPackage(it) != null }

    private fun queryContacts(q: String): List<ContactResult> {
        val uri = Uri.withAppendedPath(ContactsContract.CommonDataKinds.Phone.CONTENT_FILTER_URI, Uri.encode(q))
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.PHOTO_THUMBNAIL_URI,
            ContactsContract.CommonDataKinds.Phone.IS_SUPER_PRIMARY,
        )
        val out = LinkedHashMap<Long, ContactResult>()
        try {
            context.contentResolver.query(uri, projection, null, null, "${ContactsContract.CommonDataKinds.Phone.TIMES_CONTACTED} DESC")?.use { c ->
                while (c.moveToNext() && out.size < 5) {
                    val id = c.getLong(0)
                    if (id in out) continue
                    out[id] = ContactResult(
                        id = id,
                        name = c.getString(1) ?: continue,
                        number = c.getString(2),
                        photo = c.getString(3)?.let { Uri.parse(it) },
                    )
                }
            }
        } catch (e: Exception) {
            // Provider unavailable or sort column unsupported: retry without ordering.
            if (out.isEmpty()) try {
                context.contentResolver.query(uri, projection, null, null, null)?.use { c ->
                    while (c.moveToNext() && out.size < 5) {
                        val id = c.getLong(0)
                        if (id in out) continue
                        out[id] = ContactResult(id, c.getString(1) ?: continue, c.getString(2), c.getString(3)?.let { Uri.parse(it) })
                    }
                }
            } catch (_: Exception) { }
        }
        return out.values.toList()
    }

    companion object {
        val SETTINGS = listOf(
            SettingsShortcut("Wi-Fi", listOf("wifi", "wlan", "internet", "network"), Settings.ACTION_WIFI_SETTINGS),
            SettingsShortcut("Bluetooth", listOf("bluetooth", "bt", "pair", "headphones"), Settings.ACTION_BLUETOOTH_SETTINGS),
            SettingsShortcut("Mobile Network", listOf("mobile", "sim", "cellular", "data", "network"), Settings.ACTION_NETWORK_OPERATOR_SETTINGS),
            SettingsShortcut("Data Usage", listOf("data usage", "usage"), Settings.ACTION_DATA_USAGE_SETTINGS),
            SettingsShortcut("Airplane Mode", listOf("airplane", "flight"), Settings.ACTION_AIRPLANE_MODE_SETTINGS),
            SettingsShortcut("Display & Brightness", listOf("display", "brightness", "dark mode", "font", "screen"), Settings.ACTION_DISPLAY_SETTINGS),
            SettingsShortcut("Night Light", listOf("night", "blue light", "eye"), Settings.ACTION_NIGHT_DISPLAY_SETTINGS),
            SettingsShortcut("Sounds & Vibration", listOf("sound", "volume", "ringtone", "vibration"), Settings.ACTION_SOUND_SETTINGS),
            SettingsShortcut("Notifications", listOf("notifications", "alerts"), "android.settings.NOTIFICATION_SETTINGS"),
            SettingsShortcut("Battery", listOf("battery", "power", "saver"), Intent.ACTION_POWER_USAGE_SUMMARY),
            SettingsShortcut("Storage", listOf("storage", "space", "memory"), Settings.ACTION_INTERNAL_STORAGE_SETTINGS),
            SettingsShortcut("Apps", listOf("apps", "applications", "uninstall"), Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS),
            SettingsShortcut("Default Apps", listOf("default", "browser", "launcher", "home app"), Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS),
            SettingsShortcut("Location", listOf("location", "gps"), Settings.ACTION_LOCATION_SOURCE_SETTINGS),
            SettingsShortcut("Security", listOf("security", "lock", "fingerprint", "face", "password"), Settings.ACTION_SECURITY_SETTINGS),
            SettingsShortcut("Privacy", listOf("privacy", "permissions"), Settings.ACTION_PRIVACY_SETTINGS),
            SettingsShortcut("Accessibility", listOf("accessibility", "gestures", "talkback"), Settings.ACTION_ACCESSIBILITY_SETTINGS),
            SettingsShortcut("Date & Time", listOf("date", "time", "clock", "timezone"), Settings.ACTION_DATE_SETTINGS),
            SettingsShortcut("Language & Input", listOf("language", "keyboard", "input"), Settings.ACTION_LOCALE_SETTINGS),
            SettingsShortcut("NFC", listOf("nfc", "tap to pay"), Settings.ACTION_NFC_SETTINGS),
            SettingsShortcut("VPN", listOf("vpn"), Settings.ACTION_VPN_SETTINGS),
            SettingsShortcut("Cast", listOf("cast", "screen mirror"), Settings.ACTION_CAST_SETTINGS),
            SettingsShortcut("Wallpaper", listOf("wallpaper", "background"), Intent.ACTION_SET_WALLPAPER),
            SettingsShortcut("Developer Options", listOf("developer", "usb debugging", "adb"), Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS),
            SettingsShortcut("About Phone", listOf("about", "phone info", "version", "model"), Settings.ACTION_DEVICE_INFO_SETTINGS),
            SettingsShortcut("Settings", listOf("settings", "preferences"), Settings.ACTION_SETTINGS),
        )
    }
}
