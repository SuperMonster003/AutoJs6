package org.autojs.autojs.core.automator.pick

import org.autojs.autojs.core.accessibility.UiSelector
import org.autojs.autojs.core.automator.UiNode
import org.autojs.autojs.core.automator.UiObject
import org.autojs.autojs.core.automator.filter.BooleanFilter
import org.autojs.autojs.core.automator.filter.JsRegexUtils
import org.autojs.autojs.core.automator.filter.PatternPrecheck
import org.autojs.autojs.core.automator.selector.SelectorArgType
import org.autojs.autojs.core.automator.selector.SelectorEntry
import org.autojs.autojs.core.automator.selector.SelectorRegistry
import org.autojs.autojs.core.automator.selector.SelectorResolution
import org.autojs.autojs.core.automator.selector.SelectorStringParser
import org.mozilla.javascript.Callable
import org.mozilla.javascript.NativeJavaMethod
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.regexp.NativeRegExp
import java.math.BigInteger

/**
 * The one normalizer of the arguments of `pickup` and `detect` (docs/selector/ROADMAP.md D5, P1.1): it tells the forms
 * of a call apart by the types of the arguments, checks the root, the selector form (a string, a `RegExp`, a
 * `UiSelector`, an object of conditions, an array of these, a number), the compass, the result type (a symbol or a
 * method of [methods], no plural result for one node, no compass with `@`) and the callback, and builds a
 * [PickRequest]. Everything is told before anything is thrown, without a Java exception on the way (augment R2): a
 * refusal is a [PickVerdict.Refused] with a TypeError or RangeError naming the function and the parameter (appendix C
 * Q5 (2): [PickMessages]).
 *
 * Two entries, two timings. The new script module layer ([pickup], [detect] and the checks of the selector forms read
 * like theirs) refuses everything before the search. The entries of the current augment layer ([legacyPickup],
 * [legacyDetect]: `UiSelector.pickup`, `UiObject.detect`) take arguments whose positions their caller has told; their
 * selector is refused before the search, as `Picker.Builder` refused it, while a compass or result type they refuse
 * is carried in the request and thrown when it is used, where `Detector` and `Invoker` threw ([PickCompass.Refused],
 * [PickResult.Refused]): only the type of these errors changes (IllegalArgumentException -> TypeError / RangeError).
 *
 * Free of Android types, [N] is the node type (`UiObject` on a device, `FakeUiNode` in JVM tests).
 *
 * zh-CN: `pickup` 与 `detect` 参数的唯一归一化器 (D5, P1.1): 按参数类型区分调用形式, 检查 root, 选择器形式 (字符串, `RegExp`, `UiSelector`, 条件对象,
 * 它们的数组, 数字), 罗盘, 结果类型 (符号或 [methods] 中的方法, 单个节点没有复数结果, `@` 不能带罗盘) 与回调, 并构建 [PickRequest]. 全部先判定再抛, 途中
 * 不抛 Java 异常 (augment R2): 拒绝为带函数名与参数名的 TypeError 或 RangeError ([PickVerdict.Refused], 附录 C Q5 (2): [PickMessages]).
 * 两个入口, 两种时机. 新脚本模块层 ([pickup], [detect] 以及按其方式读取的选择器形式检查) 在搜索前拒绝全部错误. 现有 augment 层的入口 ([legacyPickup],
 * [legacyDetect]: `UiSelector.pickup`, `UiObject.detect`) 接收调用方已区分好位置的参数; 其选择器在搜索前拒绝 (同 `Picker.Builder`), 罗盘与结果类型的错误则随请求
 * 携带, 在使用时抛出, 即原 `Detector` 与 `Invoker` 抛出之处 ([PickCompass.Refused], [PickResult.Refused]): 这些错误只改变类型 (IllegalArgumentException ->
 * TypeError / RangeError). 不含 Android 类型, [N] 为节点类型 (设备上为 `UiObject`, JVM 测试中为 `FakeUiNode`).
 *
 * Created by SuperMonster003 on Oct 25, 2026.
 */
