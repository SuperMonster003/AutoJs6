package org.autojs.autojs.core.automator.selector

import org.autojs.autojs.core.accessibility.UiSelector
import org.autojs.autojs.core.automator.filter.BooleanFilter

/**
 * Turns a map selector (`{ text: "Login", boundsLeft: [0, 100], clickable: true, filter: fn }`,
 * the object form accepted by `pickup` and friends) into registry calls on a [UiSelector]. Each key
 * names a registry property; the value supplies the arguments: `null` means the no-argument
 * overload, an array is spread into the arguments (with strings leniently parsed as numbers and
 * booleans), anything else is the single argument. Script functions become [BooleanFilter.BooleanSupplier]
 * through [callables], so only `filter` accepts them. Replaces the reflective `checkSelectorMap`.
 *
 * zh-CN: 把 map 选择器 (`pickup` 等接受的对象形式) 转换为对 [UiSelector] 的注册表调用. 每个键是注册表属性名; 值提供参数:
 * `null` 为无参重载, 数组展开为参数 (字符串宽松解析为数字与布尔值), 其它为单个参数. 脚本函数经 [callables] 变为
 * [BooleanFilter.BooleanSupplier], 因此只有 `filter` 接受它. 取代反射实现的 `checkSelectorMap`.
 *
 * Created by SuperMonster003 on Sep 5, 2026.
 */
class SelectorMapParser(
    private val registry: SelectorRegistry = SelectorRegistry,
    private val callables: (Any) -> BooleanFilter.BooleanSupplier? = { null },
) {

    /** Applies every entry of [map] to [into] and returns it. zh-CN: 把 [map] 的每一项施加到 [into] 并返回它. */
    @Throws(SelectorMapException::class)
    fun parse(map: Map<*, *>, into: UiSelector): UiSelector {
        for ((key, value) in map.entries) {
            if (key !is String) continue
            apply(key, value, into)
        }
        return into
    }

    private fun apply(key: String, value: Any?, into: UiSelector) {
        if (key == ACTION && value == null) return
        val isList = value is List<*>
        val args = when (value) {
            null -> emptyList()
            is List<*> -> value.toList()
            else -> listOf(value)
        }.map { arg -> arg?.let(callables) ?: arg }
        val outcome = registry.apply(into, key, if (key == ACTION) args.filterNotNull() else args, lenient = isList)
        when (outcome) {
            SelectorRegistry.Outcome.APPLIED -> Unit
            SelectorRegistry.Outcome.NO_SUCH_PROPERTY -> throw SelectorMapException.NoSuchProperty(key, value)
            SelectorRegistry.Outcome.NO_MATCHING_OVERLOAD -> when {
                isList -> throw SelectorMapException.IllegalValue(key, value)
                else -> throw SelectorMapException.NoSuchProperty(key, value)
            }
        }
    }

    companion object {

        private const val ACTION = "action"

    }

}

/**
 * A map selector entry that cannot be applied; the script-facing layer turns it into the
 * localized message.
 * zh-CN: 无法施加的 map 选择器条目; 面向脚本的层把它转为本地化信息.
 */
sealed class SelectorMapException(val key: String, val value: Any?, message: String) : IllegalArgumentException(message) {

    /** No property [key] takes a value like [value] (unknown key, or a scalar of the wrong kind). zh-CN: 没有属性 [key] 接受 [value] 这样的值 (键未知, 或标量种类不符). */
    class NoSuchProperty(key: String, value: Any?) : SelectorMapException(key, value, "No selector property $key takes ${describe(value)}")

    /** Property [key] exists but the array [value] fits none of its overloads. zh-CN: 属性 [key] 存在, 但数组 [value] 不符合任何重载. */
    class IllegalValue(key: String, value: Any?) : SelectorMapException(key, value, "Illegal value for selector property $key: ${describe(value)}")

    companion object {

        fun describe(value: Any?): String = when (value) {
            null -> "null"
            is List<*> -> value.joinToString(", ", "[", "]") { describe(it) }
            is CharSequence -> "\"$value\""
            else -> "$value (${value::class.java.simpleName})"
        }

    }

}
