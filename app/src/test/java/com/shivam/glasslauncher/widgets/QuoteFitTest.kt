package com.shivam.glasslauncher.widgets

import org.junit.Assert.assertEquals
import org.junit.Test

class QuoteFitTest {
    @Test fun picksLargestFittingSize() = assertEquals(24f, QuoteFit.largest(40f, 13f) { it <= 24f })
    @Test fun fallsBackToMinimum() = assertEquals(13f, QuoteFit.largest(40f, 13f) { false })
    @Test fun keepsMaxWhenItFits() = assertEquals(40f, QuoteFit.largest(40f, 13f) { true })
}
