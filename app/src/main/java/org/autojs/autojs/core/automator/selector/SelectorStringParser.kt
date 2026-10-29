package org.autojs.autojs.core.automator.selector

import org.autojs.autojs.core.accessibility.UiSelector
import kotlin.math.abs

/**
 * The string selector syntax (roadmap D13, P5.3): whitespace-separated terms, each naming a
 * property of the [SelectorRegistry]:
 *
 * - `key=value`: the property with one argument; a value with spaces goes in `"..."` or `'...'`
 *   (escapes `\"`, `\\`, `\n`, `\t`); an unquoted value with commas is split into several arguments
 *   when a single one does not fit (`bounds=0,0,100,200`, `left=10,20`), and always for the vararg
 *   properties (`prefer=visible,center`, `action=CLICK,SET_TEXT`); numbers and booleans are
 *   parsed leniently as in map selectors (`depth=3`, `clickable=false`).
 * - `key~=regex` or `key=/regex/flags`: `keyMatch(regex)`, a regular expression search (like
 *   `RegExp.test`), for the string properties `text` / `desc` / `content` / `id` / `className` /
 *   `packageName`.
 * - `key` / `!key`: the no-argument overload (`clickable()`) or the boolean `false` (`clickable(false)`).
 *
 * Errors carry the character position, the offending term and candidate property names. Terms
 * are applied in order; the parser adds filters to the given selector and never consults Android,
 * so it is unit tested on the JVM. `pickup` / `wait` and the Flow conditions keep treating a plain
 * string as a content match; this syntax is only entered through `select("...")` (D13).
 *
 * zh-CN: 字符串选择器语法 (Roadmap D13, P5.3): 以空白分隔的若干条件, 每条指向 [SelectorRegistry] 的一个属性:
 * `key=value` (含空格的值用引号, 转义 `\"` / `\\` / `\n` / `\t`; 未加引号且含逗号的值在单个参数不匹配时拆成多个参数, 可变参数属性则总是拆分
 * (`prefer=visible,center`, `action=CLICK,SET_TEXT`); 数字与布尔值
 * 同 map 选择器一样宽松解析), `key~=regex` 或 `key=/regex/flags` (字符串属性的 `keyMatch(regex)`, 正则搜索), `key` / `!key`
 * (无参重载或布尔 `false`). 错误带字符位置, 出错的条件与候选属性名. 条件按顺序施加; 解析器只向给定选择器追加过滤器, 不涉及 Android,
 * 因此在 JVM 上单元测试. `pickup` / `wait` 与 Flow 条件中的普通字符串仍是内容匹配; 本语法只经 `select("...")` 进入 (D13).
 *
 * Created by SuperMonster003 on Sep 6, 2026.
 */
class SelectorStringParser(private val registry: SelectorRegistry = SelectorRegistry) {

    enum class Op { FLAG, NOT, EQUALS, MATCH }

    /** One term: [key] with [op] and the raw [value] (`null` for `key` / `!key`), starting at [position]. zh-CN: 一条条件: 从 [position] 起的 [key], [op] 与原始 [value] (`key` / `!key` 时为 `null`). */
    class Term(val key: String, val op: Op, val value: String?, val quoted: Boolean, val position: Int, val text: String) {
        override fun toString(): String = text
    }

    /** The registry properties `key~=` applies to. zh-CN: `key~=` 适用的注册表属性. */
    val matchableProperties: List<String> = registry.names.filter { "${it}Match" in registry }

    /** Applies every term of [syntax] to [into] and returns it. zh-CN: 把 [syntax] 的每条条件施加到 [into] 并返回它. */
    @Throws(SelectorStringException::class)
    fun parse(syntax: String, into: UiSelector): UiSelector {
        for (term in tokenize(syntax)) apply(syntax, term, into)
        return into
    }

