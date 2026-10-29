package com.shivam.glasslauncher.overlay

import com.shivam.glasslauncher.overlay.island.IslandText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class IslandTextTest {

    @Test
    fun emptyStringStaysEmpty() {
        assertEquals("", IslandText.clean(""))
        assertEquals("", IslandText.clean("   \n\t "))
        assertEquals("", IslandText.clean(null))
        assertEquals("", IslandText.truncate("", 10))
    }

    @Test
    fun veryLongSingleWordIsCutWithEllipsis() {
        val word = "Supercalifragilisticexpialidocious".repeat(10)
        val out = IslandText.clean(word, maxChars = 20)
        assertEquals(20, out.codePointCount(0, out.length))
        assertTrue(out.endsWith(IslandText.ELLIPSIS))
        assertTrue(word.startsWith(out.dropLast(1)))
    }

    @Test
    fun arabicAndHebrewKeepTheirOrderAndCut() {
        val arabic = "مرحبا بكم في تطبيق الموسيقى الجديد"
        val hebrew = "שלום לכולם ברוכים הבאים לאפליקציה"
        assertEquals(arabic, IslandText.clean(arabic))
        assertEquals(hebrew, IslandText.clean(hebrew))
        val cut = IslandText.clean(arabic, maxChars = 10)
        assertEquals(10, cut.codePointCount(0, cut.length))
        assertTrue(cut.endsWith(IslandText.ELLIPSIS))
        assertTrue(arabic.startsWith(cut.dropLast(1)))
    }

    @Test
    fun emojiIsNeverSplitInHalf() {
        // Each of these is one code point but two UTF-16 units; a naive substring would split them.
        assertEquals("🎵🎶🎧…", IslandText.truncate("🎵🎶🎧🎤🎸🥁🎹🎷🎺🎻", maxChars = 4))
        // A ZWJ family sequence is several code points; cutting inside it keeps only the first one whole.
        assertEquals("👨…", IslandText.truncate("👨‍👩‍👧‍👦 family", maxChars = 2))
    }

    @Test
    fun cjkCountsEachCharacter() {
        val text = "今日は素晴らしい音楽を聴きましょう"
        assertEquals("今日は素…", IslandText.truncate(text, maxChars = 5))
        assertEquals(text, IslandText.clean(text))
    }

    @Test
    fun twoHundredCharStringIsCappedAtTheLimit() {
        val s = "a".repeat(200)
        assertEquals(s, IslandText.clean(s))
        val out = IslandText.clean("b".repeat(201))
        assertEquals(IslandText.MAX_CHARS, out.codePointCount(0, out.length))
        assertTrue(out.endsWith(IslandText.ELLIPSIS))
    }

    @Test
    fun textExactlyAtTheLimitIsUntouched() {
        val exact = "x".repeat(IslandText.MAX_CHARS)
        assertEquals(exact, IslandText.clean(exact))
        assertEquals("abcde", IslandText.truncate("abcde", 5))
        assertEquals("abcd…", IslandText.truncate("abcdef", 5))
    }

    @Test
    fun whitespaceAndNewlinesCollapseToOneLine() {
        assertEquals("Line one Line two", IslandText.clean("  Line one\n\nLine two  "))
    }

    @Test
    fun missingTitleAndArtistFallBack() {
        assertEquals("Spotify", IslandText.musicTitle(null, "Spotify"))
        assertEquals("Spotify", IslandText.musicTitle("  ", "Spotify"))
        assertEquals("Now playing", IslandText.musicTitle("", null))
        assertEquals("Song", IslandText.musicTitle("Song", "Spotify"))
        assertEquals("", IslandText.musicArtist(null))
        assertEquals("", IslandText.musicArtist("   "))
    }

    @Test
    fun zeroOrNegativeLimitGivesEmpty() {
        assertEquals("", IslandText.truncate("abc", 0))
        assertEquals("", IslandText.truncate("abc", -3))
        assertEquals(IslandText.ELLIPSIS, IslandText.truncate("abc", 1))
    }
}
