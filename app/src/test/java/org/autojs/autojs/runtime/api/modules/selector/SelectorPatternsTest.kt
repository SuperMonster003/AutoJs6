package org.autojs.autojs.runtime.api.modules.selector

import org.autojs.autojs.core.automator.filter.JsRegexUtils
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.regex.PatternSyntaxException
import kotlin.random.Random

/**
 * Appendix C Q70 (2): the pattern check of [SelectorPatterns] against the compiler it stands in for
 * (`JsRegexUtils.compileJsOrPlainRegex`, the JVM engine here; Android ICU on the devices, tools/augment/device_patterns.py).
 * A refused pattern must not compile, on any input: the hand-written cases and the patterns taken from the host sources
 * (resource `augment-q70/patterns.json`), random strings of regex characters and random patterns built from the syntax
 * and then mutated. The share of failing patterns the check leaves to compiling is printed (appendix A "Q70 (2)").
 *
 * zh-CN: 附录 C Q70 (2): [SelectorPatterns] 的正则检查与其代替的编译 (JsRegexUtils.compileJsOrPlainRegex, 此处为 JVM 引擎; 设备上为 Android ICU, 见
 * tools/augment/device_patterns.py) 对照. 被拒绝的正则在任何输入上都不得能编译: 手写用例与取自宿主源码的正则 (资源 augment-q70/patterns.json), 正则字符的
 * 随机串, 以及按语法随机构造再变异的正则. 打印检查交给编译的失败正则所占比例 (附录 A "Q70 (2)").
 */
class SelectorPatternsTest {

    @Test
    fun corpusRefusalsDoNotCompile() {
        val patterns = corpus()
        val tally = Tally()
        patterns.forEach(tally::check)
        assertEquals(emptyList<String>(), tally.wrong.take(20))
        println("Q70 corpus: ${tally.report()}")
        assertTrue(tally.report(), patterns.size > 1000)
    }

    @Test
    fun randomStringRefusalsDoNotCompile() {
        val random = Random(SEED)
        val tally = Tally()
        repeat(STRING_SAMPLES) { tally.check(randomString(random, STRING_ALPHABET, 10)) }
        assertEquals(emptyList<String>(), tally.wrong.take(20))
        println("Q70 random strings: ${tally.report()}")
    }

    @Test
    fun builtPatternRefusalsDoNotCompile() {
        val random = Random(SEED + 1)
        val tally = Tally()
        repeat(BUILT_SAMPLES) {
            val pattern = PatternBuilder(random).pattern()
            tally.check(pattern)
            tally.check(mutate(random, pattern))
        }
        assertEquals(emptyList<String>(), tally.wrong.take(20))
        println("Q70 built patterns: ${tally.report()}")
    }

    @Test
    fun refusalsAreDescribedAsTheJvmDoes() {
        listOf(
            "*a", "+", "?a", "a(*b)", "a(+b)", "a|*", "a|+", "a|?", "a\\", "\\", "[a", "a[b]c[d", "a)", ")", "a(b))", "(a", "((a)",
            "/a(/i", "/[/", "(?:a", "(?=a))", "[]", "[^]", "a**", "a{2}??", "(?i)*", "(?i)(", "\\p{L}(", "\\E", "a\\yb",
        ).forEach { s -> assertEquals(s, described(compileError(s)), SelectorPatterns.refusal(s)) }
    }

    @Test
    fun refusedClasses() {
        // One case of each class of refusal (the doc of SelectorPatterns); each must not compile either.
        listOf(
            "a{2,1}", "a{10,2}?", "a{,2}", "a{", "a{x}", "a{1 }", "{a", "x{1}{", "a{2}*", "a*??", "a+++",
            "(?<1a>x)", "(?<a_b>x)", "(?<>x)", "(?<a>x)(?<a>y)", "(?<a>x)|(?<a>y)", "(?P<a>x)", "(?'a'x)", "(?|a)", "(?R)", "(?1)", "(?z)", "(?iz)", "(?",
            "\\g", "\\C", "\\0", "\\08", "\\x", "\\xg", "\\x{}", "\\x{110000}", "\\x{41", "\\u004", "\\u004g", "\\U", "\\U0041", "\\U00110000",
            "\\N", "\\Na", "\\N{NOPE NOPE}", "\\N{a", "\\p", "\\p{foo}", "\\P{foo}", "\\p{L", "\\k", "\\ka", "\\k<a>", "\\k<a>(?<a>x)", "(?<a>x)\\k<b>",
            "[z-a]", "[\\x7a-\\x61]", "[\\u007a-a]", "[\\t-\\x01]", "[a[z-a]]", "[^z-a]", "[z-a&&b]", "[\\uD835\\uDFD8-a]", "[\\b]", "[\\R]", "[\\X]", "[\\A]",
            "[\\k<a>]", "[\\x]", "[\\0]", "[\\p{foo}]", "[a-\\]", "[[]]", "/[z-a]/i", "/a{2,1}/",
        ).forEach { s ->
            assertNotNull(s, SelectorPatterns.refusal(s))
            assertNotNull(s, runCatching { JsRegexUtils.compileJsOrPlainRegex(s) }.exceptionOrNull())
        }
    }

