package com.shivam.glasslauncher.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CalendarLabelsTest {
    @Test fun farApartKeepFullWidth() = assertEquals(
        listOf(LabelSlot(0, 100f), LabelSlot(0, 100f)),
        placeLabels(listOf(0f, 200f), listOf(100f, 100f), lanes = 1, gap = 4f, minWidth = 30f),
    )

    @Test fun closeLabelsTrimToTheNextOneInOneRow() = assertEquals(
        listOf(LabelSlot(0, 76f), LabelSlot(0, 120f)),
        placeLabels(listOf(0f, 80f), listOf(150f, 120f), lanes = 1, gap = 4f, minWidth = 30f),
    )

    @Test fun secondRowTakesTheOverlap() = assertEquals(
        listOf(LabelSlot(0, 150f), LabelSlot(1, 120f)),
        placeLabels(listOf(0f, 80f), listOf(150f, 120f), lanes = 2, gap = 4f, minWidth = 30f),
    )

    @Test fun tooNarrowIsHidden() {
        val s = placeLabels(listOf(0f, 20f), listOf(150f, 120f), lanes = 1, gap = 4f, minWidth = 30f)
        assertNull(s[0])
        assertEquals(LabelSlot(0, 120f), s[1])
    }
}
