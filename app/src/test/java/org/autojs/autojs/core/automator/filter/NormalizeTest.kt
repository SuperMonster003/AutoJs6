package org.autojs.autojs.core.automator.filter

import org.autojs.autojs.core.accessibility.AccessibilityBridge
import org.autojs.autojs.core.accessibility.UiSelector
import org.autojs.autojs.core.automator.FakeUiNode
import org.autojs.autojs.core.automator.search.FastPath
import org.autojs.autojs.core.automator.selector.SelectorRegistry
import org.autojs.autojs.core.automator.selector.SelectorStringParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * `normalize(...)` (docs/selector/ROADMAP.md D10, P3.3, appendix C Q4 / Q18): each of the five options alone, their
 * combinations over the literal and pattern forms of `text` / `desc` / `content`, the names (`id`, `className`) taking
 * only `trim` and `caseInsensitive`, the last setting applying wherever it stands, the string form, the fast path and the
 * refusals of the registry, evaluated over [FakeUiNode].
 * zh-CN: `normalize(...)` (D10, P3.3, 附录 C Q4 / Q18): 五个选项各自的效果, 它们在 `text` / `desc` / `content` 的字面与模式形式上的组合, 名称属性
 * (`id`, `className`) 只采用 `trim` 与 `caseInsensitive`, 最后一个设置无论位置都生效, 字符串形式, 快路径与注册表的拒绝, 在 [FakeUiNode] 上求值.
 */
class NormalizeTest {

    private fun node(text: String, desc: String? = null, id: String? = null, className: String = "android.widget.TextView") = FakeUiNode.root("n") {
        this.text = text
        this.desc = desc
        this.id = id
        this.className = className
        packageName = "com.example"
    }

    private fun selector(build: UiSelector.() -> Unit = {}) = UiSelector(null as AccessibilityBridge?).apply { build() }

    private fun UiSelector.hits(node: FakeUiNode) = selector.snapshot().filter(node)

    private fun only(name: String) = mapOf(name to true)

    @Test
    fun eachOptionAlone() {
        val padded = node("  OK \t")
        assertFalse(selector { text("OK") }.hits(padded))
        assertTrue(selector { text("OK").normalize(only("trim")) }.hits(padded))

        assertTrue(selector { text("ok").normalize(only("caseInsensitive")) }.hits(node("OK")))
        assertFalse(selector { text("ok").normalize(only("trim")) }.hits(node("OK")))

        // Full-width letters, digits and the ideographic space. zh-CN: 全角字母, 数字与全角空格.
        val wide = node("\uFF21\uFF22\uFF23\u3000\uFF10")
        assertFalse(selector { text("ABC 0") }.hits(wide))
        assertTrue(selector { text("ABC 0").normalize(only("fullWidth")) }.hits(wide))

        // A space, a no-break space and a zero width space. zh-CN: 空格, 不换行空格与零宽空格.
        val spaced = node("a \u00A0\u200Bb")
        assertFalse(selector { text("a b") }.hits(spaced))
        assertTrue(selector { text("a b").normalize(only("whitespace")) }.hits(spaced))

        // The ligature fi and a circled digit. zh-CN: 连字 fi 与带圈数字.
        val compatible = node("\uFB01 \u2460")
        assertTrue(selector { text("fi 1").normalize(only("nfkc")) }.hits(compatible))
        assertFalse(selector { text("fi 1").normalize(only("fullWidth")) }.hits(compatible))
    }

