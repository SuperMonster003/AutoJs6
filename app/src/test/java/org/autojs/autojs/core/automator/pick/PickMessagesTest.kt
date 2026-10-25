package org.autojs.autojs.core.automator.pick

import org.autojs.autojs.app.GlobalAppContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * docs/selector/ROADMAP.md P1.1, appendix C Q5 (2): the messages of `pickup` / `detect` come from five resources of
 * their own and the reasons from existing ones. Without an Android context the English fallbacks of [PickMessages] are
 * used, and they must stay equal to the default `values/strings.xml`; the five new resources exist in all 10 languages
 * with the same placeholders and ASCII punctuation only.
 *
 * zh-CN: P1.1, 附录 C Q5 (2): `pickup` / `detect` 的消息来自五条自有资源, 原因文本来自已有资源. 没有 Android Context 时使用 [PickMessages] 的英文后备文本,
 * 须与默认 `values/strings.xml` 一致; 五条新资源在 10 种语言中都存在, 占位符相同, 只用 ASCII 标点.
 */
class PickMessagesTest {

    private val res = File("src/main/res")

    @Test
    fun fallbacksAreUsedWithoutAnApplicationContext() = withoutApplicationContext {
        assertEquals("pickup accepts 0 to 5 arguments, got 6", PickMessages.argumentCount("pickup", 0, 5, 6))
        assertEquals("detect: argument \"w\" must be of type UiObject, got number", PickMessages.argumentType("detect", "w", "UiObject", 1.0))
        assertEquals("pickup: argument \"compass\" cannot be \"c\" (Invalid rest compass: c)", PickMessages.argumentValue("pickup", "compass", "c", PickMessages.invalidRestCompass("c")))
        assertEquals("pickup: argument \"resultType\": [\"txet\", 1] is neither a result type nor a method of UiObject, candidates: text, #", PickMessages.unknownResultType("pickup", "resultType", listOf("txet", 1.0), listOf("text", "#")))
        assertEquals("pickup: argument \"selector\": \"txet\" is not a condition of UiSelector, candidates: text", PickMessages.unknownCondition("pickup", "selector", "txet", listOf("text")))
        assertEquals("Result type \"[#]\" is not supported for UiObject", PickMessages.notForOneNode(listOf("#")))
        assertEquals("Cannot use a compass when result type is selector", PickMessages.selectorWithCompass())
        assertEquals("Unknown algorithm selector param: x", PickMessages.unknownAlgorithm("x"))
        assertEquals("Invalid compass: x", PickMessages.invalidCompass("x"))
        assertEquals("Selector method should be called rather than passed as a parameter: text", PickMessages.methodNotCalled("text"))
        assertEquals("null undefined string number boolean", listOf(null, org.mozilla.javascript.Undefined.instance, "a", 1, true).joinToString(" ") { PickMessages.describe(it) })
        assertEquals("1.5 NaN \"a\" [1, \"b\"]", listOf(1.5, Double.NaN, "a", listOf(1.0, "b")).joinToString(" ") { PickMessages.show(it) })
    }

    @Test
    fun fallbacksMatchTheDefaultResources() {
        val default = resources(File(res, "values/strings.xml"))
        val calls = RESOURCE_CALL.findAll(File(SOURCE).readText()).map { it.groupValues[1] to unescapeKotlin(it.groupValues[2]) }.toList()
        assertEquals(calls.toString(), 11, calls.size)
        val wrong = calls.filter { (name, fallback) -> default[name] != fallback }
        assertEquals(emptyList<Pair<String, String>>(), wrong)
        assertTrue(NEW.all { name -> calls.any { it.first == name } })
    }

    @Test
    fun newMessagesExistInEveryLanguage() {
        val default = resources(File(res, "values/strings.xml"))
        val problems = LANGUAGES.flatMap { dir ->
            val strings = resources(File(res, "$dir/strings.xml"))
            NEW.mapNotNull { name ->
                val text = strings[name] ?: return@mapNotNull "$dir: $name is missing"
                when {
                    placeholders(text) != placeholders(default.getValue(name)) -> "$dir: $name has placeholders ${placeholders(text)}"
                    text.any { it in FULL_WIDTH_PUNCTUATION } -> "$dir: $name uses full-width punctuation"
                    dir == "values-en" && text != default.getValue(name) -> "$dir: $name differs from the default resource"
                    else -> null
                }
            }
        }
        assertTrue(problems.joinToString(System.lineSeparator()), problems.isEmpty())
    }

    private fun withoutApplicationContext(block: () -> Unit) {
        val field = GlobalAppContext::class.java.getDeclaredField("sApplicationContext").apply { isAccessible = true }
        val previous = field.get(null)
        field.set(null, null)
        try {
            block()
        } finally {
            field.set(null, previous)
        }
    }

    /** The value of a Kotlin string literal's body: a backslash escapes the next character. */
    private fun unescapeKotlin(body: String): String = Regex("""\\(.)""").replace(body) { it.groupValues[1] }

    private fun placeholders(text: String) = Regex("%(\\d+\\$)?[sd]").findAll(text).map { it.value }.toList().sorted()

    /** `<string name="x">...</string>` entries of one file, with the Android escapes resolved. */
    private fun resources(file: File): Map<String, String> =
        Regex("<string name=\"([^\"]+)\"[^>]*>(.*?)</string>", RegexOption.DOT_MATCHES_ALL).findAll(file.readText()).associate { it.groupValues[1] to unescape(it.groupValues[2]) }

    private fun unescape(raw: String): String {
        val text = raw.trim().removePrefix("<![CDATA[").removeSuffix("]]>")
        return buildString {
            var i = 0
            while (i < text.length) {
                val c = text[i]
                if (c == '\\' && i + 1 < text.length) {
                    append(text[i + 1])
                    i += 2
                } else {
                    append(c)
                    i++
                }
            }
        }
    }

    private companion object {

        const val SOURCE = "src/main/java/org/autojs/autojs/core/automator/pick/PickMessages.kt"

        /** The five resources of Q5 (2). zh-CN: Q5 (2) 的五条资源. */
        val NEW = listOf(
            "error_pick_argument_count", "error_pick_argument_type", "error_pick_argument_value", "error_pick_unknown_condition", "error_pick_unknown_result_type",
        )

        val LANGUAGES = listOf("values", "values-en", "values-ar", "values-es", "values-fr", "values-ja", "values-ko", "values-ru", "values-zh", "values-zh-rHK", "values-zh-rTW")

        /** `resource(R.string.<name>, "<fallback>"`: the resource name and the fallback literal. */
        val RESOURCE_CALL = Regex("""resource\(\s*R\.string\.(\w+),\s*"((?:[^"\\]|\\.)*)"""")

        /** Full-width comma, full stop, colon, semicolon, marks, brackets, quotes, enumeration comma and the Arabic comma. */
        val FULL_WIDTH_PUNCTUATION = listOf(0xFF0C, 0x3002, 0xFF1A, 0xFF1B, 0xFF01, 0xFF1F, 0xFF08, 0xFF09, 0x201C, 0x201D, 0x2018, 0x2019, 0x3001, 0x060C).map { it.toChar() }.toSet()
    }

}
