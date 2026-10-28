package com.shivam.glasslauncher.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalculatorTest {
    private fun eval(s: String) = Calculator.evaluateOrNull(s)

    @Test fun precedence() {
        assertEquals(14.0, eval("2+3*4")!!, 1e-9)
        assertEquals(20.0, eval("(2+3)*4")!!, 1e-9)
        assertEquals(512.0, eval("2^3^2")!!, 1e-9)
        assertEquals(-9.0, eval("-3^2")!!, 1e-9)
    }

    @Test fun symbolsAndImplicit() {
        assertEquals(12.0, eval("3 x 4")!!, 1e-9)
        assertEquals(12.0, eval("3×4")!!, 1e-9)
        assertEquals(2.0, eval("8÷4")!!, 1e-9)
        assertEquals(2 * Math.PI, eval("2pi")!!, 1e-9)
        assertEquals(21.0, eval("(1+2)(3+4)")!!, 1e-9)
    }

    @Test fun functionsAndPercent() {
        assertEquals(3.0, eval("sqrt(9)")!!, 1e-9)
        assertEquals(2.0, eval("log(100)")!!, 1e-9)
        assertEquals(120.0, eval("5!")!!, 1e-9)
        assertEquals(1.0, eval("10 % 3")!!, 1e-9)
        assertEquals(0.5, eval("50%")!!, 1e-9)
        assertEquals(1.5e3, eval("1.5e3 + 0")!!, 1e-9)
    }

    @Test fun rejectsNonMath() {
        assertNull(eval("maps"))
        assertNull(eval("42"))
        assertNull(eval("1/0"))
        assertNull(eval("2+"))
        assertNull(eval("exp"))
    }

    @Test fun formatting() {
        assertEquals("14", Calculator.format(14.0))
        assertEquals("0.333333333333", Calculator.format(1.0 / 3))
        assertEquals("0.3", Calculator.format(0.1 + 0.2))
    }
}
