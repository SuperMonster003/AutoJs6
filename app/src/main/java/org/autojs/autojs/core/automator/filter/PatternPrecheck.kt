package org.autojs.autojs.core.automator.filter

import java.util.regex.Pattern

/**
 * The pattern precheck of the selector conditions that compile a pattern (augment appendix C Q70 (2), moved here from
 * the new script module layer by docs/selector/ROADMAP.md P1.4; used by `PickArguments` and the global selector
 * functions before `JsRegexUtils.compileJsOrPlainRegex` would compile a pattern): a scan of the regular expression syntax
 * that refuses a pattern only where the engines of both the JVM (`java.util.regex`) and Android (ICU behind
 * `java.util.regex.Pattern`) refuse it, so that a pattern which does not compile costs no Java exception on the way to
 * its RangeError (roadmap R2). It never refuses a pattern that compiles: where the engines differ, or where the scan
 * cannot tell, it stops and the pattern goes to the reader, which compiles it in a `try` as before.
 *
 * Refused, as both engines refuse them (checked on the JVM and on Android 9 / 12, appendix A "Q70 (2)"):
 * - a quantifier with nothing to repeat (at the start, after `(` or `|`, after a flag group such as `(?i)`), a quantifier
 *   after a quantifier (`a**`, `a{2}??`), a `{` that does not start a well-formed interval (`a{`, `a{x}`, `a{,2}`, `a{1 }`)
 *   and an interval whose minimum exceeds its maximum (`a{2,1}`);
 * - a `(` without its `)`, a `)` without its `(`, a `(?` sequence other than `(?:`, `(?=`, `(?!`, `(?>`, `(?<=`, `(?<!`,
 *   `(?<name>` and flags, a group name that is not an ASCII letter followed by ASCII letters and digits, a name defined
 *   twice;
 * - a trailing backslash, an escape letter neither engine knows (`\g`, `\i`, `\y`, `\C`, `\E` without `\Q` ...), `\0`
 *   without an octal digit, `\x` without a hexadecimal digit, a malformed `\x{..}`, `\u` without four hexadecimal
 *   digits, `\U` without eight (the JVM has no `\U`), `\N` and `\p` / `\P` without their braces or with a name the
 *   engine does not know, `\k` other than `\k<name>` of a group opened before;
 * - a `[` without its `]` (a `]` right after `[` or `[^` is a member) and a range whose ends are reversed (`[z-a]`), the
 *   letter escapes a character class does not take (`[\b]`, `[\R]`, `[\X]`, `[\A]` ...).
 *
 * Where the engines differ, nothing is refused: the scan either goes on (the construct ends at the same place in both
 * engines) or stops (it may not). Measured differences (JVM / Android ICU):
 * - `(?#..)` is a comment in ICU and an error on the JVM: stops.
 * - `(?x)`, a flag that changes how the rest is read: stops. Flags `U` and `c` exist only on the JVM, `w` only in ICU, `(?)`
 *   is accepted only by the JVM, `(?--i)` only by ICU: goes on.
 * - `\Q..\E` quotes: stops. `\b{g}` (grapheme boundary) exists only on the JVM: stops.
 * - A quantifier after `^`, `$`, `\b`, `\B`, `\A`, `\G`, `\z`, `\Z` or a lookaround: the engines take different ones: stops.
 * - An interval after a quantifier (`a*{2}`) or at the start (`{1}`) and a lone `}`: accepted only by the JVM: goes on.
 * - An unbounded lookbehind (`(?<=a*)`): accepted only by the JVM: goes on (not refused).
 * - A back reference to a group that does not exist (`(a)\2`): accepted only by the JVM: goes on.
 * - `\c` at the end, `\x` with one hexadecimal digit (`\x4`), `\U` with eight: accepted only by ICU: goes on. The control
 *   character of `\cX` differs (the JVM takes `X ^ 64`, ICU `X & 31`), so it is no end of a range that is compared.
 * - `\p` / `\P` and `\N{..}` names differ widely (ICU takes `\p{Greek}`, `\p{Any}`, loose names; the JVM takes `\pL`,
 *   `\p{IsLatin}`); each name is therefore compiled once by the running engine and the verdict kept (at most [NAMES] names).
 * - In a character class: `--` (a difference in ICU, a range on the JVM): stops; `[:` (a POSIX class in ICU): stops;
 *   `&&` at either end of a class or of an intersection (`[a&&]`) is accepted only by the JVM: goes on; a range whose end
 *   is a set (`[a-\p{L}]`) is accepted only by ICU: goes on; an escape ICU does not know at the end of a range is read
 *   as its letter (`[A-\C]`, `[0-\p{NoSuch}]` compile in ICU): nothing there is refused, the scan stops; `[\1]` is
 *   accepted only by ICU: stops.
 * - The octal escape `\0mnn` takes at most three digits on the JVM: inside a class, a fourth octal digit stops.
 *
 * The descriptions are those of `java.util.regex.PatternSyntaxException`. The JS literal form `/source/flags` is checked by
 * its source, as `JsRegexUtils.compileJsOrPlainRegex` compiles it.
 *
 * zh-CN: 编译正则的选择器条件的正则预检 (augment 附录 C Q70 (2), 由 P1.4 从新脚本模块层迁至此处; `PickArguments` 与全局选择器函数在
 * JsRegexUtils.compileJsOrPlainRegex 编译之前使用): 逐字符扫描正则语法, 只拒绝 JVM (java.util.regex) 与 Android (java.util.regex.Pattern
 * 背后的 ICU) 两种引擎都拒绝的写法, 使无法编译的正则在报 RangeError 之前不经过 Java 异常 (R2). 不拒绝任何能编译的正则: 引擎有差异或扫描判断不了之处,
 * 扫描停止, 交给读取方照旧在 try 中编译.
 * 拒绝的写法 (两种引擎都拒绝, 已在 JVM 与 Android 9 / 12 上核对, 见附录 A "Q70 (2)"): 没有可重复内容的量词 (开头, ( 或 | 之后, (?i) 等标志组之后),
 * 量词之后的量词, 不构成合法区间的 {, 下限大于上限的区间; 不配对的括号, 未知的 (? 序列, 不合法或重复的组名; 结尾的反斜杠, 两种引擎都不认识的转义字母,
 * \0 / \x / \x{..} / \u / \U / \N / \p / \k 的格式错误与引用不存在的组; 未闭合的 [ (紧跟 [ 或 [^ 的 ] 是成员), 端点逆序的区间, 字符类不接受的字母转义.
 * 引擎有差异之处一律不拒绝: 构造在两种引擎中结束位置相同时继续扫描, 否则停止. 实测的差异逐条列在上面的英文部分; \p / \N 的名称差异很大, 每个名称由当前引擎
 * 编译一次并记住结果 (最多 [NAMES] 个). 描述沿用 java.util.regex.PatternSyntaxException 的写法.
 */
