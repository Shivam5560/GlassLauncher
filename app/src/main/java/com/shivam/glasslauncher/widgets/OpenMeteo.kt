package com.shivam.glasslauncher.widgets

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder

data class CurrentWeather(
    val time: String,
    val tempC: Double,
    val apparentC: Double?,
    val code: Int,
    val isDay: Boolean,
    val humidity: Int?,
    val windKmh: Double?,
)

data class HourWeather(val time: String, val tempC: Double, val code: Int, val isDay: Boolean)

data class DayWeather(val date: String, val code: Int, val maxC: Double, val minC: Double, val precipProbability: Int?)

data class WeatherData(
    val current: CurrentWeather,
    val hourly: List<HourWeather>,
    val daily: List<DayWeather>,
    val timezone: String,
    val utcOffsetSeconds: Int,
)

/** A geocoding hit (Open-Meteo geocoding API). */
data class GeoPlace(
    val name: String,
    val admin: String?,
    val country: String?,
    val latitude: Double,
    val longitude: Double,
) {
    val subtitle: String get() = listOfNotNull(admin, country).filter { it.isNotBlank() }.joinToString(", ")
}

/**
 * Open-Meteo (no API key; docs/CONSTRAINTS.md). URL building and JSON parsing only, so the
 * parsing is unit-tested; the network call lives in [WeatherRepository].
 */
object OpenMeteo {

    fun forecastUrl(lat: Double, lon: Double): String =
        "https://api.open-meteo.com/v1/forecast?latitude=${fmt(lat)}&longitude=${fmt(lon)}" +
            "&current=temperature_2m,apparent_temperature,relative_humidity_2m,weather_code,is_day,wind_speed_10m" +
            "&hourly=temperature_2m,weather_code,is_day" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max" +
            "&timezone=auto&forecast_days=7"

    fun geocodingUrl(query: String): String =
        "https://geocoding-api.open-meteo.com/v1/search?name=${URLEncoder.encode(query.trim(), "UTF-8")}&count=10&language=en&format=json"

    private fun fmt(v: Double) = String.format(java.util.Locale.US, "%.4f", v)

    /** Parses a forecast response. Throws on a response without current conditions. */
    fun parseForecast(json: String): WeatherData {
        val root = JSONObject(json)
        if (root.optBoolean("error", false)) throw IllegalArgumentException(root.optString("reason", "Open-Meteo error"))
        val c = root.getJSONObject("current")
        val current = CurrentWeather(
            time = c.optString("time"),
            tempC = c.getDouble("temperature_2m"),
            apparentC = c.optDoubleOrNull("apparent_temperature"),
            code = c.optInt("weather_code", 0),
            isDay = c.optInt("is_day", 1) == 1,
            humidity = c.optDoubleOrNull("relative_humidity_2m")?.toInt(),
            windKmh = c.optDoubleOrNull("wind_speed_10m"),
        )
        val hourly = root.optJSONObject("hourly")?.let { h ->
            val times = h.optJSONArray("time") ?: JSONArray()
            val temps = h.optJSONArray("temperature_2m")
            val codes = h.optJSONArray("weather_code")
            val days = h.optJSONArray("is_day")
            (0 until times.length()).mapNotNull { i ->
                val t = temps?.optDoubleOrNull(i) ?: return@mapNotNull null
                HourWeather(times.getString(i), t, codes?.optInt(i, 0) ?: 0, (days?.optInt(i, 1) ?: 1) == 1)
            }
        } ?: emptyList()
        val daily = root.optJSONObject("daily")?.let { d ->
            val dates = d.optJSONArray("time") ?: JSONArray()
            val codes = d.optJSONArray("weather_code")
            val max = d.optJSONArray("temperature_2m_max")
            val min = d.optJSONArray("temperature_2m_min")
            val rain = d.optJSONArray("precipitation_probability_max")
            (0 until dates.length()).mapNotNull { i ->
                val hi = max?.optDoubleOrNull(i) ?: return@mapNotNull null
                val lo = min?.optDoubleOrNull(i) ?: return@mapNotNull null
                DayWeather(dates.getString(i), codes?.optInt(i, 0) ?: 0, hi, lo, rain?.optDoubleOrNull(i)?.toInt())
            }
        } ?: emptyList()
        return WeatherData(current, hourly, daily, root.optString("timezone", "UTC"), root.optInt("utc_offset_seconds", 0))
    }

    fun parseGeocoding(json: String): List<GeoPlace> {
        val root = JSONObject(json)
        val results = root.optJSONArray("results") ?: return emptyList()
        return (0 until results.length()).mapNotNull { i ->
            val o = results.optJSONObject(i) ?: return@mapNotNull null
            if (!o.has("latitude") || !o.has("longitude")) return@mapNotNull null
            GeoPlace(
                name = o.optString("name"),
                admin = o.optString("admin1").ifBlank { null },
                country = o.optString("country").ifBlank { null },
                latitude = o.getDouble("latitude"),
                longitude = o.getDouble("longitude"),
            )
        }
    }

    /**
     * The next [count] hourly entries starting with the hour that contains the current time
     * (times are local ISO strings like `2026-10-28T14:00`, so they compare as text).
     */
    fun upcomingHours(data: WeatherData, count: Int): List<HourWeather> {
        val nowHour = data.current.time.take(13) // yyyy-MM-ddTHH
        val start = data.hourly.indexOfFirst { it.time.take(13) >= nowHour }
        if (start < 0) return emptyList()
        return data.hourly.subList(start, minOf(data.hourly.size, start + count))
    }

    private fun JSONObject.optDoubleOrNull(key: String): Double? =
        if (!has(key) || isNull(key)) null else optDouble(key).takeIf { !it.isNaN() }

    private fun JSONArray.optDoubleOrNull(i: Int): Double? =
        if (i >= length() || isNull(i)) null else optDouble(i).takeIf { !it.isNaN() }
}
