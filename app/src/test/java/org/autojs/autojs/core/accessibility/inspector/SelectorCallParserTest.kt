package org.autojs.autojs.core.accessibility.inspector

import org.autojs.autojs.core.accessibility.AccessibilityBridge
import org.autojs.autojs.core.accessibility.UiSelector
import org.autojs.autojs.core.automator.FakeUiNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * [SelectorCallParser]: chained calls with string / number / boolean / regex literals land on the
 * registry, the `selector()` / `$()` prefix is optional, regex literals map to the `Match` /
 * `Matches` string overloads, errors carry position / term / candidates, and an expression that
 * stops mid-call is reported as incomplete rather than wrong (roadmap P5.1, D12).
 * zh-CN: [SelectorCallParser]: 带字符串 / 数字 / 布尔 / 正则字面量的链式调用落到注册表, `selector()` / `$()` 前缀可选, 正则字面量映射到
 * `Match` / `Matches` 字符串重载, 错误携带位置 / 条件 / 候选, 停在调用中途的表达式报告为未结束而非错误 (Roadmap P5.1, D12).
 */
class SelectorCallParserTest {

    private val parser = SelectorCallParser()

    private fun screen() = FakeUiNode.root("screen") {
        packageName = "com.example"
        child("login") {
            text = "Login"
            clickable = true
            enabled = true
            id = "com.example:id/login"
            className = "android.widget.Button"
            bounds(0, 0, 100, 100)
        }
        child("logout") {
            text = "Logout"
            clickable = true
            enabled = false
            bounds(0, 100, 100, 200)
        }
        child("blog") {
            text = "Blog"
            desc = "Say \"hi\""
        }
        child("signIn") {
            text = "Sign in"
            child("deep") {
                text = "Deep"
                clickable = true
            }
        }
    }

    private fun select(expression: String): UiSelector = parser.parse(expression, UiSelector(null as AccessibilityBridge?))

    private fun names(expression: String): List<String> = select(expression).searchIn(screen()).map { it.name }

    private fun rendered(expression: String): String = select(expression).toString()

    private fun error(expression: String): SelectorCallException = assertThrows(SelectorCallException::class.java) { select(expression) }

    @Test
    fun chainedCallsApplyInOrder() {
        assertEquals(listOf("login"), names("text(\"Login\").clickable().depth(1)"))
        assertEquals("text(\"Login\").clickable().depth(1)", rendered("text(\"Login\").clickable().depth(1)"))
        assertEquals(listOf("login", "logout", "deep"), names("clickable(true)"))
        assertEquals(listOf("logout"), names("enabled(false)"))
        assertEquals("bounds(0, 0, 100, 100)", rendered("bounds(0, 0, 100, 100)"))
        assertEquals(listOf("deep"), names("minDepth(2)"))
    }

    @Test
    fun prefixAndWhitespaceAreOptional() {
        assertEquals(listOf("login"), names("selector().text(\"Login\")"))
        assertEquals(listOf("login"), names("$().text(\"Login\")"))
        assertEquals(listOf("login"), names("  text( 'Login' ) . clickable( )  "))
        assertEquals(0, select("").selector.filters.size)
        assertEquals(0, select("selector()").selector.filters.size)
        assertEquals(0, select("$()").selector.filters.size)
    }

    @Test
    fun stringLiteralsSupportBothQuotesAndEscapes() {
        assertEquals(listOf("blog"), names("desc(\"Say \\\"hi\\\"\")"))
        assertEquals(listOf("blog"), names("desc('Say \"hi\"')"))
        assertEquals(listOf("signIn"), names("text(\"Sign\\u0020in\")"))
        assertEquals(listOf("signIn"), names("textContains('n i')"))
    }

