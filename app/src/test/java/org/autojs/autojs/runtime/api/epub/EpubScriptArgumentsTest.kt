package org.autojs.autojs.runtime.api.epub

import com.google.gson.JsonElement
import com.google.gson.JsonParser
import org.autojs.autojs.core.plugin.epub.EpubPluginException
import org.autojs.plugin.epub.api.EpubContract
import org.autojs.plugin.epub.api.EpubErrorCodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** The script-to-request argument mapping of the `epub` API (plugin roadmap P6.1), without Rhino. */
class EpubScriptArgumentsTest {

    @Test
    fun openAcceptsAPathOrAnOptionsObjectAndRefusesUris() {
        val plain = EpubScriptArguments.open(json("\"books/moby-dick.epub\""))
        assertEquals("books/moby-dick.epub", plain.path)
        assertNull(plain.displayName)
        val options = EpubScriptArguments.open(json("""{"path":"/sdcard/a.epub","displayName":"Moby Dick"}"""))
        assertEquals("/sdcard/a.epub", options.path)
        assertEquals("Moby Dick", options.displayName)
        assertNull(EpubScriptArguments.open(json("""{"path":"a.epub","displayName":"  "}""")).displayName)
        assertTrue(invalidMessage { EpubScriptArguments.open(null) }.contains("expects a file path"))
        assertTrue(invalidMessage { EpubScriptArguments.open(json("\" \"")) }.contains("must not be blank"))
        assertTrue(invalidMessage { EpubScriptArguments.open(json("42")) }.contains("must be a string"))
        assertTrue(invalidMessage { EpubScriptArguments.open(json("""{"path":"a.epub","name":"x"}""")) }.contains("unknown option 'options.name'"))
        assertTrue(invalidMessage { EpubScriptArguments.open(json("""{"displayName":"x"}""")) }.contains("'options.path' is required"))
        listOf("content://media/external/file/1", "file:///sdcard/a.epub", "https://example.com/a.epub", "android.resource://pkg/raw/a").forEach { uri ->
            assertTrue(uri, invalidMessage { EpubScriptArguments.open(json("\"$uri\"")) }.contains("URIs such as content:// are not accepted"))
        }
        assertEquals("notes 12:30.epub", EpubScriptArguments.open(json("\"notes 12:30.epub\"")).path)
        assertEquals("./books/x.epub", EpubScriptArguments.open(json("\"./books/x.epub\"")).path)
    }

    @Test
    fun textTargetsAreHrefIndexObjectOrWholeBook() {
        val href = EpubScriptArguments.text(json("\"OEBPS/c1.xhtml#s1\""), null)
        assertEquals("OEBPS/c1.xhtml#s1", href.href)
        assertNull(href.index)
        assertFalse(href.wholeBook)
        assertEquals(0, href.offset)
        assertEquals(EpubScriptArguments.DEFAULT_MAX_CHARS, href.maxChars)
        assertNull(href.format)

        val index = EpubScriptArguments.text(json("2"), json("""{"offset":10,"maxChars":500,"format":"markdown"}"""))
        assertEquals(2, index.index)
        assertNull(index.href)
        assertEquals(10, index.offset)
        assertEquals(500, index.maxChars)
        assertEquals(EpubContract.FORMAT_MARKDOWN, index.format)

        val entry = EpubScriptArguments.text(json("""{"title":"Chapter 1","href":"OEBPS/c1.xhtml","children":[]}"""), null)
        assertEquals("OEBPS/c1.xhtml", entry.href)

        val whole = EpubScriptArguments.text(null, json("""{"maxChars":100}"""))
        assertTrue(whole.wholeBook)
        assertEquals(100, whole.maxChars)
        assertTrue(EpubScriptArguments.textAll(null).wholeBook)
        assertEquals(4 * 1024 * 1024, EpubScriptArguments.DEFAULT_MAX_CHARS)
    }

    @Test
    fun textRefusalsNameTheArgument() {
        assertTrue(invalidMessage { EpubScriptArguments.text(json("-1"), null) }.contains("'target' index must be a non-negative integer"))
        assertTrue(invalidMessage { EpubScriptArguments.text(json("1.5"), null) }.contains("'target' must be an integer"))
        assertTrue(invalidMessage { EpubScriptArguments.text(json("true"), null) }.contains("'target' must be an href string"))
        assertTrue(invalidMessage { EpubScriptArguments.text(json("\"\""), null) }.contains("'target' must not be blank"))
        assertTrue(invalidMessage { EpubScriptArguments.text(json("""{"title":"x"}"""), null) }.contains("'target.href' is required"))
        assertTrue(invalidMessage { EpubScriptArguments.text(json("[1]"), null) }.contains("got an array"))
        assertTrue(invalidMessage { EpubScriptArguments.text(null, json("""{"limit":10}""")) }.contains("unknown option 'options.limit' (use 'options.maxChars')"))
        assertTrue(invalidMessage { EpubScriptArguments.text(null, json("""{"encoding":"utf8"}""")) }.contains("unknown option 'options.encoding'"))
        assertTrue(invalidMessage { EpubScriptArguments.text(json("0"), json("""{"offset":-1}""")) }.contains("'options.offset' must be a non-negative integer"))
        assertTrue(invalidMessage { EpubScriptArguments.text(null, json("""{"offset":5}""")) }.contains("applies to a single resource target"))
        assertTrue(invalidMessage { EpubScriptArguments.text(json("0"), json("""{"maxChars":0}""")) }.contains("'options.maxChars' must be a positive integer"))
        assertTrue(invalidMessage { EpubScriptArguments.text(json("0"), json("""{"maxChars":"many"}""")) }.contains("'options.maxChars' must be an integer"))
        assertTrue(invalidMessage { EpubScriptArguments.text(json("0"), json("""{"format":"html"}""")) }.contains("'options.format' must be one of text, markdown"))
        assertTrue(invalidMessage { EpubScriptArguments.text(json("0"), json("\"markdown\"")) }.contains("'options' must be an object"))
        val long = "a".repeat(EpubContract.MAX_HREF_LENGTH + 1)
        assertTrue(invalidMessage { EpubScriptArguments.text(json("\"$long\""), null) }.contains("exceeds ${EpubContract.MAX_HREF_LENGTH} characters"))
    }