    @Test
    fun leftToCompiling() {
        // Each fails on one engine only, or the scan stops before a failure it cannot place; none is refused.
        listOf(
            "(?#c)", "(?#[)[z-a]", "(?x)a{2, 1}", "\\Q(\\E", "\\Q[z-a", "\\b{g}", "^*", "\\b*", "(?=a)*", "a*{2}", "{1}", "}", "(?<=a*)", "(a)\\2",
            "\\c", "a\\xb", "\\U0001F600", "\\pL", "\\p{IsLatin}", "[A-\\C]a", "[0-\\p{NoSuch}]", "[ -\\k<a>]", "[a-\\x]", "[a&&]", "[&&a]", "[a--b]", "[!--]", "[[:foo:]]", "[a-\\p{L}]", "[\\1]",
            "[\\0400-0]", "(?)", "(?U)", "(?w)", "(?--i)", "\\1", "/x/u",
        ).forEach { assertNull(it, SelectorPatterns.refusal(it)) }
        // Compiling on both. zh-CN: 两种引擎都能编译.
        listOf(
            "", "a", "[(]", "[a]]", "[]a]", "[^]a]", "[a-]", "[-a]", "[\\w-]", "[a-b-a]", "[a[b]c]", "[a-z&&[^aeiou]]", "a{2}?", "a*+", "a{0,0}",
            "(?<a>x)\\k<a>", "(?i:a)", "(?-:a)", "(?i-)", "(?<=a{1,3})", "\\x{10FFFF}", "\\x{0000041}", "\\u0041", "\\cA", "\\00", "\\0377",
            "\\N{LATIN SMALL LETTER A}", "\\p{L}", "\\p{Lu}", "[\\p{L}-a]", "/a/i", "x[(]](?:)", "\\{", "a\\Q\\E*",
        ).forEach {
            assertNull(it, SelectorPatterns.refusal(it))
            JsRegexUtils.compileJsOrPlainRegex(it)
        }
    }

    @Test
    fun namesAreCompiledOnce() {
        // The second check of a name reads the kept verdict; both give the same refusal.
        val first = SelectorPatterns.refusal("\\p{NoSuchProperty}")
        assertNotNull(first)
        assertEquals(first, SelectorPatterns.refusal("\\p{NoSuchProperty}"))
        assertNull(SelectorPatterns.refusal("\\p{Lu}\\p{Lu}"))
        // More names than are kept: verdicts are compiled again, never wrong.
        repeat(200) { assertNotNull(SelectorPatterns.refusal("\\p{NoSuch$it}")) }
        assertNull(SelectorPatterns.refusal("\\p{Lu}"))
    }

    /** Refusals that compile ([wrong]) and how many failing patterns are refused. */
    private class Tally {
        val wrong = mutableListOf<String>()
        var total = 0
        var failing = 0
        var refused = 0

        fun check(s: String) {
            total++
            val compiles = runCatching { JsRegexUtils.compileJsOrPlainRegex(s) }.isSuccess
            val refusal = SelectorPatterns.refusal(s)
            if (!compiles) failing++
            if (refusal != null) {
                refused++
                if (compiles) wrong += s
            }
        }

        fun report() = "$total patterns, $failing fail to compile, $refused refused (${wrong.size} wrongly), ${failing - refused} left to compiling" +
            " (${if (failing == 0) 0 else (failing - refused) * 1000 / failing / 10.0}%)"
    }

    /** Random patterns from the syntax both engines share and the constructs they differ in. */
    private class PatternBuilder(private val random: Random) {

        private val names = listOf("a", "b", "n1", "Ab")
        private val sb = StringBuilder()
        private var depth = 0

        fun pattern(): String {
            alternation()
            return sb.toString()
        }

        private fun pick(vararg options: String) = options[random.nextInt(options.size)]

        private fun alternation() {
            sequence()
            while (random.nextInt(5) == 0) {
                sb.append('|')
                sequence()
            }
        }

        private fun sequence() {
            repeat(random.nextInt(4)) {
                atom()
                if (random.nextInt(3) == 0) quantifier()
            }
        }

        private fun atom() {
            when (random.nextInt(if (depth > 2) 6 else 9)) {
                0, 1 -> sb.append(pick("a", "b", "z", ".", "-", "#", " ", "}", "]", "\u00e9", "\ud835\udfd8", "^", "$", ","))
                2 -> sb.append(escape(inClass = false))
                3 -> sb.append(pick("\\b", "\\B", "\\A", "\\z", "\\Z", "\\G", "\\d", "\\W", "\\R", "\\X", "\\h", "\\1", "\\2"))
                4, 5 -> characterClass()
                else -> group()
            }
        }

