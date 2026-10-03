package org.autojs.autojs.core.automator.filter

import org.autojs.autojs.core.accessibility.AccessibilityBridge
import org.autojs.autojs.core.accessibility.UiSelector
import org.autojs.autojs.core.accessibility.inspector.SelectorCallParser
import org.autojs.autojs.core.automator.FakeUiNode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import org.mozilla.javascript.CompilerEnvirons
import org.mozilla.javascript.Context
import org.mozilla.javascript.Parser

class JsLiteralTest {

    private val values = listOf(
        "",
        "a\"b\\c",
        "first\nsecond\r\n\tthird",
        "before\u2028middle\u2029after",
        "登录 😀",
        "\"); throw new Error(\"unexpected\"); //",
        (0..31).map(Int::toChar).joinToString("") + (127..159).map(Int::toChar).joinToString(""),
    )

    private fun selector() = UiSelector(null as AccessibilityBridge?)

    @Test
    fun selectorStringsParseAndMatchTheOriginalValue() {
        for (value in values) {
            val root = FakeUiNode.root { text = "screen root" }
            val target = root.child("target") { text = value }
            root.child("other") { text = "unrelated" }
            val expression = selector().text(value).toString()
            val parsed = SelectorCallParser().parse(expression, selector())
            assertEquals(listOf(target), parsed.searchIn(root))
            assertEquals(expression, parsed.toString())
            Parser(CompilerEnvirons().apply { languageVersion = Context.VERSION_ES6 }).parse(expression + ";", "selector", 1)
        }
    }

    @Test
    fun literalsRetainTheirExactJavaScriptValue() {
        val context = Context.enter()
        try {
            context.languageVersion = Context.VERSION_ES6
            context.isInterpretedMode = true
            val scope = context.initStandardObjects()
            for (value in values) {
                val literal = JsLiteral.quote(value)
                assertEquals(value, Context.toString(context.evaluateString(scope, literal, "literal", 1, null)))
                assertFalse(literal.any { it.isISOControl() || it == '\u2028' || it == '\u2029' })
            }
        } finally {
            Context.exit()
        }
    }

    @Test
    fun plainStringMatchersUseTheSameLiteralEncoding() {
        val value = "a\"b\\c\n"
        val cases = listOf(
            selector().text(value) to listOf("exact"),
            selector().textContains(value) to listOf("exact", "surrounded", "ends", "starts"),
            selector().textStartsWith(value) to listOf("exact", "starts"),
            selector().textEndsWith(value) to listOf("exact", "ends"),
        )
        for ((original, expected) in cases) {
            val root = FakeUiNode.root {
                child("exact") { text = value }
                child("surrounded") { text = "before" + value + "after" }
                child("ends") { text = "before" + value }
                child("starts") { text = value + "after" }
            }
            assertEquals(expected, SelectorCallParser().parse(original.toString(), selector()).searchIn(root).map { it.name })
        }
    }

    @Test
    @Suppress("DEPRECATION")
    fun regexStringArgumentsRetainTheirBackslashesAfterRendering() {
        for (original in listOf(selector().textMatches("a\\.b"), selector().textMatch("a\\.b"))) {
            val root = FakeUiNode.root {
                child("literal-dot") { text = "a.b" }
                child("wildcard") { text = "axb" }
            }
            val parsed = SelectorCallParser().parse(original.toString(), selector())
            assertEquals(listOf(root.find("literal-dot")), parsed.searchIn(root))
        }
    }
}
