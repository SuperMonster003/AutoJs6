package org.autojs.autojs.runtime.api.epub

import com.google.gson.JsonNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** The contract documents as plain values and the table-of-contents tree (plugin roadmap P6.1), without Rhino. */
class EpubScriptValuesTest {

    @Test
    fun blankAndInvalidTextParseToJsonNull() {
        listOf(null, "", "   ", "{not json", "[1,").forEach { json ->
            assertSame(json.toString(), JsonNull.INSTANCE, EpubScriptValues.parse(json))
            assertNull(json.toString(), EpubScriptValues.toPlain(json))
        }
        assertEquals(emptyList<Any?>(), EpubScriptValues.list("{}"))
        assertEquals(emptyMap<String, Any?>(), EpubScriptValues.map("[]"))
    }

    @Test
    fun metadataKeepsKeyOrderAndNumberShapes() {
        val metadata = EpubScriptValues.map(
            """{"title":"Minimal","authors":["Jane Doe"],"language":"en","subjects":[],"layout":"reflowable","readingProgression":"ltr","positions":3,"cover":"OEBPS/cover.png","rating":4.5,"big":2147483648}""",
        )
        assertEquals(listOf("title", "authors", "language", "subjects", "layout", "readingProgression", "positions", "cover", "rating", "big"), metadata.keys.toList())
        assertEquals(listOf("Jane Doe"), metadata["authors"])
        assertEquals(3, metadata["positions"])
        assertTrue(metadata["positions"] is Int)
        assertEquals(4.5, metadata["rating"])
        assertEquals(2147483648.0, metadata["big"])
        assertEquals(emptyList<Any?>(), metadata["subjects"])
    }

    @Test
    fun flatTocBecomesTheChildrenTree() {
        val tree = EpubScriptValues.tocTree(
            """[
                {"title":"Part I","href":"OEBPS/part1.xhtml","depth":0},
                {"title":"Chapter 1","href":"OEBPS/c1.xhtml","depth":1},
                {"title":"Section 1.1","href":"OEBPS/c1.xhtml#s1","depth":2},
                {"title":"Section 1.2","href":"OEBPS/c1.xhtml#s2","depth":2},
                {"title":"Chapter 2","href":"OEBPS/c2.xhtml","depth":1},
                {"title":"Part II","href":"OEBPS/part2.xhtml","depth":0}
            ]""",
        )
        assertEquals(listOf("Part I", "Part II"), tree.map { it["title"] })
        val part1 = tree[0]
        assertEquals("OEBPS/part1.xhtml", part1["href"])
        assertEquals(listOf("title", "href", "children"), part1.keys.toList())
        val chapters = children(part1)
        assertEquals(listOf("Chapter 1", "Chapter 2"), chapters.map { it["title"] })
        assertEquals(listOf("Section 1.1", "Section 1.2"), children(chapters[0]).map { it["title"] })
        assertEquals(emptyList<Any?>(), children(chapters[1]))
        assertEquals(emptyList<Any?>(), children(tree[1]))
    }

    @Test
    fun depthJumpsAndOddEntriesStillFormATree() {
        val tree = EpubScriptValues.tocTree(
            """[
                {"title":"Deep start","href":"a.xhtml","depth":2},
                {"title":"Root","href":"b.xhtml","depth":0},
                {"title":"Jump","href":"c.xhtml","depth":3},
                {"title":"Back","href":"d.xhtml","depth":1},
                {"title":"Negative","href":"e.xhtml","depth":-4},
                "not an entry",
                {"href":"f.xhtml"}
            ]""",
        )
        assertEquals(listOf("Deep start", "Root", "Negative", null), tree.map { it["title"] })
        assertEquals("f.xhtml", tree[3]["href"])
        val root = tree[1]
        assertEquals(listOf("Jump", "Back"), children(root).map { it["title"] })
        assertEquals(emptyList<Any?>(), children(children(root)[0]))
        assertEquals(emptyList<Map<String, Any?>>(), EpubScriptValues.tocTree("[]"))
        assertEquals(emptyList<Map<String, Any?>>(), EpubScriptValues.tocTree(null))
    }

    @Test
    fun searchResultsAndBookmarksStayNestedPlainValues() {
        val results = EpubScriptValues.list(
            """[{"href":"OEBPS/c1.xhtml","title":"Chapter 1","locator":{"href":"OEBPS/c1.xhtml","type":"application/xhtml+xml","locations":{"progression":0.25,"totalProgression":0.1,"position":1}},"text":{"before":"the old ","highlight":"lantern","after":" on the pier"}}]""",
        )
        val hit = results.single() as Map<*, *>
        val locator = hit["locator"] as Map<*, *>
        val locations = locator["locations"] as Map<*, *>
        assertEquals(0.25, locations["progression"])
        assertEquals(1, locations["position"])
        assertEquals("lantern", (hit["text"] as Map<*, *>)["highlight"])
        val bookmarks = EpubScriptValues.list("""[{"locator":{"href":"OEBPS/c1.xhtml"},"createdAt":1789920631874,"title":"Chapter 1"}]""")
        assertEquals(1789920631874.0, (bookmarks.single() as Map<*, *>)["createdAt"])
    }

    @Suppress("UNCHECKED_CAST")
    private fun children(node: Map<String, Any?>): List<Map<String, Any?>> = node[EpubScriptValues.CHILDREN] as List<Map<String, Any?>>
}