class PickArguments<N : UiNode<N>>(private val nodeType: Class<N>, private val methods: NodeMethodTable) {

    /**
     * How a request builds its selector: [newSelector] makes a selector on the bridge of the calling script (so that
     * its window filter and blacklist apply), [filter] turns a script function of an object selector into a filter.
     * zh-CN: 请求如何构建选择器: [newSelector] 在调用方脚本的 bridge 上创建选择器 (使其窗口过滤器与黑名单生效), [filter] 把对象选择器中的脚本函数变为过滤器.
     */
    class Binding(val newSelector: () -> UiSelector, val filter: (Callable) -> BooleanFilter.BooleanSupplier)

    /** `pickup(root?, selector?, compass?, resultType?, callback?)` of the new layer, [args] unwrapped. zh-CN: 新层的 `pickup`, [args] 已解包. */
    fun pickup(args: Array<out Any?>, binding: Binding, function: String = PICKUP): PickVerdict<N> {
        if (args.size > 5) return refused(PickError.Kind.TYPE, PickMessages.argumentCount(function, 0, 5, args.size))
        if (args.isEmpty()) return request(function, null, null, null, PickResultType.WIDGET, null, binding, strict = true, single = false)
        // A node first is the root from two arguments on, and the first of five arguments always is.
        // zh-CN: 两个参数起, 首个参数为节点时它是 root; 五个参数时首个参数总是 root.
        val hasRoot = args.size == 5 || args.size >= 2 && nodeType.isInstance(args[0])
        if (hasRoot) nodeError(function, ROOT, args[0])?.let { return PickVerdict.Refused(it) }
        val at = if (hasRoot) 1 else 0
        val (compass, resultType, callback) = positions(args, at, PickResultType.WIDGET)
        typeErrors(function, compass, callback)?.let { return PickVerdict.Refused(it) }
        @Suppress("UNCHECKED_CAST")
        val root = if (hasRoot) args[0]?.takeIf { nodeType.isInstance(it) } as N? else null
        return request(function, root, args[at], compass as? CharSequence, resultType, callback as? Callable, binding, strict = true, single = false)
    }

    /** `detect(w, compass?, resultType?, callback?)` of the new layer, [args] unwrapped. zh-CN: 新层的 `detect`, [args] 已解包. */
    fun detect(args: Array<out Any?>, function: String = DETECT): PickVerdict<N> {
        if (args.size !in 2..4) return refused(PickError.Kind.TYPE, PickMessages.argumentCount(function, 2, 4, args.size))
        nodeError(function, WIDGET, args[0])?.let { return PickVerdict.Refused(it) }
        val (compass, resultType, callback) = positions(args, 0, PickResultType.WIDGET)
        typeErrors(function, compass, callback)?.let { return PickVerdict.Refused(it) }
        @Suppress("UNCHECKED_CAST")
        val node = args[0]?.takeIf { nodeType.isInstance(it) } as N?
        return request(function, node, null, compass as? CharSequence, resultType, callback as? Callable, null, strict = true, single = true)
    }

    /**
     * `UiSelector.pickup(root, selector, compass, resultType, callback)`: the positions told by the caller (the current
     * augment layer, the toolkit, flows); a nullish compass passes the node on, a null result type is a widget.
     * zh-CN: `UiSelector.pickup`: 位置已由调用方区分 (现有 augment 层, 工具集, Flow); 空罗盘即节点自身, null 结果类型即控件.
     */
    fun legacyPickup(root: N?, selector: Any?, compass: CharSequence?, resultType: Any?, callback: Callable?, binding: Binding, function: String = PICKUP): PickVerdict<N> =
        request(function, root, selector, compass, resultType ?: PickResultType.WIDGET, callback, binding, strict = false, single = false)

