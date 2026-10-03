package org.autojs.autojs.core.accessibility.inspector

import org.autojs.autojs.core.accessibility.UiSelector
import org.autojs.autojs.core.automator.filter.JsLiteral
import org.autojs.autojs.core.automator.selector.SelectorArgType
import org.autojs.autojs.core.automator.selector.SelectorEntry
import org.autojs.autojs.core.automator.selector.SelectorRegistry
import org.autojs.autojs.core.automator.selector.SelectorStringParser

/**
 * The selector mode parser (roadmap P5.1, D12): reads a chained selector expression the way a
 * script would write it, `text("OK").clickable().depth(3)`, and applies every call to a
 * [UiSelector] through [SelectorRegistry.apply]. Literals are single or double quoted strings
 * (with escapes), integers and decimals, `true` / `false` and regular expression literals
 * `/re/flags`; an optional `selector()` / `$()` prefix is skipped. Nothing is evaluated by Rhino:
 * the expression is re-parsed on every keystroke, so it has to be predictable and free of side
 * effects.
 *
 * Regular expression literals never become `NativeRegExp` (that needs a Rhino context): they are
 * handed to the string overload of the matching `...Match` / `...Matches` method, whose
 * [org.autojs.autojs.core.automator.filter.StringMatcher] compiles the JS literal with its flags.
 * `textContains(/re/)` becomes `textMatch("/re/")` (both search) and `text(/re/)` becomes
 * `textMatches("/re/")` (both match the whole value), so the semantics of the script call are kept
 * and the rendered filter stays a legal selector. A literal where a script would not accept a
 * `RegExp` (say `textStartsWith(/x/)`) is an error, as it would be in a script.
 *
 * An expression that stops in the middle of a call (`text("Lo`, `text(`, `text("Login").`) raises
 * [SelectorCallException] with `incomplete = true` so that a live evaluator can say "expression
 * not finished" instead of reporting a mistake the user has not made yet; a name is only checked
 * against the registry once its `(` has been typed.
 *
 * zh-CN: 选择器模式解析器 (Roadmap P5.1, D12): 按脚本写法读取链式选择器表达式 `text("确定").clickable().depth(3)`, 每个调用经
 * [SelectorRegistry.apply] 施加到 [UiSelector]. 字面量为单 / 双引号字符串 (含转义), 整数与小数, `true` / `false` 与正则字面量
 * `/re/flags`; 可选前缀 `selector()` / `$()` 被跳过. 不经 Rhino 求值: 每次击键都重新解析, 需要可预测且无副作用.
 *
 * 正则字面量不会变成 `NativeRegExp` (需要 Rhino 上下文): 交给对应 `...Match` / `...Matches` 方法的字符串重载, 由其
 * [org.autojs.autojs.core.automator.filter.StringMatcher] 按带标志的 JS 字面量编译. `textContains(/re/)` 变为 `textMatch("/re/")`
 * (都是搜索), `text(/re/)` 变为 `textMatches("/re/")` (都是整体匹配), 既保留脚本调用的语义, 渲染出的过滤器也仍是合法选择器.
 * 脚本不接受 `RegExp` 的位置 (如 `textStartsWith(/x/)`) 同样报错.
 *
 * 表达式停在调用中途 (`text("Lo`, `text(`, `text("Login").`) 时抛出 `incomplete = true` 的 [SelectorCallException], 便于边输入边
 * 求值时提示 "表达式未结束" 而非误报; 方法名只在键入 `(` 之后才对照注册表检查.
 *
 * Created by SuperMonster003 on Oct 2, 2026.
 */
class SelectorCallParser(private val registry: SelectorRegistry = SelectorRegistry) {

    private val strings = SelectorStringParser(registry)

    /** One `name(args)` of the chain; [text] is its source text and [position] the offset of [name]. zh-CN: 链中的一个 `name(args)`; [text] 为其源文本, [position] 为 [name] 的偏移. */
    class Call(val name: String, val args: List<Any?>, val position: Int, val text: String)

    /** A `/re/flags` argument before it is mapped to a string overload. zh-CN: 映射到字符串重载之前的 `/re/flags` 参数. */
    class RegexLiteral(val literal: String) {
        override fun toString() = literal
    }

    /** Applies every call of [expression] to [into] and returns it. zh-CN: 把 [expression] 的每个调用施加到 [into] 并返回它. */
    fun parse(expression: String, into: UiSelector): UiSelector {
        for (call in calls(expression)) apply(expression, call, into)
        return into
    }

