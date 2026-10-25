package org.autojs.autojs.core.automator.pick

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * docs/selector/ROADMAP.md P1.4: [CompassParser.tryParse] (the compass scan of the former `SelectorChecks`, appendix C
 * Q69 (2)) against what `UiObject.compass` does with the same string ([Compass.PASS_ON], then the regular expression of
 * [CompassParser.isCompass], then [CompassParser.parse]), on every string of up to five characters of the compass
 * alphabet and on the boundaries of the numbers: the same steps, the same refused remainder.
 *
 * zh-CN: P1.4: [CompassParser.tryParse] (原 `SelectorChecks` 的罗盘扫描, 附录 C Q69 (2)) 与 `UiObject.compass` 对同一字符串的处理
 * ([Compass.PASS_ON], 再 [CompassParser.isCompass] 的正则, 再 [CompassParser.parse]) 对照: 罗盘字母表上长度不超过 5 的全部字符串及数值边界,
 * 步骤相同, 被拒绝的剩余部分相同.
 */
class CompassTryParseTest {

    @Test
    fun agreesWithTheParser() {
        val wrong = mutableListOf<String>()
        var steps = 0
        forEachString(ALPHABET, 5) { s ->
            mismatch(s)?.let { wrong += it }
            if (CompassParser.tryParse(s) is CompassParse.Steps) steps++
        }
        EXTRAS.forEach { s -> mismatch(s)?.let { wrong += it } }
        assertEquals(emptyList<String>(), wrong.take(20))
        assertTrue("$steps", steps > 1000)
    }

    @Test
    fun boundaries() {
        assertTrue(CompassParser.tryParse(Compass.PASS_ON) === CompassParse.PassOn)
        assertEquals(emptyList<CompassStep>(), (CompassParser.tryParse("") as CompassParse.Steps).steps)
        assertEquals(listOf(CompassStep.Up(2), CompassStep.Children(listOf(1))), (CompassParser.tryParse(StringBuilder("p2c1")) as CompassParse.Steps).steps)
        listOf("p", "p2147483647", "c-2147483648", "c0>1c-2", "s<1", "k99", "p00000000002147483647").forEach {
            assertTrue(it, CompassParser.tryParse(it) is CompassParse.Steps)
        }
        mapOf("c" to "c", "s" to "s", "s<" to "s<", "c0>" to ">", "p2147483648" to "p2147483648", "c-2147483649" to "c-2147483649", "k99999999999" to "k99999999999").forEach { (s, rest) ->
            assertEquals(s, rest, (CompassParser.tryParse(s) as CompassParse.BadRest).rest)
        }
        listOf("x", "x2", "%%", "p%").forEach { assertEquals(it, it, (CompassParser.tryParse(it) as CompassParse.NotCompass).compass) }
    }

    /** What differs between [CompassParser.tryParse] and the regular expressions of `UiObject.compass` for [s], or null. */
    private fun mismatch(s: String): String? {
        val actual = CompassParser.tryParse(s)
        if (CompassParser.isCompass(s) != (s.isEmpty() || SYNTAX.matches(s))) return "isCompass($s)"
        val expected: Any = when {
            s == Compass.PASS_ON -> "PassOn"
            !Compass.isCompass(s) -> "NotCompass($s)"
            else -> try {
                CompassParser.parse(s)
            } catch (e: CompassSyntaxException) {
                "BadRest(${e.rest})"
            }
        }
        val told: Any = when (actual) {
            CompassParse.PassOn -> "PassOn"
            is CompassParse.NotCompass -> "NotCompass(${actual.compass})"
            is CompassParse.BadRest -> "BadRest(${actual.rest})"
            is CompassParse.Steps -> actual.steps
        }
        return if (told != expected) "$s: $told / $expected" else null
    }

    private fun forEachString(alphabet: String, maxLength: Int, action: (String) -> Unit) {
        val chars = CharArray(maxLength)
        fun fill(length: Int, at: Int) {
            if (at == length) return action(String(chars, 0, length))
            for (c in alphabet) {
                chars[at] = c
                fill(length, at + 1)
            }
        }
        for (length in 0..maxLength) fill(length, 0)
    }

    private companion object {
        const val ALPHABET = "pkcs<>-019x%"

        /** The regular expression `UiObject.compass` matched before P1.4 (CompassParser.SYNTAX). */
        val SYNTAX = Regex("(([pkc>]|s[<>]?)-?\\d*)+")

        val EXTRAS = listOf(
            "p2147483647", "p2147483648", "c-2147483648", "c-2147483649", "s<-2147483648", "s>2147483648", "k99999999999", "p00000000002147483647",
            "c0>", "c-1-2", "c1c", "s<<1", "pp2kk3", "c\u0663", "p\u0663", "\u0663", "s", "%%", "p%",
        )
    }

}
