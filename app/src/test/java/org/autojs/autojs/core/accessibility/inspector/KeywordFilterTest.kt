package org.autojs.autojs.core.accessibility.inspector

import org.autojs.autojs.core.automator.FakeUiNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.regex.PatternSyntaxException

/**
 * Keyword mode of the inspector search over [FakeUiNode]s: property coverage and hit order,
 * case rules of plain keywords and patterns, JavaScript regex literals, highlight ranges.
 * zh-CN: 布局分析关键词模式在 [FakeUiNode] 上的行为: 属性覆盖与命中顺序, 普通关键词与模式的大小写规则, JavaScript 正则字面量, 高亮区间.
 */
class KeywordFilterTest {

    private val button = FakeUiNode.root("button") {
        text = "Sign In"
        desc = "Submit the form"
        id = "com.example:id/btn_login"
        className = "android.widget.Button"
    }

    private val icon = FakeUiNode.root("icon") {
        className = "android.widget.ImageView"
    }

    @Test
    fun plainKeywordIsCaseInsensitiveSubstringOverAllProperties() {
        assertTrue(KeywordFilter.of("sign", regex = false).filter(button))
        assertTrue(KeywordFilter.of("FORM", regex = false).filter(button))
        assertTrue(KeywordFilter.of("btn_", regex = false).filter(button))
        assertTrue(KeywordFilter.of("widget.button", regex = false).filter(button))
        assertFalse(KeywordFilter.of("logout", regex = false).filter(button))
        assertFalse(KeywordFilter.of("sign", regex = false).filter(icon))
    }

    @Test
    fun hitReportsTheFirstPropertyInOrder() {
        assertEquals(KeywordProperty.TEXT, KeywordFilter.of("in", regex = false).hit(button))
        assertEquals(KeywordProperty.DESC, KeywordFilter.of("submit", regex = false).hit(button))
        assertEquals(KeywordProperty.ID, KeywordFilter.of("example", regex = false).hit(button))
        assertEquals(KeywordProperty.CLASS_NAME, KeywordFilter.of("widget", regex = false).hit(button))
        assertNull(KeywordFilter.of("nothing", regex = false).hit(button))
    }

    @Test
    fun nullPropertiesNeverMatch() {
        val bare = FakeUiNode.root("bare")
        assertFalse(KeywordFilter.of("x", regex = false).filter(bare))
        assertNull(KeywordFilter.of("x", regex = false).hit(bare))
        assertFalse(KeywordFilter.of("/./", regex = true).filter(bare))
    }

    @Test
    fun plainPatternIsCaseInsensitiveLikeThePlainKeyword() {
        assertTrue(KeywordFilter.of("sign\\s+in", regex = true).filter(button))
        assertTrue(KeywordFilter.of("^SUBMIT", regex = true).filter(button))
        assertFalse(KeywordFilter.of("^In", regex = true).filter(button))
    }

    @Test
    fun regexLiteralHonoursItsOwnFlags() {
        assertTrue(KeywordFilter.of("/sign in/i", regex = true).filter(button))
        assertFalse(KeywordFilter.of("/sign in/", regex = true).filter(button))
        assertTrue(KeywordFilter.of("/btn_(login|logout)/", regex = true).filter(button))
    }

    @Test(expected = PatternSyntaxException::class)
    fun malformedPatternFailsAtConstruction() {
        KeywordFilter.of("(", regex = true)
    }

    @Test
    fun firstMatchRangeLocatesTheHighlight() {
        assertEquals(5..6, KeywordFilter.of("IN", regex = false).firstMatchRange("Sign In"))
        assertEquals(0..3, KeywordFilter.of("/^s\\w+/i", regex = true).firstMatchRange("Sign In"))
        assertNull(KeywordFilter.of("out", regex = false).firstMatchRange("Sign In"))
    }

    @Test
    fun labelShowsTheKeywordForTheExplainer() {
        assertEquals("keyword(\"sign\")", KeywordFilter.of("sign", regex = false).toString())
        assertEquals("keyword(/sign/i)", KeywordFilter.of("/sign/i", regex = true).toString())
    }

}
