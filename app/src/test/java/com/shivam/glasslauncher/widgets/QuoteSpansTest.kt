package com.shivam.glasslauncher.widgets

import org.junit.Assert.assertEquals
import org.junit.Test

class QuoteSpansTest {
    private val gold = 0xFFFFD479.toInt()
    private val rose = 0xFFFF8FA3.toInt()

    @Test fun roundTripsThroughStorage() {
        val spans = listOf(QuoteSpan(0, 4, gold), QuoteSpan(10, 12, rose))
        assertEquals(spans, QuoteSpans.decode(QuoteSpans.encode(spans)))
        assertEquals(emptyList<QuoteSpan>(), QuoteSpans.decode("garbage;1,x,2"))
    }

    @Test fun colouringReplacesOverlapsAndClearSplits() {
        val one = QuoteSpans.apply(emptyList(), 0, 10, gold)
        assertEquals(listOf(QuoteSpan(0, 3, gold), QuoteSpan(3, 6, rose), QuoteSpan(6, 10, gold)), QuoteSpans.apply(one, 3, 6, rose))
        assertEquals(listOf(QuoteSpan(0, 3, gold), QuoteSpan(6, 10, gold)), QuoteSpans.apply(one, 3, 6, null))
    }

    @Test fun editsKeepColoursOnTheSameWords() {
        // "hello world" with "world" gold; typing "big " before it moves the colour along.
        val spans = listOf(QuoteSpan(6, 11, gold))
        assertEquals(listOf(QuoteSpan(10, 15, gold)), QuoteSpans.shift(spans, "hello world", "hello big world"))
        // Deleting the coloured word removes its colour.
        assertEquals(emptyList<QuoteSpan>(), QuoteSpans.shift(spans, "hello world", "hello "))
    }
}
