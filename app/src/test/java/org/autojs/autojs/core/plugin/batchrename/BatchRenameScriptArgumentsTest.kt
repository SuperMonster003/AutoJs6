package org.autojs.autojs.core.plugin.batchrename

import com.google.gson.JsonParser
import org.autojs.plugin.batchrename.api.BatchRenameOptionKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.fail
import org.junit.Test

/** Argument normalization of `files.batchRename` (plugin roadmap P3.2, D17, D27). zh-CN: `files.batchRename` 的参数规范化. */
class BatchRenameScriptArgumentsTest {

    private fun json(text: String) = JsonParser.parseString(text)

    private fun expectCode(code: String, block: () -> Unit) {
        try {
            block()
        } catch (e: BatchRenameScriptException) {
            assertEquals(e.message, code, e.code)
            return
        }
        fail("expected $code")
    }

    @Test
    fun `paths are a string or an array of non-blank strings`() {
        assertEquals(listOf("a.txt"), BatchRenameScriptArguments.paths(json("\"a.txt\"")))
        assertEquals(listOf("a", "b/c"), BatchRenameScriptArguments.paths(json("[\"a\", \"b/c\"]")))
        listOf("null", "[]", "[\"a\", 1]", "\"  \"", "{}", "3").forEach { text ->
            expectCode(BatchRenameScriptCodes.INVALID_PATHS) { BatchRenameScriptArguments.paths(json(text)) }
        }
        expectCode(BatchRenameScriptCodes.INVALID_PATHS) { BatchRenameScriptArguments.paths(null) }
    }

    @Test
    fun `options take the PowerRename defaults`() {
        val options = BatchRenameScriptArguments.options(json("{\"search\": \"IMG\"}"), null)
        assertEquals(
            linkedMapOf<String, Any>(
                "search" to "IMG", "replace" to "", "regex" to false, "regexFlags" to "", "matchAll" to false, "caseSensitive" to false,
                "target" to "all", "includeFiles" to true, "includeFolders" to true, "includeSubfolders" to true, "format" to "none",
                "enumerate" to false, "random" to false, "dateTime" to "modified", "normalize" to "none", "halfWidth" to false,
                "sanitize" to false, "sanitizeReplacement" to "_", "trim" to false, "continueNumbering" to false,
                "onConflict" to "fail", "conflictNumberPattern" to " ({n})", "pinyin" to false, "pinyinTone" to "none",
                "pinyinSeparator" to "none", "pinyinCase" to "lower", "pinyinInitials" to false, "metadata" to false,
            ),
            options,
        )
        assertEquals(options.keys.toList(), options.keys.distinct())
    }

    @Test
    fun `explicit values, false choices and unknown keys`() {
        val options = BatchRenameScriptArguments.options(
            json("{\"search\": \"\", \"replace\": \"x\", \"target\": \"name\", \"dateTime\": false, \"normalize\": \"nfc\", \"onConflict\": \"number\", \"includeSubfolders\": false, \"unknown\": 1, \"onProgress\": null}"),
            null,
        )
        assertEquals("", options[BatchRenameOptionKeys.SEARCH])
        assertEquals("x", options[BatchRenameOptionKeys.REPLACE])
        assertEquals("name", options[BatchRenameOptionKeys.TARGET])
        assertEquals("none", options[BatchRenameOptionKeys.DATE_TIME])
        assertEquals("nfc", options[BatchRenameOptionKeys.NORMALIZE])
        assertEquals("number", options[BatchRenameOptionKeys.ON_CONFLICT])
        assertEquals(false, options[BatchRenameOptionKeys.INCLUDE_SUBFOLDERS])
        assertNull(options["unknown"])
    }

    @Test
    fun `a RegExp search decides regex, matchAll and caseSensitive`() {
        val options = BatchRenameScriptArguments.options(
            json("{\"search\": {}, \"regex\": false, \"matchAll\": false, \"caseSensitive\": false, \"replace\": \"$1\"}"),
            BatchRenameRegExp("^IMG_(\\d+)", "gsu"),
        )
        assertEquals("^IMG_(\\d+)", options[BatchRenameOptionKeys.SEARCH])
        assertEquals(true, options[BatchRenameOptionKeys.REGEX])
        assertEquals(true, options[BatchRenameOptionKeys.MATCH_ALL])
        assertEquals(true, options[BatchRenameOptionKeys.CASE_SENSITIVE])
        assertEquals("su", options[BatchRenameOptionKeys.REGEX_FLAGS])
        val insensitive = BatchRenameScriptArguments.options(json("{}"), BatchRenameRegExp("a", "i"))
        assertEquals(false, insensitive[BatchRenameOptionKeys.CASE_SENSITIVE])
        assertEquals(false, insensitive[BatchRenameOptionKeys.MATCH_ALL])
        expectCode(BatchRenameScriptCodes.INVALID_OPTIONS) { BatchRenameScriptArguments.options(json("{}"), BatchRenameRegExp("a", "v")) }
    }

