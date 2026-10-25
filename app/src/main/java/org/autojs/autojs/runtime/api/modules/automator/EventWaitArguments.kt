package org.autojs.autojs.runtime.api.modules.automator

import org.autojs.autojs.core.automator.filter.JsRegexUtils
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.mozilla.javascript.Callable
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.regexp.NativeRegExp

/**
 * Argument parsing of the event-driven waits (roadmap P5.2): `waitForIdle(options?)`,
 * `waitForEvent(type?, filter?, timeout?)`, `waitForToast(text?, timeout?)` and
 * `waitForNotification(filter?, timeout?)`, shared by the synchronous globals and the Flow steps.
 * Parsing happens on the calling thread; the resulting [Filter]s run on the waiting thread. Event
 * type names are resolved through a given function so that this object stays Android-free.
 * zh-CN: 事件驱动等待的参数解析 (Roadmap P5.2): `waitForIdle(options?)`, `waitForEvent(type?, filter?, timeout?)`,
 * `waitForToast(text?, timeout?)` 与 `waitForNotification(filter?, timeout?)`, 同步全局函数与 Flow 步骤共用. 解析在调用线程上进行,
 * 得到的 [Filter] 在等待线程上运行. 事件类型名经给定的函数解析, 使本对象不依赖 Android.
 *
 * Created by SuperMonster003 on Sep 6, 2026.
 */
internal object EventWaitArguments {

    const val KEY_QUIET_FOR = "quietFor"
    const val KEY_TIMEOUT = "timeout"
    const val KEY_EVENT_TYPES = "eventTypes"
    val IDLE_KEYS = setOf(KEY_QUIET_FOR, KEY_TIMEOUT, KEY_EVENT_TYPES)

    const val KEY_PACKAGE_NAME = "packageName"
    const val KEY_CLASS_NAME = "className"
    const val KEY_TITLE = "title"
    const val KEY_TEXT = "text"
    val EVENT_FILTER_KEYS = setOf(KEY_PACKAGE_NAME, KEY_CLASS_NAME, KEY_TEXT)
    val TOAST_FILTER_KEYS = setOf(KEY_PACKAGE_NAME, KEY_TEXT)
    val NOTIFICATION_FILTER_KEYS = setOf(KEY_PACKAGE_NAME, KEY_TITLE, KEY_TEXT)

    /** Keys a string matches as a whole; the others by substring. zh-CN: 字符串整体匹配的键; 其余按子串匹配. */
    private val WHOLE_KEYS = setOf(KEY_PACKAGE_NAME, KEY_CLASS_NAME)

    /** The event types `waitForIdle` watches unless told otherwise. zh-CN: 未指定时 `waitForIdle` 观察的事件类型. */
    val IDLE_TYPE_NAMES = listOf("WINDOW_STATE_CHANGED", "WINDOW_CONTENT_CHANGED", "WINDOWS_CHANGED")

    const val DEFAULT_QUIET_FOR = 500L

    /** Any event type. zh-CN: 任意事件类型. */
    const val ANY_TYPE = "*"

    const val UNBOUNDED = Long.MAX_VALUE

    private val CAMEL_BOUNDARY = Regex("([a-z0-9])([A-Z])")

    /** A parsed `waitForIdle`. [typeNames] is `null` for any type. zh-CN: 解析后的 `waitForIdle`. [typeNames] 为 `null` 表示任意类型. */
    class Idle(val quietFor: Long, val timeout: Long, val types: Set<Int>?, val typeNames: List<String>?, val explicitTypes: Boolean) {
        val detail: String = "quiet $quietFor ms" + (if (explicitTypes) ", events ${typeNames?.joinToString("|") ?: ANY_TYPE}" else "")
    }

    /** A parsed `waitForEvent`. [types] is `null` for any type. zh-CN: 解析后的 `waitForEvent`. [types] 为 `null` 表示任意类型. */
    class Event(val types: Set<Int>?, typeNames: List<String>?, val filter: Filter?, val timeout: Long) {
        val detail: String = (typeNames?.joinToString("|") ?: "any") + (filter?.let { " ${it.detail}" } ?: "")
    }

    /** A parsed `waitForToast`. zh-CN: 解析后的 `waitForToast`. */
    class Toast(val filter: Filter?, val timeout: Long) {
        val detail: String = filter?.detail ?: "any"
    }

    /** A parsed `waitForNotification`. zh-CN: 解析后的 `waitForNotification`. */
    class Notification(val filter: Filter?, val timeout: Long) {
        val detail: String = filter?.detail ?: "any"
    }

    /** A text test: a whole or substring match, or a regular expression search. zh-CN: 文本测试: 整体或子串匹配, 或正则搜索. */
    class Matcher(val detail: String, val test: (String?) -> Boolean) {

        companion object {

            fun whole(expected: String) = Matcher(quote(expected)) { it == expected }

            fun contains(expected: String) = Matcher(quote(expected)) { it != null && it.contains(expected) }

            fun regex(regex: Regex, detail: String) = Matcher(detail) { it != null && regex.containsMatchIn(it) }

            private fun quote(s: String) = "\"" + s.replace("\"", "\\\"") + "\""

        }

    }

