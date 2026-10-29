package org.autojs.autojs.core.automator.selector

import org.autojs.autojs.core.automator.pick.NodeMethodRegistry
import org.autojs.autojs.core.automator.pick.PickResultType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * docs/augment/ROADMAP.md appendix C Q141: [SelectorStringParser.Suggestions] (names lowered once, distances bounded by
 * the budget, row by row) gives the candidates the former `suggest` gave, in the same order, for the names of the
 * selector registry and the result types of `pickup`, for correct spellings, other cases, one or two typos and
 * unrelated words; the bounds (length, characters, rows, the nearest kept so far) never drop a candidate.
 *
 * zh-CN: augment 附录 C Q141: [SelectorStringParser.Suggestions] (名称只转一次小写, 编辑距离受预算约束并逐行计算) 对选择器注册表名称与 `pickup` 结果类型,
 * 在正确拼写, 大小写不同, 一两处错字与无关单词下, 给出与原 `suggest` 相同的候选与顺序; 各种界限 (长度, 字符, 逐行, 已保留的最近者) 不会漏掉候选.
 */
class SelectorSuggestionsTest {

    private val registryNames = SelectorRegistry.names

    private val resultSpellings = (SYMBOLS.flatMap { PickResultType.spellings(it) } + NodeMethodRegistry.uiObject.spellings).distinct()

    @Test
    fun sampledKeysGiveTheFormerCandidates() {
        val keys = listOf(
            "text", "Text", "TEXT", "clickable", "ClickAble", "className", "id", "desc", "depth",
            "txet", "clikable", "clickabel", "classNmae", "pakageName", "boundsInScren", "textMatchs", "dsec",
            "nope", "zzzzzz", "aj6-none", "开始", "x", "c", "Match", "cl", "get", "getText", "gettext", "hintText",
            "viewIdResourceName", "isClickable", "chlid", "parnet", "bounds", "boundz", "#", "{}", "w+", "widget", "wigdet",
            "point", "points", "selector", "exists", "content", "contnet", "click", "clik",
        )
        for (names in listOf(registryNames, resultSpellings)) {
            val suggestions = SelectorStringParser.Suggestions(names)
            for (key in keys) for (limit in listOf(0, 1, 2, 5, 10, 1000)) {
                assertEquals("\"$key\" (limit $limit)", reference(key, names, limit), suggestions.suggest(key, limit))
            }
        }
    }

    @Test
    fun everyNameWithOneOrTwoTyposGivesTheFormerCandidates() {
        val random = Random(141)
        for (names in listOf(registryNames, resultSpellings)) {
            val suggestions = SelectorStringParser.Suggestions(names)
            for (name in names) {
                val keys = listOf(name, name.uppercase(), typo(name, random), typo(typo(name, random), random), name.drop(1), name.dropLast(2) + "zz")
                for (key in keys) assertEquals("\"$key\"", reference(key, names), suggestions.suggest(key))
            }
        }
    }

    @Test
    fun randomWordsGiveTheFormerCandidates() {
        val random = Random(142)
        val alphabet = "abcdeilnorstxyzABCT-_#$.{}[]0123456789"
        val suggestions = SelectorStringParser.Suggestions(registryNames)
        repeat(3000) {
            val key = String(CharArray(1 + random.nextInt(12)) { alphabet[random.nextInt(alphabet.length)] })
            assertEquals("\"$key\"", reference(key, registryNames, 3), suggestions.suggest(key, 3))
        }
    }

    @Test
    fun nearerNamesFoundLaterReplaceTheFarthestKept() {
        // "abcdefgh": a budget of 2. zh-CN: 预算为 2.
        val names = listOf("abcdefzz", "abcdefgz", "abcdefyy", "abcdefgx", "abcdefgh!", "hgfedcba", "abcd", "ABCDEFGH")
        val suggestions = SelectorStringParser.Suggestions(names)
        assertEquals(listOf("ABCDEFGH", "abcdefgh!", "abcdefgz"), suggestions.suggest("abcdefgh", 3))
        assertEquals(listOf("abcdefgz", "abcdefgx"), SelectorStringParser.Suggestions(names.take(4)).suggest("abcdefgh", 2))
        assertEquals(listOf("abcdefgz", "abcdefgx", "abcdefzz"), SelectorStringParser.Suggestions(names.take(4)).suggest("abcdefgh", 3))
        // Same characters (no character bound) and the same length (no length bound): only the rows tell. zh-CN: 字符与长度都相同, 只能由逐行计算判定.
        assertEquals(emptyList<String>(), SelectorStringParser.Suggestions(listOf("hgfedcba")).suggest("abcdefgh"))
        for (limit in 0..8) assertEquals("limit $limit", reference("abcdefgh", names, limit), suggestions.suggest("abcdefgh", limit))
    }