    @Test
    fun searchNormalizesTheQueryAndPagesWithinTheCeiling() {
        val plain = EpubScriptArguments.search(json("\"  old   lantern \""), null)
        assertEquals("old lantern", plain.query)
        assertEquals(0, plain.offset)
        assertEquals(0, plain.limit)
        val paged = EpubScriptArguments.search(json("\"lantern\""), json("""{"offset":100,"limit":400}"""))
        assertEquals(100, paged.offset)
        assertEquals(400, paged.limit)
        assertEquals(450, EpubScriptArguments.search(json("\"x\""), json("""{"offset":450}""")).offset)
        assertTrue(invalidMessage { EpubScriptArguments.search(null, null) }.contains("'query' is required"))
        assertTrue(invalidMessage { EpubScriptArguments.search(json("\"   \""), null) }.contains("'query' must not be blank"))
        assertTrue(invalidMessage { EpubScriptArguments.search(json("7"), null) }.contains("'query' must be a string"))
        assertTrue(invalidMessage { EpubScriptArguments.search(json("\"${"q".repeat(257)}\""), null) }.contains("exceeds 256 characters"))
        assertTrue(invalidMessage { EpubScriptArguments.search(json("\"x\""), json("""{"limit":0}""")) }.contains("'options.limit' must be between 1 and 500, got 0"))
        assertTrue(invalidMessage { EpubScriptArguments.search(json("\"x\""), json("""{"limit":501}""")) }.contains("'options.limit' must be between 1 and 500"))
        assertTrue(invalidMessage { EpubScriptArguments.search(json("\"x\""), json("""{"offset":-1}""")) }.contains("'options.offset' must be a non-negative integer"))
        assertTrue(invalidMessage { EpubScriptArguments.search(json("\"x\""), json("""{"offset":451}""")) }.contains("must not exceed 500"))
        assertTrue(invalidMessage { EpubScriptArguments.search(json("\"x\""), json("""{"offset":100,"limit":401}""")) }.contains("got 501"))
        assertTrue(invalidMessage { EpubScriptArguments.search(json("\"x\""), json("""{"folder":"INBOX"}""")) }.contains("unknown option 'options.folder'"))
    }

    @Test
    fun exportOptionsCarryOverwriteOnly() {
        assertFalse(EpubScriptArguments.export(null).overwrite)
        assertFalse(EpubScriptArguments.export(json("{}")).overwrite)
        assertTrue(EpubScriptArguments.export(json("""{"overwrite":true}""")).overwrite)
        assertTrue(invalidMessage { EpubScriptArguments.export(json("""{"overwrite":"yes"}""")) }.contains("'options.overwrite' must be a boolean"))
        assertTrue(invalidMessage { EpubScriptArguments.export(json("""{"fileName":"x"}""")) }.contains("unknown option 'options.fileName'"))
        assertTrue(invalidMessage { EpubScriptArguments.export(json("true")) }.contains("'options' must be an object"))
    }

    @Test
    fun hrefsAreNonBlankAndBounded() {
        assertEquals("OEBPS/images/dot.png", EpubScriptArguments.href(json("\"OEBPS/images/dot.png\""), "href"))
        assertTrue(invalidMessage { EpubScriptArguments.href(null, "href") }.contains("'href' is required"))
        assertTrue(invalidMessage { EpubScriptArguments.href(json("\"\""), "href") }.contains("'href' must not be blank"))
        assertTrue(invalidMessage { EpubScriptArguments.href(json("{}"), "href") }.contains("'href' must be a string, got an object"))
    }

    private fun json(text: String): JsonElement = JsonParser.parseString(text)

    private fun invalidMessage(block: () -> Unit): String {
        try {
            block()
        } catch (e: EpubPluginException) {
            assertEquals(EpubErrorCodes.INVALID_ARGUMENT, e.code)
            return e.error.message
        }
        fail("expected INVALID_ARGUMENT")
        throw AssertionError()
    }
}
