package org.autojs.autojs.runtime.api.modules.automator

import org.autojs.autojs.core.automator.diagnostics.DumpFormat
import org.autojs.autojs.core.automator.diagnostics.DumpOptions
import org.autojs.autojs.core.automator.toolkit.UiNodes
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.Context
import org.mozilla.javascript.VarScope

/**
 * The option forms of `auto.dump` / `dumpSubtree` on a bare Rhino context: nothing, a format
 * name, an options object with every key, the lenient depth and indent numbers, `properties` as
 * one name or an array, and the refused keys and types.
 * zh-CN: `auto.dump` / `dumpSubtree` 的选项形式, 在裸 Rhino 上下文上: 不传, 形式名, 带每个键的选项对象, 宽松的深度与缩进数字,
 * `properties` 的单名或数组形式, 以及被拒绝的键与类型.
 */
class DumpArgumentsTest {

    private lateinit var cx: Context

    private lateinit var scope: VarScope

    @Before
    fun enterContext() {
        cx = Context.enter()
        scope = cx.initStandardObjects()
    }

    @After
    fun exitContext() {
        Context.exit()
    }

    private fun js(source: String): Any? = cx.evaluateString(scope, source, "test", 1, null)

    private fun options(source: String, allowRoot: Boolean = true): DumpOptions = DumpArguments.parse(js(source), allowRoot).options

    @Test
    fun nothingAndAFormatNameGiveDefaults() {
        val defaults = DumpArguments.parse(null)
        assertEquals(DumpFormat.TEXT, defaults.options.format)
        assertEquals(DumpOptions.UNLIMITED, defaults.options.maxDepth)
        assertNull(defaults.options.properties)
        assertFalse(defaults.options.visibleOnly)
        assertEquals(UiNodes.DEFAULT_MAX_NODES, defaults.options.maxNodes)
        assertEquals("  ", defaults.options.indent)
        assertNull(defaults.root)
        assertEquals(DumpFormat.TEXT, DumpArguments.parse(js("undefined")).options.format)
        assertEquals(DumpFormat.XML, options("'xml'").format)
        assertEquals(DumpFormat.JSON, options("'JSON'").format)
    }

    @Test
    fun theOptionsObjectSetsEveryKey() {
        val o = options("({ format: 'json', maxDepth: 2, properties: ['text', 'bounds'], visibleOnly: true, maxNodes: 50, indent: '\\t' })")
        assertEquals(DumpFormat.JSON, o.format)
        assertEquals(2, o.maxDepth)
        assertEquals(listOf("text", "bounds"), o.properties)
        assertTrue(o.visibleOnly)
        assertEquals(50, o.maxNodes)
        assertEquals("\t", o.indent)
    }

    @Test
    fun numbersAreLenientWhereItHelps() {
        assertEquals(DumpOptions.UNLIMITED, options("({ maxDepth: -1 })").maxDepth)
        assertEquals(DumpOptions.UNLIMITED, options("({ maxDepth: Infinity })").maxDepth)
        assertEquals(0, options("({ maxDepth: 0.9 })").maxDepth)
        assertEquals(Int.MAX_VALUE, options("({ maxNodes: Infinity })").maxNodes)
        assertEquals("    ", options("({ indent: 4 })").indent)
        assertEquals("", options("({ indent: 0 })").indent)
        assertEquals(listOf("text"), options("({ properties: 'text' })").properties)
    }

    @Test
    fun unknownKeysAndBadTypesAreRefused() {
        assertRefused("({ maxDeep: 1 })", "\"maxDeep\"")
        assertRefused("({ format: 'yaml' })", "\"yaml\"")
        assertRefused("({ properties: ['text', 'nope'] })", "\"nope\"")
        assertRefused("({ visibleOnly: 'yes' })", "\"visibleOnly\"")
        assertRefused("({ maxNodes: 0 })", "\"maxNodes\"")
        assertRefused("({ maxDepth: 'deep' })", "\"maxDepth\"")
        assertRefused("({ root: 1 })", "\"root\"")
        assertRefused("({ root: null })", "\"root\"", allowRoot = false)
        assertRefused("42", "42")
    }

    private fun assertRefused(source: String, mention: String, allowRoot: Boolean = true) {
        try {
            DumpArguments.parse(js(source), allowRoot)
            fail("accepted $source")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message, e.message!!.contains(mention))
        }
    }

}
