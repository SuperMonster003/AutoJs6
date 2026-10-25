package org.autojs.autojs.core.automator.selector

import org.autojs.autojs.core.accessibility.AccessibilityBridge
import org.autojs.autojs.core.accessibility.UiSelector
import org.autojs.autojs.core.automator.filter.BooleanFilter
import org.autojs.autojs.core.automator.filter.JsRegexUtils
import org.autojs.autojs.core.automator.filter.PatternPrecheck
import org.autojs.autojs.core.automator.selector.SelectorRegistry.Outcome
import org.autojs.autojs.runtime.api.augment.differential.DifferentialRhino
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.regex.PatternSyntaxException
import kotlin.random.Random

/**
 * docs/selector/ROADMAP.md P1.4 (formerly `SelectorChecksTest`, appendix C Q69 (2)): the judgments the copies of the new
 * layer made now live in the core, and each is held against what applying the condition does. [SelectorEntry.resolve]
 * on every registry entry with arguments of each kind (the overload, no overload, or the refusal with the message
 * applying it throws), with the patterns it hands to [PatternPrecheck] (a refusal must not compile, with the message of
 * the exception).
 *
 * zh-CN: P1.4 (原 `SelectorChecksTest`, 附录 C Q69 (2)): 新层副本所作的判定现已位于 core, 逐项与施加条件的实际行为对照. [SelectorEntry.resolve]
 * 用注册表的每个条目与各类参数 (重载, 没有重载, 或拒绝且消息与施加时的异常一致), 及其交给 [PatternPrecheck] 的正则 (被拒绝的不得能编译, 消息与异常一致).
 */
class SelectorResolutionTest {

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
    fun patternRefusalsDoNotCompile() {
        val random = Random(SEED)
        var refused = 0
        var failing = 0
        val wrong = mutableListOf<String>()
        repeat(PATTERN_SAMPLES) {
            val s = randomString(random, PATTERN_ALPHABET, 8)
            val compiles = runCatching { JsRegexUtils.compileJsOrPlainRegex(s) }.isSuccess
            val refusal = PatternPrecheck.refusal(s)
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
            "/a(/i", "/[/", "(?:a", "(?=a))", "(?i)(", "\\p{L}(",
        ).forEach { s ->
            val e = runCatching { JsRegexUtils.compileJsOrPlainRegex(s) }.exceptionOrNull() as PatternSyntaxException
            assertEquals(s, described(e), PatternPrecheck.refusal(s))
        }
        // Compiling or left to compiling; since Q70 (2) the full syntax is checked (PatternPrecheckTest).
        // zh-CN: 能编译, 或交给编译判定; Q70 (2) 起检查完整语法 (见 PatternPrecheckTest).
        listOf("", "a", "[(]", "[a]]", "[]a]", "(?#[)", "\\Q(", "/a/i", "(?<n>a)").forEach {
            assertNull(it, PatternPrecheck.refusal(it))
        }
    }

