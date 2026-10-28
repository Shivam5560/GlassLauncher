package com.shivam.glasslauncher.widgets

import org.json.JSONArray
import org.json.JSONObject

/** iOS widget sizes in home-grid cells. [BAR] is the one-row search bar. */
enum class WidgetSize(val w: Int, val h: Int, val title: String) {
    SMALL(2, 2, "Small"),
    MEDIUM(4, 2, "Medium"),
    LARGE(4, 4, "Large"),
    BAR(4, 1, "Bar");

    companion object {
        fun forSpan(w: Int, h: Int): WidgetSize? = entries.firstOrNull { it.w == w && it.h == h }
    }
}

/** Our widget types, plus [APP_WIDGET] for third-party Android widgets hosted through AppWidgetHost. */
enum class WidgetKind(val title: String, val description: String, val sizes: List<WidgetSize>) {
    SMART_STACK("Smart Stack", "Swipe through widgets. Can rotate by time of day.", listOf(WidgetSize.SMALL, WidgetSize.MEDIUM, WidgetSize.LARGE)),
    MUSIC("Music", "Now playing, with controls and seeking.", listOf(WidgetSize.SMALL, WidgetSize.MEDIUM, WidgetSize.LARGE)),
    WEATHER("Weather", "Current conditions, hourly and 7-day forecast.", listOf(WidgetSize.SMALL, WidgetSize.MEDIUM, WidgetSize.LARGE)),
    CALENDAR("Calendar", "Today's date and what's up next.", listOf(WidgetSize.SMALL, WidgetSize.MEDIUM, WidgetSize.LARGE)),
    CLOCK("Clock", "Digital, analog or world clock.", listOf(WidgetSize.SMALL, WidgetSize.MEDIUM)),
    BATTERY("Batteries", "Phone and Bluetooth device batteries.", listOf(WidgetSize.SMALL, WidgetSize.MEDIUM)),
    PHOTOS("Photos", "Your picked photos, gently moving.", listOf(WidgetSize.SMALL, WidgetSize.MEDIUM, WidgetSize.LARGE)),
    NOTES("Notes", "A quick note you edit in place.", listOf(WidgetSize.SMALL, WidgetSize.MEDIUM, WidgetSize.LARGE)),
    SEARCH("Search", "A search bar that opens Spotlight.", listOf(WidgetSize.BAR)),
    APP_WIDGET("App widget", "", emptyList());

    companion object {
        /** Kinds a Smart Stack can hold. */
        val stackable = listOf(CALENDAR, WEATHER, MUSIC, BATTERY, CLOCK, PHOTOS, NOTES)
    }
}

/** Config keys shared by the widget code. */
object WidgetKeys {
    const val CLOCK_STYLE = "style" // digital | analog | world
    const val CLOCK_ZONES = "zones" // comma-separated ZoneIds
    const val NOTE_TEXT = "text"
    const val PHOTO_COUNT = "photos"
    const val STACK_CHILDREN = "children" // comma-separated instance ids
    const val STACK_ROTATE = "rotate" // "1" / "0"
    const val APP_WIDGET_ID = "appWidgetId"
    const val APP_WIDGET_PROVIDER = "provider"
    /** Third-party widgets in the Today view: height in grid rows. */
    const val TODAY_ROWS = "rows"
}

/** One widget instance: what it is, its size, and its settings. Placement lives elsewhere. */
data class WidgetInstance(
    val id: String,
    val kind: WidgetKind,
    val size: WidgetSize,
    val config: Map<String, String> = emptyMap(),
) {
    operator fun get(key: String): String? = config[key]

    fun list(key: String): List<String> = config[key]?.split(',')?.filter { it.isNotBlank() } ?: emptyList()

    val appWidgetId: Int get() = config[WidgetKeys.APP_WIDGET_ID]?.toIntOrNull() ?: -1
}

/** Everything the widget store persists. [today] is the Today view's order. */
data class WidgetState(
    val instances: Map<String, WidgetInstance> = emptyMap(),
    val today: List<String> = emptyList(),
    val seeded: Boolean = false,
) {
    fun toJson(): String {
        val inst = JSONArray()
        instances.values.forEach { i ->
            inst.put(
                JSONObject()
                    .put("id", i.id)
                    .put("kind", i.kind.name)
                    .put("size", i.size.name)
                    .put("config", JSONObject(i.config)),
            )
        }
        return JSONObject()
            .put("version", 1)
            .put("instances", inst)
            .put("today", JSONArray(today))
            .put("seeded", seeded)
            .toString()
    }

    companion object {
        fun fromJson(text: String): WidgetState {
            val root = JSONObject(text)
            val arr = root.optJSONArray("instances") ?: JSONArray()
            val map = LinkedHashMap<String, WidgetInstance>()
            for (i in 0 until arr.length()) {
                val o = arr.optJSONObject(i) ?: continue
                val kind = WidgetKind.entries.firstOrNull { it.name == o.optString("kind") } ?: continue
                val size = WidgetSize.entries.firstOrNull { it.name == o.optString("size") } ?: WidgetSize.SMALL
                val cfg = o.optJSONObject("config") ?: JSONObject()
                val config = cfg.keys().asSequence().associateWith { cfg.optString(it) }
                val id = o.getString("id")
                map[id] = WidgetInstance(id, kind, size, config)
            }
            val todayArr = root.optJSONArray("today") ?: JSONArray()
            val today = List(todayArr.length()) { todayArr.getString(it) }.filter { it in map }
            return WidgetState(map, today, root.optBoolean("seeded", false))
        }
    }
}
