package org.autojs.autojs.core.plugin.tool

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.autojs.plugin.tool.api.PluginToolErrorCodes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class PluginToolInvocationPlannerTest {

    private val tool: PluginToolDefinition = run {
        val catalog = """{"shortName": "imagequant", "tools": [{
            "name": "quantize",
            "description": "Quantizes an image",
            "inputSchema": {"type": "object", "additionalProperties": false, "required": ["image"], "properties": {
                "image": {"type": "string", "x-autojs6-type": "image"},
                "palette": {"type": "string", "x-autojs6-type": "file"},
                "colors": {"type": "integer", "minimum": 2, "maximum": 256, "default": 64},
                "dither": {"type": "boolean"}
            }},
            "risk": "write",
            "timeoutMs": 20000,
            "outputs": [{"name": "image", "mimeType": "image/png"}]
        }]}"""
        (PluginToolCatalogParser.parse(catalog, official = true) as PluginToolCatalogParse.Accepted).catalog.tools.single()
    }

    @Test
    fun specialArgumentsBecomeInputsAndDefaultsAreFilled() {
        val plan = ready(args("""{"image": "screen", "palette": "palettes/web.gpl", "dither": true}"""), outputs("""{"image": "out/a.png"}"""))
        assertEquals(listOf(PluginToolInput("image", "image", "screen"), PluginToolInput("palette", "file", "palettes/web.gpl")), plan.inputs)
        assertTrue(plan.inputs.first().isScreen)
        assertEquals(json("""{"dither": true, "colors": 64}"""), plan.arguments)
        assertEquals(mapOf("image" to "out/a.png"), plan.outputs)
        assertEquals(20_000L, plan.timeoutMs)
    }

    @Test
    fun directoryArgumentsCarryTheFileNamesTheToolAsksFor() {
        val catalog = """{"shortName": "yolo", "tools": [{
            "name": "detect",
            "description": "Detects objects",
            "inputSchema": {"type": "object", "additionalProperties": false, "required": ["image", "model"], "properties": {
                "image": {"type": "string", "x-autojs6-type": "image"},
                "model": {"type": "string", "x-autojs6-type": "directory", "x-autojs6-files": ["model.json", "model.ncnn.bin"]},
                "extra": {"type": "string", "x-autojs6-type": "directory"}
            }},
            "risk": "readonly"
        }]}"""
        val yolo = (PluginToolCatalogParser.parse(catalog, official = true) as PluginToolCatalogParse.Accepted).catalog.tools.single()
        assertEquals(mapOf("model" to listOf("model.json", "model.ncnn.bin")), yolo.directoryFiles)
        val plan = (PluginToolInvocationPlanner.plan(yolo, args("""{"image": "screen", "model": " models/yolo11n ", "extra": "more"}"""), null, null, 30_000L) as PluginToolPlanResult.Ready).plan
        assertEquals(
            listOf(
                PluginToolInput("image", "image", "screen"),
                PluginToolInput("model", "directory", "models/yolo11n", listOf("model.json", "model.ncnn.bin")),
                PluginToolInput("extra", "directory", "more"),
            ),
            plan.inputs,
        )
        val refused = PluginToolInvocationPlanner.plan(yolo, args("""{"image": "screen", "model": "screen"}"""), null, null, 30_000L)
        assertTrue(refused.toString(), refused.toString().contains("model is a directory parameter and takes a path, not \"screen\""))
    }

    @Test
    fun theTimeLimitIsTheShortestOfToolCallerAndBridge() {
        assertEquals(5_000L, ready(args("""{"image": "a.png"}"""), requestedTimeoutMs = 5_000L).timeoutMs)
        assertEquals(1_000L, ready(args("""{"image": "a.png"}"""), requestedTimeoutMs = 5_000L, ceilingMs = 1_000L).timeoutMs)
        assertEquals(20_000L, ready(args("""{"image": "a.png"}"""), requestedTimeoutMs = 0L).timeoutMs)
        assertInvalid("no time is left", args("""{"image": "a.png"}"""), ceilingMs = 0L)
    }

    @Test
    fun invalidRequestsSayWhy() {
        assertInvalid("arguments must be an object", JsonParser.parseString("[1]"))
        assertInvalid("$.image: is required", args("{}"))
        assertInvalid("$.colors: above the maximum 256", args("""{"image": "a.png", "colors": 999}"""))
        assertInvalid("$.extra: is not a parameter", args("""{"image": "a.png", "extra": 1}"""))
        assertInvalid("image must name \"screen\" or a path", args("""{"image": "  "}"""))
        assertInvalid("palette is a file parameter", args("""{"image": "a.png", "palette": "screen"}"""))
        assertInvalid("declares no output named thumbnail (declared: image)", args("""{"image": "a.png"}"""), outputs("""{"thumbnail": "t.png"}"""))
        assertInvalid("output image needs a path", args("""{"image": "a.png"}"""), outputs("""{"image": ""}"""))
        assertInvalid("more than 65536", args("""{"image": "${"x".repeat(70_000)}"}"""))
    }

    @Test
    fun anAbsentOptionalSpecialArgumentIsNoInput() {
        assertEquals(listOf("image"), ready(args("""{"image": "a.png", "palette": null}""")).inputs.map { it.parameter })
    }

    @Test
    fun providerErrorCodesMapOntoBridgeCategories() {
        assertEquals("invalid-request", PluginToolInvocationPlanner.bridgeCategoryOf(PluginToolErrorCodes.INVALID_ARGUMENTS))
        assertEquals("invalid-request", PluginToolInvocationPlanner.bridgeCategoryOf(PluginToolErrorCodes.UNKNOWN_TOOL))
        assertEquals("invalid-request", PluginToolInvocationPlanner.bridgeCategoryOf(PluginToolErrorCodes.INPUT_UNREADABLE))
        assertEquals("rate-limited", PluginToolInvocationPlanner.bridgeCategoryOf(PluginToolErrorCodes.BUSY))
        assertEquals("resource-limit", PluginToolInvocationPlanner.bridgeCategoryOf(PluginToolErrorCodes.LIMIT_EXCEEDED))
        assertEquals("timeout", PluginToolInvocationPlanner.bridgeCategoryOf(PluginToolErrorCodes.TIMEOUT))
        assertEquals("unavailable", PluginToolInvocationPlanner.bridgeCategoryOf(PluginToolErrorCodes.NOT_READY))
        listOf(PluginToolErrorCodes.FAILED, PluginToolErrorCodes.CANCELLED, PluginToolErrorCodes.UNSUPPORTED_CONTRACT_VERSION, "SOMETHING_NEW")
            .forEach { assertEquals(it, "provider-failed", PluginToolInvocationPlanner.bridgeCategoryOf(it)) }
    }

    private fun ready(arguments: com.google.gson.JsonElement, outputs: JsonObject? = null, requestedTimeoutMs: Long? = null, ceilingMs: Long = 60_000L): PluginToolInvocationPlan =
        when (val result = PluginToolInvocationPlanner.plan(tool, arguments, outputs, requestedTimeoutMs, ceilingMs)) {
            is PluginToolPlanResult.Ready -> result.plan
            is PluginToolPlanResult.Invalid -> fail("invalid: ${result.message}").let { error("unreachable") }
        }

    private fun assertInvalid(needle: String, arguments: com.google.gson.JsonElement, outputs: JsonObject? = null, ceilingMs: Long = 60_000L) {
        when (val result = PluginToolInvocationPlanner.plan(tool, arguments, outputs, null, ceilingMs)) {
            is PluginToolPlanResult.Invalid -> assertTrue("\"$needle\" in \"${result.message}\"", needle in result.message)
            is PluginToolPlanResult.Ready -> fail("ready, expected \"$needle\"")
        }
    }

    private fun args(text: String) = JsonParser.parseString(text)

    private fun outputs(text: String) = json(text)

    private fun json(text: String): JsonObject = JsonParser.parseString(text).asJsonObject
}
