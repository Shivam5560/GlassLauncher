package com.shivam.glasslauncher.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OpenMeteoTest {

    private val forecast = """
        {"latitude":28.625,"longitude":77.25,"utc_offset_seconds":19800,"timezone":"Asia/Kolkata",
         "current":{"time":"2026-10-28T14:15","interval":900,"temperature_2m":29.6,"apparent_temperature":31.2,
                    "relative_humidity_2m":48,"weather_code":2,"is_day":1,"wind_speed_10m":7.9},
         "hourly":{"time":["2026-10-28T12:00","2026-10-28T13:00","2026-10-28T14:00","2026-10-28T15:00","2026-10-28T16:00"],
                   "temperature_2m":[27.1,28.4,29.5,29.9,null],
                   "weather_code":[1,1,2,3,61],
                   "is_day":[1,1,1,1,1]},
         "daily":{"time":["2026-10-28","2026-10-29"],
                  "weather_code":[2,95],
                  "temperature_2m_max":[31.0,28.2],
                  "temperature_2m_min":[19.4,18.0],
                  "precipitation_probability_max":[5,80]}}
    """.trimIndent()

    @Test fun parsesCurrentHourlyAndDaily() {
        val d = OpenMeteo.parseForecast(forecast)
        assertEquals(29.6, d.current.tempC, 0.001)
        assertEquals(31.2, d.current.apparentC!!, 0.001)
        assertEquals(2, d.current.code)
        assertTrue(d.current.isDay)
        assertEquals(48, d.current.humidity)
        assertEquals("Asia/Kolkata", d.timezone)
        assertEquals(19800, d.utcOffsetSeconds)
        // The hour with a null temperature is skipped.
        assertEquals(4, d.hourly.size)
        assertEquals(2, d.daily.size)
        assertEquals(95, d.daily[1].code)
        assertEquals(80, d.daily[1].precipProbability)
        assertEquals(18.0, d.daily[1].minC, 0.001)
    }

    @Test fun upcomingHoursStartAtCurrentHour() {
        val d = OpenMeteo.parseForecast(forecast)
        val next = OpenMeteo.upcomingHours(d, 6)
        assertEquals(listOf("2026-10-28T14:00", "2026-10-28T15:00"), next.map { it.time })
    }

    @Test fun missingOptionalFields() {
        val minimal = """{"current":{"time":"2026-10-28T02:00","temperature_2m":-3.5,"weather_code":71,"is_day":0}}"""
        val d = OpenMeteo.parseForecast(minimal)
        assertEquals(-3.5, d.current.tempC, 0.001)
        assertFalse(d.current.isDay)
        assertNull(d.current.apparentC)
        assertNull(d.current.humidity)
        assertTrue(d.hourly.isEmpty())
        assertTrue(d.daily.isEmpty())
        assertEquals(WeatherGlyph.SNOW, WeatherCodes.glyph(d.current.code, d.current.isDay))
    }

    @Test(expected = IllegalArgumentException::class)
    fun errorResponseThrows() {
        OpenMeteo.parseForecast("""{"error":true,"reason":"Latitude must be in range of -90 to 90°."}""")
    }

    @Test fun parsesGeocoding() {
        val json = """
            {"results":[
              {"id":1261481,"name":"New Delhi","latitude":28.63576,"longitude":77.22445,"country":"India","admin1":"Delhi"},
              {"id":2,"name":"Nowhere"},
              {"id":3,"name":"Delhi","latitude":42.27,"longitude":-74.91,"country":"United States","admin1":""}
            ],"generationtime_ms":0.5}
        """.trimIndent()
        val places = OpenMeteo.parseGeocoding(json)
        assertEquals(2, places.size)
        assertEquals("New Delhi", places[0].name)
        assertEquals("Delhi, India", places[0].subtitle)
        assertEquals("United States", places[1].subtitle)
        assertEquals(77.22445, places[0].longitude, 1e-6)
        assertTrue(OpenMeteo.parseGeocoding("""{"generationtime_ms":0.2}""").isEmpty())
    }

    @Test fun urlsAreEncoded() {
        assertTrue(OpenMeteo.geocodingUrl("São Paulo").contains("name=S%C3%A3o+Paulo"))
        assertTrue(OpenMeteo.forecastUrl(28.6, 77.2).contains("latitude=28.6000&longitude=77.2000"))
    }
}