    @Test
    fun allOptionsOverTheLiteralAndPatternForms() {
        val sign = node("  \uFF33\uFF49\uFF47\uFF4E\u00A0 IN ", desc = " LOG-IN ")
        assertTrue(selector { text("sign in").normalize() }.hits(sign))
        assertTrue(selector { textStartsWith("SIGN").normalize() }.hits(sign))
        assertTrue(selector { textEndsWith("n in").normalize(true) }.hits(sign))
        assertTrue(selector { textContains("gn i").normalize() }.hits(sign))
        assertFalse(selector { textContains("gn i") }.hits(sign))
        // A pattern compares the normalized value, ignoring the case instead of lowering it (appendix C Q18).
        // zh-CN: 模式比较规范化之后的值, 以忽略大小写代替转为小写 (附录 C Q18).
        assertTrue(selector { textMatch("^sign in$").normalize() }.hits(sign))
        assertTrue(selector { textMatch("^Sign IN$").normalize(only("fullWidth") + only("whitespace") + only("trim")) }.hits(sign))
        assertFalse(selector { textMatch("^sign in$").normalize(only("fullWidth") + only("whitespace") + only("trim")) }.hits(sign))
        assertTrue(selector { textMatches("sign.in").normalize() }.hits(sign))
        assertTrue(selector { desc("log-in").normalize() }.hits(sign))
        assertTrue(selector { content("log-in").normalize() }.hits(sign))
        assertTrue(selector { content("sign in").normalize() }.hits(sign))
        assertFalse(selector { content("log-in") }.hits(sign))
    }

    @Test
    fun namesTakeOnlyTrimAndTheCase() {
        val login = node("x", id = "com.example:id/Login_Button", className = "android.widget.Button")
        assertTrue(selector { id("login_button").normalize() }.hits(login))
        assertTrue(selector { id(" com.example:id/login_button ").normalize() }.hits(login))
        assertFalse(selector { id("login_button") }.hits(login))
        // No full-width form for a name. zh-CN: 名称不做全角转换.
        assertFalse(selector { id("\uFF4Cogin_button").normalize() }.hits(login))
        assertTrue(selector { className("button").normalize() }.hits(login))
        assertTrue(selector { packageName("COM.EXAMPLE").normalize() }.hits(login))
    }

    @Test
    fun theLastSettingAppliesWhereverItStands() {
        val ok = node("OK")
        assertTrue(selector { text("ok").normalize() }.hits(ok))
        assertTrue(selector { normalize().text("ok") }.hits(ok))
        assertFalse(selector { text("ok").normalize().normalize(false) }.hits(ok))
        assertTrue(selector { normalize(false).text("ok").normalize(only("caseInsensitive")) }.hits(ok))
        assertEquals("text(\"ok\").normalize()", selector { text("ok").normalize() }.toString())
        assertEquals("normalize(false)", selector { normalize(false) }.toString())
        assertEquals("normalize({trim: true, nfkc: true})", selector { normalize(only("nfkc") + only("trim")) }.toString())
        assertEquals("normalize()", selector { normalize(NormalizeOptions.NAMES.associateWith { true }) }.toString())
    }

    @Test
    fun aNormalizedIdTakesNoFastPath() {
        assertNotNull(FastPath.keyOf(selector { id("com.example:id/a") }.selector.effectiveFilters))
        assertNull(FastPath.keyOf(selector { id("com.example:id/a").normalize() }.selector.effectiveFilters))
        assertNotNull(FastPath.keyOf(selector { id("com.example:id/a").normalize(false) }.selector.effectiveFilters))
    }

    @Test
    fun theStringAndObjectFormsAndTheRefusals() {
        val ok = node("OK")
        assertTrue(SelectorStringParser().parse("text=ok normalize", selector()).hits(ok))
        assertFalse(SelectorStringParser().parse("text=ok !normalize", selector()).hits(ok))
        val lenient = selector()
        assertEquals(SelectorRegistry.Outcome.APPLIED, SelectorRegistry.apply(lenient, "normalize", listOf("true"), lenient = true))
        assertTrue(lenient.text("ok").hits(ok))
        val unknown = assertThrows(IllegalArgumentException::class.java) { selector { normalize(mapOf("trim" to true, "x" to true)) } }
        assertEquals("Unknown option of normalize: \"x\" (options: trim, caseInsensitive, fullWidth, whitespace, nfkc)", unknown.message)
        val notBoolean = assertThrows(IllegalArgumentException::class.java) { selector { normalize(mapOf("trim" to 1)) } }
        assertEquals("The options of normalize take booleans (options: trim, caseInsensitive, fullWidth, whitespace, nfkc)", notBoolean.message)
    }

}