    /** `UiObject.detect(w, compass, resultType, callback)`: a null result type has no result, as before. zh-CN: `UiObject.detect`: null 结果类型没有结果, 同前. */
    fun legacyDetect(node: N?, compass: CharSequence?, resultType: Any?, callback: Callable?, function: String = DETECT): PickVerdict<N> =
        request(function, node, null, compass, resultType, callback, null, strict = false, single = true)

    /**
     * The selector form [value] of the argument [param] of [function], as `pickup` reads it, without building it (the
     * selector conditions of flows and `auto.explain` on the new layer, P1.4): the error, or null when it is accepted.
     * zh-CN: 按 `pickup` 的读法检查 [function] 的参数 [param] 的选择器形式 [value], 不构建选择器 (新层 Flow 与 `auto.explain` 的选择器条件, P1.4): 错误, 接受时为 null.
     */
    fun selectorError(function: String, param: String, value: Any?): PickError? = SelectorReader(function, null).read(param, value, null)

    /** The selector [value] as `pickup` builds it, or its error thrown (`auto.explain` of the current layer). zh-CN: 按 `pickup` 的方式构建的选择器, 或抛出其错误. */
    fun buildSelector(function: String, param: String, value: Any?, binding: Binding): UiSelector {
        val selector = binding.newSelector()
        SelectorReader(function, binding).read(param, value, selector)?.let { throw it.exception() }
        return selector
    }

    /** The compass [value] of [param] as `pickup` takes it: the error, or null. zh-CN: 按 `pickup` 的方式检查罗盘: 错误, 接受时为 null. */
    fun compassError(function: String, param: String, value: CharSequence): PickError? = (compassOf(function, param, value) as? PickCompass.Refused)?.error

    /**
     * The result type [value] of [param] as `pickup` ([single]: `detect`) takes it with [compass]: the error, or null.
     * zh-CN: 按 `pickup` ([single] 时为 `detect`) 的方式检查与 [compass] 同时给出的结果类型: 错误, 接受时为 null.
     */
    fun resultTypeError(function: String, param: String, value: Any?, compass: CharSequence?, single: Boolean): PickError? {
        val walk = compass?.let { compassOf(function, COMPASS, it) } ?: PickCompass.PassOn
        return (resultOf(function, param, value, walk, single) as? PickResult.Refused)?.error
    }

    /**
     * The compass and the result type after the selector of `auto.explain(selector, compass?, resultType?)` (P3.5) in
     * [rest]: one argument is the compass when it is one, else the result type; two are both. They are read as `pickup`
     * reads them, but a refusal (a compass that is no string included) stays in the [PickParts] for the report instead
     * of being thrown.
     * zh-CN: `auto.explain(selector, compass?, resultType?)` 中选择器之后的罗盘与结果类型 ([rest], P3.5): 一个参数时, 是罗盘则为罗盘, 否则为结果类型;
     * 两个参数时依次为两者. 按 `pickup` 的方式读取, 但拒绝 (含不是字符串的罗盘) 留在 [PickParts] 中供报告展示, 而不抛出.
     */
    fun explainParts(function: String, rest: List<Any?>): PickParts {
        val compass: Any?
        val resultType: Any?
        when (rest.size) {
            0 -> return PickParts(null, null, null, null)
            1 -> if (Compass.isCompass(rest[0])) {
                compass = rest[0]
                resultType = null
            } else {
                compass = null
                resultType = rest[0]
            }
            else -> {
                compass = rest[0]
                resultType = rest[1]
            }
        }
        val walk = when {
            isNullish(compass) -> null
            compass is CharSequence -> compassOf(function, COMPASS, compass)
            else -> PickCompass.Refused(PickError(PickError.Kind.TYPE, PickMessages.argumentType(function, COMPASS, "string", compass)))
        }
        val given = resultType.takeUnless(::isNullish)
        val result = when (rest.size == 1 && resultType === null) {
            true -> null
            else -> resultOf(function, RESULT_TYPE, given, walk?.takeUnless { it is PickCompass.Refused } ?: PickCompass.PassOn, single = false)
        }
        return PickParts(compass.takeUnless(::isNullish), walk, given, result)
    }