    /** The terms of [syntax], or a [SelectorStringException] at the first malformed one. zh-CN: [syntax] 的各条条件, 或首个格式错误处的 [SelectorStringException]. */
    @Throws(SelectorStringException::class)
    fun tokenize(syntax: String): List<Term> {
        val terms = ArrayList<Term>()
        var i = 0
        val n = syntax.length
        while (i < n) {
            if (syntax[i].isWhitespace()) {
                i += 1
                continue
            }
            val start = i
            val negated = syntax[i] == '!'
            if (negated) i += 1
            val keyStart = i
            while (i < n && (syntax[i].isLetterOrDigit() || syntax[i] == '_')) i += 1
            val key = syntax.substring(keyStart, i)
            if (key.isEmpty()) {
                val found = if (i < n) "\"${syntax[i]}\"" else "the end"
                throw SelectorStringException(syntax, start, null, emptyList(), "expected a property name, found $found")
            }
            if (i >= n || syntax[i].isWhitespace()) {
                terms += Term(key, if (negated) Op.NOT else Op.FLAG, null, false, start, syntax.substring(start, i))
                continue
            }
            if (negated) throw SelectorStringException(syntax, start, "!$key", emptyList(), "\"!$key\" takes no value")
            val op = when {
                syntax.startsWith("~=", i) -> {
                    i += 2
                    Op.MATCH
                }
                syntax[i] == '=' -> {
                    i += 1
                    Op.EQUALS
                }
                else -> throw SelectorStringException(syntax, i, key, emptyList(), "unexpected \"${syntax[i]}\" after \"$key\"; write $key=value, $key~=regex, $key or !$key")
            }
            if (i >= n || syntax[i].isWhitespace()) {
                throw SelectorStringException(syntax, i, syntax.substring(start, i), emptyList(), "missing value after \"${syntax.substring(start, i)}\"")
            }
            val (value, quoted, end) = readValue(syntax, i)
            i = end
            terms += Term(key, op, value, quoted, start, syntax.substring(start, i))
        }
        return terms
    }

    private fun readValue(syntax: String, from: Int): Triple<String, Boolean, Int> {
        val quote = syntax[from]
        if (quote == '"' || quote == '\'') {
            val sb = StringBuilder()
            var i = from + 1
            while (i < syntax.length) {
                val c = syntax[i]
                when {
                    c == '\\' && i + 1 < syntax.length -> {
                        sb.append(unescape(syntax[i + 1]))
                        i += 2
                    }
                    c == quote -> {
                        val end = i + 1
                        if (end < syntax.length && !syntax[end].isWhitespace()) {
                            throw SelectorStringException(syntax, end, null, emptyList(), "expected a space after the closing quote, found \"${syntax[end]}\"")
                        }
                        return Triple(sb.toString(), true, end)
                    }
                    else -> {
                        sb.append(c)
                        i += 1
                    }
                }
            }
            throw SelectorStringException(syntax, from, null, emptyList(), "unterminated quoted value")
        }
        var i = from
        while (i < syntax.length && !syntax[i].isWhitespace()) i += 1
        return Triple(syntax.substring(from, i), false, i)
    }

    private fun unescape(c: Char): Char = when (c) {
        'n' -> '\n'
        't' -> '\t'
        'r' -> '\r'
        else -> c
    }

    private fun apply(syntax: String, term: Term, into: UiSelector) {
        val entry = registry.entry(term.key) ?: throw SelectorStringException(syntax, term.position, term.text, suggest(term.key), "unknown property \"${term.key}\"")
        when (term.op) {
            Op.FLAG -> outcome(syntax, term, registry.apply(into, term.key, emptyList())) {
                "\"${term.key}\" needs a value: ${term.key}=value (${signatures(entry)})"
            }
            Op.NOT -> outcome(syntax, term, registry.apply(into, term.key, listOf(false))) {
                "\"!${term.key}\" applies to boolean properties only"
            }
            Op.EQUALS -> {
                val value = term.value!!
                val literal = if (term.quoted) null else regexLiteral(value)
                if (literal != null) {
                    match(syntax, term, into, literal)
                    return
                }
                val parts = if (term.quoted || ',' !in value) null else value.split(',').map { it.trim() }
                // A vararg property (`prefer`, `action`) also fits the whole value as one argument, so its parts go first (docs/selector/ROADMAP.md Q21).
                // zh-CN: 可变参数属性 (`prefer`, `action`) 也能以整值为单个参数匹配, 因此先按拆分后的各部分施加 (Q21).
                val args = parts?.takeIf { entry.overloads.any { overload -> overload.isVararg } } ?: listOf(value)
                var result = applyCatching(syntax, term) { registry.apply(into, term.key, args, lenient = true) }
                if (result == SelectorRegistry.Outcome.NO_MATCHING_OVERLOAD && parts != null && args !== parts) {
                    result = applyCatching(syntax, term) { registry.apply(into, term.key, parts, lenient = true) }
                }
                outcome(syntax, term, result) { "value ${quote(value)} does not fit \"${term.key}\" (${signatures(entry)})" }
            }
            Op.MATCH -> match(syntax, term, into, term.value!!)
        }
    }