    @Test
    fun regexLiteralsMapToTheMatchOverloads() {
        assertEquals(listOf("login", "logout"), names("textMatch(/^lo/i)"))
        assertEquals("textMatch(\"/^lo/i\")", rendered("textMatch(/^lo/i)"))
        assertEquals(listOf("login", "logout"), names("textMatches(/log.*/i)"))
        assertEquals(listOf("login", "logout", "blog"), names("textContains(/o/)"))
        assertEquals("textMatch(\"/o/\")", rendered("textContains(/o/)"))
        assertEquals(listOf("login"), names("text(/login/i)"))
        assertEquals("textMatches(\"/login/i\")", rendered("text(/login/i)"))
        assertEquals(listOf("login"), names("idMatch(/\\/login$/)"))
        assertEquals(listOf("login"), names("className(/android[.]widget[.]Button/)"))
        assertEquals(listOf("login"), names("classNameMatch(/[.]Button$/)"))
    }

    @Test
    fun regexLiteralsAreRejectedWhereAScriptWouldNotTakeARegExp() {
        val startsWith = error("textStartsWith(/x/)")
        assertEquals(0, startsWith.position)
        assertTrue(startsWith.detail, "regular expression literal does not fit" in startsWith.detail)
        assertTrue("depth" in error("depth(/3/)").detail)
        assertTrue(error("text(\"a\", /b/)").detail.contains("does not fit"))
        assertFalse(startsWith.incomplete)
    }

    @Test
    fun unknownMethodsReportPositionTermAndCandidates() {
        val e = error("text(\"Login\").clikable()")
        assertEquals(14, e.position)
        assertEquals("clikable", e.term)
        assertTrue(e.candidates.toString(), "clickable" in e.candidates)
        assertTrue(e.detail, "unknown selector method" in e.detail)
        assertFalse(e.incomplete)
        assertEquals(0, error("clikable(").position)
    }

    @Test
    fun argumentMismatchesNameTheCallAndTheSignatures() {
        val e = error("depth(\"x\")")
        assertEquals(0, e.position)
        assertEquals("depth(\"x\")", e.term)
        assertTrue(e.detail, "do not fit \"depth\"" in e.detail && "depth(" in e.detail)
        assertTrue(error("text(1, 2)").detail.contains("do not fit"))
        assertTrue(error("clickable(\"yes\")").detail.contains("do not fit"))
    }

    @Test
    fun malformedExpressionsPointAtTheOffendingCharacter() {
        assertEquals(10, error("text(\"a\") @").position)
        assertEquals(9, error("text(\"a\")clickable()").position)
        assertTrue("expected \".\"" in error("text(\"a\")clickable()").detail)
        assertEquals(5, error("text(clickable)").position)
        assertEquals(4, error("text\"a\"").position)
        assertEquals(0, error(".text(\"a\")").position)
        assertTrue(error("textMatch(/(/)").detail.isNotEmpty())
        assertFalse(error("textMatch(/(/)").incomplete)
    }

    @Test
    fun unfinishedExpressionsAreIncompleteNotWrong() {
        for (expression in listOf("t", "text", "text(", "text(\"Lo", "text(\"Login\"", "text(\"Login\").", "text(\"Login\").cli", "textMatch(/ab", "text(\"a\",", "text(\"a\", ")) {
            val e = error(expression)
            assertTrue(expression, e.incomplete)
            assertEquals(expression, "expression not finished", e.detail)
        }
        assertFalse(error("clikable()").incomplete)
        assertEquals(listOf("login"), names("text(\"Login\")"))
    }

    @Test
    fun callsExposeNamesArgumentsAndPositions() {
        val calls = parser.calls("selector().text(\"OK\").minDepth(2).textMatch(/x/i)")
        assertEquals(listOf("text", "minDepth", "textMatch"), calls.map { it.name })
        assertEquals(listOf(11, 22, 34), calls.map { it.position })
        assertEquals(listOf("OK"), calls[0].args)
        assertEquals(listOf(2), calls[1].args)
        assertEquals("/x/i", calls[2].args[0].toString())
        assertEquals("textMatch(/x/i)", calls[2].text)
        assertEquals(listOf(1.5), parser.calls("minWidth(1.5)")[0].args)
    }

}
