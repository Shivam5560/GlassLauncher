package com.shivam.glasslauncher.search

import java.text.Normalizer

/**
 * Scores how well a query matches a label. Prefix matches dominate, then word prefixes,
 * initials ("gm" -> "Google Maps"), substrings, in-order subsequences and finally a one-typo
 * tolerant prefix match. Returns 0 when there is no match.
 */
object FuzzyMatcher {

    private val diacritics = Regex("\\p{Mn}+")

    fun normalize(s: String): String =
        diacritics.replace(Normalizer.normalize(s, Normalizer.Form.NFD), "").lowercase().trim()

    /** Pre-computed searchable form of a label. */
    class Target(label: String) {
        val text: String = normalize(label)
        val compact: String = text.replace(Regex("[^a-z0-9]"), "")
        val words: List<String> = splitWords(label).map { normalize(it) }.filter { it.isNotEmpty() }
        val initials: String = words.joinToString("") { it.take(1) }
    }

    private fun splitWords(label: String): List<String> {
        // Split on separators and on camel case ("WhatsApp" -> "Whats", "App").
        val spaced = label.replace(Regex("([a-z])([A-Z])"), "$1 $2")
        return spaced.split(Regex("[^\\p{L}\\p{N}]+"))
    }

    fun score(query: String, target: Target): Int {
        val q = normalize(query)
        if (q.isEmpty()) return 0
        val t = target.text
        if (t == q) return 1000
        if (t.startsWith(q)) return 900 - (t.length - q.length).coerceAtMost(50)
        val qc = q.replace(Regex("[^a-z0-9]"), "")
        if (qc.isNotEmpty() && target.compact.startsWith(qc)) return 850 - (target.compact.length - qc.length).coerceAtMost(50)
        target.words.forEachIndexed { i, w ->
            if (w.startsWith(q)) return 700 - i * 10 - (w.length - q.length).coerceAtMost(30)
        }
        if (q.length >= 2 && target.initials.startsWith(qc)) return 600 + qc.length
        val idx = t.indexOf(q)
        if (idx >= 0) return 500 - idx.coerceAtMost(50)
        val gaps = subsequenceGaps(qc, target.compact)
        if (gaps >= 0 && qc.length >= 2) return (300 - gaps * 8).coerceAtLeast(120)
        if (qc.length >= 4) {
            val prefix = target.compact.take(qc.length)
            if (damerauLevenshtein(qc, prefix) <= 1) return 100
        }
        return 0
    }

    /** Number of skipped characters if [q] is an in-order subsequence of [t], or -1. */
    private fun subsequenceGaps(q: String, t: String): Int {
        if (q.isEmpty()) return -1
        var ti = 0
        var gaps = 0
        var started = false
        for (c in q) {
            var found = false
            while (ti < t.length) {
                if (t[ti] == c) { found = true; ti++; break }
                if (started) gaps++
                ti++
            }
            if (!found) return -1
            started = true
        }
        return gaps
    }

    fun damerauLevenshtein(a: String, b: String): Int {
        val d = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) d[i][0] = i
        for (j in 0..b.length) d[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                d[i][j] = minOf(d[i - 1][j] + 1, d[i][j - 1] + 1, d[i - 1][j - 1] + cost)
                if (i > 1 && j > 1 && a[i - 1] == b[j - 2] && a[i - 2] == b[j - 1]) {
                    d[i][j] = minOf(d[i][j], d[i - 2][j - 2] + 1)
                }
            }
        }
        return d[a.length][b.length]
    }
}
