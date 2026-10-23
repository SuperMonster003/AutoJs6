package org.autojs.autojs.runtime.api.modules.selector

import org.autojs.autojs.core.accessibility.UiSelector
import org.autojs.autojs.core.automator.UiObject
import org.autojs.autojs.core.automator.filter.BooleanFilter
import org.autojs.autojs.core.automator.pick.Compass
import org.autojs.autojs.core.automator.pick.CompassParser
import org.autojs.autojs.core.automator.pick.CompassSyntaxException
import org.autojs.autojs.core.automator.pick.Invoker
import org.autojs.autojs.core.automator.pick.PickResultType
import org.autojs.autojs.core.automator.selector.SelectorArgType
import org.autojs.autojs.core.automator.selector.SelectorEntry
import org.autojs.autojs.core.automator.selector.SelectorMapException
import org.autojs.autojs.core.automator.selector.SelectorMapParser
import org.autojs.autojs.core.automator.selector.SelectorRegistry
import org.autojs.autojs.runtime.api.modules.ScriptMessages
import org.autojs.autojs.runtime.api.modules.global.GlobalValues
import org.autojs.autojs.runtime.api.modules.util.UtilValues
import org.mozilla.javascript.Callable
import org.mozilla.javascript.NativeJavaMethod
import org.mozilla.javascript.regexp.NativeRegExp
import java.math.BigInteger
import org.mozilla.javascript.ScriptRuntime as RhinoScriptRuntime

/**
 * The checks of the selector forms of `pickup`, `detect`, `auto.explain`, `flow` and the automation toolkit on the new
 * script module layer (roadmap P3.3, appendix C Q66 (2), Q67), made before the current readers (`Picker.Builder`,
 * `Detector`, `UiObject.compass`) take the same values. They use the readers' own tables (the selector registry and its
 * map parser, the result type symbols, the method lookup of `Invoker`, the compass syntax), so what those accept is
 * accepted here and nothing is copied. What they refused is a TypeError (a value of another type, a condition that is no
 * property of `UiSelector` or takes no value of that kind, a selector method given without calling it) or a RangeError (a
 * value a condition refuses, a result type that is no symbol or method of `UiObject`, a plural result type of `detect`, a
 * compass that does not parse), D10, and now before the search: the readers checked result type methods and compasses
 * only once a node was found (appendix E "P3.3").
 *
 * zh-CN: 新脚本模块层上 pickup, detect, auto.explain, flow 与自动化工具集的选择器形式检查 (P3.3, 附录 C Q66 (2), Q67), 在现有读取方 (Picker.Builder,
 * Detector, UiObject.compass) 读取同一值之前进行. 检查使用读取方自己的表 (选择器注册表及其 map 解析器, 结果类型符号, Invoker 的方法查找, 罗盘语法), 所以它们接受的
 * 这里同样接受, 不复制任何一份. 它们拒绝的值为 TypeError (其他类型, 不是 UiSelector 属性或不接受该种类值的条件, 未调用的选择器方法) 或 RangeError (条件拒绝的值,
 * 不是 UiObject 结果类型符号或方法的结果类型, detect 的复数结果类型, 无法解析的罗盘), D10, 且提前到搜索之前: 现有读取方只在找到节点后才检查结果类型方法与罗盘 (附录 E "P3.3").
 */
internal object SelectorForms {

    private const val SELECTOR = "PickupSelector"
    private const val PICKUP_RESULT = "PickupResult"
    private const val DETECT_RESULT = "DetectResult"
    private const val COMPASS = "DetectCompass"

    /** The script functions of a map selector stand for filters; the parse only needs one that is no null. */
    private val NO_FILTER = BooleanFilter.BooleanSupplier { false }

    private val PARSER = SelectorMapParser(SelectorRegistry) { if (it is Callable) NO_FILTER else null }

    private val PLURAL = setOf(PickResultType.WidgetCollection, PickResultType.Widgets, PickResultType.Contents, PickResultType.Points)

    /**
     * A selector form as `Picker.Builder.setSelector` reads it: nullish (no condition), a string, a `RegExp`, a
     * `UiSelector`, a number, an object of conditions, or an array of these (null elements skipped).
     */
    fun selector(function: String, param: String, value: Any?) {
        when (value) {
            null, is String, is NativeRegExp, is UiSelector -> Unit
            is Map<*, *> -> conditions(function, param, value)
            is List<*> -> value.forEachIndexed { i, element -> if (element != null) selector(function, "$param[$i]", element) }
            is BigInteger, is Double -> Unit
            is NativeJavaMethod -> throw GlobalValues.cannotBe(function, param, "a method of UiSelector that is not called (${value.functionName})")
            else -> throw GlobalValues.typeError(function, param, SELECTOR, value)
        }
    }

    /** The conditions of an object selector, each parsed alone by the map parser so that the refusal names its key. */
    private fun conditions(function: String, param: String, map: Map<*, *>) {
        for ((key, value) in map.entries) {
            if (key !is String) continue
            val where = "$param.$key"
            try {
                PARSER.parse(java.util.Collections.singletonMap(key, value), UiSelector())
            } catch (e: SelectorMapException) {
                val entry = SelectorRegistry.entry(key) ?: throw GlobalValues.cannotBe(function, where, "given (not a condition of UiSelector)")
                throw GlobalValues.typeError(function, where, accepted(entry), value)
            } catch (e: IllegalArgumentException) {
                throw rejected(function, where, value, e)
            }
        }
    }

