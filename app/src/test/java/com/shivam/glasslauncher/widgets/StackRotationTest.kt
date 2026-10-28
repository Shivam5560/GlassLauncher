package com.shivam.glasslauncher.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StackRotationTest {
    private val stack = listOf(WidgetKind.CALENDAR, WidgetKind.WEATHER, WidgetKind.MUSIC, WidgetKind.BATTERY)

    @Test fun periods() {
        assertEquals(StackRotation.Period.NIGHT, StackRotation.period(4))
        assertEquals(StackRotation.Period.MORNING, StackRotation.period(5))
        assertEquals(StackRotation.Period.MORNING, StackRotation.period(10))
        assertEquals(StackRotation.Period.DAY, StackRotation.period(11))
        assertEquals(StackRotation.Period.EVENING, StackRotation.period(17))
        assertEquals(StackRotation.Period.EVENING, StackRotation.period(22))
        assertEquals(StackRotation.Period.NIGHT, StackRotation.period(23))
        assertEquals(StackRotation.Period.NIGHT, StackRotation.period(24))
    }

    @Test fun calendarInTheMorningMusicInTheEvening() {
        assertEquals(0, StackRotation.pick(stack, 8))
        assertEquals(1, StackRotation.pick(stack, 13))
        assertEquals(2, StackRotation.pick(stack, 20))
        assertEquals(3, StackRotation.pick(stack, 2))
    }

    @Test fun fallsBackToNextFavourite() {
        val noCalendar = listOf(WidgetKind.MUSIC, WidgetKind.WEATHER)
        assertEquals(1, StackRotation.pick(noCalendar, 8))
        assertNull(StackRotation.pick(listOf(WidgetKind.NOTES), 8))
    }
}