    /**
     * The calls of [expression] with the optional prefix dropped. A name is checked against the
     * registry as soon as its `(` is read, so `clikable(` is "unknown" while `clikable` is still
     * incomplete; arguments are only checked by [parse].
     * zh-CN: [expression] 的各调用 (可选前缀已去掉). 方法名在读到其 `(` 时即对照注册表检查, 因此 `clikable(` 为 "未知" 而 `clikable`
     * 仍为未结束; 参数只由 [parse] 检查.
     */
    fun calls(expression: String): List<Call> {
        val lexer = Lexer(expression)
        val calls = ArrayList<Call>()
        if (lexer.peek().type == T.END) return calls
        var first = true
        while (true) {
            val name = lexer.next()
            when (name.type) {
                T.IDENT -> Unit
                T.END -> throw lexer.incomplete(name.position)
                else -> throw lexer.error(name.position, "expected a selector method name")
            }
            val open = lexer.next()
            when (open.type) {
                T.LPAREN -> Unit
                T.END -> throw lexer.incomplete(open.position)
                else -> throw lexer.error(open.position, "expected \"(\" after \"${name.value}\"")
            }
            val mayBePrefix = first && (name.value == PREFIX_SELECTOR || name.value == PREFIX_DOLLAR)
            if (!mayBePrefix && name.value as String !in registry) throw unknown(expression, name.position, name.value)
            val args = ArrayList<Any?>()
            var close = lexer.peek()
            if (close.type == T.RPAREN) {
                lexer.next()
            } else {
                while (true) {
                    val arg = lexer.next()
                    when (arg.type) {
                        T.STRING, T.NUMBER, T.BOOLEAN, T.REGEX -> args += arg.value
                        T.END -> throw lexer.incomplete(arg.position)
                        else -> throw lexer.error(arg.position, "expected a string, number, boolean or regular expression literal")
                    }
                    close = lexer.next()
                    when (close.type) {
                        T.COMMA -> continue
                        T.RPAREN -> break
                        T.END -> throw lexer.incomplete(close.position)
                        else -> throw lexer.error(close.position, "expected \",\" or \")\"")
                    }
                }
            }
            val callName = name.value as String
            val isPrefix = first && args.isEmpty() && (callName == PREFIX_SELECTOR || callName == PREFIX_DOLLAR)
            if (!isPrefix) {
                if (callName !in registry) throw unknown(expression, name.position, callName)
                calls += Call(callName, args, name.position, expression.substring(name.position, close.end))
            }
            first = false
            val after = lexer.next()
            when (after.type) {
                T.END -> return calls
                T.DOT -> Unit
                else -> throw lexer.error(after.position, "expected \".\" between calls")
            }
        }
    }

    private fun unknown(expression: String, position: Int, name: String) =
        SelectorCallException(expression, position, name, strings.suggest(name), "unknown selector method \"$name\"")

    private fun apply(expression: String, call: Call, into: UiSelector) {
        val entry = registry.entry(call.name) ?: throw unknown(expression, call.position, call.name)
        var name = call.name
        var args: List<Any?> = call.args
        if (args.any { it is RegexLiteral }) {
            if (args.size != 1 || !acceptsRegex(entry)) {
                throw SelectorCallException(expression, call.position, call.text, emptyList(), "a regular expression literal does not fit \"${call.name}\" (${strings.signatures(entry)})")
            }
            name = regexTarget(call.name)
            if (name !in registry) {
                throw SelectorCallException(expression, call.position, call.text, emptyList(), "a regular expression literal does not fit \"${call.name}\"")
            }
            args = listOf((args[0] as RegexLiteral).literal)
        }
        val outcome = try {
            registry.apply(into, name, args)
        } catch (e: IllegalArgumentException) {
            throw SelectorCallException(expression, call.position, call.text, emptyList(), e.message ?: e.toString())
        }
        if (outcome != SelectorRegistry.Outcome.APPLIED) {
            throw SelectorCallException(expression, call.position, call.text, emptyList(), "arguments (${call.args.joinToString(", ", transform = ::literal)}) do not fit \"${call.name}\" (${strings.signatures(entry)})")
        }
    }

    /** Whether a script could pass a `RegExp` as the single argument of [entry]. zh-CN: 脚本能否把 `RegExp` 作为 [entry] 的唯一参数传入. */
    private fun acceptsRegex(entry: SelectorEntry): Boolean = entry.overloads.any { it.params.size == 1 && it.params[0].type == SelectorArgType.REGEX }

    private fun regexTarget(name: String): String = when {
        name.endsWith("Match") || name.endsWith("Matches") -> name
        name.endsWith("Contains") -> name.removeSuffix("Contains") + "Match"
        else -> name + "Matches"
    }

    private enum class T { IDENT, DOT, LPAREN, RPAREN, COMMA, STRING, NUMBER, BOOLEAN, REGEX, END }

    private class Token(val type: T, val position: Int, val end: Int, val value: Any? = null)

    /** Tokens on demand, so that an error is reported where the reader actually stops. zh-CN: 按需产出词法单元, 错误报告在读取实际停下的位置. */
    private class Lexer(private val s: String) {

        private var index = 0

        private var buffered: Token? = null

        fun peek(): Token = buffered ?: read().also { buffered = it }

        fun next(): Token = buffered?.also { buffered = null } ?: read()

        fun error(position: Int, detail: String) = SelectorCallException(s, position, null, emptyList(), detail)