    /** The values the overloads of a condition take: a single argument, `null` (none), or an array of the arguments. */
    private fun accepted(entry: SelectorEntry): String = entry.overloads.map { overload ->
        when {
            overload.isVararg -> "string | number | Array"
            overload.params.isEmpty() -> "null"
            overload.params.size == 1 -> kind(overload.params[0].type)
            else -> overload.params.joinToString(", ", "[", "]") { kind(it.type) }
        }
    }.distinct().joinToString(" | ")

    private fun kind(type: SelectorArgType) = when (type) {
        SelectorArgType.STRING -> "string"
        SelectorArgType.REGEX -> "RegExp"
        SelectorArgType.BOOLEAN -> "boolean"
        SelectorArgType.INT, SelectorArgType.DOUBLE -> "number"
        SelectorArgType.APP -> "App"
        SelectorArgType.FILTER -> "function"
        SelectorArgType.ACTIONS -> "string | number"
    }

    /** A value a condition took but refused (NaN for an integer, an unknown algorithm, a pattern that does not compile). */
    fun rejected(function: String, param: String, value: Any?, cause: IllegalArgumentException): RuntimeException {
        val reason = cause.message?.lineSequence()?.firstOrNull()?.takeIf { it.isNotBlank() } ?: cause.javaClass.simpleName
        return RhinoScriptRuntime.rangeError(ScriptMessages.failedToCall(function, ScriptMessages.argumentCannotBe(param, "${show(value)} ($reason)")))
    }

    private fun show(value: Any?): String = if (value is List<*>) value.joinToString(", ", "[", "]") { show(it) } else UtilValues.quote(value)

    /**
     * A result type as `Detector` reads it for the [compass] given with it: a symbol, a method of `UiObject` with its
     * arguments, or a value without a result (accepted, the result is null). The selector itself cannot be walked by a
     * compass, and [single] (`detect`, one node) has no plural results.
     */
    fun resultType(function: String, param: String, value: Any?, compass: CharSequence?, single: Boolean) {
        when (val type = PickResultType.of(value)) {
            PickResultType.Selector -> if (compass != null && compass != Compass.PASS_ON) {
                throw GlobalValues.cannotBe(function, param, "${UtilValues.quote(value)} with compass ${UtilValues.quote(compass)}")
            }
            in PLURAL -> if (single) throw GlobalValues.rangeError(function, param, DETECT_RESULT, value)
            is PickResultType.Invocation -> {
                // A one-element array whose head is no method falls back to the array form of the symbol (`["#"]`), not for one node.
                // zh-CN: 首项不是方法的单元素数组回退到符号的数组形式 (["#"]), 单个节点时没有这一形式.
                val arrayForm = value is List<*> && type.arguments.isEmpty() && !single && PickResultType.arrayFormOf(type.name) != null
                if (!arrayForm && !invocable(type.name, type.arguments)) {
                    throw GlobalValues.rangeError(function, param, if (single) DETECT_RESULT else PICKUP_RESULT, value)
                }
            }
            else -> Unit
        }
    }

    /** Whether `Invoker` finds a method [name] (or its getter) of `UiObject` that takes [arguments] as it converts them. */
    private fun invocable(name: String, arguments: List<Any?>): Boolean {
        if (name.isEmpty()) return true
        val interceptor = try {
            Invoker.Interceptor(name, arguments.toTypedArray())
        } catch (e: IllegalArgumentException) {
            return false
        }
        return listOf(name, "get${name.replaceFirstChar { it.uppercase() }}").any { candidate ->
            val method = try {
                UiObject::class.java.getMethod(candidate, *interceptor.classes)
            } catch (e: NoSuchMethodException) {
                return@any false
            }
            method.parameterCount == interceptor.args.size && method.parameterTypes.indices.all { fits(method.parameterTypes[it], interceptor.args[it]) }
        }
    }

    /** Whether reflection passes [arg] to a parameter of [type] (unboxing and widening of the primitives `Invoker` uses). */
    private fun fits(type: Class<*>, arg: Any?): Boolean = when (type) {
        Int::class.javaPrimitiveType -> arg is Int || arg is Short || arg is Byte || arg is Char
        Float::class.javaPrimitiveType -> arg is Float || arg is Long || arg is Int || arg is Short || arg is Byte || arg is Char
        Boolean::class.javaPrimitiveType -> arg is Boolean
        else -> !type.isPrimitive && (arg == null || type.isInstance(arg))
    }

    /** A compass as `UiObject.compass` reads it once a node is found (Q67): `%` passes the node on, anything else must parse. */
    fun compass(function: String, param: String, value: Any?) {
        if (value !is CharSequence || value == Compass.PASS_ON) return
        val parses = Compass.isCompass(value) && try {
            CompassParser.parse(value)
            true
        } catch (e: CompassSyntaxException) {
            false
        }
        if (!parses) throw GlobalValues.rangeError(function, param, COMPASS, value)
    }

}