    private fun match(syntax: String, term: Term, into: UiSelector, regex: String) {
        val name = "${term.key}Match"
        if (name !in registry) {
            throw SelectorStringException(syntax, term.position, term.text, emptyList(), "\"${term.key}~=\" applies to string properties only (${matchableProperties.joinToString(", ")})")
        }
        outcome(syntax, term, applyCatching(syntax, term) { registry.apply(into, name, listOf(regex)) }) {
            "regular expression ${quote(regex)} does not fit \"$name\""
        }
    }

    /** A registry call whose filter factory rejects the value (an invalid regular expression, say) reported at the term. zh-CN: 过滤器工厂拒绝值 (如无效的正则) 时在该条件处报告的注册表调用. */
    private fun applyCatching(syntax: String, term: Term, block: () -> SelectorRegistry.Outcome): SelectorRegistry.Outcome = try {
        block()
    } catch (e: IllegalArgumentException) {
        throw SelectorStringException(syntax, term.position, term.text, emptyList(), e.message ?: e.toString())
    }

    private inline fun outcome(syntax: String, term: Term, outcome: SelectorRegistry.Outcome, detail: () -> String) {
        if (outcome != SelectorRegistry.Outcome.APPLIED) throw SelectorStringException(syntax, term.position, term.text, emptyList(), detail())
    }

    /** `bounds(l, t, r, b) | ...`: the overloads of [entry] as a script would write them. zh-CN: [entry] 的各重载, 按脚本的写法. */
    fun signatures(entry: SelectorEntry): String = entry.overloads.joinToString(" | ") { overload ->
        "${entry.name}(${overload.params.joinToString(", ") { "${it.name}: ${it.type.kotlinType}" }})"
    }

    /**
     * Registry names close to [key], nearest first: the same name in another case, names starting
     * with it, names containing it, then names within a small edit distance; at most [limit].
     * zh-CN: 与 [key] 相近的注册表名, 最近的在前: 大小写不同的同名, 以它开头的, 含有它的, 再是编辑距离很小的; 至多 [limit] 个.
     */
    fun suggest(key: String, limit: Int = 5): List<String> = registry.suggestions.suggest(key, limit)

    /**
     * [names] prepared for [suggest]: each name in lower case and the characters it has, worked out once, so that a call
     * only computes the edit distances that can stay within the budget, each row by row until it cannot. Failing calls
     * of `pickup` / `detect` list candidates from about 250 names; lowering every name four times and computing every
     * distance in full took 1.8-3.4 ms per call on XQ-AT72 (docs/augment/ROADMAP.md appendix C Q141).
     * zh-CN: 为 [suggest] 预处理的 [names]: 每个名称的小写形式与所含字符只算一次, 调用时只计算可能不超过预算的编辑距离, 且逐行计算, 超出即停.
     * `pickup` / `detect` 的失败调用要从约 250 个名称中列出候选; 每个名称转小写 4 次且完整计算每个编辑距离时, XQ-AT72 上每次需 1.8-3.4 ms (augment 附录 C Q141).
     */
    class Suggestions(names: Collection<String>) {

        // The first of equal names only: the candidates are distinct, and each keeps the place of its first occurrence.
        // zh-CN: 相同的名称只保留第一个: 候选互不相同, 且各自保持首次出现的位置.
        private val names: Array<String> = LinkedHashSet(names).toTypedArray()
        private val lowered = Array(this.names.size) { this.names[it].lowercase() }
        private val masks = LongArray(this.names.size) { charMask(lowered[it]) }

