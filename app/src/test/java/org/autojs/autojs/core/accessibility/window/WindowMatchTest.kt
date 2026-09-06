package org.autojs.autojs.core.accessibility.window

import org.autojs.autojs.core.accessibility.window.WindowMatch.TextRule
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [WindowMatch] (roadmap P5.3): the rules of a window filter object over fake window facts.
 * zh-CN: [WindowMatch] (Roadmap P5.3): 窗口过滤器对象的规则, 作用于假的窗口事实.
 */
class WindowMatchTest {

    private data class Win(
        val type: Int,
        val pkg: String? = null,
        val title: String? = null,
        val display: Int = 0,
        val active: Boolean = false,
        val focused: Boolean = false,
        val id: Int = 0,
        val layer: Int = 0,
    )

    private class Facts : WindowMatch.Facts<Win> {
        var packageReads = 0
        var titleReads = 0
        override fun type(window: Win) = window.type
        override fun packageName(window: Win): String? {
            packageReads += 1
            return window.pkg
        }
        override fun title(window: Win): String? {
            titleReads += 1
            return window.title
        }
        override fun displayId(window: Win) = window.display
        override fun isActive(window: Win) = window.active
        override fun isFocused(window: Win) = window.focused
        override fun id(window: Win) = window.id
        override fun layer(window: Win) = window.layer
    }

    private val facts = Facts()

    private val settings = Win(1, "com.android.settings", "Settings", active = true, focused = true, id = 7, layer = 3)
    private val keyboard = Win(2, "com.android.inputmethod", null, layer = 5)
    private val overlay = Win(4, "org.autojs.autojs6", "Floaty", display = 1, id = 9)

    @Test
    fun noRulesMatchEveryWindow() {
        val any = WindowMatch()
        assertTrue(any.isEmpty)
        assertTrue(listOf(settings, keyboard, overlay).all { any.matches(it, facts) })
        assertEquals(0, facts.packageReads)
        assertEquals("{  }", any.toString())
    }

    @Test
    fun everyGivenRuleMustHold() {
        val match = WindowMatch(types = setOf(1, 4), packageName = TextRule.whole("com.android.settings"), active = true)
        assertFalse(match.isEmpty)
        assertTrue(match.matches(settings, facts))
        assertFalse(match.matches(keyboard, facts))
        assertFalse(match.matches(overlay, facts))
        assertFalse(WindowMatch(types = setOf(1), focused = false).matches(settings, facts))
        assertTrue(WindowMatch(displayId = 1).matches(overlay, facts))
        assertFalse(WindowMatch(displayId = 1).matches(settings, facts))
        assertTrue(WindowMatch(id = 7, layer = 3).matches(settings, facts))
        assertFalse(WindowMatch(id = 7, layer = 4).matches(settings, facts))
    }

    @Test
    fun textRulesAreWholeStringsOrRegexSearches() {
        assertTrue(WindowMatch(title = TextRule.whole("Settings")).matches(settings, facts))
        assertFalse(WindowMatch(title = TextRule.whole("settings")).matches(settings, facts))
        assertFalse(WindowMatch(title = TextRule.whole("Settings")).matches(keyboard, facts))
        val regex = TextRule.regex(Regex("^com\\.android\\."), "/^com\\.android\\./")
        assertTrue(WindowMatch(packageName = regex).matches(settings, facts))
        assertTrue(WindowMatch(packageName = regex).matches(keyboard, facts))
        assertFalse(WindowMatch(packageName = regex).matches(overlay, facts))
        assertFalse(WindowMatch(title = TextRule.regex(Regex("a"), "/a/")).matches(keyboard, facts))
    }

    @Test
    fun theCostlierFactsAreReadOnlyWhenARuleNeedsThemAndAfterTheCheapOnes() {
        WindowMatch(types = setOf(3), packageName = TextRule.whole("x"), title = TextRule.whole("y")).matches(settings, facts)
        assertEquals(0, facts.packageReads)
        assertEquals(0, facts.titleReads)
        WindowMatch(packageName = TextRule.whole("x"), title = TextRule.whole("y")).matches(settings, facts)
        assertEquals(1, facts.packageReads)
        assertEquals(0, facts.titleReads)
        WindowMatch(title = TextRule.whole("Settings")).matches(settings, facts)
        assertEquals(1, facts.packageReads)
        assertEquals(1, facts.titleReads)
    }

    @Test
    fun typeNamesAcceptSeveralSpellings() {
        assertEquals(1, WindowMatch.typeOf("application"))
        assertEquals(2, WindowMatch.typeOf("inputMethod"))
        assertEquals(2, WindowMatch.typeOf("input_method"))
        assertEquals(2, WindowMatch.typeOf("TYPE_INPUT_METHOD"))
        assertEquals(4, WindowMatch.typeOf(" accessibilityOverlay "))
        assertEquals(5, WindowMatch.typeOf("SPLIT_SCREEN_DIVIDER"))
        assertEquals(6, WindowMatch.typeOf("magnification_overlay"))
        assertNull(WindowMatch.typeOf("bogus"))
        assertNull(WindowMatch.typeOf(""))
        assertEquals("application", WindowMatch.typeName(1))
        assertEquals("type7", WindowMatch.typeName(7))
    }

    @Test
    fun describesItselfLikeAnObjectLiteral() {
        val match = WindowMatch(types = setOf(1, 3), packageName = TextRule.whole("com.x"), title = TextRule.regex(Regex("Set"), "/Set/"), displayId = 0, active = true, focused = false, id = 1, layer = 2)
        assertEquals("{ type: application|system, packageName: \"com.x\", title: /Set/, displayId: 0, active: true, focused: false, id: 1, layer: 2 }", match.toString())
    }

}
