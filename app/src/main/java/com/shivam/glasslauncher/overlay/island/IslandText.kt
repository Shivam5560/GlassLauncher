package com.shivam.glasslauncher.overlay.island

/**
 * Text rules for the island, with no Android types so they run in plain JVM unit tests.
 * Rendering is handled in IslandView: every island label is one line (or two where noted) with an
 * ellipsis at the end. These rules make sure the input is short, single-line and never empty.
 */
object IslandText {
    /** Longest string the island will lay out; anything longer is cut with an ellipsis. */
    const val MAX_CHARS = 200
    const val ELLIPSIS = "…"

    /** Collapses whitespace and newlines, trims, and caps at [maxChars]. Null or blank gives "". */
    fun clean(raw: String?, maxChars: Int = MAX_CHARS): String {
        if (raw == null) return ""
        val single = raw.trim().replace(WHITESPACE, " ")
        return truncate(single, maxChars)
    }

    /**
     * Keeps at most [maxChars] code points; when cut, the last one is replaced by [ELLIPSIS].
     * Counts code points, not UTF-16 units, so an emoji or CJK character is never split in half.
     */
    fun truncate(text: String, maxChars: Int): String {
        if (maxChars <= 0) return ""
        if (text.codePointCount(0, text.length) <= maxChars) return text
        if (maxChars == 1) return ELLIPSIS
        val end = text.offsetByCodePoints(0, maxChars - 1)
        return text.substring(0, end).trimEnd() + ELLIPSIS
    }

    /** The first non-blank of [value] (cleaned) or [fallback]. */
    fun orFallback(value: String?, fallback: String): String = clean(value).ifEmpty { fallback }

    /** Music title line: the track title, else the player's app name, else "Now playing". */
    fun musicTitle(title: String?, appLabel: String?): String =
        clean(title).ifEmpty { orFallback(appLabel, NOW_PLAYING) }

    /** Music artist line: empty when the player sends none, so the row is simply left out. */
    fun musicArtist(artist: String?): String = clean(artist)

    private const val NOW_PLAYING = "Now playing"
    private val WHITESPACE = Regex("\\s+")
}
