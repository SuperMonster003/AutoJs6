package org.autojs.autojs.core.automator.pick

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * The alias table of the `pickup` / `detect` result types: every spelling maps to its type, the
 * plural forms are derived consistently, and everything else is a method name or unsupported.
 * zh-CN: `pickup` / `detect` 结果类型的别名表: 每种拼写映射到其类型, 复数形式的派生一致, 其余为方法名或不支持.
 */
class PickResultTypeTest {

    @Test
    fun everySpellingMapsToItsType() {
        val expected = mapOf(
            PickResultType.Widget to listOf("#", "w", "widget"),
            PickResultType.WidgetCollection to listOf("{}", "wc", "collection", "list", "#{}", "{#}", "w{}", "{w}"),
            PickResultType.Widgets to listOf("[]", "ws", "widgets", "##", "#[]", "[#]", "w[]", "[w]"),
            PickResultType.Content to listOf("$", "txt", "content"),
            PickResultType.Contents to listOf("contents", "$$", "$[]", "[$]", "txt[]", "[txt]", "content[]", "[content]"),
            PickResultType.Point to listOf(".", "pt", "point"),
            PickResultType.Points to listOf("points", "pts", "..", ".[]", "[.]", "point[]", "[point]", "pt[]", "[pt]"),
            PickResultType.Selector to listOf("@", "selector", "sel"),
            PickResultType.Existence to listOf("?", "exists"),
        )
        expected.forEach { (type, spellings) ->
            assertEquals(spellings, PickResultType.spellings(type))
            spellings.forEach { spelling -> assertEquals(spelling, type, PickResultType.of(spelling)) }
        }
        assertEquals(PickResultType.WIDGET, "widget")
        assertEquals(PickResultType.Widget, PickResultType.of(PickResultType.WIDGET))
    }

    @Test
    fun spellingsAreUniqueAcrossTypes() {
        val all = listOf(
            PickResultType.Widget, PickResultType.WidgetCollection, PickResultType.Widgets,
            PickResultType.Content, PickResultType.Contents, PickResultType.Point, PickResultType.Points,
            PickResultType.Selector, PickResultType.Existence,
        ).flatMap { PickResultType.spellings(it) }
        assertEquals(all.size, all.toSet().size)
    }

    @Test
    fun otherStringsAreMethodNamesAndTheEmptyStringIsNothing() {
        assertEquals(PickResultType.Nothing, PickResultType.of(""))
        assertEquals(PickResultType.Invocation("text", emptyList()), PickResultType.of("text"))
        assertEquals(PickResultType.Invocation("bounds", emptyList()), PickResultType.of("bounds"))
        assertEquals(PickResultType.Invocation("Text", emptyList()), PickResultType.of("Text"))
    }

    @Test
    fun listsAreInvocationsWithArgumentsOrTheWidgetsWhenEmpty() {
        assertEquals(PickResultType.Widgets, PickResultType.of(emptyList<Any>()))
        assertEquals(PickResultType.Invocation("child", listOf(0)), PickResultType.of(listOf("child", 0)))
        assertEquals(PickResultType.Invocation("#", emptyList()), PickResultType.of(listOf("#")))
        assertEquals(PickResultType.Invocation("compass", listOf("p", 2.0)), PickResultType.of(listOf("compass", "p", 2.0)))
        assertEquals(PickResultType.Unsupported(listOf(1, "x")), PickResultType.of(listOf(1, "x")))
    }

    @Test
    fun otherValuesAreUnsupported() {
        assertEquals(PickResultType.Unsupported(5), PickResultType.of(5))
        assertEquals(PickResultType.Unsupported(true), PickResultType.of(true))
        assertEquals(PickResultType.Unsupported(null), PickResultType.of(null))
        val builder = StringBuilder("#")
        assertEquals(PickResultType.Unsupported(builder), PickResultType.of(builder))
    }

    @Test
    fun theArrayFormOfASymbolIsItsPlural() {
        assertEquals(PickResultType.Widgets, PickResultType.arrayFormOf("#"))
        assertEquals(PickResultType.Widgets, PickResultType.arrayFormOf("widget"))
        assertEquals(PickResultType.Contents, PickResultType.arrayFormOf("$"))
        assertEquals(PickResultType.Contents, PickResultType.arrayFormOf("content"))
        assertEquals(PickResultType.Points, PickResultType.arrayFormOf("."))
        assertNull(PickResultType.arrayFormOf("[]"))
        assertNull(PickResultType.arrayFormOf("@"))
        assertNull(PickResultType.arrayFormOf("text"))
        assertNull(PickResultType.arrayFormOf(null))
    }

}
