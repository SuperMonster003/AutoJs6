package org.autojs.autojs.runtime.api.modules.selector

import org.autojs.autojs.core.accessibility.AccessibilityBridge
import org.autojs.autojs.core.accessibility.UiSelector
import org.autojs.autojs.core.automator.filter.BooleanFilter
import org.autojs.autojs.core.automator.filter.JsRegexUtils
import org.autojs.autojs.core.automator.pick.Compass
import org.autojs.autojs.core.automator.pick.CompassParser
import org.autojs.autojs.core.automator.pick.CompassSyntaxException
import org.autojs.autojs.core.automator.pick.Invoker
import org.autojs.autojs.core.automator.selector.SelectorRegistry
import org.autojs.autojs.core.automator.selector.SelectorRegistry.Outcome
import org.autojs.autojs.runtime.api.augment.differential.DifferentialRhino
import org.autojs.autojs.runtime.api.modules.selector.SelectorChecks.Verdict
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.regex.PatternSyntaxException
import kotlin.random.Random

/**
 * Appendix C Q69 (2): each check of [SelectorChecks] against the reader it copies, so that the copies cannot drift from
 * the core until roadmap P5.2 merges them. The compass checks are compared on every string of up to five characters
 * of the compass alphabet, the pattern check on random patterns (a refusal must not compile), the condition check on
 * every registry entry with arguments of each kind and the interceptor check on every intercepted method.
 *
 * zh-CN: 附录 C Q69 (2): [SelectorChecks] 的每项检查与其复制的读取方逐一对照, 使复制品在 P5.2 合并之前不会与 core 漂移. 罗盘检查在罗盘字母表上
 * 长度不超过 5 的全部字符串上比较, 正则检查用随机模式 (被拒绝的不得能编译), 条件检查用注册表的每个条目与各类参数, Interceptor 检查用每个被拦截的方法.
 */
class SelectorChecksTest {

    private lateinit var rhino: DifferentialRhino

    @Before
    fun setUp() {
        rhino = DifferentialRhino()
    }

    @After
    fun tearDown() {
        rhino.close()
    }

    @Test
    fun compassesAgreeWithTheParser() {
        val wrong = mutableListOf<String>()
        var parsed = 0
        forEachString(COMPASS_ALPHABET, 5) { s ->
            compassMismatch(s)?.let { wrong += it }
            if (s != Compass.PASS_ON && SelectorChecks.compassParses(s)) parsed++
        }
        COMPASS_EXTRAS.forEach { s -> compassMismatch(s)?.let { wrong += it } }
        assertEquals(emptyList<String>(), wrong.take(20))
        assertTrue("$parsed", parsed > 1000)
    }

    @Test
    fun compassBoundaries() {
        assertFalse(SelectorChecks.isCompass(null))
        assertFalse(SelectorChecks.isCompass(1))
        assertFalse(SelectorChecks.isCompass(1.5))
        assertTrue(SelectorChecks.isCompass(""))
        assertTrue(SelectorChecks.isCompass(Compass.PASS_ON))
        assertTrue(SelectorChecks.isCompass(StringBuilder("p2c1")))
        assertTrue(SelectorChecks.isCompass("c"))
        assertFalse(SelectorChecks.isCompass("x2"))
        listOf("p", "p2147483647", "c-2147483648", "c0>1c-2", "s<1", "k99", "p00000000002147483647").forEach { assertTrue(it, SelectorChecks.compassParses(it)) }
        listOf("c", "s", "s<", "c0>", "p2147483648", "c-2147483649", "s>2147483648", "k99999999999", "x").forEach { assertFalse(it, SelectorChecks.compassParses(it)) }
    }

    @Test
    fun patternRefusalsDoNotCompile() {
        val random = Random(SEED)
        var refused = 0
        var failing = 0
        val wrong = mutableListOf<String>()
        repeat(PATTERN_SAMPLES) {
            val s = randomString(random, PATTERN_ALPHABET, 8)
            val compiles = runCatching { JsRegexUtils.compileJsOrPlainRegex(s) }.isSuccess
            val refusal = SelectorChecks.patternRefusal(s)
            if (!compiles) failing++
            if (refusal != null) {
                refused++
                if (compiles) wrong += s
            }
        }
        assertEquals(emptyList<String>(), wrong.take(20))
        assertTrue("$refused of $failing", refused * 2 > failing)
    }

