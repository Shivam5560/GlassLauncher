package com.shivam.glasslauncher.search

import org.junit.Assert.assertEquals
import org.junit.Test

class SpotlightSearchTest {
    private val day = 86_400_000L
    private val now = 1_000 * day

    private fun rank(q: String, vararg files: Pair<String, Long>) =
        FileSearch.rank(q, files.toList(), now, { it.first }, { it.second }).map { it.first }

    @Test fun fileRanking() {
        // Name start beats a mid-word hit even when the mid-word file is newer.
        assertEquals(listOf("Invoice_March.pdf", "ReInvoiced.txt"), rank("invoice", "ReInvoiced.txt" to now, "Invoice_March.pdf" to now - 90 * day))
        // Equal matches: newest first.
        assertEquals(listOf("tax 2024.pdf", "tax 2023.pdf"), rank("tax", "tax 2023.pdf" to now - 400 * day, "tax 2024.pdf" to now - 40 * day))
        // Every word must match; the weakest word decides.
        assertEquals("passport_scan.jpg", rank("pass scan", "scan_old.jpg" to now, "passport_scan.jpg" to now - 200 * day).first())
        // Extensions are searchable.
        assertEquals(listOf("notes.pdf"), rank("pdf", "notes.pdf" to now))
    }

    @Test fun recentSearches() {
        val list = SearchHistory.add("Maps", listOf("whatsapp", "maps", "calc"))
        assertEquals(listOf("Maps", "whatsapp", "calc"), list) // case-insensitive de-dupe, newest first
        val full = (1..SearchHistory.MAX).fold(emptyList<String>()) { acc, i -> SearchHistory.add("q$i", acc) }
        assertEquals(SearchHistory.MAX, SearchHistory.add("new", full).size)
        assertEquals("new", SearchHistory.add("new", full).first())
    }
}