        /**
         * The names close to [key], nearest first: the same name in another case, names starting with it, names
         * containing it, then names within an edit distance of a third of its length (at least 1), the nearer first;
         * at most [limit]. zh-CN: 与 [key] 相近的名称, 最近的在前: 大小写不同的同名, 以它开头的, 含有它的, 再是编辑距离不超过其长度三分之一 (至少 1) 的,
         * 近的在前; 至多 [limit] 个.
         */
        fun suggest(key: String, limit: Int = 5): List<String> {
            val lower = key.lowercase()
            if (lower.isEmpty()) return emptyList()
            val keyMask = charMask(lower)
            val budget = maxOf(1, key.length / 3)
            val exact = ArrayList<String>(1)
            val prefix = ArrayList<String>()
            val contains = ArrayList<String>()
            // The nearest [limit] other names within the budget, by distance and then in the order of the names.
            // zh-CN: 预算内最近的 [limit] 个其他名称, 按距离, 距离相同时按名称顺序.
            val capacity = maxOf(limit, 0)
            val closeNames = arrayOfNulls<String>(capacity)
            val closeDistances = IntArray(capacity)
            var closeCount = 0
            val rows = Array(2) { IntArray(lower.length + 1) }
            for (i in names.indices) {
                val name = lowered[i]
                val mask = masks[i]
                // A name with a character the key lacks cannot be or contain it. zh-CN: 缺少键中某个字符的名称不可能等于或包含它.
                if (name.length >= lower.length && keyMask and mask.inv() == 0L) {
                    val at = indexOf(name, lower)
                    if (at >= 0) {
                        when {
                            at > 0 -> contains += names[i]
                            name.length == lower.length -> exact += names[i]
                            else -> prefix += names[i]
                        }
                        continue
                    }
                }
                // A later name of the same distance comes after the ones kept, so once [capacity] are kept it has to be nearer.
                // zh-CN: 距离相同的后续名称排在已保留者之后, 因此保留满 [capacity] 个之后, 它必须更近.
                val bound = when {
                    closeCount < capacity -> budget
                    capacity == 0 -> -1
                    else -> minOf(budget, closeDistances[capacity - 1] - 1)
                }
                if (bound < 0 || abs(name.length - lower.length) > bound) continue
                // Every character one string has and the other lacks takes an edit of its own. zh-CN: 一方有而另一方没有的每个字符都需要各自的一次编辑.
                if ((keyMask and mask.inv()).countOneBits() > bound || (mask and keyMask.inv()).countOneBits() > bound) continue
                val distance = editDistance(name, lower, bound, rows)
                if (distance > bound) continue
                // After the kept names of the same or a smaller distance; the farthest drops out when every place is taken.
                // zh-CN: 排在距离相同或更小的已保留名称之后; 位置已满时最远的一个被挤出.
                var at = minOf(closeCount, capacity - 1)
                while (at > 0 && closeDistances[at - 1] > distance) {
                    closeDistances[at] = closeDistances[at - 1]
                    closeNames[at] = closeNames[at - 1]
                    at -= 1
                }
                closeDistances[at] = distance
                closeNames[at] = names[i]
                if (closeCount < capacity) closeCount += 1
            }
            val out = ArrayList<String>(exact.size + prefix.size + contains.size + closeCount)
            out += exact
            out += prefix
            out += contains
            for (k in 0 until closeCount) out += closeNames[k]!!
            return out.take(limit)
        }

    }