    @Test
    fun equalNamesKeepTheirFirstPlace() {
        val names = listOf("texts", "text", "Text", "texts", "txt", "text")
        for (key in listOf("text", "tex", "txet", "T")) {
            assertEquals(key, reference(key, names), SelectorStringParser.Suggestions(names).suggest(key))
            assertEquals(key, reference(key, names), SelectorStringParser.suggest(key, names))
        }
        assertEquals(emptyList<String>(), SelectorStringParser.Suggestions(names).suggest(""))
        assertEquals(emptyList<String>(), SelectorStringParser.Suggestions(emptyList()).suggest("text"))
    }

    @Test
    fun theRegistryAndTheParserUseThePreparedNames() {
        val parser = SelectorStringParser()
        for (key in listOf("txt", "Text", "clik", "nope")) {
            assertEquals(reference(key, registryNames), parser.suggest(key))
            assertEquals(reference(key, registryNames), SelectorRegistry.suggestions.suggest(key))
        }
        assertTrue(SelectorRegistry.suggestions === SelectorRegistry.suggestions)
    }

    /**
     * A coarse bound, far above the cost: 500 failing lookups among the result types (the slowest case on XQ-AT72, 3.4 ms
     * per call before Q141) take well under the time of the former computation.
     * zh-CN: 粗略的上限, 远高于实际开销: 在结果类型中查找 500 次失败的名称 (Q141 之前 XQ-AT72 上最慢的一项, 每次 3.4 ms), 用时远少于原计算.
     */
    @Test
    fun failingLookupsTakeAFractionOfTheFormerTime() {
        val suggestions = SelectorStringParser.Suggestions(resultSpellings)
        val keys = listOf("nope", "aj6-none")
        fun prepared() = (1..250).sumOf { keys.sumOf { key -> suggestions.suggest(key).size } }
        fun former() = (1..250).sumOf { keys.sumOf { key -> reference(key, resultSpellings).size } }
        repeat(3) { prepared(); former() }
        val preparedNanos = (1..3).minOf { nanos(::prepared) }
        val formerNanos = (1..3).minOf { nanos(::former) }
        assertTrue("prepared ${preparedNanos / 1000} us, former ${formerNanos / 1000} us", preparedNanos * 4 < formerNanos)
    }

    private fun nanos(block: () -> Int): Long {
        val start = System.nanoTime()
        block()
        return System.nanoTime() - start
    }

    private fun typo(s: String, random: Random): String {
        if (s.isEmpty()) return "x"
        val at = random.nextInt(s.length)
        return when (random.nextInt(4)) {
            0 -> s.removeRange(at, at + 1)
            1 -> s.substring(0, at) + "q" + s.substring(at)
            2 -> s.substring(0, at) + (if (s[at] == 'e') 'a' else 'e') + s.substring(at + 1)
            else -> if (at + 1 < s.length) s.substring(0, at) + s[at + 1] + s[at] + s.substring(at + 2) else s + "s"
        }
    }

    private companion object {

        val SYMBOLS = listOf(
            PickResultType.Widget, PickResultType.WidgetCollection, PickResultType.Widgets, PickResultType.Content, PickResultType.Contents,
            PickResultType.Point, PickResultType.Points, PickResultType.Selector, PickResultType.Existence,
        )

        /** `SelectorStringParser.suggest` before Q141, the reference. zh-CN: Q141 之前的 `SelectorStringParser.suggest`, 作为参照. */
        fun reference(key: String, names: Collection<String>, limit: Int = 5): List<String> {
            val lower = key.lowercase()
            if (lower.isEmpty()) return emptyList()
            val exact = names.filter { it.lowercase() == lower }
            val prefix = names.filter { it.lowercase().startsWith(lower) }
            val contains = names.filter { lower in it.lowercase() }
            val budget = maxOf(1, key.length / 3)
            val close = names.filter { SelectorStringParser.editDistance(it.lowercase(), lower) <= budget }.sortedBy { SelectorStringParser.editDistance(it.lowercase(), lower) }
            return (exact + prefix + contains + close).distinct().take(limit)
        }

    }

}
