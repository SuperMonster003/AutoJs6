package org.autojs.autojs.runtime.api.modules

import org.autojs.autojs.app.GlobalAppContext
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale
import java.util.MissingFormatArgumentException

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
        assertEquals("Argument \"cycle\" must be a positive number, got 0", ScriptMessages.argumentPositive("cycle", "0"))
        assertEquals("Argument \"range\" contains no valid number", ScriptMessages.argumentNoNumber("range"))
        assertEquals("Argument \"pointA\" must have a length of 2, got 3", ScriptMessages.argumentLength("pointA", 2, 3))
        assertEquals("No integer lies within the range [1.2, 1.8]", ScriptMessages.rangeNoInteger("1.2", "1.8"))
        assertEquals("Unknown built-in object extension \"x\", expected one of Arrayx", ScriptMessages.unknownBuiltInExtension("x", "Arrayx"))
        assertEquals("An operation is not implemented", ScriptMessages.notImplemented())
        assertEquals("UI thread cannot be blocked. Try running sleep() in sub-thread or sub-script", ScriptMessages.resource(org.autojs.autojs6.R.string.error_function_called_in_ui_thread, "UI thread cannot be blocked. Try running %s() in sub-thread or sub-script", "sleep"))
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
            strings.getValue("error_script_argument_must_be_positive") to ScriptMessages.ARGUMENT_POSITIVE,
            strings.getValue("error_script_argument_has_no_valid_number") to ScriptMessages.ARGUMENT_NO_NUMBER,
            strings.getValue("error_script_argument_must_have_length") to ScriptMessages.ARGUMENT_LENGTH,
            strings.getValue("error_script_range_has_no_integer") to ScriptMessages.RANGE_NO_INTEGER,
            strings.getValue("error_script_unknown_built_in_extension") to ScriptMessages.UNKNOWN_BUILT_IN_EXTENSION,
            strings.getValue("error_an_operation_is_not_implemented") to ScriptMessages.NOT_IMPLEMENTED,
            plurals.getValue("error_method_only_accepts_n_arguments/one") to ScriptMessages.EXACTLY_ONE,
            plurals.getValue("error_method_only_accepts_n_arguments/other") to ScriptMessages.EXACTLY_OTHER,
            plurals.getValue("error_method_only_accepts_no_less_than_n_arguments/one") to ScriptMessages.NO_LESS_THAN_ONE,
            plurals.getValue("error_method_only_accepts_no_less_than_n_arguments/other") to ScriptMessages.NO_LESS_THAN_OTHER,
        )
        expected.forEach { (resource, fallback) -> assertEquals(resource, fallback) }
    }

    @Test
    fun newMessagesExistInEveryLanguage() = checkEveryLanguage(
        *MESSAGE_STRINGS.filter { it.startsWith("error_script_") || it == "error_unknown_charset" || it == "error_an_operation_is_not_implemented" }.toTypedArray()
    )

    /**
     * P3.0: every `ScriptMessages.resource(R.string.name, "fallback", ...)` in the sources of the script module layer
     * names a resource whose default text is the fallback literal, present in every language with the same placeholders
     * and ASCII punctuation (R4).
     */
    @Test
    fun resourceFallbacksInModulesMatchTheDefaultResources() {
        val default = resources(File(res, "values/strings.xml"))
        // ScriptMessages.kt itself only shows the form in its documentation.
        val calls = File("src/main/java/org/autojs/autojs/runtime/api/modules").walkTopDown().filter { it.isFile && it.extension == "kt" && it.name != "ScriptMessages.kt" }.flatMap { file ->
            RESOURCE_CALL.findAll(file.readText()).map { Triple(file.name, it.groupValues[1], unescapeKotlin(it.groupValues[2])) }
        }.toList()
        val problems = calls.mapNotNull { (file, name, fallback) ->
            when (val text = default[name]) {
                null -> "$file: R.string.$name does not exist"
                fallback -> null
                else -> "$file: the fallback of R.string.$name differs from the default resource \"$text\""
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
        checkEveryLanguage(*calls.map { it.second }.distinct().toTypedArray())
    }

    /** P1.3: the strings of the developer options entries follow the same rules. */
    @Test
    fun developerOptionStringsExistInEveryLanguage() = checkEveryLanguage(
        "summary_new_script_module_layer",
        "summary_script_module_debug_print",
        "summary_script_module_debug_print_modules",
        "text_new_script_module_layer",
        "text_script_module_debug_print",
        "text_script_module_debug_print_modules",
        "text_script_modules",
    )

    /**
     * P1.4: [ScriptMessages.format] (no java.util.Formatter on the error path) gives what `Resources.getString(id, args)`
     * gives, that is `String.format` in the resource locale, for every message of the layer in every language,
     * including localized digits; other specifiers and missing arguments go to `String.format` itself.
     */
    @Test
    fun formatMatchesStringFormatInEveryLanguage() {
        val languages = mapOf(
            "values" to Locale.ENGLISH, "values-en" to Locale.ENGLISH, "values-ar" to Locale.forLanguageTag("ar"), "values-es" to Locale.forLanguageTag("es"),
            "values-fr" to Locale.FRENCH, "values-ja" to Locale.JAPANESE, "values-ko" to Locale.KOREAN, "values-ru" to Locale.forLanguageTag("ru"),
            "values-zh" to Locale.SIMPLIFIED_CHINESE, "values-zh-rHK" to Locale.forLanguageTag("zh-HK"), "values-zh-rTW" to Locale.TRADITIONAL_CHINESE,
        )
        val problems = mutableListOf<String>()
        var checked = 0
        languages.forEach { (dir, locale) ->
            val strings = resources(File(res, "$dir/strings.xml"))
            val plurals = if (File(res, "$dir/plurals.xml").exists()) plurals(File(res, "$dir/plurals.xml")) else emptyMap()
            val patterns = MESSAGE_STRINGS.mapNotNull { strings[it] } + plurals.filterKeys { key -> MESSAGE_PLURALS.any { key.startsWith("$it/") } }.values
            patterns.forEach { pattern ->
                listOf(arrayOf<Any?>("base64.encode", 2, "x"), arrayOf<Any?>("f", 1234567, "-"), arrayOf<Any?>(-12, -3, 0)).forEach { sample ->
                    val args = argumentsFor(pattern, sample)
                    checked++
                    val expected = String.format(locale, pattern, *args)
                    val actual = ScriptMessages.format(locale, ScriptMessages.zeroDigit(locale), pattern, args)
                    if (actual != expected) problems += "$dir: \"$pattern\" with ${args.toList()}: expected \"$expected\", got \"$actual\""
                }
            }
        }
        assertTrue(problems.joinToString("\n"), problems.isEmpty())
        assertTrue("only $checked messages were checked", checked >= 200)
        // Digits of locales whose zero is not '0', and the specifiers and cases handed to String.format.
        listOf(Locale.forLanguageTag("ar-EG"), Locale.forLanguageTag("th-TH-u-nu-thai"), Locale.forLanguageTag("hi-IN-u-nu-deva"), Locale.ROOT).forEach { locale ->
            listOf(
                "%1\$d..%2\$d" to arrayOf<Any?>(-7, 1234567890123L),
                "%d %s %%" to arrayOf<Any?>(42, null),
                "%5d|%-4s|%x|%n|%S" to arrayOf<Any?>(7, "a", 255, "s"),
                "%s %s" to arrayOf<Any?>(1.5, true),
                "%d" to arrayOf<Any?>(1.5),
            ).forEach { (pattern, args) ->
                val expected = runCatching { String.format(locale, pattern, *args) }
                val actual = runCatching { ScriptMessages.format(locale, ScriptMessages.zeroDigit(locale), pattern, args) }
                assertEquals("$locale \"$pattern\"", expected.getOrNull() ?: expected.exceptionOrNull()?.javaClass, actual.getOrNull() ?: actual.exceptionOrNull()?.javaClass)
            }
        }
        assertEquals("missing arguments fail like String.format", MissingFormatArgumentException::class.java, runCatching { ScriptMessages.format(Locale.ROOT, '0', "%2\$s", arrayOf("a")) }.exceptionOrNull()?.javaClass)
    }

    /** One argument per placeholder of [pattern]: strings for `%s`, integers for `%d`, picked from [sample] by position. */
    private fun argumentsFor(pattern: String, sample: Array<Any?>): Array<Any?> {
        val specifiers = Regex("%(?:(\\d+)\\$)?([sd])").findAll(pattern).toList()
        var sequential = 0
        val kinds = sortedMapOf<Int, Char>()
        specifiers.forEach { m -> kinds[m.groupValues[1].toIntOrNull()?.minus(1) ?: sequential++] = m.groupValues[2][0] }
        return Array((kinds.keys.maxOrNull() ?: -1) + 1) { i ->
            val value = sample[i % sample.size]
            if (kinds[i] == 'd') (value as? Int) ?: (i + 2) else value?.toString() ?: "null"
        }
    }

    private fun checkEveryLanguage(vararg names: String) {
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

    /** The value of a Kotlin string literal's body: a backslash escapes the next character (n and t are line feed and tab). */
    private fun unescapeKotlin(body: String): String = Regex("""\\(.)""").replace(body) { m ->
        when (val c = m.groupValues[1]) {
            "n" -> 10.toChar().toString()
            "t" -> 9.toChar().toString()
            else -> c
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

        /** The resources [ScriptMessages] formats. */
        private val MESSAGE_STRINGS = listOf(
            "error_failed_to_call_method_with_cause",
            "error_method_only_accepts_a_number_of_arguments_in_the_range_n_to_m",
            "error_script_argument_cannot_be",
            "error_script_argument_must_be_integer",
            "error_script_argument_must_be_of_type",
            "error_script_no_overload_accepts_arguments",
            "error_unknown_charset",
            // P2.1 (Arrayx / Mathx / Numberx / jsox).
            "error_script_argument_has_no_valid_number",
            "error_script_argument_must_be_positive",
            "error_script_argument_must_have_length",
            "error_script_range_has_no_integer",
            "error_script_unknown_built_in_extension",
            // P3.0 (TODO of global).
            "error_an_operation_is_not_implemented",
        )

        /** `ScriptMessages.resource(R.string.<name>, "<fallback>"`: the resource name and the fallback literal. */
        private val RESOURCE_CALL = Regex("""ScriptMessages\.resource\(\s*R\.string\.(\w+),\s*"((?:[^"\\]|\\.)*)"""")
        private val MESSAGE_PLURALS = listOf("error_method_only_accepts_n_arguments", "error_method_only_accepts_no_less_than_n_arguments")

        private const val FULL_WIDTH_PUNCTUATION = "\uFF0C\u3002\uFF1A\uFF1B\uFF01\uFF1F\uFF08\uFF09\u201C\u201D\u2018\u2019\u3001\u060C"
    }

}