    companion object {

        private val REGEX_FLAGS = setOf('g', 'i', 'm', 's', 'u', 'y', 'd')

        /**
         * [names] close to [key], nearest first, by the rules of the instance `suggest` (also the candidates of the
         * result types and node methods of `pickup` / `detect`, docs/selector/ROADMAP.md P1.2); at most [limit]. Names
         * asked again and again go through one [Suggestions] instead.
         * zh-CN: [names] 中与 [key] 相近的名称, 最近的在前, 规则同实例方法 `suggest` (`pickup` / `detect` 的结果类型与节点方法的候选也用它, P1.2); 至多 [limit] 个.
         * 反复查询的名称改用同一个 [Suggestions].
         */
        fun suggest(key: String, names: Collection<String>, limit: Int = 5): List<String> = Suggestions(names).suggest(key, limit)

        /**
         * The characters of [s] as bits: `a`-`z` and `0`-`9` one each, the others hashed onto the rest. Bits one string has and
         * the other lacks count characters that take an edit each (shared bits only make the count smaller).
         * zh-CN: [s] 所含字符的位集: `a`-`z` 与 `0`-`9` 各占一位, 其他字符散列到其余各位. 一方有而另一方没有的位数是各需一次编辑的字符数 (共用的位只会使其更小).
         */
        private fun charMask(s: String): Long {
            var mask = 0L
            for (c in s) {
                val bit = when (c) {
                    in 'a'..'z' -> c - 'a'
                    in '0'..'9' -> 26 + (c - '0')
                    else -> 36 + c.code % 28
                }
                mask = mask or (1L shl bit)
            }
            return mask
        }

        /**
         * Where [key] first occurs in [name], or -1. A loop of its own: `startsWith` / `contains` of kotlin.text stayed
         * interpreted in the debug host on XQ-AT72 and took a fifth of a failing `pickup` (docs/augment/ROADMAP.md appendix C Q141).
         * zh-CN: [key] 在 [name] 中首次出现的位置, 没有时为 -1. 自行循环: kotlin.text 的 `startsWith` / `contains` 在 XQ-AT72 的 debug 宿主上一直解释执行,
         * 占失败的 `pickup` 的五分之一 (augment 附录 C Q141).
         */
        private fun indexOf(name: String, key: String): Int {
            outer@ for (start in 0..name.length - key.length) {
                for (k in 0 until key.length) if (name[start + k] != key[k]) continue@outer
                return start
            }
            return -1
        }

        /**
         * The edit distance of [a] and [b], or a number past [bound] once every entry of a row is (the entries of a row
         * never get smaller than the smallest of the row before); [rows] are two rows for [b], reused.
         * zh-CN: [a] 与 [b] 的编辑距离, 或在某一行全部超过 [bound] 时返回大于 [bound] 的数 (每行的值不小于上一行的最小值); [rows] 为按 [b] 分配, 可复用的两行.
         */
        private fun editDistance(a: String, b: String, bound: Int, rows: Array<IntArray>): Int {
            var previous = rows[0]
            var current = rows[1]
            for (j in 0..b.length) previous[j] = j
            for (i in 1..a.length) {
                current[0] = i
                var smallest = i
                for (j in 1..b.length) {
                    val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                    val value = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + cost)
                    current[j] = value
                    if (value < smallest) smallest = value
                }
                if (smallest > bound) return bound + 1
                val swap = previous
                previous = current
                current = swap
            }
            return previous[b.length]
        }

        /** `/re/flags` as given, or `null` when [value] is not a regular expression literal. zh-CN: 原样的 `/re/flags`, [value] 不是正则字面量时为 `null`. */
        fun regexLiteral(value: String): String? {
            if (value.length < 3 || value[0] != '/') return null
            val last = value.lastIndexOf('/')
            if (last <= 0) return null
            if (value.substring(last + 1).any { it !in REGEX_FLAGS }) return null
            return value
        }

        fun editDistance(a: String, b: String): Int {
            if (a == b) return 0
            if (a.isEmpty()) return b.length
            if (b.isEmpty()) return a.length
            var previous = IntArray(b.length + 1) { it }
            var current = IntArray(b.length + 1)
            for (i in 1..a.length) {
                current[0] = i
                for (j in 1..b.length) {
                    val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                    current[j] = minOf(previous[j] + 1, current[j - 1] + 1, previous[j - 1] + cost)
                }
                val swap = previous
                previous = current
                current = swap
            }
            return previous[b.length]
        }

        private fun quote(s: String) = "\"" + s.replace("\"", "\\\"") + "\""

    }

}

/**
 * A string selector that cannot be parsed or applied: [position] is the 0-based character offset,
 * [term] the offending term (when known) and [candidates] property names close to an unknown key.
 * zh-CN: 无法解析或施加的字符串选择器: [position] 为从 0 起的字符偏移, [term] 为出错的条件 (已知时), [candidates] 为与未知键相近的属性名.
 */
class SelectorStringException(
    val syntax: String,
    val position: Int,
    val term: String?,
    val candidates: List<String>,
    val detail: String,
) : IllegalArgumentException(render(syntax, position, detail, candidates)) {

    companion object {

        private fun render(syntax: String, position: Int, detail: String, candidates: List<String>): String {
            val hint = if (candidates.isEmpty()) "" else "; did you mean ${candidates.joinToString(", ")}?"
            return "Invalid selector string at $position in \"$syntax\": $detail$hint"
        }

    }

}
