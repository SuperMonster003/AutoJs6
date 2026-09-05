package org.autojs.autojs.core.automator.filter

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.regex.Pattern

/**
 * The JavaScript regex literal handling behind the string matchers: literal parsing, flag mapping,
 * plain patterns, and the rendering used by the selector string form.
 * zh-CN: 字符串比较器背后的 JavaScript 正则字面量处理: 字面量解析, 标志映射, 普通模式, 以及选择器字符串形式所用的渲染.
 */
class JsRegexUtilsTest {

    @Test
    fun literalsAreSplitAtTheClosingSlashOutsideEscapesAndClasses() {
        assertEquals("a" to "i", JsRegexUtils.parseJsRegexLiteral("/a/i"))
        assertEquals("a\\/b" to "", JsRegexUtils.parseJsRegexLiteral("/a\\/b/"))
        assertEquals("[/]" to "g", JsRegexUtils.parseJsRegexLiteral("/[/]/g"))
        assertEquals("" to "", JsRegexUtils.parseJsRegexLiteral("//"))
        assertNull(JsRegexUtils.parseJsRegexLiteral("a/b"))
        assertNull(JsRegexUtils.parseJsRegexLiteral("/"))
        assertNull(JsRegexUtils.parseJsRegexLiteral("/unterminated"))
        assertNull(JsRegexUtils.parseJsRegexLiteral(""))
    }

    @Test
    fun flagsMapToPatternFlagsAndUnknownOnesAreIgnored() {
        assertEquals(0, JsRegexUtils.toJavaPatternFlags(""))
        assertEquals(Pattern.CASE_INSENSITIVE, JsRegexUtils.toJavaPatternFlags("i"))
        assertEquals(Pattern.MULTILINE, JsRegexUtils.toJavaPatternFlags("m"))
        assertEquals(Pattern.DOTALL, JsRegexUtils.toJavaPatternFlags("s"))
        assertEquals(Pattern.UNICODE_CASE or Pattern.UNICODE_CHARACTER_CLASS, JsRegexUtils.toJavaPatternFlags("u"))
        assertEquals(Pattern.CASE_INSENSITIVE or Pattern.MULTILINE, JsRegexUtils.toJavaPatternFlags("gim"))
        assertEquals(0, JsRegexUtils.toJavaPatternFlags("gy"))
    }

    @Test
    fun plainStringsAndLiteralsCompileToEquivalentRegexes() {
        assertTrue(JsRegexUtils.compileJsOrPlainRegex("/login/i").matches("LOGIN"))
        assertFalse(JsRegexUtils.compileJsOrPlainRegex("/login/").matches("LOGIN"))
        assertTrue(JsRegexUtils.compileJsOrPlainRegex("^L.*n$").matches("Login"))
        assertTrue(JsRegexUtils.compileJsOrPlainRegex("/^b$/m").containsMatchIn("a\nb"))
        assertFalse(JsRegexUtils.compileJsOrPlainRegex("/^b$/").containsMatchIn("a\nb"))
        assertTrue(JsRegexUtils.compileJsOrPlainRegex("/a.b/s").matches("a\nb"))
        // A string that only starts with a slash is a plain pattern.
        assertTrue(JsRegexUtils.compileJsOrPlainRegex("/a").matches("/a"))
    }

    @Test
    fun literalsRenderAsJavaScriptWithSlashesEscaped() {
        assertEquals("/a/i", JsRegexUtils.formatAsJsRegexLiteral("/a/i"))
        assertEquals("/a/", JsRegexUtils.formatAsJsRegexLiteral("a"))
        assertEquals("/a\\/b/", JsRegexUtils.formatAsJsRegexLiteral("a/b"))
        assertEquals("/(?:)/", JsRegexUtils.formatAsJsRegexLiteral("//"))
        assertEquals("/(?:)/g", JsRegexUtils.formatAsJsRegexLiteral("//g"))
    }

}