    private fun request(
        function: String, root: N?, selectorArg: Any?, compassArg: CharSequence?, resultArg: Any?, callback: Callable?,
        binding: Binding?, strict: Boolean, single: Boolean,
    ): PickVerdict<N> {
        val selector = binding?.let { b ->
            b.newSelector().also { selector -> SelectorReader(function, b).read(SELECTOR, selectorArg, selector)?.let { return PickVerdict.Refused(it) } }
        }
        val compass = compassArg?.let { compassOf(function, COMPASS, it) } ?: PickCompass.PassOn
        if (strict && compass is PickCompass.Refused) return PickVerdict.Refused(compass.error)
        val result = resultOf(function, RESULT_TYPE, resultArg, compass, single)
        if (strict && result is PickResult.Refused) return PickVerdict.Refused(result.error)
        return PickVerdict.Accepted(PickRequest(function, root, selector, compass, result, callback))
    }

    /** The compass, the result type and the callback after the selector at [at], told apart by their types. */
    private fun positions(a: Array<out Any?>, at: Int, defaultResult: Any?): Triple<Any?, Any?, Any?> {
        var compass: Any? = Compass.PASS_ON
        var resultType: Any? = defaultResult
        var callback: Any? = null
        when (a.size - at - 1) {
            0 -> Unit
            1 -> when {
                a[at + 1] is Callable -> callback = a[at + 1]
                Compass.isCompass(a[at + 1]) -> compass = a[at + 1]
                else -> resultType = a[at + 1]
            }
            2 -> when {
                a[at + 2] is Callable && Compass.isCompass(a[at + 1]) -> { compass = a[at + 1]; callback = a[at + 2] }
                a[at + 2] is Callable -> { resultType = a[at + 1]; callback = a[at + 2] }
                else -> { compass = a[at + 1]; resultType = a[at + 2] }
            }
            else -> { compass = a[at + 1]; resultType = a[at + 2]; callback = a[at + 3] }
        }
        return Triple(compass, resultType, callback)
    }

    private fun nodeError(function: String, param: String, value: Any?): PickError? =
        if (isNullish(value) || nodeType.isInstance(value)) null else PickError(PickError.Kind.TYPE, PickMessages.argumentType(function, param, UI_OBJECT_TYPE, value))

    private fun typeErrors(function: String, compass: Any?, callback: Any?): PickError? = when {
        !isNullish(compass) && compass !is CharSequence -> PickError(PickError.Kind.TYPE, PickMessages.argumentType(function, COMPASS, "string", compass))
        !isNullish(callback) && callback !is Callable -> PickError(PickError.Kind.TYPE, PickMessages.argumentType(function, CALLBACK, "function", callback))
        else -> null
    }

    private fun compassOf(function: String, param: String, value: CharSequence): PickCompass = when (val parse = CompassParser.tryParse(value)) {
        CompassParse.PassOn -> PickCompass.PassOn
        is CompassParse.Steps -> PickCompass.Steps(parse.steps)
        is CompassParse.NotCompass -> PickCompass.Refused(range(PickMessages.argumentValue(function, param, value, PickMessages.invalidCompass(parse.compass))))
        is CompassParse.BadRest -> PickCompass.Refused(range(PickMessages.argumentValue(function, param, value, PickMessages.invalidRestCompass(parse.rest))))
    }