    @Test
    fun `wrong option types and values are INVALID_OPTIONS`() {
        listOf(
            "null",
            "\"text\"",
            "{}",
            "{\"search\": 1}",
            "{\"search\": \"a\", \"regex\": \"yes\"}",
            "{\"search\": \"a\", \"replace\": 2}",
            "{\"search\": \"a\", \"target\": \"stem\"}",
            "{\"search\": \"a\", \"format\": false}",
            "{\"search\": \"a\", \"dateTime\": true}",
            "{\"search\": \"a\", \"onConflict\": \"rename\"}",
            "{\"search\": \"a\", \"pinyin\": \"yes\"}",
            "{\"search\": \"a\", \"pinyinTone\": \"marks\"}",
            "{\"search\": \"a\", \"pinyinCase\": false}",
        ).forEach { text ->
            expectCode(BatchRenameScriptCodes.INVALID_OPTIONS) { BatchRenameScriptArguments.options(json(text), null) }
        }
    }

    @Test
    fun `pinyin options reach only plugins with the pinyin feature`() {
        val options = BatchRenameScriptArguments.options(
            json("{\"search\": \"\", \"pinyin\": true, \"pinyinTone\": \"number\", \"pinyinSeparator\": false, \"pinyinCase\": \"capitalize\", \"pinyinInitials\": true}"),
            null,
        )
        assertEquals(listOf(true, "number", "none", "capitalize", true), PINYIN_KEYS.map { options[it] })
        val features = listOf("counter", "dateTime", "random", "cleanup")
        assertEquals(options, BatchRenameScriptArguments.forPluginFeatures(options, features + "pinyin" + "metadata"))
        expectCode(BatchRenameScriptCodes.PLUGIN_INCOMPATIBLE) { BatchRenameScriptArguments.forPluginFeatures(options, features) }

        // Without pinyin in use, an older plugin gets the options of contract v1 1.0.0. zh-CN: 未用到拼音时旧插件收到 1.0.0 的选项.
        val plain = BatchRenameScriptArguments.options(json("{\"search\": \"a\", \"replace\": \"$$" + "{pinyin}\", \"pinyinTone\": \"mark\"}"), null)
        assertEquals(plain.keys - PINYIN_KEYS.toSet() - "metadata", BatchRenameScriptArguments.forPluginFeatures(plain, features).keys)
        val variable = plain + (BatchRenameOptionKeys.REPLACE to "IMG_$" + "{pinyin}")
        expectCode(BatchRenameScriptCodes.PLUGIN_INCOMPATIBLE) { BatchRenameScriptArguments.forPluginFeatures(variable, features) }
    }

    @Test
    fun `with a preset only the given options travel`() {
        val sparse = BatchRenameScriptArguments.options(json("{\"preset\": \"photos\", \"replace\": \"x\", \"normalize\": false, \"trim\": null}"), null)
        assertEquals(linkedMapOf<String, Any>("preset" to "photos", "replace" to "x", "normalize" to "none"), sparse)
        val regExp = BatchRenameScriptArguments.options(json("{\"preset\": \"photos\"}"), BatchRenameRegExp("^a", "gi"))
        assertEquals(listOf("preset", "search", "regex", "regexFlags", "matchAll", "caseSensitive"), regExp.keys.toList())
        listOf("{\"preset\": \"\"}", "{\"preset\": \"  \"}", "{\"preset\": 3}", "{\"preset\": \"p\", \"target\": \"stem\"}").forEach { text ->
            expectCode(BatchRenameScriptCodes.INVALID_OPTIONS) { BatchRenameScriptArguments.options(json(text), null) }
        }

        val preset = mapOf<String, Any>("search" to "IMG", "replace" to "Pic", "format" to "upper", "futureOption" to true)
        val merged = BatchRenameScriptArguments.withPreset(sparse, preset)
        assertEquals(BatchRenameScriptArguments.options(json("{\"search\": \"\"}"), null).keys, merged.keys)
        assertEquals(listOf("IMG", "x", "upper", "fail"), listOf(merged["search"], merged["replace"], merged["format"], merged["onConflict"]))

        val features = listOf("counter", "dateTime", "random", "cleanup", "pinyin")
        expectCode(BatchRenameScriptCodes.PLUGIN_INCOMPATIBLE) { BatchRenameScriptArguments.forPluginFeatures(sparse, features) }
        assertEquals(sparse, BatchRenameScriptArguments.forPluginFeatures(sparse, features + "presets"))
    }