        private fun escape(inClass: Boolean): String = pick(
            "\\t", "\\n", "\\e", "\\a", "\\f", "\\x41", "\\x7a", "\\x{41}", "\\x{1F600}", "\\x{7}", "\\u0041", "\\u007a", "\\uD835\\uDFD8",
            "\\cA", "\\cz", "\\0101", "\\07", "\\00", "\\p{L}", "\\p{Lu}", "\\P{Nd}", "\\pL", "\\p{Greek}", "\\p{IsLatin}", "\\p{InGreek}",
            "\\N{LATIN SMALL LETTER A}", "\\\\", "\\-", "\\]", "\\[", "\\.", "\\{", "\\}", "\\(", "\\)", "\\*", "\\/", "\\ ", "\\#",
            "\\k<a>", "\\k<n1>", "\\Qa(\\E", "\\${pick("g", "i", "y", "C", "E", "U", "x", "u", "k", "N", "p", "0", "c")}",
            if (inClass) "\\b" else "\\s",
        )

        private fun member(): String = when (random.nextInt(4)) {
            0 -> escape(inClass = true)
            else -> pick("a", "b", "z", "0", "9", "-", "^", "&", "(", ")", "{", "*", "|", " ", "\u00e9", "\ud835\udfd8", "!", "~")
        }

        private fun characterClass() {
            sb.append('[')
            if (random.nextInt(4) == 0) sb.append('^')
            if (random.nextInt(8) == 0) sb.append(']')
            repeat(random.nextInt(4)) {
                when (random.nextInt(8)) {
                    0 -> sb.append(member()).append('-').append(member())
                    1 -> sb.append("&&")
                    2 -> if (depth < 3) {
                        depth++
                        characterClass()
                        depth--
                    }
                    3 -> sb.append(pick("[:alpha:]", "--", "\\d", "\\p{L}"))
                    else -> sb.append(member())
                }
            }
            if (random.nextInt(12) != 0) sb.append(']')
        }

        private fun group() {
            depth++
            sb.append(pick("(", "(", "(?:", "(?=", "(?!", "(?<=", "(?<!", "(?>", "(?<${names.random(random)}>", "(?i)", "(?i:", "(?-m:", "(?#c)", "(?x)", "(?U)", "(?w)"))
            if (sb.endsWith(")")) {
                depth--
                return
            }
            alternation()
            if (random.nextInt(12) != 0) sb.append(')')
            depth--
        }

        private fun quantifier() {
            sb.append(pick("*", "+", "?", "{2}", "{0,}", "{1,3}", "{3,1}", "{,2}", "{x}", "{", "{2147483648}", "{1, 2}"))
            if (random.nextInt(3) == 0) sb.append(pick("?", "+", "*", "{2}"))
        }

    }

    private fun mutate(random: Random, pattern: String): String {
        val sb = StringBuilder(pattern)
        repeat(1 + random.nextInt(2)) {
            val at = if (sb.isEmpty()) 0 else random.nextInt(sb.length + 1)
            when (random.nextInt(3)) {
                0 -> if (at < sb.length) sb.deleteCharAt(at)
                1 -> sb.insert(at, MUTATION_ALPHABET[random.nextInt(MUTATION_ALPHABET.length)])
                else -> if (at + 1 < sb.length) {
                    val c = sb[at]
                    sb.setCharAt(at, sb[at + 1])
                    sb.setCharAt(at + 1, c)
                }
            }
        }
        return sb.toString()
    }

    private fun compileError(s: String): Throwable = runCatching { JsRegexUtils.compileJsOrPlainRegex(s) }.exceptionOrNull() ?: throw AssertionError("compiles: $s")

    private fun described(e: Throwable): String? = when {
        e !is PatternSyntaxException -> e.message
        e.index >= 0 -> "${e.description} near index ${e.index}"
        else -> e.description
    }

    private fun randomString(random: Random, alphabet: String, maxLength: Int) = buildString {
        repeat(1 + random.nextInt(maxLength)) { append(alphabet[random.nextInt(alphabet.length)]) }
    }

    /** The patterns of resource `augment-q70/patterns.json`, a JSON array of strings in ASCII. */
    private fun corpus(): List<String> {
        val json = javaClass.classLoader!!.getResource("augment-q70/patterns.json")!!.readText()
        val out = ArrayList<String>()
        var i = 0
        while (true) {
            i = json.indexOf('"', i)
            if (i < 0) break
            val sb = StringBuilder()
            i++
            while (json[i] != '"') {
                val c = json[i++]
                if (c != '\\') {
                    sb.append(c)
                    continue
                }
                when (val e = json[i++]) {
                    'n' -> sb.append('\n')
                    't' -> sb.append('\t')
                    'r' -> sb.append('\r')
                    'b' -> sb.append('\b')
                    'f' -> sb.append('\u000c')
                    'u' -> {
                        sb.append(json.substring(i, i + 4).toInt(16).toChar())
                        i += 4
                    }
                    else -> sb.append(e)
                }
            }
            i++
            out += sb.toString()
        }
        return out
    }

    private companion object {
        const val SEED = 70
        const val STRING_SAMPLES = 300_000
        const val BUILT_SAMPLES = 150_000
        const val STRING_ALPHABET = "[]()*+?|\\a{}^-:=!<>.,0123QEcpdxkbuUN/&#"
        const val MUTATION_ALPHABET = "[]()*+?|\\{}-,0&:<>^"
    }

}
