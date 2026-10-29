package com.shivam.glasslauncher.widgets

import org.junit.Assert.*
import org.junit.Test

class ChecklistTest {
    @Test fun taskStateSurvivesWidgetStoreRoundTrip() {
        val tasks = listOf(ChecklistItem("one", "Milk, bread 🥐", true), ChecklistItem("two", "Milk, bread 🥐"))
        val widget = WidgetInstance("list", WidgetKind.CHECKLIST, WidgetSize.MEDIUM,
            mapOf(WidgetKeys.CHECKLIST to Checklist.encode(tasks)))
        val restored = WidgetState.fromJson(WidgetState(mapOf(widget.id to widget)).toJson())
        assertEquals(tasks, Checklist.decode(restored.instances.getValue("list")[WidgetKeys.CHECKLIST]))
    }

    @Test fun corruptOrAbsentDataDoesNotCrashHome() {
        assertTrue(Checklist.decode("not json").isEmpty())
        assertTrue(Checklist.decode(null).isEmpty())
        assertTrue(Checklist.decode("[{},null,4]").isEmpty())
    }

    @Test fun dataIsBoundedAndIdsAreUnique() {
        val tasks = (0..30).map { ChecklistItem("$it", "a".repeat(200)) }
        val restored = Checklist.decode(Checklist.encode(tasks))
        assertEquals(Checklist.MAX_ITEMS, restored.size)
        assertTrue(restored.all { it.text.length == Checklist.MAX_TEXT })
        assertEquals(1, Checklist.decode(Checklist.encode(listOf(tasks[0], tasks[0]))).size)
    }
}