    @Test
    fun `preview takes names, items or arrays of them`() {
        assertEquals(listOf(BatchRenamePreviewItem("a.txt")) to false, BatchRenameScriptArguments.previewItems(json("\"a.txt\"")))
        assertEquals(
            listOf(BatchRenamePreviewItem("a.txt"), BatchRenamePreviewItem("dir", directory = true, lastModified = 1_792_053_000_000L)) to true,
            BatchRenameScriptArguments.previewItems(json("[\"a.txt\", {\"name\": \"dir\", \"isDirectory\": true, \"lastModified\": 1792053000000}]")),
        )
        // JSON.stringify turns a Date into ISO text. zh-CN: JSON.stringify 把 Date 转为 ISO 文本.
        assertEquals(1_792_053_000_000L, BatchRenameScriptArguments.previewItems(json("{\"name\": \"a\", \"lastModified\": \"2026-10-15T08:30:00.000Z\"}")).first.single().lastModified)
        assertEquals(emptyList<BatchRenamePreviewItem>() to true, BatchRenameScriptArguments.previewItems(json("[]")))
        listOf(
            "null", "1", "{}", "[\"\"]", "[\"a/b\"]", "[\"..\"]", "{\"name\": 3}", "{\"name\": \"a\", \"isDirectory\": \"yes\"}",
            "{\"name\": \"a\", \"lastModified\": \"yesterday\"}", "{\"name\": \"a\", \"lastModified\": 1.5}", "[\"${"n".repeat(1025)}\"]",
        ).forEach { text -> expectCode(BatchRenameScriptCodes.INVALID_OPTIONS) { BatchRenameScriptArguments.previewItems(json(text)) } }
        expectCode(BatchRenameScriptCodes.LIMIT_EXCEEDED) { BatchRenameScriptArguments.previewItems(json("[" + List(2001) { "\"n$it\"" }.joinToString(",") + "]")) }
    }

    @Test
    fun `metadata needs the plugin feature and is read only for its variables`() {
        val off = BatchRenameScriptArguments.options(json("{\"search\": \"a\"}"), null)
        val on = BatchRenameScriptArguments.options(json("{\"search\": \"a\", \"replace\": \"${'$'}CAMERA_MODEL\", \"metadata\": true}"), null)
        assertEquals(true, on["metadata"])
        val old = listOf("counter", "dateTime", "random", "cleanup", "pinyin", "presets")
        assertEquals(off.keys - "metadata", BatchRenameScriptArguments.forPluginFeatures(off, old).keys)
        expectCode(BatchRenameScriptCodes.PLUGIN_INCOMPATIBLE) { BatchRenameScriptArguments.forPluginFeatures(on, old) }
        assertEquals(on, BatchRenameScriptArguments.forPluginFeatures(on, old + "metadata"))
        mapOf(
            "${'$'}CAMERA_MAKE" to true, "x_${'$'}DATE_TAKEN_YYYY" to true, "${'$'}${'$'}ISO" to false, "${'$'}${'$'}${'$'}ISO" to true,
            "${'$'}DATE" to false, "${'$'}{ISO}" to false, "${'$'}YYYY" to false, "" to false,
        ).forEach { (replace, expected) -> assertEquals(replace, expected, BatchRenameScriptArguments.usesMetadataVariables(replace)) }
        expectCode(BatchRenameScriptCodes.INVALID_OPTIONS) { BatchRenameScriptArguments.options(json("{\"search\": \"a\", \"metadata\": 1}"), null) }
    }

    @Test
    fun `importPresets takes skip, overwrite or rename`() {
        assertEquals("skip", BatchRenameScriptArguments.presetConflict(null))
        assertEquals("skip", BatchRenameScriptArguments.presetConflict(json("{}")))
        assertEquals("rename", BatchRenameScriptArguments.presetConflict(json("{\"onConflict\": \"rename\"}")))
        assertEquals("overwrite", BatchRenameScriptArguments.presetConflict(json("{\"onConflict\": \"overwrite\"}")))
        listOf("{\"onConflict\": \"merge\"}", "{\"onConflict\": true}", "\"skip\"").forEach { text ->
            expectCode(BatchRenameScriptCodes.INVALID_OPTIONS) { BatchRenameScriptArguments.presetConflict(json(text)) }
        }
    }

    @Test
    fun `the pinyin variable is read like the plugin reads it`() {
        mapOf(
            "$" + "{pinyin}" to true, "a$$$" + "{pinyin}" to true, "$$" + "{pinyin}" to false, "$" + "{Pinyin}" to false,
            "$" + "{pinyin" to false, "" to false,
        ).forEach { (replace, expected) -> assertEquals(replace, expected, BatchRenameScriptArguments.usesPinyinVariable(replace)) }
    }