internal object PatternPrecheck {

    /** The names of `\p` / `\P` / `\N` whose verdict is kept. zh-CN: 记住结果的 \p / \P / \N 名称个数. */
    private const val NAMES = 64

    /** Groups nested deeper than this are not scanned (the engines parse recursively too). */
    private const val DEPTH = 200

    private const val BACKSLASH = '\\'

    /** Escape letters outside a character class neither engine knows. zh-CN: 字符类之外两种引擎都不认识的转义字母. */
    private const val UNKNOWN_LETTERS = "gijlmoqyCEFIJKLMOTY"

    /** Escape letters inside a character class neither engine takes. zh-CN: 字符类之内两种引擎都不接受的转义字母. */
    private const val UNKNOWN_CLASS_LETTERS = "bgijklmoqyzABCEFGIJKLMORTXYZ"

    /** Escapes outside a class that match an empty string: no quantifier may be taken for granted after them. */
    private const val ZERO_WIDTH_LETTERS = "bBAGzZ"

    /** Escapes of a set of characters (a class or a class member that is no single character). */
    private const val SET_LETTERS = "dDsSwWhHvV"

    /** The inline flags the scan reads; `x` stops it. zh-CN: 扫描能读的内联标志, 遇 x 停止. */
    private const val FLAGS = "imsxduUcw-"

