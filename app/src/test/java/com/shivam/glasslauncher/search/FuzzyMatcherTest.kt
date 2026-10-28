package com.shivam.glasslauncher.search

import org.junit.Assert.assertTrue
import org.junit.Test

class FuzzyMatcherTest {
    private fun s(q: String, label: String) = FuzzyMatcher.score(q, FuzzyMatcher.Target(label))

    @Test fun ordering() {
        assertTrue(s("ma", "Maps") > s("ma", "Gmail"))
        assertTrue(s("gm", "Google Maps") > 0)
        assertTrue(s("app", "WhatsApp") > 0)
        assertTrue(s("whtsp", "WhatsApp") > 0)
        assertTrue(s("spotfy", "Spotify") > 0)
        assertTrue(s("zzz", "Spotify") == 0)
        assertTrue(s("cam", "Camera") > s("cam", "Webcam"))
    }
}