        fun incomplete(position: Int) = SelectorCallException(s, position, null, emptyList(), "expression not finished", incomplete = true)

        private fun read(): Token {
            while (index < s.length && s[index].isWhitespace()) index++
            if (index >= s.length) return Token(T.END, s.length, s.length)
            val start = index
            val c = s[index]
            return when {
                c == '.' -> single(T.DOT)
                c == '(' -> single(T.LPAREN)
                c == ')' -> single(T.RPAREN)
                c == ',' -> single(T.COMMA)
                c == '"' || c == '\'' -> string(c)
                c == '/' -> regex()
                c.isDigit() || (c == '-' && index + 1 < s.length && s[index + 1].isDigit()) -> number()
                isIdentifierStart(c) -> identifier()
                else -> throw error(start, "unexpected character \"$c\"")
            }
        }

        private fun single(type: T): Token = Token(type, index, index + 1).also { index++ }

        private fun string(quote: Char): Token {
            val start = index
            val sb = StringBuilder()
            index++
            while (index < s.length) {
                when (val c = s[index++]) {
                    quote -> return Token(T.STRING, start, index, sb.toString())
                    '\\' -> {
                        if (index >= s.length) break
                        when (val e = s[index++]) {
                            'n' -> sb.append('\n')
                            't' -> sb.append('\t')
                            'r' -> sb.append('\r')
                            'b' -> sb.append('\b')
                            'f' -> sb.append('\u000C')
                            '0' -> sb.append('\u0000')
                            'u' -> {
                                val hex = s.substring(index, minOf(index + 4, s.length))
                                val code = hex.takeIf { it.length == 4 }?.toIntOrNull(16) ?: throw error(index - 2, "invalid unicode escape")
                                sb.append(code.toChar())
                                index += 4
                            }
                            else -> sb.append(e)
                        }
                    }
                    else -> sb.append(c)
                }
            }
            throw incomplete(start)
        }

        private fun regex(): Token {
            val start = index
            index++
            var escaped = false
            var inClass = false
            while (index < s.length) {
                val c = s[index++]
                when {
                    escaped -> escaped = false
                    c == '\\' -> escaped = true
                    c == '[' -> inClass = true
                    c == ']' && inClass -> inClass = false
                    c == '/' && !inClass -> {
                        while (index < s.length && s[index].isLetter()) index++
                        return Token(T.REGEX, start, index, RegexLiteral(s.substring(start, index)))
                    }
                }
            }
            throw incomplete(start)
        }

        private fun number(): Token {
            val start = index
            if (s[index] == '-') index++
            while (index < s.length && s[index].isDigit()) index++
            var decimal = false
            if (index + 1 < s.length && s[index] == '.' && s[index + 1].isDigit()) {
                decimal = true
                index++
                while (index < s.length && s[index].isDigit()) index++
            }
            val text = s.substring(start, index)
            val value: Any = if (decimal) text.toDouble() else text.toIntOrNull() ?: text.toDouble()
            return Token(T.NUMBER, start, index, value)
        }

        private fun identifier(): Token {
            val start = index
            while (index < s.length && isIdentifierPart(s[index])) index++
            return when (val text = s.substring(start, index)) {
                "true" -> Token(T.BOOLEAN, start, index, true)
                "false" -> Token(T.BOOLEAN, start, index, false)
                else -> Token(T.IDENT, start, index, text)
            }
        }

        private fun isIdentifierStart(c: Char) = c.isLetter() || c == '_' || c == '$'

        private fun isIdentifierPart(c: Char) = c.isLetterOrDigit() || c == '_' || c == '$'

    }

    companion object {

        private const val PREFIX_SELECTOR = "selector"

        private const val PREFIX_DOLLAR = "$"

        /** [value] as the user would type it, for messages. zh-CN: 用于消息的 [value] 书写形式. */
        fun literal(value: Any?): String = when (value) {
            is String -> JsLiteral.quote(value)
            else -> value.toString()
        }

    }

}

/**
 * A selector expression that cannot be parsed or applied: [position] is the 0-based character
 * offset, [term] the method name or call text (when known), [candidates] registry names close to
 * an unknown method and [incomplete] whether the expression merely stops before the call ends.
 * zh-CN: 无法解析或施加的选择器表达式: [position] 为从 0 起的字符偏移, [term] 为方法名或调用文本 (已知时), [candidates] 为与未知
 * 方法相近的注册表名, [incomplete] 表示表达式只是停在调用结束之前.
 */
class SelectorCallException(
    val expression: String,
    val position: Int,
    val term: String?,
    val candidates: List<String>,
    val detail: String,
    val incomplete: Boolean = false,
) : IllegalArgumentException(render(expression, position, detail, candidates)) {

    companion object {

        private fun render(expression: String, position: Int, detail: String, candidates: List<String>): String {
            val hint = if (candidates.isEmpty()) "" else "; did you mean ${candidates.joinToString(", ")}?"
            return "Invalid selector expression at $position in \"$expression\": $detail$hint"
        }

    }

}