    /** A predicate over a candidate event / toast / notification. zh-CN: 对候选事件 / toast / 通知的谓词. */
    sealed class Filter {

        abstract val detail: String

        /** A script function called with the candidate; a truthy result accepts. zh-CN: 以候选者为参数调用的脚本函数; 真值表示接受. */
        class Script(val fn: Callable) : Filter() {
            override val detail = "fn"
        }

        /** Property matchers that must all hold. zh-CN: 必须全部成立的属性匹配器. */
        class Properties(val matchers: Map<String, Matcher>) : Filter() {

            override val detail = matchers.entries.joinToString(", ", "{ ", " }") { (key, matcher) -> "$key: ${matcher.detail}" }

            /** Whether every matcher accepts the candidate's property of its key. zh-CN: 每个匹配器是否都接受候选者对应键的属性. */
            fun matches(property: (String) -> String?): Boolean = matchers.all { (key, matcher) -> matcher.test(property(key)) }

        }

        /** One matcher against any of several properties. zh-CN: 对若干属性之一成立即可的匹配器. */
        class Text(val matcher: Matcher, val keys: List<String>) : Filter() {

            override val detail = "${keys.joinToString("|")} ~ ${matcher.detail}"

            fun matches(property: (String) -> String?): Boolean = keys.any { matcher.test(property(it)) }

        }

    }

    /** `waitForIdle(quietFor?)` / `waitForIdle({ quietFor?, timeout?, eventTypes? })`. */
    fun idle(args: Array<out Any?>, defaultTimeout: Long, resolveType: (String) -> Int): Idle {
        atMost("waitForIdle", args, 1)
        var quietFor = DEFAULT_QUIET_FOR
        var timeout = defaultTimeout
        var names: List<String>? = IDLE_TYPE_NAMES
        var explicit = false
        when (val arg = args.getOrNull(0)) {
            null, is Undefined -> {}
            is Number -> quietFor = millis(KEY_QUIET_FOR, arg)
            is NativeObject -> {
                checkKeys("waitForIdle", arg, IDLE_KEYS)
                value(arg, KEY_QUIET_FOR)?.let { quietFor = millis(KEY_QUIET_FOR, it) }
                value(arg, KEY_TIMEOUT)?.let { timeout = millis(KEY_TIMEOUT, it) }
                value(arg, KEY_EVENT_TYPES)?.let {
                    names = typeNames(it, "waitForIdle.$KEY_EVENT_TYPES")
                    explicit = true
                }
            }
            else -> throw WrappedIllegalArgumentException("waitForIdle: a number of milliseconds or an options object expected, got ${describe(arg)}")
        }
        return Idle(quietFor, timeout, names?.let { resolve(it, resolveType) }, names?.map { it.lowercase() }, explicit)
    }

    /** `waitForEvent(type?, filter?, timeout?)`; the type may be a name, an array of names, `"*"` or `null`. */
    fun event(args: Array<out Any?>, defaultTimeout: Long, resolveType: (String) -> Int): Event {
        atMost("waitForEvent", args, 3)
        val names = typeNames(args.getOrNull(0), "waitForEvent")
        var filter: Filter? = null
        var timeout = defaultTimeout
        var filterSeen = false
        var timeoutSeen = false
        for (i in 1 until args.size) {
            val arg = args[i]
            when {
                arg == null || arg is Undefined -> {}
                arg is Number -> {
                    if (timeoutSeen) throw WrappedIllegalArgumentException("waitForEvent: the timeout was given twice")
                    timeout = millis(KEY_TIMEOUT, arg)
                    timeoutSeen = true
                }
                else -> {
                    if (filterSeen) throw WrappedIllegalArgumentException("waitForEvent: the filter was given twice")
                    filter = filter(arg, EVENT_FILTER_KEYS, "waitForEvent")
                    filterSeen = true
                }
            }
        }
        return Event(names?.let { resolve(it, resolveType) }, names?.map { it.lowercase() }, filter, timeout)
    }

    /** `waitForToast(text?, timeout?)`: the text a substring, a regex, a function, or `{ packageName?, text? }`. */
    fun toast(args: Array<out Any?>, defaultTimeout: Long): Toast {
        val (filter, timeout) = textAndTimeout("waitForToast", args, defaultTimeout, TOAST_FILTER_KEYS, listOf(KEY_TEXT))
        return Toast(filter, timeout)
    }

    /** `waitForNotification(filter?, timeout?)`: the filter a substring of the title or text, a regex, a function, or `{ packageName?, title?, text? }`. */
    fun notification(args: Array<out Any?>, defaultTimeout: Long): Notification {
        val (filter, timeout) = textAndTimeout("waitForNotification", args, defaultTimeout, NOTIFICATION_FILTER_KEYS, listOf(KEY_TITLE, KEY_TEXT))
        return Notification(filter, timeout)
    }

