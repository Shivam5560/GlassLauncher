package com.shivam.glasslauncher.widgets

import org.junit.Assert.assertEquals
import org.junit.Test

class WeatherCodesTest {

    @Test fun clearDayAndNight() {
        assertEquals(WeatherGlyph.SUN, WeatherCodes.glyph(0, isDay = true))
        assertEquals(WeatherGlyph.MOON, WeatherCodes.glyph(0, isDay = false))
        assertEquals(WeatherGlyph.PARTLY_CLOUDY_NIGHT, WeatherCodes.glyph(2, isDay = false))
    }

    @Test fun everyWmoGroupMaps() {
        val expected = mapOf(
            3 to WeatherGlyph.CLOUD,
            45 to WeatherGlyph.FOG, 48 to WeatherGlyph.FOG,
            51 to WeatherGlyph.DRIZZLE, 55 to WeatherGlyph.DRIZZLE,
            56 to WeatherGlyph.SLEET, 67 to WeatherGlyph.SLEET,
            61 to WeatherGlyph.RAIN, 80 to WeatherGlyph.RAIN,
            65 to WeatherGlyph.HEAVY_RAIN, 82 to WeatherGlyph.HEAVY_RAIN,
            71 to WeatherGlyph.SNOW, 86 to WeatherGlyph.SNOW,
            95 to WeatherGlyph.THUNDER, 99 to WeatherGlyph.THUNDER,
        )
        expected.forEach { (code, glyph) -> assertEquals("code $code", glyph, WeatherCodes.glyph(code, true)) }
        assertEquals("Thunderstorm, Hail", WeatherCodes.description(96))
        assertEquals("Fog", WeatherCodes.description(48))
    }

    @Test fun unknownCodeFallsBackToCloud() {
        assertEquals(WeatherGlyph.CLOUD, WeatherCodes.glyph(42, true))
        assertEquals("Cloudy", WeatherCodes.description(42))
    }

    @Test fun units() {
        assertEquals(21, WeatherCodes.display(21.4, fahrenheit = false))
        assertEquals(71, WeatherCodes.display(21.4, fahrenheit = true))
        assertEquals(-4, WeatherCodes.display(-20.0, fahrenheit = true))
    }
}