    /** The verdicts of the names compiled (escape -> compiles), most recently used last. */
    private val names = object : LinkedHashMap<String, Boolean>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?) = size > NAMES
    }

    /** Why [pattern] does not compile on either engine, or null when it may compile. zh-CN: [pattern] 在两种引擎上都无法编译的原因, 可能能编译时为 null. */
    fun refusal(pattern: String): String? = Scan(pattern).run()

    /** Whether the engine running compiles the property or name escape [escape] (`\p{..}`, `\pL`, `\N{..}`), in a class when [inClass]. */
    private fun nameCompiles(escape: String, inClass: Boolean): Boolean {
        val key = if (inClass) "[$escape]" else escape
        synchronized(names) { names[key]?.let { return it } }
        val compiles = try {
            Pattern.compile(key)
            true
        } catch (e: IllegalArgumentException) {
            // PatternSyntaxException, or IllegalArgumentException from the ICU property lookup.
            false
        }
        synchronized(names) { names[key] = compiles }
        return compiles
    }

    /** What a scanned piece of a sequence leaves for a quantifier after it. */
    private enum class Last {
        /** Nothing to repeat: the start of a sequence, or after a flag group. */
        NONE,

        /** A character, class or group that a quantifier repeats. */
        ATOM,

        /** A quantifier: another quantifier is dangling. */
        QUANTIFIED,

        /** An anchor or a lookaround: the engines take different quantifiers after it. */
        ZERO,
    }

    /**
     * One scan of one pattern. Each step returns false to end the scan: [refusal] is then set, or null when the scan
     * stops where it cannot tell.
     */
    private class Scan(input: String) {

        // JsRegexUtils.compileJsOrPlainRegex compiles the source of a literal (`/source/flags`), any other string as it is.
        private val s = JsRegexUtils.parseJsRegexLiteral(input)?.first ?: input
        private val n = s.length
        private var i = 0
        private var depth = 0
        var refusal: String? = null

        /** The group names opened so far, created with the first one. */
        private var groupNames: HashSet<String>? = null

        fun run(): String? {
            // The JVM removes `\Q..\E` from the whole pattern before it parses it (Pattern.RemoveQEQuoting), so the quoted text
            // may complete an escape or an interval before it (`\u004\Q1\E`, `a{\Q1\E}`); ICU quotes in place.
            var k = 0
            while (k < n - 1) {
                if (s[k] == BACKSLASH) {
                    if (s[k + 1] == 'Q') return null
                    k += 2
                } else {
                    k++
                }
            }
            if (!alternation()) return refusal
            // alternation stops at a `)` it does not close or at the end.
            if (i < n) return refuse(if (i > 0) "Unmatched closing ')' near index ${i - 1}" else "Unmatched closing ')'")
            return null
        }

        private fun refuse(description: String): String {
            refusal = description
            return description
        }

        /** Refuses for [description] and ends the scan, as a step that returns a Boolean. */
        private fun no(description: String): Boolean {
            refuse(description)
            return false
        }

        /** Refuses for [description] and ends the scan, as a step that returns a value. */
        private fun fail(description: String): Nothing? {
            refuse(description)
            return null
        }

        /** Ends the scan without a refusal: the engines differ here, or the scan cannot tell. */
        private fun halt(): Nothing? = null

        private fun dangling(index: Int) = fail("Dangling meta character '${s[index]}' near index $index")

        private fun at(k: Int): Char = if (k < n) s[k] else '\u0000'

        /** Sequences separated by `|` up to a `)` or the end. */
        private fun alternation(): Boolean {
            while (true) {
                if (!sequence()) return false
                if (i < n && s[i] == '|') {
                    i++
                    continue
                }
                return true
            }
        }

        private fun sequence(): Boolean {
            var last = Last.NONE
            while (i < n) {
                val c = s[i]
                last = when (c) {
                    '|', ')' -> return true
                    '(' -> group() ?: return false
                    '[' -> {
                        if (!characterClass()) return false
                        Last.ATOM
                    }
                    BACKSLASH -> escape() ?: return false
                    '^', '$' -> {
                        i++
                        Last.ZERO
                    }
                    '*', '+', '?', '{' -> quantifier(last) ?: return false
                    else -> {
                        i += Character.charCount(s.codePointAt(i))
                        Last.ATOM
                    }
                }
            }
            return true
        }

        /** The quantifier at [i] after [last]; the new [Last] or null to end the scan. */
        private fun quantifier(last: Last): Last? {
            val c = s[i]
            when (last) {
                Last.ZERO -> return halt()
                Last.NONE -> {
                    if (c != '{') return dangling(i)
                    // `{1}` at the start is taken by the JVM only (as what, the scan does not follow); any other `{` is an error in both.
                    return if (isDigit(at(i + 1))) halt() else fail("Illegal repetition near index ${i - 1}")
                }
                Last.QUANTIFIED -> {
                    if (c != '{') return dangling(i)
                    // An interval after a quantifier is taken by the JVM only; a malformed one is refused by both.
                    interval() ?: return null
                    return halt()
                }
                Last.ATOM -> {
                    if (c == '{') {
                        interval() ?: return null
                    } else {
                        i++
                    }
                    // A lazy or possessive quantifier.
                    if (i < n && (s[i] == '?' || s[i] == '+')) i++
                    return Last.QUANTIFIED
                }
            }
        }

        /**
         * The interval `{n}`, `{n,}` or `{n,m}` at [i]: true when it is well-formed, null (refused) otherwise or when the
         * minimum exceeds the maximum. Numbers of more than nine digits are not compared (the engines differ in range).
         */
        private fun interval(): Boolean? {
            val start = i
            var k = i + 1
            val minStart = k
            while (k < n && isDigit(s[k])) k++
            if (k == minStart) return fail("Illegal repetition near index ${start - 1}")
            val minEnd = k
            var maxStart = -1
            var maxEnd = -1
            if (k < n && s[k] == ',') {
                k++
                maxStart = k
                while (k < n && isDigit(s[k])) k++
                maxEnd = k
            }
            if (k >= n || s[k] != '}') return fail("Unclosed counted closure near index $k")
            if (maxStart in 0 until maxEnd && minEnd - minStart <= 9 && maxEnd - maxStart <= 9) {
                val min = s.substring(minStart, minEnd).toInt()
                val max = s.substring(maxStart, maxEnd).toInt()
                if (min > max) return fail("Illegal repetition range near index $k")
            }
            i = k + 1
            return true
        }

        /** The group at [i]; what it leaves for a quantifier, or null to end the scan. */
        private fun group(): Last? {
            val open = i
            if (++depth > DEPTH) return halt()
            val after: Last
            when (at(open + 1)) {
                '*', '+' -> return dangling(open + 1)
                '?' -> {
                    val c = at(open + 2)
                    when {
                        c == ':' || c == '>' -> {
                            i = open + 3
                            after = Last.ATOM
                        }
                        c == '=' || c == '!' -> {
                            i = open + 3
                            after = Last.ZERO
                        }
                        c == '<' && (at(open + 3) == '=' || at(open + 3) == '!') -> {
                            // A lookbehind; its bounds are not checked (the JVM takes unbounded ones, ICU does not).
                            i = open + 4
                            after = Last.ZERO
                        }
                        c == '<' -> {
                            if (!groupName(open + 3)) return null
                            after = Last.ATOM
                        }
                        c == '#' -> return halt()
                        c == ')' || c in FLAGS -> return flags(open)
                        open + 2 >= n -> return fail("Unknown group type near index ${open + 1}")
                        else -> return fail("Unknown inline modifier near index ${open + 2}")
                    }
                }
                else -> {
                    i = open + 1
                    after = Last.ATOM
                }
            }
            if (!alternation()) return null
            if (i >= n) return fail("Unclosed group near index $n")
            // At the `)` of this group.
            i++
            depth--
            return after
        }

        /** The name of `(?<name>` at [start], up to and past its `>`. */
        private fun groupName(start: Int): Boolean {
            var k = start
            if (!isAsciiLetter(at(k))) return no("capturing group name does not start with a Latin letter near index $k")
            while (k < n && (isAsciiLetter(s[k]) || isDigit(s[k]))) k++
            if (at(k) != '>') return no("named capturing group is missing trailing '>' near index $k")
            val name = s.substring(start, k)
            val defined = groupNames ?: HashSet<String>().also { groupNames = it }
            if (!defined.add(name)) return no("Named capturing group <$name> is already defined near index $k")
            i = k + 1
            return true
        }

        /** The flags of `(?flags)` or `(?flags:` at [open]. */
        private fun flags(open: Int): Last? {
            var k = open + 2
            var off = false
            while (k < n && s[k] in FLAGS) {
                when (s[k]) {
                    '-' -> off = true
                    // Free spacing changes how the rest is read (comments, spaces), differently in the two engines.
                    'x' -> if (!off) return halt()
                }
                k++
            }
            return when (at(k)) {
                ')' -> {
                    i = k + 1
                    depth--
                    // A flag group repeats nothing: a quantifier after it is dangling in both engines.
                    Last.NONE
                }
                ':' -> {
                    i = k + 1
                    if (!alternation()) return null
                    if (i >= n) return fail("Unclosed group near index $n")
                    i++
                    depth--
                    Last.ATOM
                }
                else -> fail(if (k >= n) "Unknown group type near index ${k - 1}" else "Unknown inline modifier near index $k")
            }
        }

        /** The escape at [i] outside a character class; what it leaves for a quantifier, or null to end the scan. */
        private fun escape(): Last? {
            val start = i
            if (start + 1 >= n) return fail("Unescaped trailing backslash near index $n")
            val c = s[start + 1]
            i = start + 2
            when {
                c in UNKNOWN_LETTERS -> return fail("Illegal/unsupported escape sequence near index ${start + 1}")
                c == 'b' || c == 'B' -> return if (at(i) == '{') halt() else Last.ZERO
                c in ZERO_WIDTH_LETTERS -> return Last.ZERO
                c == 'Q' -> return halt()
                c == 'k' -> return if (reference(start)) Last.ATOM else null
                c in '1'..'9' -> {
                    // A back reference: the JVM takes one to a group that does not exist, ICU does not. Its digits are literal either way.
                    while (i < n && isDigit(s[i])) i++
                    return Last.ATOM
                }
                else -> return if (character(start, c, inClass = false) == null) null else Last.ATOM
            }
        }

        /** `\k<name>` at [start]: the name of a group opened before. */
        private fun reference(start: Int): Boolean {
            if (at(start + 2) != '<') return no("\\k is not followed by '<' for named capturing group near index ${start + 2}")
            var k = start + 3
            if (!isAsciiLetter(at(k))) return no("capturing group name does not start with a Latin letter near index $k")
            while (k < n && (isAsciiLetter(s[k]) || isDigit(s[k]))) k++
            if (at(k) != '>') return no("named capturing group is missing trailing '>' near index $k")
            val name = s.substring(start + 3, k)
            if (groupNames?.contains(name) != true) return no("named capturing group <$name> does not exist near index $k")
            i = k + 1
            return true
        }

        /**
         * The escape `\c` at [start] (its letter already read) that stands for one character or a set, in a class when
         * [inClass]; [i] is past the letter. The code point of the character, [SET] for a set, [UNKNOWN] for a character
         * whose code point the engines may read differently, or null to end the scan.
         */
        private fun character(start: Int, c: Char, inClass: Boolean): Int? {
            return when (c) {
                't' -> '\t'.code
                'n' -> '\n'.code
                'r' -> '\r'.code
                'f' -> 0x0C
                'a' -> 0x07
                'e' -> 0x1B
                in SET_LETTERS -> SET
                'R', 'X' -> SET
                '0' -> octal(start, inClass)
                'x' -> hexadecimal(start)
                'u' -> utf16(start)
                'U' -> utf32(start)
                'c' -> {
                    // `\cX`: ICU takes `\c` at the end, the JVM does not. The two read X differently.
                    if (i < n) i += Character.charCount(s.codePointAt(i))
                    UNKNOWN
                }
                'N' -> named(start, inClass)
                'p', 'P' -> property(start, inClass)
                else -> {
                    if (isAsciiLetter(c) || isDigit(c)) return fail("Illegal/unsupported escape sequence near index ${start + 1}")
                    // Any other character stands for itself; a surrogate pair is one character.
                    val cp = s.codePointAt(start + 1)
                    i = start + 1 + Character.charCount(cp)
                    cp
                }
            }
        }

        /** `\0` at [start]: one to three octal digits (`\0mnn` with m at most 3, as the JVM reads it). */
        private fun octal(start: Int, inClass: Boolean): Int? {
            if (!isOctal(at(i))) return fail("Illegal octal escape sequence near index ${start + 1}")
            val first = s[i]
            var digits = 1
            i++
            while (digits < (if (first <= '3') 3 else 2) && isOctal(at(i))) {
                i++
                digits++
            }
            // ICU may read a further octal digit into the escape; in a class that moves the end of a range.
            if (inClass && isOctal(at(i))) return halt()
            return UNKNOWN
        }

        /** `\xhh`, `\x{h..}` at [start]. */
        private fun hexadecimal(start: Int): Int? {
            if (at(i) == '{') {
                val close = s.indexOf('}', i + 1)
                if (close < 0) return fail("Unclosed hexadecimal escape sequence near index ${n - 1}")
                val digits = s.substring(i + 1, close)
                if (digits.isEmpty() || !digits.all(::isHex)) return fail("Illegal hexadecimal escape sequence near index ${i + 1}")
                val significant = digits.trimStart('0')
                if (significant.length > 6 || significant.isNotEmpty() && significant.toInt(16) > Character.MAX_CODE_POINT) {
                    return fail("Hexadecimal codepoint is too big near index $close")
                }
                i = close + 1
                // The JVM takes any number of leading zeros, ICU at most eight digits.
                return if (digits.length > 8) UNKNOWN else significant.ifEmpty { "0" }.toInt(16)
            }
            if (!isHex(at(i))) return fail("Illegal hexadecimal escape sequence near index ${i - 1}")
            if (!isHex(at(i + 1))) {
                // `\xh`: ICU takes one digit, the JVM does not.
                i++
                return UNKNOWN
            }
            val cp = s.substring(i, i + 2).toInt(16)
            i += 2
            return cp
        }

        /** `\uhhhh` at [start]; a high surrogate escape followed by a low surrogate escape is one character. */
        private fun utf16(start: Int): Int? {
            val unit = hexDigits(i, 4) ?: return fail("Illegal Unicode escape sequence near index ${i - 1}")
            i += 4
            if (Character.isHighSurrogate(unit.toChar()) && at(i) == BACKSLASH && at(i + 1) == 'u') {
                val low = hexDigits(i + 2, 4)
                if (low != null && Character.isLowSurrogate(low.toChar())) {
                    i += 6
                    return Character.toCodePoint(unit.toChar(), low.toChar())
                }
            }
            return unit
        }

        /** `\Uhhhhhhhh` at [start]: ICU only; the JVM refuses any `\U`. */
        private fun utf32(start: Int): Int? {
            val cp = hexDigits(i, 8)
            if (cp == null || cp > Character.MAX_CODE_POINT) return fail("Illegal/unsupported escape sequence near index ${start + 1}")
            i += 8
            return cp
        }

        /** `\N{name}` at [start]. */
        private fun named(start: Int, inClass: Boolean): Int? {
            if (at(i) != '{') return fail("Illegal/unsupported escape sequence near index ${start + 1}")
            val close = s.indexOf('}', i + 1)
            if (close < 0) return fail("Unclosed character name escape sequence near index ${n - 1}")
            i = close + 1
            if (!nameCompiles(s.substring(start, i), inClass)) return fail("Unknown character name [${s.substring(start + 3, close)}] near index $close")
            return UNKNOWN
        }

        /** `\pL`, `\p{name}` (or `\P`) at [start]. */
        private fun property(start: Int, inClass: Boolean): Int? {
            when {
                at(i) == '{' -> {
                    val close = s.indexOf('}', i + 1)
                    if (close < 0) return fail("Unclosed character family near index ${n - 1}")
                    i = close + 1
                }
                i >= n -> return fail("Illegal character family near index ${n - 1}")
                // `\pL`: a one letter name. Other characters there are not read the same way by both engines.
                isAsciiLetter(s[i]) -> i++
                else -> return halt()
            }
            if (!nameCompiles(s.substring(start, i), inClass)) return fail("Unknown character property name near index ${i - 1}")
            return SET
        }

        /**
         * The character class at [i], up to and past its `]`; nested classes and intersections included. Members are
         * characters, ranges of characters and sets; a range whose ends are both characters is checked for their order.
         */
        private fun characterClass(): Boolean {
            if (++depth > DEPTH) return false
            i++
            if (at(i) == '^') i++
            // A `]` right after `[` or `[^` is a member in both engines.
            var previous = NOTHING
            if (at(i) == ']') {
                previous = ']'.code
                i++
            }
            while (true) {
                if (i >= n) return no("Unclosed character class near index ${n - 1}")
                when (val c = s[i]) {
                    ']' -> {
                        i++
                        depth--
                        return true
                    }
                    '[' -> {
                        // `[:alpha:]` is a POSIX class in ICU and a nested class on the JVM.
                        if (at(i + 1) == ':') return false
                        if (!characterClass()) return false
                        previous = SET
                    }
                    '&' -> {
                        i++
                        if (at(i) == '&') {
                            // An intersection; at either end of the class the engines differ, but neither refuses more there.
                            i++
                            previous = NOTHING
                        } else {
                            previous = '&'.code
                        }
                    }
                    '-' -> {
                        // `--` is a set difference in ICU and a range on the JVM.
                        if (at(i + 1) == '-') return false
                        if (previous == NOTHING || previous == SET || at(i + 1) == ']' || at(i + 1) == '[') {
                            // A member: at the start, after a set, before the end or before a nested class.
                            i++
                            previous = '-'.code
                        } else {
                            i++
                            val high = classMember()
                            if (high == null) {
                                // ICU reads an escape it does not know at the end of a range as its letter (`[A-\C]`,
                                // `[0-\p{NoSuch}]`), so nothing there is refused.
                                refusal = null
                                return false
                            }
                            when {
                                // A set at the end: the JVM refuses it, ICU takes it (`[a-\p{L}]`).
                                high == SET || high == UNKNOWN || previous == UNKNOWN -> Unit
                                previous > high -> return no("Illegal character range near index ${i - 1}")
                            }
                            previous = NOTHING
                        }
                    }
                    else -> {
                        if (c == BACKSLASH) {
                            previous = classMember() ?: return false
                        } else {
                            val cp = s.codePointAt(i)
                            i += Character.charCount(cp)
                            previous = cp
                        }
                    }
                }
            }
        }

        /** One member of a class at [i] (a character or an escape); its code point, [SET], [UNKNOWN] or null to end the scan. */
        private fun classMember(): Int? {
            if (i >= n) return fail("Unclosed character class near index ${n - 1}")
            if (s[i] != BACKSLASH) {
                val cp = s.codePointAt(i)
                i += Character.charCount(cp)
                return cp
            }
            val start = i
            if (start + 1 >= n) return fail("Unclosed character class near index ${n - 1}")
            val c = s[start + 1]
            i = start + 2
            return when {
                c in UNKNOWN_CLASS_LETTERS -> fail("Illegal/unsupported escape sequence near index ${start + 1}")
                c == 'Q' -> halt()
                // A back reference has no meaning in a class: the JVM refuses it, ICU reads it as an octal escape.
                c in '1'..'9' -> halt()
                else -> character(start, c, inClass = true)
            }
        }

        /** The value of [count] hexadecimal digits at [k], or null when there are fewer. */
        private fun hexDigits(k: Int, count: Int): Int? {
            if (k + count > n) return null
            var v = 0L
            for (j in k until k + count) {
                val d = Character.digit(s[j], 16)
                if (d < 0 || !isHex(s[j])) return null
                v = v * 16 + d
            }
            return if (v > Int.MAX_VALUE) Int.MAX_VALUE else v.toInt()
        }

        companion object {
            /** A member that is a set of characters. */
            const val SET = -1

            /** A character whose code point the engines may read differently. */
            const val UNKNOWN = -2

            /** No member before (the start of a class, after a range or an intersection). */
            const val NOTHING = -3

            fun isDigit(c: Char) = c in '0'..'9'
            fun isOctal(c: Char) = c in '0'..'7'
            fun isHex(c: Char) = c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'
            fun isAsciiLetter(c: Char) = c in 'a'..'z' || c in 'A'..'Z'
        }

    }

}