    /**
     * The result type [value] for [compass], `Detector` and `Invoker` told without calling anything: a nullish value is
     * a widget for `pickup` and nothing for `detect` ([single]), the empty string nothing (not even the callback).
     */
    private fun resultOf(function: String, param: String, value: Any?, compass: PickCompass, single: Boolean): PickResult {
        if (value == null) return if (single) PickResult.Empty else PickResult.Symbol(PickResultType.Widget)
        return when (val type = PickResultType.of(value)) {
            PickResultType.Nothing -> PickResult.Empty
            PickResultType.Selector -> when (compass) {
                PickCompass.PassOn -> PickResult.Symbol(type)
                else -> PickResult.Refused(PickError(PickError.Kind.TYPE, PickMessages.argumentValue(function, param, value, PickMessages.selectorWithCompass())), always = true)
            }
            PickResultType.WidgetCollection, PickResultType.Widgets, PickResultType.Contents, PickResultType.Points -> when {
                single -> PickResult.Refused(range(PickMessages.argumentValue(function, param, value, PickMessages.notForOneNode(value))), always = true)
                else -> PickResult.Symbol(type)
            }
            is PickResultType.Invocation -> invocation(function, param, value, type, single)
            is PickResultType.Unsupported -> PickResult.Nothing
            else -> PickResult.Symbol(type)
        }
    }

    private fun invocation(function: String, param: String, value: Any, call: PickResultType.Invocation, single: Boolean): PickResult {
        if (call.name.isEmpty()) return PickResult.Nothing
        val resolution = methods.resolve(call.name, call.arguments)
        if (resolution is NodeMethodResolution.Found) return PickResult.Call(call.name, resolution)
        // A one-element list whose head is no method falls back to the array form of its symbol (`["#"]`); one node has none.
        // zh-CN: 首项不是方法的单元素列表回退到符号的数组形式 (["#"]); 单个节点没有这一形式.
        val arrayForm = if (value is List<*> && call.arguments.isEmpty()) PickResultType.arrayFormOf(call.name) else null
        if (arrayForm != null && !single) return PickResult.ArrayForm(arrayForm)
        val message = when {
            arrayForm != null -> PickMessages.argumentValue(function, param, value, PickMessages.notForOneNode(value))
            resolution is NodeMethodResolution.NoOverload -> PickMessages.argumentValue(function, param, value, resolution.signatures.joinToString(" | "))
            resolution is NodeMethodResolution.Rejected -> PickMessages.argumentValue(function, param, value, resolution.reason)
            else -> PickMessages.unknownResultType(function, param, value, resultCandidates(call.name))
        }
        return PickResult.Refused(range(message), always = false)
    }

    private fun resultCandidates(name: String): List<String> =
        SelectorStringParser.suggest(name, resultSpellings).ifEmpty { FALLBACK_RESULT_CANDIDATES }

    private val resultSpellings: List<String> by lazy { (SYMBOLS.flatMap { PickResultType.spellings(it) } + methods.spellings).distinct() }

