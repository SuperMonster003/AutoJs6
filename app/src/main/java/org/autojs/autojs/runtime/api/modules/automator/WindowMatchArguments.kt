package org.autojs.autojs.runtime.api.modules.automator

import org.autojs.autojs.core.accessibility.window.WindowMatch
import org.autojs.autojs.core.automator.filter.JsRegexUtils
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.regexp.NativeRegExp
import kotlin.math.floor

/**
 * The window filter object of `auto.setWindowFilter` / `auto.findWindows` / `auto.findWindowRoots`
 * (roadmap P5.3) parsed into a [WindowMatch]: `type` (a name or an array of names: `application`,
 * `inputMethod`, `system`, `accessibilityOverlay`, `splitScreenDivider`, `magnificationOverlay`),
 * `packageName` / `title` (a whole string or a regular expression), `displayId` / `id` / `layer`
 * (integers), `active` / `focused` (booleans); unknown keys are refused. Android-free.
 * zh-CN: `auto.setWindowFilter` / `auto.findWindows` / `auto.findWindowRoots` 的窗口过滤器对象 (Roadmap P5.3) 解析为 [WindowMatch]:
 * `type` (名称或名称数组), `packageName` / `title` (整个字符串或正则), `displayId` / `id` / `layer` (整数), `active` / `focused`
 * (布尔); 未知键报错. 不依赖 Android.
 *
 * Created by SuperMonster003 on Sep 6, 2026.
 */
internal object WindowMatchArguments {

    const val KEY_TYPE = "type"
    const val KEY_PACKAGE_NAME = "packageName"
    const val KEY_TITLE = "title"
    const val KEY_DISPLAY_ID = "displayId"
    const val KEY_ACTIVE = "active"
    const val KEY_FOCUSED = "focused"
    const val KEY_ID = "id"
    const val KEY_LAYER = "layer"

    val KEYS = listOf(KEY_TYPE, KEY_PACKAGE_NAME, KEY_TITLE, KEY_DISPLAY_ID, KEY_ACTIVE, KEY_FOCUSED, KEY_ID, KEY_LAYER)

    /** [WindowMatch] of a filter object; `null` / `undefined` match every window. zh-CN: 过滤器对象的 [WindowMatch]; `null` / `undefined` 匹配所有窗口. */
    fun parse(value: Any?, what: String = "window filter"): WindowMatch {
        if (value == null || value is Undefined) return WindowMatch()
        val obj = value as? NativeObject ?: throw WrappedIllegalArgumentException("$what: an object with ${KEYS.joinToString(" / ")} expected, got ${describe(value)}")
        obj.ids.map { it.toString() }.firstOrNull { it !in KEYS }?.let {
            throw WrappedIllegalArgumentException("$what: unknown key \"$it\"; accepted: ${KEYS.joinToString(", ")}")
        }
        return WindowMatch(
            types = value(obj, KEY_TYPE)?.let { types(it, what) },
            packageName = value(obj, KEY_PACKAGE_NAME)?.let { text(it, "$what.$KEY_PACKAGE_NAME") },
            title = value(obj, KEY_TITLE)?.let { text(it, "$what.$KEY_TITLE") },
            displayId = value(obj, KEY_DISPLAY_ID)?.let { int(it, "$what.$KEY_DISPLAY_ID") },
            active = value(obj, KEY_ACTIVE)?.let { bool(it, "$what.$KEY_ACTIVE") },
            focused = value(obj, KEY_FOCUSED)?.let { bool(it, "$what.$KEY_FOCUSED") },
            id = value(obj, KEY_ID)?.let { int(it, "$what.$KEY_ID") },
            layer = value(obj, KEY_LAYER)?.let { int(it, "$what.$KEY_LAYER") },
        )
    }

    private fun types(value: Any, what: String): Set<Int> {
        val names: List<Any?> = when (value) {
            is NativeArray -> value.toList()
            else -> listOf(value)
        }
        return names.mapTo(LinkedHashSet()) { name ->
            val text = (name as? CharSequence)?.toString() ?: throw WrappedIllegalArgumentException("$what.$KEY_TYPE: a window type name expected, got ${describe(name)}")
            WindowMatch.typeOf(text) ?: throw WrappedIllegalArgumentException("$what.$KEY_TYPE: unknown window type \"$text\"; accepted: ${WindowMatch.TYPES.keys.joinToString(", ")}")
        }
    }

    private fun text(value: Any, what: String): WindowMatch.TextRule = when (value) {
        is CharSequence -> WindowMatch.TextRule.whole(value.toString())
        is NativeRegExp -> value.toString().let { WindowMatch.TextRule.regex(JsRegexUtils.compileJsOrPlainRegex(it), it) }
        else -> throw WrappedIllegalArgumentException("$what: a string or a regular expression expected, got ${describe(value)}")
    }

    private fun int(value: Any, what: String): Int {
        val number = (value as? Number)?.toDouble() ?: throw WrappedIllegalArgumentException("$what: an integer expected, got ${describe(value)}")
        if (number.isNaN() || number.isInfinite() || number != floor(number)) throw WrappedIllegalArgumentException("$what: an integer expected, got ${describe(value)}")
        return number.toInt()
    }

    private fun bool(value: Any, what: String): Boolean = value as? Boolean ?: throw WrappedIllegalArgumentException("$what: a boolean expected, got ${describe(value)}")

    private fun value(obj: NativeObject, key: String): Any? = obj.get(key, obj).let {
        if (it == null || it is Undefined || it === Scriptable.NOT_FOUND) null else it
    }

    private fun describe(value: Any?): String = when (value) {
        null -> "null"
        is Undefined -> "undefined"
        is CharSequence -> "\"$value\""
        is Number, is Boolean -> value.toString()
        is Scriptable -> value.className
        else -> value.toString()
    }

}
