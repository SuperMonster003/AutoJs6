package org.autojs.autojs.runtime.api.modules.epub

import org.autojs.autojs.rhino.TopLevelScope
import org.autojs.autojs.runtime.api.epub.EpubScriptValues
import org.autojs.autojs.util.RhinoUtils
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.ScriptableObject

/**
 * Shape snapshots of the script values the `epub` API delivers (plugin roadmap P6.1, appendix
 * A.2 and A.3), on bare Rhino: the contract documents converted through the same path the
 * script objects use (`EpubScriptValues` then `RhinoUtils.toJsValue`) and read back with
 * `JSON.stringify` in JavaScript.
 */
class EpubObjectsTest {

    private lateinit var context: Context
    private lateinit var scope: TopLevelScope

    @Before
    fun enterContext() {
        context = Context.enter().apply {
            languageVersion = Context.VERSION_ES6
            isInterpretedMode = true
        }
        scope = TopLevelScope().apply { initStandardObjects(context, false) }
    }

    @After
    fun exitContext() {
        Context.exit()
    }

    @Test
    fun metadataIsAPlainObjectWithTheAppendixShape() {
        val json = """{"title":"Minimal EPUB 3","authors":["Jane Doe"],"language":"en","identifier":"urn:uuid:1","publisher":"P","published":"2024-01-01T00:00:00Z","modified":"2024-01-02T00:00:00Z","description":"d","subjects":["Fiction"],"layout":"reflowable","readingProgression":"ltr","cover":"OEBPS/cover.png","positions":3}"""
        val value = RhinoUtils.toJsValue(scope, EpubScriptValues.map(json))
        assertTrue(value is NativeObject)
        assertEquals(json, stringify(value))
        assertEquals(
            "Jane Doe|3|number|object",
            evaluateWith(value, "v.authors.join(', ') + '|' + v.positions + '|' + typeof v.positions + '|' + typeof v.subjects"),
        )
    }

    @Test
    fun tocIsATreeOfTitleHrefChildren() {
        val flat = """[{"title":"Chapter 1","href":"OEBPS/c1.xhtml","depth":0},{"title":"Section 1.1","href":"OEBPS/c1.xhtml#s1","depth":1},{"title":"Chapter 2","href":"OEBPS/c2.xhtml","depth":0}]"""
        val value = RhinoUtils.toJsValue(scope, EpubScriptValues.tocTree(flat))
        assertTrue(value is NativeArray)
        assertEquals(
            """[{"title":"Chapter 1","href":"OEBPS/c1.xhtml","children":[{"title":"Section 1.1","href":"OEBPS/c1.xhtml#s1","children":[]}]},{"title":"Chapter 2","href":"OEBPS/c2.xhtml","children":[]}]""",
            stringify(value),
        )
        assertEquals("2|true|Section 1.1", evaluateWith(value, "v.length + '|' + Array.isArray(v[0].children) + '|' + v[0].children[0].title"))
    }

    @Test
    fun readingOrderSearchResultsAndBookmarksKeepTheContractDocuments() {
        val readingOrder = """[{"href":"OEBPS/c1.xhtml","type":"application/xhtml+xml","title":"Chapter 1"},{"href":"OEBPS/c2.xhtml","type":"application/xhtml+xml"}]"""
        assertEquals(readingOrder, stringify(RhinoUtils.toJsValue(scope, EpubScriptValues.list(readingOrder))))

        val results = """[{"href":"OEBPS/c1.xhtml","title":"Chapter 1","locator":{"href":"OEBPS/c1.xhtml","type":"application/xhtml+xml","title":"Chapter 1","locations":{"progression":0.25,"totalProgression":0.1,"position":1,"cssSelector":"p:nth-child(2)"},"text":{"before":"the old ","highlight":"lantern","after":" on the pier"}},"text":{"before":"the old ","highlight":"lantern","after":" on the pier"}}]"""
        val hits = RhinoUtils.toJsValue(scope, EpubScriptValues.list(results))
        assertEquals(results, stringify(hits))
        assertEquals("lantern|0.25|1", evaluateWith(hits, "v[0].text.highlight + '|' + v[0].locator.locations.progression + '|' + v[0].locator.locations.position"))

        val bookmarks = """[{"locator":{"href":"OEBPS/c1.xhtml","type":"application/xhtml+xml","locations":{"progression":0.5}},"createdAt":1789920631874,"title":"Chapter 1","text":"First words"}]"""
        assertEquals(bookmarks, stringify(RhinoUtils.toJsValue(scope, EpubScriptValues.list(bookmarks))))

        // Contract version 2: the annotation document of `book.annotations()` and the `highlight` event.
        val annotations = """[{"id":3,"style":"highlight","color":"#FFEB3B","note":"first\nnote","quote":"The lighthouse keeper","title":"Chapter 1","locator":{"href":"OEBPS/c1.xhtml","type":"application/xhtml+xml","locations":{"progression":0.1},"text":{"highlight":"The lighthouse keeper"}},"createdAt":1789920631874,"updatedAt":1789920631874}]"""
        val highlights = RhinoUtils.toJsValue(scope, EpubScriptValues.list(annotations))
        assertEquals(annotations, stringify(highlights))
        assertEquals("3|#FFEB3B|The lighthouse keeper|0.1", evaluateWith(highlights, "v[0].id + '|' + v[0].color + '|' + v[0].locator.text.highlight + '|' + v[0].locator.locations.progression"))
    }

    @Test
    fun epubErrorInstancesSerializeToCodeOnlyAndStringifyWithTheCode() {
        ScriptableObject.putProperty(scope, "EpubError", EpubJsErrors.constructor(context, scope))
        assertEquals(
            """{"code":"PLUGIN_UNAVAILABLE"}|EpubError [PLUGIN_UNAVAILABLE]: gone|EpubError""",
            context.evaluateString(scope, "var e = new EpubError('gone', 'PLUGIN_UNAVAILABLE'); JSON.stringify(e) + '|' + e + '|' + e.name", "test.js", 1, null),
        )
    }

    private fun stringify(value: Any?): String = evaluateWith(value, "JSON.stringify(v)")

    private fun evaluateWith(value: Any?, expression: String): String {
        ScriptableObject.putProperty(scope, "v", value)
        return Context.toString(context.evaluateString(scope, expression, "test.js", 1, null))
    }
}