    @Test
    fun `plugin error codes map to script codes`() {
        assertEquals(BatchRenameScriptCodes.INVALID_OPTIONS, BatchRenameScriptCodes.fromPluginErrorCode("INVALID_OPTIONS"))
        assertEquals(BatchRenameScriptCodes.LIMIT_EXCEEDED, BatchRenameScriptCodes.fromPluginErrorCode("LIMIT_EXCEEDED"))
        assertEquals(BatchRenameScriptCodes.PLUGIN_INCOMPATIBLE, BatchRenameScriptCodes.fromPluginErrorCode("UNSUPPORTED_CONTRACT_VERSION"))
        listOf("INVALID_REQUEST", "INTERNAL_ERROR", "SOMETHING_NEW", null).forEach { code ->
            assertEquals(BatchRenameScriptCodes.INTERNAL, BatchRenameScriptCodes.fromPluginErrorCode(code))
        }
    }

    @Test
    fun `apply options, batch IDs and history limits`() {
        assertNull(BatchRenameScriptArguments.conflictOverride(null))
        assertNull(BatchRenameScriptArguments.conflictOverride(json("{\"onProgress\": null}")))
        assertEquals("skip", BatchRenameScriptArguments.conflictOverride(json("{\"onConflict\": \"skip\"}")))
        expectCode(BatchRenameScriptCodes.INVALID_OPTIONS) { BatchRenameScriptArguments.conflictOverride(json("{\"onConflict\": 1}")) }

        val id = "b_" + "0123456789abcdef".repeat(2)
        assertEquals(id, BatchRenameScriptArguments.batchId(json("\"$id\"")))
        assertEquals(id, BatchRenameScriptArguments.batchId(json("{\"batchId\": \"$id\", \"items\": []}")))
        listOf("null", "\"b_1\"", "{\"batchId\": null}", "{}", "3").forEach { text ->
            expectCode(BatchRenameScriptCodes.UNDO_UNAVAILABLE) { BatchRenameScriptArguments.batchId(json(text)) }
        }

        assertEquals(20, BatchRenameScriptArguments.historyLimit(null, 20))
        assertEquals(3, BatchRenameScriptArguments.historyLimit(json("3"), 20))
        assertEquals(20, BatchRenameScriptArguments.historyLimit(json("100"), 20))
        assertEquals(0, BatchRenameScriptArguments.historyLimit(json("0"), 20))
        listOf("-1", "1.5", "\"3\"").forEach { text ->
            expectCode(BatchRenameScriptCodes.INVALID_OPTIONS) { BatchRenameScriptArguments.historyLimit(json(text), 20) }
        }

        // deleteHistory takes a batch ID or the result of apply; clearHistory an optional source (plugin roadmap D39).
        // zh-CN: deleteHistory 接受批次 ID 或 apply 的结果; clearHistory 接受可选的来源 (插件路线图 D39).
        assertEquals(id, BatchRenameScriptArguments.historyBatchId(json("\"$id\"")))
        assertEquals(id, BatchRenameScriptArguments.historyBatchId(json("{\"batchId\": \"$id\", \"items\": []}")))
        listOf("null", "\"b_1\"", "{\"batchId\": null}", "{}", "3").forEach { text ->
            expectCode(BatchRenameScriptCodes.INVALID_OPTIONS) { BatchRenameScriptArguments.historyBatchId(json(text)) }
        }
        expectCode(BatchRenameScriptCodes.INVALID_OPTIONS) { BatchRenameScriptArguments.historyBatchId(null) }
        assertNull(BatchRenameScriptArguments.historySource(null))
        assertNull(BatchRenameScriptArguments.historySource(json("{}")))
        assertNull(BatchRenameScriptArguments.historySource(json("{\"source\": null}")))
        assertEquals(BatchRenameSource.SCRIPT, BatchRenameScriptArguments.historySource(json("{\"source\": \"script\"}")))
        assertEquals(BatchRenameSource.EXPLORER, BatchRenameScriptArguments.historySource(json("{\"source\": \"explorer\"}")))
        listOf("{\"source\": \"plugin\"}", "{\"source\": 1}", "\"script\"").forEach { text ->
            expectCode(BatchRenameScriptCodes.INVALID_OPTIONS) { BatchRenameScriptArguments.historySource(json(text)) }
        }
    }

    private companion object {
        val PINYIN_KEYS = listOf(
            BatchRenameOptionKeys.PINYIN,
            BatchRenameOptionKeys.PINYIN_TONE,
            BatchRenameOptionKeys.PINYIN_SEPARATOR,
            BatchRenameOptionKeys.PINYIN_CASE,
            BatchRenameOptionKeys.PINYIN_INITIALS,
        )
    }
}
