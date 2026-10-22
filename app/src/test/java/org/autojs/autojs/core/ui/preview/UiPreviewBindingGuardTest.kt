package org.autojs.autojs.core.ui.preview

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mozilla.javascript.Context
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.TopLevel

class UiPreviewBindingGuardTest {

    private val constants = mapOf<String, Any?>("idx" to 1.0, "key" to "label", "evil" to "constructor", "list" to listOf(1))

    private fun allows(expression: String) = UiPreviewBindingGuard.allows(expression) { constants[it] }

    @Test
    fun valuesMayBeCombined() {
        listOf(
            "a",
            "a.b.c",
            "x.toFixed(2)",
            "c ? 'a' : 'b'",
            "items[0]",
            "tabs[idx]",
            "names[key]",
            "names['title']",
            "-size * 2 + 1",
            "!flag && other || 'none'",
            "typeof value",
            "`#\${color}`",
            "[1, 'two', ...rest]",
            "({ text: title, [key]: 1, ...style })",
            "/a+/.test(name)",
            "",
            "a;",
        ).forEach { assertTrue(it, allows(it)) }
    }

    @Test
    fun anythingThatCouldRunCodeIsRejected() {
        listOf(
            "''.constructor.constructor('return this')()",
            "a.prototype",
            "a.__parent__",
            "a.__proto__",
            "a.__lookupGetter__('x')",
            "(function () { return 1 })()",
            "(() => 1)()",
            "while (1);",
            "a = 1",
            "a += 1",
            "a++",
            "new Date()",
            "delete a.b",
            "a[evil]",
            "a['constructor']",
            "a[list]",
            "a[b]",
            "a[1 + 1]",
            "x.Object",
            "x.Function('1')",
            "x.getClass()",
            "Reflect.getPrototypeOf(a)",
            "({ get x() { return 1 } })",
            "({ m() {} })",
            "a; b = 1",
            "<a/>",
            "a..b",
            "a.@b",
            "a(",
            "var a = 1",
        ).forEach { assertFalse(it, allows(it)) }
    }

    @Test
    fun theSandboxConvertsAgainstItself() {
        val cx = Context.enter()
        try {
            cx.languageVersion = Context.VERSION_ES6
            cx.isInterpretedMode = true
            val scope = cx.initSafeStandardObjects(TopLevel(), true)
            val values = cx.evaluateString(scope, "({ title: 'T' })", "values", 1, null) as Scriptable
            val sandbox = UiPreviewBindingSandbox(values, scope)
            // The host converts the binding context against itself before every attribute (UI.bindValueForApplyingAttribute).
            assertSame(sandbox, Context.toObject(sandbox, sandbox))
            assertEquals("T", sandbox.get("title", sandbox))
        } finally {
            Context.exit()
        }
    }
}