    @Test
    fun patternRefusalCases() {
        listOf(
            "*a", "+", "?a", "a(*b)", "a(+b)", "a|*", "a|+", "a|?", "a\\", "\\", "[a", "a[b]c[d", "a)", ")", "a(b))", "(a", "((a)", "\\((",
            "/a(/i", "/[/", "(?:a", "(?=a))",
        ).forEach { s ->
            val e = runCatching { JsRegexUtils.compileJsOrPlainRegex(s) }.exceptionOrNull() as PatternSyntaxException
            assertEquals(s, described(e), SelectorChecks.patternRefusal(s))
        }
        // Compiling or left to compiling. zh-CN: 能编译, 或交给编译判定.
        listOf("", "a", "[(]", "[a]]", "[]a]", "(?i)(", "(?#[)", "\\Q(", "\\x", "a{", "/a/i", "(?<n>a)", "x[(]](", "\\p{L}(").forEach {
            assertNull(it, SelectorChecks.patternRefusal(it))
        }
    }

    @Test
    fun conditionsAgreeWithTheRegistry() {
        val samples = conditionSamples()
        val wrong = mutableListOf<String>()
        val seen = HashSet<String>()
        for (entry in SelectorRegistry.entries) for (args in samples) for (lenient in listOf(false, true)) {
            // AppFilter(String) looks the name up in the package manager, which the JVM has not.
            if (entry.name == "currentApp" && args.any { it is CharSequence }) continue
            val verdict = SelectorChecks.condition(entry, args, lenient)
            // The message of an unknown algorithm is a string resource, which the JVM has not (see conditionBoundaries).
            if (entry.name == "algorithm" && verdict is Verdict.Rejected) continue
            val outcome = runCatching { SelectorRegistry.apply(UiSelector(null as AccessibilityBridge?), entry.name, args, lenient) }
            val agrees = when (verdict) {
                Verdict.Accepted -> outcome.getOrNull() == Outcome.APPLIED
                Verdict.NoOverload -> outcome.getOrNull() == Outcome.NO_MATCHING_OVERLOAD
                is Verdict.Rejected -> outcome.exceptionOrNull().let { it is IllegalArgumentException && described(it) == verdict.reason }
                Verdict.Unsure -> outcome.getOrNull() == Outcome.APPLIED || outcome.exceptionOrNull() is IllegalArgumentException
            }
            if (!agrees) wrong += "${entry.name}(${args.joinToString { "$it" }}) lenient=$lenient: ${verdict.javaClass.simpleName} / $outcome"
            seen += verdict.javaClass.simpleName
        }
        assertEquals(emptyList<String>(), wrong.take(20))
        assertEquals(setOf("Accepted", "NoOverload", "Rejected", "Unsure"), seen)
    }

    @Test
    fun conditionBoundaries() {
        fun verdict(name: String, vararg args: Any?, lenient: Boolean = false) = SelectorChecks.condition(SelectorRegistry.entry(name)!!, args.toList(), lenient)
        assertEquals("Cannot round NaN value.", (verdict("depth", Double.NaN) as Verdict.Rejected).reason)
        assertTrue(verdict("depth", Float.NaN) is Verdict.Rejected)
        assertTrue(verdict("depth", Double.POSITIVE_INFINITY) is Verdict.Accepted)
        assertTrue(verdict("depth", "NaN", lenient = true) is Verdict.NoOverload)
        assertTrue(verdict("depth", "2", lenient = true) is Verdict.Accepted)
        assertTrue(verdict("depth", "2") is Verdict.NoOverload)
        assertTrue(verdict("depth") is Verdict.NoOverload)
        assertTrue(verdict("algorithm", "Dfs") is Verdict.Accepted)
        assertTrue(verdict("algorithm", "") is Verdict.Rejected)
        assertTrue(verdict("algorithm", "bfs ") is Verdict.Rejected)
        assertTrue(verdict("algorithm", 1.0) is Verdict.NoOverload)
        assertTrue(verdict("textMatches", "") is Verdict.Unsure)
        assertTrue(verdict("textMatches", "[") is Verdict.Rejected)
        assertTrue(verdict("text", "[") is Verdict.Accepted)
        assertTrue(verdict("action") is Verdict.Accepted)
        assertTrue(verdict("action", null) is Verdict.NoOverload)
    }