    @Test
    fun resolutionsAgreeWithApplying() {
        val samples = conditionSamples()
        val wrong = mutableListOf<String>()
        val seen = HashSet<String>()
        for (entry in SelectorRegistry.entries) for (args in samples) for (lenient in listOf(false, true)) {
            // AppFilter(String) looks the name up in the package manager, which the JVM has not.
            if (entry.name == "currentApp" && args.any { it is CharSequence }) continue
            val resolution = entry.resolve(args, lenient)
            // UiSelector.useAlgorithm reads its message from the string resources, which the JVM has not; the resolution
            // has the fallback of the default resource (resolutionBoundaries, PickMessagesTest).
            if (entry.name == "algorithm" && resolution is SelectorResolution.Rejected) continue
            val outcome = runCatching { SelectorRegistry.apply(UiSelector(null as AccessibilityBridge?), entry.name, args, lenient) }
            val (kind, agrees) = when (resolution) {
                is SelectorResolution.Resolved -> when (val refusal = resolution.patterns.firstNotNullOfOrNull { PatternPrecheck.refusal(it) }) {
                    // A pattern the precheck leaves to compiling: the engine of the JVM may still refuse it.
                    // zh-CN: 预检交给编译判定的正则: JVM 的引擎仍可能拒绝.
                    null -> "Resolved" to (outcome.getOrNull() == Outcome.APPLIED || resolution.patterns.isNotEmpty() && outcome.exceptionOrNull() is IllegalArgumentException)
                    else -> "Precheck" to outcome.exceptionOrNull().let { it is IllegalArgumentException && described(it) == refusal }
                }
                SelectorResolution.NoOverload -> "NoOverload" to (outcome.getOrNull() == Outcome.NO_MATCHING_OVERLOAD)
                is SelectorResolution.Rejected -> "Rejected" to outcome.exceptionOrNull().let { it is IllegalArgumentException && described(it) == resolution.reason }
            }
            if (!agrees) wrong += "${entry.name}(${args.joinToString { "$it" }}) lenient=$lenient: $kind / $outcome"
            seen += kind
        }
        assertEquals(emptyList<String>(), wrong.take(20))
        assertEquals(setOf("Resolved", "Precheck", "NoOverload", "Rejected"), seen)
    }

    @Test
    fun resolutionBoundaries() {
        fun resolve(name: String, vararg args: Any?, lenient: Boolean = false) = SelectorRegistry.entry(name)!!.resolve(args.toList(), lenient)
        fun patterns(name: String, vararg args: Any?) = (resolve(name, *args) as SelectorResolution.Resolved).patterns
        assertEquals("Cannot round NaN value.", (resolve("depth", Double.NaN) as SelectorResolution.Rejected).reason)
        assertTrue(resolve("depth", Float.NaN) is SelectorResolution.Rejected)
        assertEquals(listOf(Int.MAX_VALUE), (resolve("depth", Double.POSITIVE_INFINITY) as SelectorResolution.Resolved).arguments)
        assertTrue(resolve("depth", "NaN", lenient = true) === SelectorResolution.NoOverload)
        assertTrue(resolve("depth", "2", lenient = true) is SelectorResolution.Resolved)
        assertTrue(resolve("depth", "2") === SelectorResolution.NoOverload)
        assertTrue(resolve("depth") === SelectorResolution.NoOverload)
        assertTrue(resolve("algorithm", "Dfs") is SelectorResolution.Resolved)
        assertEquals("Unknown algorithm selector param: ", (resolve("algorithm", "") as SelectorResolution.Rejected).reason)
        assertEquals("Unknown algorithm selector param: bfs ", (resolve("algorithm", "bfs ") as SelectorResolution.Rejected).reason)
        assertTrue(resolve("algorithm", 1.0) === SelectorResolution.NoOverload)
        assertEquals(listOf(""), patterns("textMatches", ""))
        assertEquals(listOf("["), patterns("textMatches", "["))
        assertNotNull(PatternPrecheck.refusal("["))
        assertEquals(listOf("a(b)"), patterns("textMatch", "a(b)"))
        assertEquals(listOf("/a(b)/i"), patterns("textMatches", rhino.evalKt("/a(b)/i")))
        assertEquals(emptyList<String>(), patterns("text", "["))
        assertTrue(resolve("action") is SelectorResolution.Resolved)
        assertTrue(resolve("action", null) === SelectorResolution.NoOverload)
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

    private fun randomString(random: Random, alphabet: String, maxLength: Int) = buildString {
        repeat(1 + random.nextInt(maxLength)) { append(alphabet[random.nextInt(alphabet.length)]) }
    }

    private companion object {
        const val SEED = 69
        const val PATTERN_SAMPLES = 200_000
        const val PATTERN_ALPHABET = "[]()*+?|\\a{}^-:=!<>.QEcpdx/"
    }

}
