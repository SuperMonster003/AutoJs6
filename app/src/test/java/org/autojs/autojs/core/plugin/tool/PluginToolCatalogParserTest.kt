package org.autojs.autojs.core.plugin.tool

import org.autojs.plugin.tool.api.PluginToolLimits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PluginToolCatalogParserTest {

    @Test
    fun aValidCatalogKeepsEveryToolWithDefaults() {
        val catalog = accepted(catalog(tool("convert"), tool("list_configs", extra = """"title": "List", "timeoutMs": 5000, "maxConcurrency": 2""")))
        assertEquals("opencc", catalog.shortName)
        assertNull(catalog.sharedFamily)
        assertEquals(listOf("opencc_convert", "opencc_list_configs"), catalog.tools.map { it.fullName })
        val convert = catalog.tools[0]
        assertEquals(PluginToolLimits.DEFAULT_TIMEOUT_MS, convert.timeoutMs)
        assertEquals(1, convert.maxConcurrency)
        assertNull(convert.title)
        assertEquals("readonly", convert.risk)
        assertEquals(5000L, catalog.tools[1].timeoutMs)
        assertEquals(2, catalog.tools[1].maxConcurrency)
        assertEquals("List", catalog.tools[1].title)
        assertTrue(catalog.diagnostics.isEmpty())
    }

    @Test
    fun documentLevelProblemsRefuseTheWholeCatalog() {
        assertRefused("not JSON", "{")
        assertRefused("not a JSON object", "[]")
        assertRefused("shortName is missing", """{"tools": [${tool("a")}]}""")
        assertRefused("does not match", catalog(tool("a"), shortName = "Open_CC"))
        assertRefused("is reserved", catalog(tool("a"), shortName = "ocr"))
        assertRefused("tools is empty", """{"shortName": "opencc", "tools": []}""")
        assertRefused("tools is not an array", """{"shortName": "opencc", "tools": {}}""")
        assertRefused("more than 64", catalog(*Array(65) { tool("t$it") }))
        assertRefused("tool names repeat: convert", catalog(tool("convert"), tool("convert")))
        assertRefused("bytes, more than", catalog(tool("a", extra = """"description2": "${"x".repeat(PluginToolLimits.MAX_CATALOG_BYTES)}"""")))
    }

    @Test
    fun sharedFamiliesNeedTheAllowListAndAnOfficialPlugin() {
        val ocr = catalog(tool("recognize"), extraRoot = """"sharedFamily": "ocr",""")
        assertEquals("ocr", accepted(ocr, official = true).sharedFamily)
        assertRefused("only OFFICIAL plugins", ocr, official = false)
        assertRefused("is not a shared family", catalog(tool("a"), extraRoot = """"sharedFamily": "yolo","""), official = true)
        assertNull(accepted(catalog(tool("a"), extraRoot = """"sharedFamily": null,""")).sharedFamily)
    }

    @Test
    fun aBrokenToolIsDroppedAloneWithItsReason() {
        val catalog = accepted(
            catalog(
                tool("good"),
                tool("Bad"),
                tool("no_risk", risk = null),
                tool("bad_risk", risk = "dangerous"),
                tool("open_schema", schema = """{"type": "object", "properties": {}}"""),
                tool("long_timeout", extra = """"timeoutMs": 300001"""),
                tool("fraction", extra = """"timeoutMs": 1.5"""),
                tool("crowd", extra = """"maxConcurrency": 5"""),
                tool("x".repeat(PluginToolLimits.MAX_FULL_NAME_CHARS)),
                """{"name": "no_description", "inputSchema": $CLOSED, "risk": "readonly"}""",
                "42",
            ),
        )
        assertEquals(listOf("opencc_good"), catalog.tools.map { it.fullName })
        assertEquals(10, catalog.diagnostics.size)
        listOf("(Bad)", "(no_risk)", "(bad_risk)", "(open_schema) dropped: inputSchema", "(long_timeout)", "(fraction)", "(crowd)", "more than 50 characters", "(no_description)", "not an object")
            .forEach { needle -> assertTrue("$needle in ${catalog.diagnostics}", catalog.diagnostics.any { needle in it }) }
    }

    @Test
    fun aCatalogWithoutUsableToolsIsRefused() {
        assertRefused("no usable tool", catalog(tool("bad_risk", risk = "dangerous")))
    }

    @Test
    fun outputsAreValidated() {
        val png = """"outputs": [{"name": "image", "mimeType": "image/png", "description": "the quantized image"}]"""
        val tool = accepted(catalog(tool("quantize", risk = "write", extra = png))).tools.single()
        assertEquals(listOf(PluginToolOutput("image", "image/png", "the quantized image")), tool.outputs)
        val broken = accepted(
            catalog(
                tool("ok"),
                tool("repeat", extra = """"outputs": [{"name": "a", "mimeType": "image/png"}, {"name": "a", "mimeType": "image/png"}]"""),
                tool("no_mime", extra = """"outputs": [{"name": "a"}]"""),
                tool("too_many", extra = """"outputs": [${(0..8).joinToString { """{"name": "o$it", "mimeType": "text/plain"}""" }}]"""),
            ),
        )
        assertEquals(listOf("opencc_ok"), broken.tools.map { it.fullName })
        assertTrue(broken.diagnostics.any { "output names repeat" in it })
        assertTrue(broken.diagnostics.any { "no valid mimeType" in it })
        assertTrue(broken.diagnostics.any { "more than 8 entries" in it })
    }

    @Test
    fun unknownFieldsAreIgnoredAndTheSchemaHookDecides() {
        val catalog = accepted(catalog(tool("a", extra = """"futureField": {"x": 1}"""), extraRoot = """"futureRoot": true,"""))
        assertEquals(1, catalog.tools.size)
        val selective = PluginToolCatalogParser.parse(catalog(tool("a"), tool("b", schema = """{"type": "object", "additionalProperties": false, "title": "marked"}""")), official = false) { schema ->
            if (schema.has("title")) "rejected by the hook" else null
        }
        assertEquals(listOf("opencc_a"), (selective as PluginToolCatalogParse.Accepted).catalog.tools.map { it.fullName })
        assertTrue(selective.catalog.diagnostics.single().contains("inputSchema: rejected by the hook"))
        val rejecting = PluginToolCatalogParser.parse(catalog(tool("a")), official = false) { "rejected by the hook" }
        assertTrue((rejecting as PluginToolCatalogParse.Refused).reason.contains("rejected by the hook"))
    }

    private fun accepted(json: String, official: Boolean = false): PluginToolCatalog =
        when (val parse = PluginToolCatalogParser.parse(json, official)) {
            is PluginToolCatalogParse.Accepted -> parse.catalog
            is PluginToolCatalogParse.Refused -> fail("refused: ${parse.reason}").let { error("unreachable") }
        }

    private fun assertRefused(needle: String, json: String, official: Boolean = false) {
        when (val parse = PluginToolCatalogParser.parse(json, official)) {
            is PluginToolCatalogParse.Refused -> assertTrue("\"$needle\" in \"${parse.reason}\"", needle in parse.reason)
            is PluginToolCatalogParse.Accepted -> fail("accepted, expected a refusal containing \"$needle\"")
        }
    }

    private fun catalog(vararg tools: String, shortName: String = "opencc", extraRoot: String = ""): String =
        """{$extraRoot "shortName": "$shortName", "tools": [${tools.joinToString()}]}"""

    private fun tool(name: String, risk: String? = "readonly", schema: String = CLOSED, extra: String = ""): String = buildString {
        append("""{"name": "$name", "description": "Tool $name", "inputSchema": $schema""")
        if (risk != null) append(""", "risk": "$risk"""")
        if (extra.isNotEmpty()) append(", ").append(extra)
        append("}")
    }

    private companion object {
        const val CLOSED = """{"type": "object", "properties": {"text": {"type": "string"}}, "additionalProperties": false}"""
    }
}
