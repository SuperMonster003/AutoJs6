package org.autojs.autojs.runtime.api.modules.util

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.Context
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.VarScope
import kotlin.system.measureTimeMillis

class InspectArrayTest {

    private lateinit var context: Context
    private lateinit var scope: VarScope

    @Before
    fun enterContext() {
        context = Context.enter()
        scope = context.initStandardObjects()
    }

    @After
    fun exitContext() {
        Context.exit()
    }

    @Test
    fun largeArraysKeepHeadTailSummaryAndCustomProperties() {
        val value = evaluate(
            """
            const value = Array(500000).fill('Hello');
            value.extra = 'custom';
            value;
            """.trimIndent(),
        )

        lateinit var result: String
        val elapsed = measureTimeMillis {
            result = Util.formatRhino(value)
        }
        println("Inspecting a 500000-item Rhino array took ${elapsed}ms")

        val summary = "... 499880 more items ..."
        assertEquals(100, Regex("\\bHello\\b").findAll(result.substringBefore(summary)).count())
        assertTrue(result.contains(summary))
        assertTrue(result.contains("extra: 'custom'"))
        assertEquals(20, Regex("\\bHello\\b").findAll(result.substringAfter(summary)).count())
    }

    @Test
    fun hiddenArrayPropertiesKeepBracketNotation() {
        val value = evaluate(
            """
            const value = ['visible'];
            Object.defineProperty(value, 'hidden', { value: 'secret' });
            value;
            """.trimIndent(),
        )
        val options = evaluate("({ showHidden: true })")

        assertTrue(Inspect.inspectRhino(value, options).contains("[hidden]: 'secret'"))
    }

    @Test
    fun sparseArraysKeepHeadTailAndCustomPropertiesWithoutPrintingOmittedValues() {
        val value = evaluate(
            """
            const value = Array(125);
            value[0] = 'HEAD_ZERO';
            value[99] = 'HEAD_END';
            value[102] = 'OMITTED_VALUE';
            value[124] = 'TAIL_END';
            value.extra = 'CUSTOM_VALUE';
            value;
            """.trimIndent(),
        )
        val options = evaluate("({ maxArrayItems: 120 })")

        val result = Inspect.inspectRhino(value, options)

        listOf("HEAD_ZERO", "HEAD_END", "TAIL_END", "CUSTOM_VALUE").forEach { marker ->
            assertEquals(1, Regex(marker).findAll(result).count())
        }
        assertTrue(result.contains("... 5 more items ..."))
        assertFalse(result.contains("OMITTED_VALUE"))
    }

    private fun evaluate(source: String): Scriptable {
        return context.evaluateString(scope, source, "inspect-array", 1, null) as Scriptable
    }

}
