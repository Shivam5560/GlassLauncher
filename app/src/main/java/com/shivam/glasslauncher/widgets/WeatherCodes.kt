package com.shivam.glasslauncher.widgets

/** The glyphs the weather widget draws (original line art in [WidgetGlyphs]). */
enum class WeatherGlyph { SUN, MOON, PARTLY_CLOUDY_DAY, PARTLY_CLOUDY_NIGHT, CLOUD, FOG, DRIZZLE, RAIN, HEAVY_RAIN, SLEET, SNOW, THUNDER }

/**
 * WMO weather interpretation codes as used by Open-Meteo, mapped to a short description and a
 * glyph. Unknown codes fall back to cloud. Pure, so it is unit-tested.
 */
object WeatherCodes {

    fun description(code: Int): String = when (code) {
        0 -> "Clear"
        1 -> "Mostly Clear"
        2 -> "Partly Cloudy"
        3 -> "Cloudy"
        45, 48 -> "Fog"
        51, 53, 55 -> "Drizzle"
        56, 57 -> "Freezing Drizzle"
        61, 63 -> "Rain"
        65 -> "Heavy Rain"
        66, 67 -> "Freezing Rain"
        71, 73 -> "Snow"
        75 -> "Heavy Snow"
        77 -> "Snow Grains"
        80, 81 -> "Showers"
        82 -> "Heavy Showers"
        85, 86 -> "Snow Showers"
        95 -> "Thunderstorm"
        96, 99 -> "Thunderstorm, Hail"
        else -> "Cloudy"
    }

    fun glyph(code: Int, isDay: Boolean): WeatherGlyph = when (code) {
        0 -> if (isDay) WeatherGlyph.SUN else WeatherGlyph.MOON
        1, 2 -> if (isDay) WeatherGlyph.PARTLY_CLOUDY_DAY else WeatherGlyph.PARTLY_CLOUDY_NIGHT
        3 -> WeatherGlyph.CLOUD
        45, 48 -> WeatherGlyph.FOG
        51, 53, 55 -> WeatherGlyph.DRIZZLE
        56, 57, 66, 67, 77 -> WeatherGlyph.SLEET
        61, 63, 80, 81 -> WeatherGlyph.RAIN
        65, 82 -> WeatherGlyph.HEAVY_RAIN
        71, 73, 75, 85, 86 -> WeatherGlyph.SNOW
        95, 96, 99 -> WeatherGlyph.THUNDER
        else -> WeatherGlyph.CLOUD
    }

    /** Celsius to the display unit, rounded. */
    fun display(tempC: Double, fahrenheit: Boolean): Int =
        Math.round(if (fahrenheit) tempC * 9.0 / 5.0 + 32.0 else tempC).toInt()
}
