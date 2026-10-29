package com.shivam.glasslauncher.widgets

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/** Original line-art glyphs for widgets (24x24 grid, like `GlassIcons`), tinted at draw time. */
object WidgetGlyphs {
    private fun icon(name: String, vararg paths: String, fill: Boolean = false, stroke: Float = 1.8f): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach { d ->
                addPath(
                    pathData = addPathNodes(d),
                    fill = if (fill) SolidColor(Color.White) else null,
                    stroke = if (fill) null else SolidColor(Color.White),
                    strokeLineWidth = stroke,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    // Weather
    private const val CLOUD = "M7,18.5H17A3.5,3.5 0 0,0 17.4,11.5A5,5 0 0,0 7.8,10.2A4.2,4.2 0 0,0 7,18.5Z"
    val Sun = icon(
        "sun", "M12,8a4,4 0 1,0 0,8a4,4 0 1,0 0,-8z",
        "M12,2.5V4.5", "M12,19.5V21.5", "M2.5,12H4.5", "M19.5,12H21.5",
        "M5.3,5.3L6.7,6.7", "M17.3,17.3L18.7,18.7", "M5.3,18.7L6.7,17.3", "M17.3,6.7L18.7,5.3",
    )
    val Moon = icon("moon", "M19,14.5A7.5,7.5 0 0,1 9.5,5A7.5,7.5 0 1,0 19,14.5Z")
    val PartlyDay = icon("partlyDay", "M9,5.2a3.4,3.4 0 0,1 4.9,3.1", "M9,2.5V3.5", "M3.5,8H4.5", "M5,4L5.7,4.7", "M13,4L12.3,4.7", CLOUD)
    val PartlyNight = icon("partlyNight", "M13.5,6.5A3.6,3.6 0 0,1 8.4,3A3.8,3.8 0 1,0 13.5,6.5Z", CLOUD)
    val Cloud = icon("cloud", CLOUD)
    val Fog = icon("fog", "M7,12H17A3.5,3.5 0 0,0 17.4,5A5,5 0 0,0 7.8,3.7A4.2,4.2 0 0,0 7,12Z", "M4,15.5H20", "M6,19H18")
    val Drizzle = icon("drizzle", "M7,15H17A3.5,3.5 0 0,0 17.4,8A5,5 0 0,0 7.8,6.7A4.2,4.2 0 0,0 7,15Z", "M9,18L8.6,19", "M13,18L12.6,19", "M16.5,18L16.1,19")
    val Rain = icon("rain", "M7,14H17A3.5,3.5 0 0,0 17.4,7A5,5 0 0,0 7.8,5.7A4.2,4.2 0 0,0 7,14Z", "M8.5,17L7.5,20", "M12.5,17L11.5,20", "M16.5,17L15.5,20")
    val HeavyRain = icon("heavyRain", "M7,13H17A3.5,3.5 0 0,0 17.4,6A5,5 0 0,0 7.8,4.7A4.2,4.2 0 0,0 7,13Z", "M7.5,15.5L6,21", "M11,15.5L9.5,21", "M14.5,15.5L13,21", "M18,15.5L16.5,21")
    val Sleet = icon("sleet", "M7,14H17A3.5,3.5 0 0,0 17.4,7A5,5 0 0,0 7.8,5.7A4.2,4.2 0 0,0 7,14Z", "M8.5,17L7.5,20", "M12,18.5L12,18.6", "M16,17L15,20")
    val Snow = icon("snow", "M7,14H17A3.5,3.5 0 0,0 17.4,7A5,5 0 0,0 7.8,5.7A4.2,4.2 0 0,0 7,14Z", "M8,18L8,18.1", "M12,17L12,17.1", "M16,18L16,18.1", "M10,20.5L10,20.6", "M14,20.5L14,20.6")
    val Thunder = icon("thunder", "M7,14H17A3.5,3.5 0 0,0 17.4,7A5,5 0 0,0 7.8,5.7A4.2,4.2 0 0,0 7,14Z", "M12.5,14.5L10.5,18H13.5L11.5,21.5")

    fun weather(g: WeatherGlyph): ImageVector = when (g) {
        WeatherGlyph.SUN -> Sun
        WeatherGlyph.MOON -> Moon
        WeatherGlyph.PARTLY_CLOUDY_DAY -> PartlyDay
        WeatherGlyph.PARTLY_CLOUDY_NIGHT -> PartlyNight
        WeatherGlyph.CLOUD -> Cloud
        WeatherGlyph.FOG -> Fog
        WeatherGlyph.DRIZZLE -> Drizzle
        WeatherGlyph.RAIN -> Rain
        WeatherGlyph.HEAVY_RAIN -> HeavyRain
        WeatherGlyph.SLEET -> Sleet
        WeatherGlyph.SNOW -> Snow
        WeatherGlyph.THUNDER -> Thunder
    }

    /** Accent colour for a weather glyph (sun yellow, rain blue, ...). */
    fun weatherTint(g: WeatherGlyph): Color = when (g) {
        WeatherGlyph.SUN, WeatherGlyph.PARTLY_CLOUDY_DAY -> Color(0xFFFFD60A)
        WeatherGlyph.MOON, WeatherGlyph.PARTLY_CLOUDY_NIGHT -> Color(0xFFE5E5EA)
        WeatherGlyph.DRIZZLE, WeatherGlyph.RAIN, WeatherGlyph.HEAVY_RAIN -> Color(0xFF64D2FF)
        WeatherGlyph.THUNDER -> Color(0xFFFFD60A)
        else -> Color.White
    }

    // Media
    val Play = icon("play", "M8,5.5V18.5L18.5,12Z", fill = true)
    val Pause = icon("pause", "M7,5h3.5v14h-3.5z", "M13.5,5h3.5v14h-3.5z", fill = true)
    val Next = icon("next", "M5,6V18L12.5,12Z", "M12.5,6V18L20,12Z", fill = true)
    val Previous = icon("previous", "M19,6V18L11.5,12Z", "M11.5,6V18L4,12Z", fill = true)
    val Note = icon("musicNote", "M9,18V6L19,4V16", "M9,18a2.5,2.5 0 1,1 -5,0a2.5,2.5 0 1,1 5,0z", "M19,16a2.5,2.5 0 1,1 -5,0a2.5,2.5 0 1,1 5,0z")

    // Devices and misc
    val Phone = icon("phoneDevice", "M8,2.8h8a1.5,1.5 0 0,1 1.5,1.5v15.4a1.5,1.5 0 0,1 -1.5,1.5h-8a1.5,1.5 0 0,1 -1.5,-1.5v-15.4a1.5,1.5 0 0,1 1.5,-1.5z", "M10.5,18.5H13.5")
    val Earbuds = icon("earbuds", "M7,4a3,3 0 0,1 3,3V11a1.5,1.5 0 0,1 -3,0V19", "M17,4a3,3 0 0,0 -3,3V11a1.5,1.5 0 0,0 3,0V19")
    val Bolt = icon("bolt", "M13,2L5,13.5H11L10,22L19,10H13Z", fill = true)
    val Calendar = icon("calendar", "M5,5.5h14v14h-14z", "M5,9.5H19", "M9,3.5V7", "M15,3.5V7")
    val Clock = icon("clock", "M12,3a9,9 0 1,0 0,18a9,9 0 1,0 0,-18z", "M12,7V12L15,14")
    val Photo = icon("photo", "M4,5.5h16v13h-16z", "M4,16L9,11L13,15L15.5,12.5L20,17", "M15.5,8.5L15.5,8.6")
    val Notes = icon("notes", "M6,3.5h12v17h-12z", "M9,8H15", "M9,12H15", "M9,16H13")
    val Stack = icon("stack", "M6,8h12v12h-12z", "M8,5H16", "M10,2.5H14")
    val Pin = icon("pin", "M12,21C12,21 5.5,14.5 5.5,9.5a6.5,6.5 0 0,1 13,0C18.5,14.5 12,21 12,21Z", "M12,7.5a2,2 0 1,0 0,4a2,2 0 1,0 0,-4z")
    val Refresh = icon("refresh", "M19,8A7.5,7.5 0 1,0 19.5,14", "M19.5,3.5V8.5H14.5")
    val Star = icon("star", "M12,3.5L14.6,9L20.5,9.6L16,13.6L17.3,19.5L12,16.5L6.7,19.5L8,13.6L3.5,9.6L9.4,9Z")
    val StarFill = icon("starFill", "M12,3.5L14.6,9L20.5,9.6L16,13.6L17.3,19.5L12,16.5L6.7,19.5L8,13.6L3.5,9.6L9.4,9Z", fill = true)
    val ArrowUp = icon("arrowUp", "M12,19V5", "M6,11L12,5L18,11")
    val ArrowDown = icon("arrowDown", "M12,5V19", "M6,13L12,19L18,13")
    val Widgets = icon("widgets", "M4,4h7v7h-7z", "M13,4h7v7h-7z", "M4,13h16v7h-16z")
    val Pages = icon("pages", "M7,4h10v16h-10z", "M4,6.5V17.5", "M20,6.5V17.5")
    val Resize = icon("resize", "M9,20H20V9", "M14,20H20V14")
    val Sliders = icon("sliders", "M4,7H20", "M4,17H20", "M9,4.5V9.5", "M15,14.5V19.5")

    val Compass = icon("compass", "M12,3a9,9 0 1,0 0,18a9,9 0 1,0 0,-18z", "M14.8,9.2L13.2,13.2L9.2,14.8L10.8,10.8Z")
    val Glance = icon("glance", "M4,8H20", "M7,13H17", "M9,17H15")

    fun kind(k: WidgetKind): ImageVector = when (k) {
        WidgetKind.SMART_STACK -> Stack
        WidgetKind.MUSIC -> Note
        WidgetKind.WEATHER -> PartlyDay
        WidgetKind.CALENDAR -> Calendar
        WidgetKind.CLOCK -> Clock
        WidgetKind.BATTERY -> Bolt
        WidgetKind.PHOTOS -> Photo
        WidgetKind.NOTES -> Notes
        WidgetKind.SEARCH -> com.shivam.glasslauncher.ui.glass.GlassIcons.Search
        WidgetKind.GLANCE -> Glance
        WidgetKind.COMPASS -> Compass
        WidgetKind.ICON_GROUP -> Widgets
        WidgetKind.CHECKLIST -> Notes
        WidgetKind.FOCUS -> Clock
        WidgetKind.QUICK_ACTIONS -> Sliders
        WidgetKind.APP_WIDGET -> Widgets
    }
}
