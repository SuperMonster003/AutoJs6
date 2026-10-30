package org.autojs.autojs.engine

import android.util.Log
import org.autojs.plugin.bun.runtime.api.BunRuntimeContract
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

// api-coverage P4.3 (D-10, Q171 (a)): the console.control println route of the Bun host client's console output.
class BunConsoleOutputTest {

    private fun call(vararg entries: List<Any?>) = JSONArray().put(JSONArray(entries.map { JSONArray(it) }))

    @Test fun printsTheLinesInOrderAtRhinosLevels() {
        val printed = mutableListOf<Pair<Int, String>>()
        val output = BunConsoleOutput { level, text -> printed += level to text }
        assertTrue(output.println(call(listOf("log", "a"), listOf("verbose", "b"), listOf("info", "c"), listOf("warn", "d"), listOf("error", "e"), listOf("assert", "f"))))
        assertEquals(listOf(Log.DEBUG to "a", Log.VERBOSE to "b", Log.INFO to "c", Log.WARN to "d", Log.ERROR to "e", Log.ASSERT to "f"), printed)
    }

    @Test fun rejectsMalformedCallsBeforePrintingAnything() {
        val printed = mutableListOf<Pair<Int, String>>()
        val output = BunConsoleOutput { level, text -> printed += level to text }
        for (args in listOf(JSONArray(), call(listOf("log", "a")).put(1), call(listOf("debug", "a")), call(listOf("log", 1)), call(listOf("log", "a"), listOf("log")))) {
            assertThrows(args.toString(), IllegalArgumentException::class.java) { output.println(args) }
        }
        assertTrue(printed.isEmpty())
    }

    @Test fun answersFalseOnceTheTextBudgetIsSpent() {
        var count = 0
        val output = BunConsoleOutput { _, _ -> count++ }
        val half = "x".repeat((BunRuntimeContract.MAX_OUTPUT_BYTES / 2).toInt())
        assertTrue(output.println(call(listOf("log", half))))
        assertTrue(output.println(call(listOf("log", half))))
        assertFalse(output.println(call(listOf("log", "x"))))
        assertEquals(2, count)
    }

}
