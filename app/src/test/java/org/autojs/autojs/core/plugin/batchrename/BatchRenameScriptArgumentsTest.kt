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
                "onConflict" to "fail", "conflictNumberPattern" to " ({n})",
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
        ).forEach { text ->
            expectCode(BatchRenameScriptCodes.INVALID_OPTIONS) { BatchRenameScriptArguments.options(json(text), null) }
        }
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
    }
}
