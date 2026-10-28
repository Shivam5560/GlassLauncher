package com.shivam.glassicons

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Validates the generated icon pack: every drawable referenced by appfilter.xml and drawable.xml
 * exists in all three variants (light, `_dark`, `_tinted`), components are well formed and unique,
 * and the assets copy of appfilter matches the resource one.
 */
class AppfilterTest {

    private val res = File("src/main/res")
    private val drawables = File(res, "drawable")

    private fun parse(file: File) = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(file).documentElement

    private fun items(root: Element, tag: String): List<Element> {
        val nodes = root.getElementsByTagName(tag)
        return (0 until nodes.length).map { nodes.item(it) as Element }
    }

    private val appfilter by lazy { parse(File(res, "xml/appfilter.xml")) }

    @Test
    fun everyMappedDrawableExistsInAllVariants() {
        val names = items(appfilter, "item").map { it.getAttribute("drawable") }.toSet()
        assertTrue("appfilter should map at least 55 icons, has ${names.size}", names.size >= 55)
        val missing = names.flatMap { n -> listOf(n, "${n}_dark", "${n}_tinted") }
            .filterNot { File(drawables, "$it.xml").exists() }
        assertEquals("Missing drawables: $missing", emptyList<String>(), missing)
    }

    @Test
    fun drawableXmlListsOnlyExistingIconsInAllVariants() {
        val listed = items(parse(File(res, "xml/drawable.xml")), "item").map { it.getAttribute("drawable") }
        assertEquals("duplicates in drawable.xml", listed.size, listed.toSet().size)
        val missing = listed.flatMap { n -> listOf(n, "${n}_dark", "${n}_tinted") }
            .filterNot { File(drawables, "$it.xml").exists() }
        assertEquals("Missing drawables: $missing", emptyList<String>(), missing)
        val mapped = items(appfilter, "item").map { it.getAttribute("drawable") }.toSet()
        assertEquals("drawable.xml and appfilter.xml should cover the same icons", mapped, listed.toSet())
    }

    @Test
    fun componentsAreWellFormedAndUnique() {
        val comps = items(appfilter, "item").map { it.getAttribute("component") }
        val pattern = Regex("""ComponentInfo\{[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+}""")
        val bad = comps.filterNot { pattern.matches(it) }
        assertEquals("Malformed components: $bad", emptyList<String>(), bad)
        val dupes = comps.groupBy { it }.filter { it.value.size > 1 }.keys
        assertEquals("Duplicate components: $dupes", emptySet<String>(), dupes)
    }

    @Test
    fun themingDrawablesExist() {
        for (tag in listOf("iconback", "iconmask", "iconupon")) {
            val img = items(appfilter, tag).single().getAttribute("img1")
            assertTrue("$tag drawable $img missing", File(drawables, "$img.xml").exists())
        }
    }

    @Test
    fun assetsCopyMatchesResource() {
        assertEquals(File(res, "xml/appfilter.xml").readText(), File("src/main/assets/appfilter.xml").readText())
    }

    @Test
    fun tintedVariantsAreGlyphOnly() {
        // The launcher draws its own tile behind tinted glyphs, so they must be monochrome white.
        val offenders = drawables.listFiles()!!.filter { it.name.endsWith("_tinted.xml") }.filter { f ->
            val text = f.readText()
            Regex("""android:(fill|stroke)Color="#([0-9A-Fa-f]{6,8})"""").findAll(text).any { m ->
                !m.groupValues[2].uppercase().endsWith("FFFFFF")
            } || text.contains("<gradient")
        }.map { it.name }
        assertEquals("Tinted variants must be white glyphs only: $offenders", emptyList<String>(), offenders)
    }
}
