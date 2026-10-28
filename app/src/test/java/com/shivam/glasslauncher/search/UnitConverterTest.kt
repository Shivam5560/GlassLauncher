package com.shivam.glasslauncher.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UnitConverterTest {
    @Test fun length() {
        val c = UnitConverter.convert("5 km to mi")!!
        assertEquals(3.10686, c.result, 1e-4)
        assertEquals(30.48, UnitConverter.convert("1 ft in cm")!!.result, 1e-9)
    }

    @Test fun weight() {
        assertEquals(2.20462, UnitConverter.convert("1kg to lb")!!.result, 1e-4)
    }

    @Test fun temperature() {
        assertEquals(212.0, UnitConverter.convert("100 c to f")!!.result, 1e-9)
        assertEquals(0.0, UnitConverter.convert("32 fahrenheit in celsius")!!.result, 1e-9)
        assertEquals(273.15, UnitConverter.convert("0 °c to k")!!.result, 1e-9)
    }

    @Test fun bareUnitUsesDefaultTarget() {
        val c = UnitConverter.convert("10 km")!!
        assertEquals("mi", c.to.symbol)
    }

    @Test fun rejects() {
        assertNull(UnitConverter.convert("5 km to kg"))
        assertNull(UnitConverter.convert("spotify"))
    }
}