    /**
     * Reads a selector form into the selector given ([binding] builds it), or only checks it (no binding, no selector):
     * each condition is resolved by the registry first ([SelectorEntry.resolve]) and its patterns prechecked
     * ([PatternPrecheck]); what is left is compiled, in a `try` (where the regex engines of the JVM and Android differ).
     */
    private inner class SelectorReader(private val function: String, private val binding: Binding?) {

        fun read(param: String, value: Any?, into: UiSelector?): PickError? = when (value) {
            null -> null
            is String -> null.also { into?.append(binding!!.newSelector().content(value)) }
            is NativeRegExp -> into?.let { target -> compiled(param, value) { target.append(binding!!.newSelector().contentMatch(value)) } }
            is UiSelector -> null.also { into?.append(value) }
            is Map<*, *> -> conditions(param, value, into)
            is List<*> -> {
                var error: PickError? = null
                for (i in value.indices) {
                    val element = value[i] ?: continue
                    error = read("$param[$i]", element, into) ?: continue
                    break
                }
                error
            }
            is BigInteger -> null.also { into?.append(binding!!.newSelector().content(value.toString())) }
            is Double -> null.also { into?.append(binding!!.newSelector().content(value.toInt().toString())) }
            is NativeJavaMethod -> PickError(PickError.Kind.TYPE, PickMessages.argumentValue(function, param, value, PickMessages.methodNotCalled(value.functionName)))
            else -> PickError(PickError.Kind.TYPE, PickMessages.argumentType(function, param, SELECTOR_TYPE, value))
        }

        /** The conditions of an object selector, applied to one selector that is then appended (as the map parser applied them). */
        private fun conditions(param: String, map: Map<*, *>, into: UiSelector?): PickError? {
            val conditions = into?.let { binding!!.newSelector() }
            // A script object in the order of its entries (NativeObject.EntrySet: getIds, then get for each), without the entry objects.
            // zh-CN: 脚本对象按其条目的顺序读取 (NativeObject.EntrySet: getIds 后逐个 get), 不创建条目对象.
            if (map is NativeObject) {
                for (id in map.ids) if (id is String) condition(param, id, map[id], conditions)?.let { return it }
            } else {
                for ((key, value) in map.entries) if (key is String) condition(param, key, value, conditions)?.let { return it }
            }
            conditions?.let { into.append(it) }
            return null
        }

        private fun condition(param: String, key: String, value: Any?, into: UiSelector?): PickError? {
            if (key == ACTION && value == null) return null
            val where = "$param.$key"
            val entry = SelectorRegistry.entry(key) ?: return PickError(PickError.Kind.TYPE, PickMessages.unknownCondition(function, param, key, conditionCandidates(key)))
            val args = when (value) {
                null -> emptyList()
                is List<*> -> value.map(::filterOf).let { if (key == ACTION) it.filterNotNull() else it }
                else -> listOf(filterOf(value))
            }
            return when (val resolution = entry.resolve(args, lenient = value is List<*>)) {
                SelectorResolution.NoOverload -> PickError(PickError.Kind.TYPE, PickMessages.argumentType(function, where, accepted(entry), value))
                is SelectorResolution.Rejected -> range(PickMessages.argumentValue(function, where, value, resolution.reason))
                is SelectorResolution.Resolved -> {
                    resolution.patterns.firstNotNullOfOrNull { PatternPrecheck.refusal(it) }?.let { return range(PickMessages.argumentValue(function, where, value, it)) }
                    when {
                        into != null -> compiled(where, value) { resolution.overload.apply(into, resolution.arguments) }
                        resolution.patterns.isEmpty() -> null
                        else -> compiled(where, value) { resolution.patterns.forEach { JsRegexUtils.compileJsOrPlainRegex(it) } }
                    }
                }
            }
        }

        private fun filterOf(value: Any?): Any? = if (value is Callable) binding?.filter?.invoke(value) ?: NO_FILTER else value

        /** Runs [build] (which compiles a pattern the precheck left to compiling), its pattern error a RangeError. */
        private inline fun compiled(param: String, value: Any?, build: () -> Unit): PickError? = try {
            build()
            null
        } catch (e: IllegalArgumentException) {
            range(PickMessages.argumentValue(function, param, value, firstLine(e)))
        }

    }

    private fun conditionCandidates(key: String): List<String> =
        SelectorStringParser.suggest(key, SelectorRegistry.names).ifEmpty { FALLBACK_CONDITION_CANDIDATES.filter { it in SelectorRegistry } }

