package com.shivam.glasslauncher.widgets

/** One coloured run of a quote: characters [start, end) in ARGB [color]. */
data class QuoteSpan(val start: Int, val end: Int, val color: Int)

/**
 * Per-word colours for the Quotes widget, stored beside the text as "start,end,argb;…" so the quote
 * itself stays plain. Spans never overlap: colouring a range replaces whatever was there.
 */
object QuoteSpans {

    fun decode(s: String?): List<QuoteSpan> = s.orEmpty().split(';').mapNotNull { part ->
        val f = part.split(',')
        if (f.size != 3) return@mapNotNull null
        val a = f[0].toIntOrNull() ?: return@mapNotNull null
        val b = f[1].toIntOrNull() ?: return@mapNotNull null
        val c = f[2].toLongOrNull(16)?.toInt() ?: return@mapNotNull null
        if (b > a) QuoteSpan(a, b, c) else null
    }

    fun encode(spans: List<QuoteSpan>): String =
        spans.joinToString(";") { "${it.start},${it.end},${Integer.toHexString(it.color)}" }

    /** Colours [start, end) (or removes colour when [color] is null). */
    fun apply(spans: List<QuoteSpan>, start: Int, end: Int, color: Int?): List<QuoteSpan> {
        if (end <= start) return spans
        val out = ArrayList<QuoteSpan>()
        for (s in spans) {
            if (s.end <= start || s.start >= end) { out += s; continue }
            if (s.start < start) out += s.copy(end = start)
            if (s.end > end) out += s.copy(start = end)
        }
        if (color != null) out += QuoteSpan(start, end, color)
        return out.sortedBy { it.start }
    }

    /** Keeps colours on the same words after an edit: spans after the change shift, the edited run is cut out. */
    fun shift(spans: List<QuoteSpan>, old: String, new: String): List<QuoteSpan> {
        if (old == new) return spans
        var prefix = 0
        while (prefix < old.length && prefix < new.length && old[prefix] == new[prefix]) prefix++
        var suffix = 0
        while (suffix < old.length - prefix && suffix < new.length - prefix &&
            old[old.length - 1 - suffix] == new[new.length - 1 - suffix]) suffix++
        val oldEnd = old.length - suffix
        val delta = new.length - old.length
        return spans.mapNotNull { s ->
            val a = if (s.start >= oldEnd) s.start + delta else minOf(s.start, prefix)
            val b = if (s.end >= oldEnd) s.end + delta else minOf(s.end, prefix)
            if (b > a) s.copy(start = a, end = b) else null
        }
    }

    /** Spans clipped to a text of [length] (a stored quote may have been edited elsewhere). */
    fun clip(spans: List<QuoteSpan>, length: Int) =
        spans.mapNotNull { s -> s.copy(end = minOf(s.end, length)).takeIf { it.end > it.start } }
}
