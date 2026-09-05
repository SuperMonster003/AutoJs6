package org.autojs.autojs.core.automator.selector

import org.autojs.autojs.core.accessibility.AccessibilityBridge
import org.autojs.autojs.core.accessibility.UiSelector
import org.autojs.autojs.core.automator.FakeUiNode
import org.autojs.autojs.core.automator.filter.ActionFilter
import org.autojs.autojs.core.automator.filter.BooleanFilter
import org.autojs.autojs.core.automator.filter.BoundsFilter
import org.autojs.autojs.core.automator.filter.Filter
import org.autojs.autojs.core.automator.filter.MetricsFilter
import org.autojs.autojs.core.automator.filter.ToleranceFilter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The map selector rules that `checkSelectorMap` used to implement reflectively, now decided by
 * [SelectorMapParser] over the registry.
 * zh-CN: `checkSelectorMap` 原先以反射实现的 map 选择器规则, 现由 [SelectorMapParser] 基于注册表决定.
 */
class SelectorMapParserTest {

    /** Stands in for a script function; the parser sees it through the callables adapter. zh-CN: 代表脚本函数; 解析器经 callables 适配器识别它. */
    private class ScriptFunction(val predicate: (String) -> Boolean)

    private val parser = SelectorMapParser(SelectorRegistry) { value ->
        (value as? ScriptFunction)?.let { function -> BooleanFilter.BooleanSupplier { node -> function.predicate(node.text()) } }
    }

    private val login = FakeUiNode.root("login") {
        text = "Login"
        clickable = true
        actions = listOf("ACTION_CLICK", "ACTION_FOCUS")
        child("icon")
    }

    private fun parse(vararg pairs: Pair<Any?, Any?>): List<Filter> {
        return parser.parse(mapOf(*pairs), UiSelector(null as AccessibilityBridge?)).selector.filters.toList()
    }

    @Test
    fun scalarsBecomeSingleArguments() {
        val filters = parse("text" to "Login", "clickable" to true, "depth" to 1.0)
        assertEquals(3, filters.size)
        assertTrue(filters[0].filter(login))
        assertTrue(filters[1].filter(login))
        assertTrue(filters[2].filter(login.find("icon")))
        assertFalse(filters[2].filter(login))
    }

    @Test
    fun nullSelectsTheNoArgumentOverloadAndSkipsAction() {
        val filters = parse("clickable" to null, "action" to null)
        assertEquals(1, filters.size)
        assertTrue(filters.single().filter(login))
        assertFalse(filters.single().filter(login.find("icon")))
    }

    @Test
    fun arraysAreSpreadIntoArguments() {
        assertEquals("boundsLeft(10, 20)", parse("boundsLeft" to listOf(10.0, 20.0)).single().toString())
        assertEquals("boundsLeft(10)", parse("boundsLeft" to listOf(10.0)).single().toString())
        assertTrue(parse("bounds" to listOf(0, 0, 100, 100)).single() is BoundsFilter)
        assertTrue(parse("screenCenterX" to listOf(true, 0.1)).single() is ToleranceFilter)
        assertTrue(parse("screenCenterX" to listOf(0.1)).single() is ToleranceFilter)
        assertTrue(parse("screenCenterX" to emptyList<Any>()).single() is ToleranceFilter)
        assertTrue(parse("clickable" to emptyList<Any>()).single().filter(login))
        assertTrue(parse("clickable" to listOf(false)).single().filter(login.find("icon")))
        assertTrue(parse("text" to listOf("Login")).single().filter(login))
    }

    @Test
    fun arrayElementsAreParsedLeniently() {
        assertTrue(parse("depth" to listOf("1")).single().filter(login.find("icon")))
        assertTrue(parse("clickable" to listOf("true")).single().filter(login))
        assertEquals("left(10)", parse("left" to listOf("10")).single().toString())
        assertTrue(parse("left" to listOf("10")).single() is MetricsFilter)
        assertTrue(parse("screenCenterX" to listOf("true", "0.1")).single() is ToleranceFilter)
    }

    @Test
    fun actionAcceptsScalarsAndArrays() {
        assertTrue(parse("action" to "CLICK").single().filter(login))
        assertTrue(parse("action" to listOf("CLICK", "FOCUS")).single().filter(login))
        assertTrue(parse("action" to listOf("CLICK", null, 16.0)).single().filter(login))
        assertFalse(parse("action" to "LONG_CLICK").single().filter(login))
        assertTrue(parse("action" to emptyList<Any>()).single() is ActionFilter)
    }

    @Test
    fun scriptFunctionsOnlyFitFilter() {
        val isLogin = ScriptFunction { it == "Login" }
        assertTrue(parse("filter" to isLogin).single().filter(login))
        assertFalse(parse("filter" to isLogin).single().filter(login.find("icon")))
        assertTrue(parse("filter" to listOf(isLogin)).single().filter(login))
        assertThrows(SelectorMapException.NoSuchProperty::class.java) { parse("clickable" to isLogin) }
    }

    @Test
    fun unknownKeysAndUnfittingValuesAreReportedByKind() {
        val unknown = assertThrows(SelectorMapException.NoSuchProperty::class.java) { parse("nope" to 1.0) }
        assertEquals("nope", unknown.key)
        assertEquals(1.0, unknown.value)
        assertThrows(SelectorMapException.NoSuchProperty::class.java) { parse("depth" to "1") }
        assertThrows(SelectorMapException.NoSuchProperty::class.java) { parse("text" to 1.0) }
        val illegal = assertThrows(SelectorMapException.IllegalValue::class.java) { parse("depth" to listOf(1.0, 2.0)) }
        assertEquals(listOf(1.0, 2.0), illegal.value)
        assertThrows(SelectorMapException.IllegalValue::class.java) { parse("text" to listOf(1.0)) }
        assertThrows(SelectorMapException.IllegalValue::class.java) { parse("bounds" to listOf(1.0, 2.0)) }
        assertThrows(SelectorMapException.NoSuchProperty::class.java) { parse("nope" to listOf(1.0)) }
    }

    @Test
    fun nonStringKeysAreIgnoredAndOrderIsKept() {
        val filters = parse(1 to "x", "text" to "Login", "clickable" to true)
        assertEquals(2, filters.size)
        assertTrue(filters[0].filter(login))
        assertEquals("clickable(true)", filters[1].toString())
    }

}
