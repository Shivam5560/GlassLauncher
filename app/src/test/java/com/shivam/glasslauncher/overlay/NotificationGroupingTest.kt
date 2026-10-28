package com.shivam.glasslauncher.overlay

import com.shivam.glasslauncher.overlay.notifications.NotifEntry
import com.shivam.glasslauncher.overlay.notifications.NotificationGrouping
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotificationGroupingTest {

    private fun n(
        key: String,
        pkg: String,
        time: Long,
        group: String? = null,
        summary: Boolean = false,
        media: Boolean = false,
        clearable: Boolean = true,
    ) = NotifEntry(
        key = key, packageName = pkg, appLabel = pkg.substringAfterLast('.'), title = key, text = "",
        postTime = time, groupKey = group, isGroupSummary = summary, isMedia = media, isClearable = clearable,
    )

    @Test
    fun groupsPerAppNewestFirst() {
        val stacks = NotificationGrouping.stacks(
            listOf(
                n("a1", "com.chat", 100),
                n("b1", "com.mail", 300),
                n("a2", "com.chat", 200),
                n("a3", "com.chat", 50),
            ),
        )
        assertEquals(listOf("com.mail", "com.chat"), stacks.map { it.packageName })
        assertEquals(listOf("a2", "a1", "a3"), stacks[1].items.map { it.key })
        assertEquals("a2", stacks[1].latest.key)
        assertEquals(3, stacks[1].count)
    }

    @Test
    fun stacksOrderedByNewestNotification() {
        val stacks = NotificationGrouping.stacks(
            listOf(n("old", "com.a", 10), n("new", "com.b", 20), n("newer", "com.a", 30)),
        )
        assertEquals(listOf("com.a", "com.b"), stacks.map { it.packageName })
    }

    @Test
    fun collapsedPileShowsAtMostThreeLayers() {
        val items = (1..5).map { n("k$it", "com.chat", it.toLong()) }
        val collapsed = NotificationGrouping.stacks(items).single()
        assertFalse(collapsed.expanded)
        assertEquals(3, collapsed.visibleLayers)
        assertEquals(4, collapsed.hiddenCount)

        val two = NotificationGrouping.stacks(items.take(2)).single()
        assertEquals(2, two.visibleLayers)
        assertEquals(1, two.hiddenCount)
    }

    @Test
    fun expandedStackShowsEveryCard() {
        val items = (1..5).map { n("k$it", "com.chat", it.toLong()) }
        val stack = NotificationGrouping.stacks(items, expanded = setOf("com.chat")).single()
        assertTrue(stack.expanded)
        assertEquals(5, stack.visibleLayers)
        assertEquals(0, stack.hiddenCount)
    }

    @Test
    fun singleNotificationNeverExpands() {
        val stack = NotificationGrouping.stacks(listOf(n("only", "com.chat", 1)), expanded = setOf("com.chat")).single()
        assertFalse(stack.expanded)
        assertEquals(1, stack.visibleLayers)
    }

    @Test
    fun summaryDroppedWhenChildrenPresent() {
        val stacks = NotificationGrouping.stacks(
            listOf(
                n("summary", "com.chat", 300, group = "g", summary = true),
                n("c1", "com.chat", 100, group = "g"),
                n("c2", "com.chat", 200, group = "g"),
            ),
        )
        assertEquals(listOf("c2", "c1"), stacks.single().items.map { it.key })
    }

    @Test
    fun loneSummaryIsKept() {
        val stacks = NotificationGrouping.stacks(listOf(n("summary", "com.chat", 300, group = "g", summary = true)))
        assertEquals(listOf("summary"), stacks.single().items.map { it.key })
    }

    @Test
    fun mediaIsPinnedNotStacked() {
        val stacks = NotificationGrouping.stacks(
            listOf(n("player", "com.music", 500, media = true), n("msg", "com.chat", 100)),
        )
        assertEquals(listOf("com.chat"), stacks.map { it.packageName })
    }

    @Test
    fun clearAllSkipsOngoingAndMedia() {
        val entries = listOf(
            n("msg", "com.chat", 100),
            n("upload", "com.drive", 200, clearable = false),
            n("player", "com.music", 300, media = true, clearable = true),
        )
        assertEquals(listOf("msg"), NotificationGrouping.clearAllKeys(entries))
        val stack = NotificationGrouping.stacks(entries).first { it.packageName == "com.drive" }
        assertTrue(stack.clearableKeys.isEmpty())
    }

    @Test
    fun emptyInputGivesNoStacks() {
        assertTrue(NotificationGrouping.stacks(emptyList()).isEmpty())
    }
}
