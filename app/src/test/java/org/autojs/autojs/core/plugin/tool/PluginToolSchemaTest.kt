package org.autojs.autojs.core.plugin.tool

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginToolSchemaTest {

    @Test
    fun aRichSchemaWithinTheSubsetIsAccepted() {
        assertNull(PluginToolSchema.checkInput(RICH))
        assertEquals(linkedMapOf("image" to "image", "archive" to "file"), PluginToolSchema.specialParameters(RICH))
        assertNull(PluginToolSchema.checkOutput(json("""{"type": "object", "properties": {"text": {"type": "string"}}, "additionalProperties": false}""")))
    }

    @Test
    fun schemasOutsideTheSubsetAreRejectedWithTheirPath() {
        mapOf(
            """{"type": "array", "items": {"type": "string"}}""" to "the root is not of type object",
            """{"type": "object", "properties": {}}""" to "$: objects must be closed",
            """{"type": "object", "additionalProperties": false, "properties": {"o": {"type": "object", "properties": {}}}}""" to "$.o: objects must be closed",
            obj(""""s": {"type": "string", "pattern": "^a$"}""") to "$.s: unsupported keywords pattern for type string",
            obj(""""s": {"type": "string", "format": "uri"}""") to "unsupported keywords format",
            obj(""""n": {"type": "null"}""") to "$.n: type must be one of",
            obj(""""o": {"type": "object", "additionalProperties": false, "enum": [1]}""") to "$.o: enum is only allowed on scalars",
            obj(""""s": {"type": "string", "enum": []}""") to "$.s: enum is empty",
            obj(""""s": {"type": "string", "enum": ["a", "a"]}""") to "$.s: enum repeats a value",
            obj(""""i": {"type": "integer", "enum": [1, 2.5]}""") to "$.i: enum[1] does not match",
            obj(""""i": {"type": "integer", "minimum": 5, "default": 1}""") to "$.i: default does not match its schema",
            obj(""""s": {"type": "string", "minLength": 3, "maxLength": 2}""") to "$.s: minLength is above maxLength",
            obj(""""s": {"type": "string", "maxLength": -1}""") to "maxLength is not a non-negative whole number",
            obj(""""n": {"type": "number", "exclusiveMinimum": 3, "maximum": 2}""") to "$.n: the lower bound is above the upper bound",
            obj(""""a": {"type": "array"}""") to "$.a: items must be one schema",
            obj(""""a": {"type": "array", "items": {"type": "array", "items": {"type": "string"}}}""") to "$.a: array items must be scalars or objects",
            obj(""""c": {"oneOf": [{"type": "string"}]}""") to "$.c: oneOf needs at least 2 schemas",
            obj(""""c": {"oneOf": [{"type": "string"}, {"type": "integer"}], "type": "string"}""") to "unsupported keywords type beside oneOf",
            """{"type": "object", "additionalProperties": false, "properties": {"a": {"type": "string"}}, "required": ["b"]}""" to "required names the unknown property b",
            """{"type": "object", "additionalProperties": false, "properties": {"a": {"type": "string"}}, "required": ["a", "a"]}""" to "required repeats a name",
            obj(""""s": {"type": "string", "title": "${"t".repeat(129)}"}""") to "$.s: title is not a string",
        ).forEach { (schema, needle) ->
            val reason = PluginToolSchema.checkInput(json(schema))
            assertTrue("\"$needle\" in \"$reason\" for $schema", reason?.contains(needle) == true)
        }
    }

    @Test
    fun specialTypesOnlyOnTopLevelStringsOfInputSchemas() {
        mapOf(
            obj(""""o": {"type": "object", "additionalProperties": false, "properties": {"img": {"type": "string", "x-autojs6-type": "image"}}}""") to "$.o.img: x-autojs6-type is only allowed on top-level properties",
            obj(""""a": {"type": "array", "items": {"type": "string", "x-autojs6-type": "file"}}""") to "$.a[]: x-autojs6-type is only allowed on top-level properties",
            obj(""""c": {"oneOf": [{"type": "string", "x-autojs6-type": "image"}, {"type": "integer"}]}""") to "$.c.oneOf[0]: x-autojs6-type is only allowed on top-level properties",
            obj(""""i": {"type": "integer", "x-autojs6-type": "image"}""") to "$.i: an image, file or directory parameter must be of type string",
            obj(""""i": {"type": "string", "x-autojs6-type": "video"}""") to "$.i: x-autojs6-type must be image or file or directory",
            obj((0..8).joinToString { """"f$it": {"type": "string", "x-autojs6-type": "file"}""" }) to "more than 8 image, file or directory parameters",
            obj(""""d": {"type": "string", "x-autojs6-type": "file", "x-autojs6-files": ["a"]}""") to "$.d: x-autojs6-files is only allowed beside x-autojs6-type directory",
            obj(""""d": {"type": "string", "x-autojs6-files": ["a"]}""") to "$.d: x-autojs6-files is only allowed beside x-autojs6-type directory",
            obj(""""d": {"type": "string", "x-autojs6-type": "directory", "x-autojs6-files": []}""") to "$.d: x-autojs6-files needs 1 to 16 names",
            obj(""""d": {"type": "string", "x-autojs6-type": "directory", "x-autojs6-files": ["a", "a"]}""") to "$.d: x-autojs6-files repeats a name",
            obj(""""d": {"type": "string", "x-autojs6-type": "directory", "x-autojs6-files": ["sub/a"]}""") to "$.d: x-autojs6-files entry \"sub/a\" is not a plain file name",
            obj(""""d": {"type": "string", "x-autojs6-type": "directory", "x-autojs6-files": [".."]}""") to "is not a plain file name",
            obj(""""d": {"type": "string", "x-autojs6-type": "directory", "x-autojs6-files": "model.json"}""") to "$.d: x-autojs6-files is not an array",
        ).forEach { (schema, needle) ->
            val reason = PluginToolSchema.checkInput(json(schema))
            assertTrue("\"$needle\" in \"$reason\"", reason?.contains(needle) == true)
        }
        val reason = PluginToolSchema.checkOutput(json(obj(""""img": {"type": "string", "x-autojs6-type": "image"}""")))
        assertTrue(reason, reason?.contains("only allowed in input schemas") == true)
    }

    @Test
    fun directoryParametersMayNameTheFilesTheyNeed() {
        val schema = json(obj(
            """"image": {"type": "string", "x-autojs6-type": "image"}, "model": {"type": "string", "x-autojs6-type": "directory", "x-autojs6-files": ["model.json", "model.ncnn.param", "model.ncnn.bin"]}, "any": {"type": "string", "x-autojs6-type": "directory"}""",
        ))
        assertEquals(null, PluginToolSchema.checkInput(schema))
        assertEquals(mapOf("image" to "image", "model" to "directory", "any" to "directory"), PluginToolSchema.specialParameters(schema))
        assertEquals(mapOf("model" to listOf("model.json", "model.ncnn.param", "model.ncnn.bin")), PluginToolSchema.directoryFiles(schema))
    }

    @Test
    fun depthAndPropertyCountAreBounded() {
        var nested = """{"type": "string"}"""
        repeat(8) { nested = """{"type": "object", "additionalProperties": false, "properties": {"n": $nested}}""" }
        assertTrue(PluginToolSchema.checkInput(json(nested))!!.contains("nested deeper than 8 levels"))
        val wide = obj((0..64).joinToString { """"p$it": {"type": "boolean"}""" })
        assertTrue(PluginToolSchema.checkInput(json(wide))!!.contains("more than 64 properties"))
    }

    @Test
    fun validArgumentsHaveNoProblems() {
        val args = json("""{"text": "héllo", "count": 3, "ratio": 0.5, "mode": "fast", "flag": true, "image": "screen", "archive": "a.zip",
            "options": {"depth": 2}, "items": [{"id": 1}], "choice": "x", "optional": null}""")
        assertEquals(emptyList<String>(), PluginToolSchema.validate(RICH, args))
    }

    @Test
    fun argumentProblemsNameTheirPath() {
        val problems = PluginToolSchema.validate(
            RICH,
            json("""{"count": 2.5, "ratio": 1, "mode": "slow", "flag": "yes", "image": 3, "extra": 1,
                "options": {"depth": 9, "more": true}, "items": [{"id": 1}, {"name": "x"}, 4, 5], "choice": 1.5}"""),
        )
        listOf(
            "$.text: is required",
            "$.count: expected a whole number",
            "$.ratio: not below 1",
            "$.mode: not one of",
            "$.flag: expected true or false",
            "$.image: expected a string",
            "$.extra: is not a parameter of this tool",
            "$.options.depth: above the maximum 4",
            "$.options.more: is not a parameter of this tool",
            "$.items: more than 3 items",
            "$.items[1].id: is required",
            "$.items[1].name: is not a parameter of this tool",
            "$.items[2]: expected an object",
            "$.choice: matches 0 of the 2 oneOf schemas",
        ).forEach { needle -> assertTrue("$needle in $problems", problems.any { it.startsWith(needle) }) }
    }

    @Test
    fun stringLengthsCountCodePointsAndOneOfNeedsExactlyOneMatch() {
        val schema = json(obj(""""s": {"type": "string", "maxLength": 2}, "c": {"oneOf": [{"type": "number"}, {"type": "integer"}]}"""))
        assertEquals(emptyList<String>(), PluginToolSchema.validate(schema, json("""{"s": "😀a", "c": 1.5}""")))
        val problems = PluginToolSchema.validate(schema, json("""{"s": "abc", "c": 2}"""))
        assertTrue(problems.toString(), problems.any { it.startsWith("$.s: longer than 2") })
        assertTrue(problems.toString(), problems.any { it.startsWith("$.c: matches 2 of the 2 oneOf schemas") })
    }

    @Test
    fun defaultsFillAbsentAndNullPropertiesRecursively() {
        val filled = PluginToolSchema.withDefaults(RICH, json("""{"text": "a", "mode": null, "options": {}}"""))
        assertEquals("normal", filled.get("mode").asString)
        assertEquals(1, filled.getAsJsonObject("options").get("depth").asInt)
        assertEquals("a", filled.get("text").asString)
        assertTrue(!filled.has("count"))
        val kept = PluginToolSchema.withDefaults(RICH, json("""{"text": "a", "mode": "fast"}"""))
        assertEquals("fast", kept.get("mode").asString)
    }

    private fun json(text: String): JsonObject = JsonParser.parseString(text).asJsonObject

    private fun obj(properties: String): String = """{"type": "object", "additionalProperties": false, "properties": {$properties}}"""

    private companion object {
        val RICH: JsonObject = JsonParser.parseString(
            """
            {
              "type": "object",
              "title": "Rich",
              "description": "Every construct of the subset",
              "properties": {
                "text": {"type": "string", "minLength": 1, "maxLength": 10, "description": "Some text"},
                "count": {"type": "integer", "minimum": 0, "maximum": 10},
                "ratio": {"type": "number", "exclusiveMinimum": 0, "exclusiveMaximum": 1},
                "mode": {"type": "string", "enum": ["fast", "normal"], "default": "normal"},
                "flag": {"type": "boolean", "default": false},
                "image": {"type": "string", "x-autojs6-type": "image", "description": "screen or a path"},
                "archive": {"type": "string", "x-autojs6-type": "file"},
                "options": {"type": "object", "additionalProperties": false, "properties": {"depth": {"type": "integer", "minimum": 1, "maximum": 4, "default": 1}}},
                "items": {"type": "array", "maxItems": 3, "items": {"type": "object", "additionalProperties": false, "properties": {"id": {"type": "integer"}}, "required": ["id"]}},
                "choice": {"oneOf": [{"type": "string"}, {"type": "integer"}]},
                "optional": {"type": "string"}
              },
              "required": ["text"],
              "additionalProperties": false
            }
            """,
        ).asJsonObject
    }
}