    private fun textAndTimeout(what: String, args: Array<out Any?>, defaultTimeout: Long, keys: Set<String>, textKeys: List<String>): Pair<Filter?, Long> {
        atMost(what, args, 2)
        val first = args.getOrNull(0)
        if (first is Number) {
            atMost(what, args, 1)
            return null to millis(KEY_TIMEOUT, first)
        }
        val filter = when (first) {
            null, is Undefined -> null
            is CharSequence -> Filter.Text(Matcher.contains(first.toString()), textKeys)
            is NativeRegExp -> Filter.Text(regexMatcher(first), textKeys)
            else -> filter(first, keys, what)
        }
        val second = args.getOrNull(1)
        val timeout = if (second == null || second is Undefined) defaultTimeout else millis(KEY_TIMEOUT, second)
        return filter to timeout
    }

    /** A function, or an object of [keys] whose values are strings or regular expressions. zh-CN: 函数, 或值为字符串或正则的 [keys] 对象. */
    fun filter(value: Any?, keys: Set<String>, what: String): Filter? = when {
        value == null || value is Undefined -> null
        value is Callable -> Filter.Script(value)
        value is NativeObject -> {
            checkKeys(what, value, keys)
            val matchers = LinkedHashMap<String, Matcher>()
            for (key in keys) {
                val v = value(value, key) ?: continue
                matchers[key] = when (v) {
                    is CharSequence -> v.toString().let { if (key in WHOLE_KEYS) Matcher.whole(it) else Matcher.contains(it) }
                    is NativeRegExp -> regexMatcher(v)
                    else -> throw WrappedIllegalArgumentException("$what.$key: a string or a regular expression expected, got ${describe(v)}")
                }
            }
            Filter.Properties(matchers)
        }
        else -> throw WrappedIllegalArgumentException("$what: a function or an object with ${keys.joinToString(" / ")} expected, got ${describe(value)}")
    }

    /** Type names as `WINDOW_STATE_CHANGED` (from any of `windowStateChanged` / `window_state_changed` / `TYPE_WINDOW_STATE_CHANGED`), or `null` for any. */
    fun typeNames(value: Any?, what: String): List<String>? = when {
        value == null || value is Undefined -> null
        value is CharSequence -> value.toString().trim().let { if (it == ANY_TYPE) null else listOf(normalizeTypeName(it)) }
        value is NativeArray -> value.map { normalizeTypeName((it as? CharSequence ?: throw WrappedIllegalArgumentException("$what: event type names expected, got ${describe(it)}")).toString()) }
        else -> throw WrappedIllegalArgumentException("$what: an event type name, an array of names or \"$ANY_TYPE\" expected, got ${describe(value)}")
    }

    fun normalizeTypeName(name: String): String = name.trim().replace(CAMEL_BOUNDARY, "$1_$2").uppercase().removePrefix("TYPE_")

    private fun resolve(names: List<String>, resolveType: (String) -> Int): Set<Int> = names.mapTo(LinkedHashSet()) { name ->
        try {
            resolveType(name)
        } catch (_: Exception) {
            throw WrappedIllegalArgumentException("Unknown event type: ${name.lowercase()}")
        }
    }

    /** A non-negative number of milliseconds; `Infinity` is [UNBOUNDED]. zh-CN: 非负的毫秒数; `Infinity` 为 [UNBOUNDED]. */
    fun millis(what: String, value: Any?): Long {
        val number = when (value) {
            is Number -> value.toDouble()
            is CharSequence -> value.toString().toDoubleOrNull() ?: throw WrappedIllegalArgumentException("$what: a number of milliseconds expected, got ${describe(value)}")
            else -> throw WrappedIllegalArgumentException("$what: a number of milliseconds expected, got ${describe(value)}")
        }
        return when {
            number.isNaN() || number < 0 -> throw WrappedIllegalArgumentException("$what: a non-negative number of milliseconds expected, got ${describe(value)}")
            number == Double.POSITIVE_INFINITY -> UNBOUNDED
            else -> number.toLong()
        }
    }

    private fun regexMatcher(regex: NativeRegExp): Matcher {
        val literal = regex.toString()
        return Matcher.regex(JsRegexUtils.compileJsOrPlainRegex(literal), literal)
    }

    private fun atMost(what: String, args: Array<out Any?>, max: Int) {
        if (args.size > max) throw WrappedIllegalArgumentException("$what: at most $max argument(s) expected, got ${args.size}")
    }

    private fun checkKeys(what: String, obj: NativeObject, keys: Set<String>) {
        obj.ids.map { it.toString() }.firstOrNull { it !in keys }?.let {
            throw WrappedIllegalArgumentException("$what: unknown option \"$it\"; accepted: ${keys.joinToString(", ")}")
        }
    }

    private fun value(obj: NativeObject, key: String): Any? = obj.get(key, obj).let {
        if (it == null || it is Undefined || it === Scriptable.NOT_FOUND) null else it
    }

    private fun describe(value: Any?): String = when (value) {
        null -> "null"
        is Undefined -> "undefined"
        is CharSequence -> "\"$value\""
        is Number -> value.toString()
        is Callable -> "function"
        is Scriptable -> value.className
        else -> value.toString()
    }

}
