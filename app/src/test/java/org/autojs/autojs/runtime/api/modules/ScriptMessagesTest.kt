package org.autojs.autojs.runtime.api.modules

import org.autojs.autojs.app.GlobalAppContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Roadmap P1.2 / T5: the messages of the script module layer come from the string resources; without an Android
 * context (plain JVM) the English fallbacks are used, and they must stay equal to the default resources. Every
 * message resource exists in all 10 languages with the same placeholders and ASCII punctuation only.
 */
class ScriptMessagesTest {

    private val res = File("src/main/res")

    @Test
    fun fallbacksAreUsedWithoutAnApplicationContext() = withoutApplicationContext {
        assertEquals("Failed to call method \"base64.encode\": [ x ]", ScriptMessages.failedToCall("base64.encode", "x"))
        assertEquals("Method only accepts 1 argument", ScriptMessages.arity(1, 1))
        assertEquals("Method only accepts 2 arguments", ScriptMessages.arity(2, 2))
        assertEquals("Method only accepts no less than 1 argument", ScriptMessages.arity(1, -1))
        assertEquals("Method only accepts a number of arguments in the range [1..2]", ScriptMessages.arity(1, 2))
        assertEquals("Argument \"o\" must be of type string, got Array", ScriptMessages.argumentType("o", "string", "Array"))
        assertEquals("Argument \"o\" cannot be undefined", ScriptMessages.argumentCannotBe("o", "undefined"))
        assertEquals("Argument \"n\" must be a 32-bit integer, got 1.5", ScriptMessages.argumentInteger("n", 32, "1.5"))
        assertEquals("No overload accepts the arguments (boolean). Candidates: f(a: kotlin.String)", ScriptMessages.noOverload("boolean", "f(a: kotlin.String)"))
        assertEquals("Unknown charset \"x\"", ScriptMessages.unknownCharset("x"))
    }

    @Test
    fun fallbacksMatchTheDefaultResources() {
        val strings = resources(File(res, "values/strings.xml"))
        val plurals = plurals(File(res, "values/plurals.xml"))
        val expected = mapOf(
            strings.getValue("error_failed_to_call_method_with_cause") to ScriptMessages.FAILED_TO_CALL,
            strings.getValue("error_method_only_accepts_a_number_of_arguments_in_the_range_n_to_m") to ScriptMessages.IN_RANGE,
            strings.getValue("error_script_argument_must_be_of_type") to ScriptMessages.ARGUMENT_TYPE,
            strings.getValue("error_script_argument_cannot_be") to ScriptMessages.ARGUMENT_CANNOT_BE,
            strings.getValue("error_script_argument_must_be_integer") to ScriptMessages.ARGUMENT_INTEGER,
            strings.getValue("error_script_no_overload_accepts_arguments") to ScriptMessages.NO_OVERLOAD,
            strings.getValue("error_unknown_charset") to ScriptMessages.UNKNOWN_CHARSET,
            plurals.getValue("error_method_only_accepts_n_arguments/one") to ScriptMessages.EXACTLY_ONE,
            plurals.getValue("error_method_only_accepts_n_arguments/other") to ScriptMessages.EXACTLY_OTHER,
            plurals.getValue("error_method_only_accepts_no_less_than_n_arguments/one") to ScriptMessages.NO_LESS_THAN_ONE,
            plurals.getValue("error_method_only_accepts_no_less_than_n_arguments/other") to ScriptMessages.NO_LESS_THAN_OTHER,
        )
        expected.forEach { (resource, fallback) -> assertEquals(resource, fallback) }
    }

    @Test
    fun newMessagesExistInEveryLanguage() {
        val names = listOf(
            "error_script_argument_cannot_be",
            "error_script_argument_must_be_integer",
            "error_script_argument_must_be_of_type",
            "error_script_no_overload_accepts_arguments",
            "error_unknown_charset",
        )
        val default = resources(File(res, "values/strings.xml"))
        val languages = listOf("values", "values-en", "values-ar", "values-es", "values-fr", "values-ja", "values-ko", "values-ru", "values-zh", "values-zh-rHK", "values-zh-rTW")
        val problems = languages.flatMap { dir ->
            val strings = resources(File(res, "$dir/strings.xml"))
            names.mapNotNull { name ->
                val text = strings[name] ?: return@mapNotNull "$dir: $name is missing"
                when {
                    placeholders(text) != placeholders(default.getValue(name)) -> "$dir: $name has placeholders ${placeholders(text)}"
                    text.any { it in FULL_WIDTH_PUNCTUATION } -> "$dir: $name uses full-width punctuation"
                    dir == "values-en" && text != default.getValue(name) -> "$dir: $name differs from the default resource"
                    else -> null
                }
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
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

    private fun placeholders(text: String) = Regex("%(\\d+\\$)?[sd]").findAll(text).map { it.value }.toList().sorted()

    /** `<string name="x">...</string>` entries of one file, with the Android escapes resolved. */
    private fun resources(file: File): Map<String, String> =
        Regex("<string name=\"([^\"]+)\"[^>]*>(.*?)</string>", RegexOption.DOT_MATCHES_ALL).findAll(file.readText()).associate { it.groupValues[1] to unescape(it.groupValues[2]) }

    private fun plurals(file: File): Map<String, String> =
        Regex("<plurals name=\"([^\"]+)\">(.*?)</plurals>", RegexOption.DOT_MATCHES_ALL).findAll(file.readText()).flatMap { plural ->
            Regex("<item quantity=\"([^\"]+)\">(.*?)</item>").findAll(plural.groupValues[2]).map { "${plural.groupValues[1]}/${it.groupValues[1]}" to unescape(it.groupValues[2]) }
        }.toMap()

    private fun unescape(raw: String): String {
        val text = raw.trim().removePrefix("<![CDATA[").removeSuffix("]]>")
        return buildString {
            var i = 0
            while (i < text.length) {
                val c = text[i]
                if (c == '\\' && i + 1 < text.length) {
                    append(if (text[i + 1] == 'n') '\n' else text[i + 1])
                    i += 2
                } else {
                    append(c)
                    i++
                }
            }
        }
    }

    companion object {
        private const val FULL_WIDTH_PUNCTUATION = "\uFF0C\u3002\uFF1A\uFF1B\uFF01\uFF1F\uFF08\uFF09\u201C\u201D\u2018\u2019\u3001\u060C"
    }

}