    /** The values the overloads of a condition take: a single argument, `null` (none), or an array of the arguments. */
    private fun accepted(entry: SelectorEntry): String = entry.overloads.map { overload ->
        when {
            overload.isVararg -> if (overload.params[0].type == SelectorArgType.ACTIONS) "string | number | Array" else "string | Array"
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
        SelectorArgType.OPTIONS -> "object"
        SelectorArgType.KEYS -> "string"
    }

    private fun refused(kind: PickError.Kind, message: String) = PickVerdict.Refused<N>(PickError(kind, message))

    private fun range(message: String) = PickError(PickError.Kind.RANGE, message)

    private fun isNullish(value: Any?) = value == null || Undefined.isUndefined(value)

    companion object {

        const val PICKUP = "pickup"
        const val DETECT = "detect"

        private const val ROOT = "root"
        private const val WIDGET = "w"
        private const val SELECTOR = "selector"
        private const val COMPASS = "compass"
        private const val RESULT_TYPE = "resultType"
        private const val CALLBACK = "callback"
        private const val ACTION = "action"

        /** The type names of the declarations (d.ts) the messages use. zh-CN: 消息所用的声明 (d.ts) 类型名. */
        private const val SELECTOR_TYPE = "PickupSelector"
        private const val UI_OBJECT_TYPE = "UiObject"

        private val SYMBOLS by lazy {
            listOf(
                PickResultType.Widget, PickResultType.WidgetCollection, PickResultType.Widgets, PickResultType.Content, PickResultType.Contents,
                PickResultType.Point, PickResultType.Points, PickResultType.Selector, PickResultType.Existence,
            )
        }

        private val FALLBACK_RESULT_CANDIDATES = listOf("#", "{}", "[]", "$", ".")
        private val FALLBACK_CONDITION_CANDIDATES = listOf("text", "desc", "id", "className", "clickable")

        /** The script functions of an object selector that is only checked stand for filters; the check only needs one that is no null. */
        private val NO_FILTER = BooleanFilter.BooleanSupplier { false }

        /**
         * Whether [value] has the shape of a selector form (a string, a `RegExp`, a `UiSelector`, an object, an array,
         * a number), so that flows and the toolkit tell it from a node, a function or a promise; the reader still refuses
         * what does not hold up. zh-CN: [value] 是否具有选择器形式的外形 (字符串, `RegExp`, `UiSelector`, 对象, 数组, 数字), 供 Flow 与
         * 工具集与节点, 函数或 Promise 区分; 不成立的内容仍由读取方拒绝.
         */
        fun isSelectorForm(value: Any?): Boolean = when (value) {
            is String, is NativeRegExp, is UiSelector, is Map<*, *>, is List<*>, is BigInteger, is Number -> true
            else -> false
        }

        /**
         * The [pattern] of [value] (a `RegExp` by its `/source/flags` form) given as [param] of [function], as a selector
         * condition compiles it: prechecked ([PatternPrecheck]), then compiled in a `try`; the RangeError, or null.
         * zh-CN: 作为 [function] 的参数 [param] 给出的 [value] 的正则 [pattern] (RegExp 取其 /source/flags 形式), 按选择器条件的方式编译: 先预检
         * ([PatternPrecheck]), 再在 try 中编译; 返回 RangeError, 能编译时为 null.
         */
        fun patternError(function: String, param: String, value: Any?, pattern: String): PickError? {
            PatternPrecheck.refusal(pattern)?.let { return PickError(PickError.Kind.RANGE, PickMessages.argumentValue(function, param, value, it)) }
            return try {
                JsRegexUtils.compileJsOrPlainRegex(pattern)
                null
            } catch (e: IllegalArgumentException) {
                PickError(PickError.Kind.RANGE, PickMessages.argumentValue(function, param, value, firstLine(e)))
            }
        }

        /** The first line of the message of [e] (a pattern error spans three), or its class. zh-CN: [e] 消息的首行 (正则错误有三行), 或其类名. */
        internal fun firstLine(e: Exception): String = e.message?.lineSequence()?.firstOrNull()?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName

        /** The normalizer of the nodes of a device (`UiObject`) with the registry of D11. zh-CN: 设备节点 (`UiObject`) 的归一化器, 注册表按 D11. */
        val uiObject: PickArguments<UiObject> by lazy { PickArguments(UiObject::class.java, NodeMethodRegistry.uiObject) }

    }

}