    @Test
    fun interceptorAgreesWithInvoker() {
        val names = Invoker.Interceptor.interceptedMap.keys + "nope"
        val samples = listOf(
            emptyList(), listOf(Double.NaN), listOf(1.0), listOf(1.5), listOf(Double.POSITIVE_INFINITY), listOf(Double.MAX_VALUE), listOf(Float.NaN),
            listOf(Double.NaN, Double.NaN), listOf(1.0, Double.NaN), listOf(Double.NaN, 1.0), listOf("a"), listOf("a", Double.NaN), listOf(null),
            listOf(1.0, 2.0, Double.NaN),
        )
        var throwing = 0
        for (name in names) for (args in samples) {
            val error = runCatching { Invoker.Interceptor(name, args.toTypedArray()) }.exceptionOrNull()
            assertTrue("$name$args: $error", error == null || error is IllegalArgumentException)
            assertEquals("$name$args", error != null, SelectorChecks.interceptorThrows(name, args))
            if (error != null) throwing++
        }
        assertTrue(throwing > 0)
    }

    private fun conditionSamples(): List<List<Any?>> {
        val regex = rhino.evalKt("/a(b)/i")
        val plainRegex = rhino.evalKt("/[\\]]/")
        val supplier = BooleanFilter.BooleanSupplier { false }
        return listOf(
            emptyList(), listOf("a"), listOf(""), listOf("["), listOf("(?i)("), listOf("a|*"), listOf("/a(/i"), listOf("BFS"), listOf("dfs"), listOf("x"),
            listOf("1"), listOf("1.5"), listOf("true"), listOf("NaN"), listOf(Double.NaN), listOf(Float.NaN), listOf(1.0), listOf(1.5), listOf(-0.0),
            listOf(Double.POSITIVE_INFINITY), listOf(Double.NEGATIVE_INFINITY), listOf(Double.MAX_VALUE), listOf(3), listOf(true), listOf(null),
            listOf(supplier), listOf(regex), listOf(plainRegex), listOf(1.0, 2.0), listOf(1.0, Double.NaN), listOf(Double.NaN, "a"),
            listOf("1", "2"), listOf("a", "b"), listOf(null, 1.0), listOf(1.0, 2.0, 3.0, 4.0), listOf(0.0, 0.0, Double.NaN, 1.0), listOf("0", "0", "1", "NaN"),
        )
    }

    private fun described(e: Throwable): String? = when {
        e !is PatternSyntaxException -> e.message
        e.index >= 0 -> "${e.description} near index ${e.index}"
        else -> e.description
    }

    private fun compassMismatch(s: String): String? {
        if (SelectorChecks.isCompass(s) != Compass.isCompass(s)) return "isCompass($s)"
        if (s == Compass.PASS_ON) return null
        val parses = Compass.isCompass(s) && try {
            CompassParser.parse(s)
            true
        } catch (e: CompassSyntaxException) {
            false
        }
        return if (SelectorChecks.compassParses(s) != parses) "compassParses($s)" else null
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

    private fun randomString(random: Random, alphabet: String, maxLength: Int) = buildString {
        repeat(1 + random.nextInt(maxLength)) { append(alphabet[random.nextInt(alphabet.length)]) }
    }

    private companion object {
        const val SEED = 69
        const val PATTERN_SAMPLES = 200_000
        const val COMPASS_ALPHABET = "pkcs<>-019x%"
        const val PATTERN_ALPHABET = "[]()*+?|\\a{}^-:=!<>.QEcpdx/"
        val COMPASS_EXTRAS = listOf(
            "p2147483647", "p2147483648", "c-2147483648", "c-2147483649", "s<-2147483648", "s>2147483648", "k99999999999", "p00000000002147483647",
            "c0>", "c-1-2", "c1c", "s<<1", "pp2kk3", "c\u0663", "p\u0663", "\u0663", "s", "%%", "p%",
        )
    }

}
