package com.shivam.glasslauncher.widgets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ClaudeUsageTest {
    @Test fun readsUsageWidget() = assertEquals(
        ClaudeUsage(0, "Starts when a message is sent", 78, "Resets Mon, 10:30 pm", 83),
        ClaudeUsage.parse(listOf("Claude", "Current session", "0%", "Starts when a message is sent", "Weekly limits", "78%", "Resets Mon, 10:30 pm", "83%")),
    )

    @Test fun unknownLayoutFallsBack() = assertNull(ClaudeUsage.parse(listOf("Claude", "Loading…")))

    @Test fun keepsLastReadingWhileUsageReloads() {
        val old = ClaudeUsage(10, null, 50, null, null)
        // Header drawn before the ListView rows arrive: keep the tank, don't flash u.sage's widget.
        assertEquals(old, ClaudeUsage.next(old, listOf("Claude", "Updated 2m ago")))
        assertNull(ClaudeUsage.next(null, listOf("Claude")))
    }

    @Test fun signInStateShowsUsageWidget() =
        assertNull(ClaudeUsage.next(ClaudeUsage(10, null, 50, null, null), listOf("Tap to sign in")))

    @Test fun readsSessionCountdown() {
        val u = ClaudeUsage.parse(listOf("Current session", "17%", "Resets in 4 hr 23 min", "Weekly limits", "80%", "Resets Mon, 10:30 pm", "85%"))!!
        assertEquals(263, u.sessionResetMinutes)
        assertEquals("4h 23m", ClaudeUsage.formatLeft(263))
        assertEquals("45m", ClaudeUsage.formatLeft(45))
        assertNull(ClaudeUsage.minutesIn("Starts when a message is sent"))
    }

    @Test fun liquidFollowsThemeUntilNearLimit() {
        val accent = androidx.compose.ui.graphics.Color.Blue
        assertEquals(accent, liquidColor(80f, accent))
        assertEquals(liquidColor(90f, accent), liquidColor(100f, androidx.compose.ui.graphics.Color.Green))
    }
}
