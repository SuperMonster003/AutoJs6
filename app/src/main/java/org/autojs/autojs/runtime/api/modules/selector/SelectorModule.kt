package org.autojs.autojs.runtime.api.modules.selector

import org.autojs.autojs.core.accessibility.UiSelector
import org.autojs.autojs.core.automator.UiObject
import org.autojs.autojs.core.automator.filter.BooleanFilter
import org.autojs.autojs.core.automator.selector.SelectorRegistry
import org.autojs.autojs.rhino.extension.AnyExtensions.jsUnwrapped
import org.autojs.autojs.rhino.extension.ScriptableExtensions.hasProp
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.selector.SelectorGlobals
import org.autojs.autojs.runtime.api.augment.selector.SelectorMethodResolver
import org.autojs.autojs.runtime.api.modules.ScriptArgs
import org.autojs.autojs.runtime.api.modules.ScriptAttributes
import org.autojs.autojs.runtime.api.modules.ScriptFunction
import org.autojs.autojs.runtime.api.modules.ScriptGlobal
import org.autojs.autojs.runtime.api.modules.ScriptInstall
import org.autojs.autojs.runtime.api.modules.ScriptInvoke
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.autojs.autojs.runtime.api.modules.ScriptModuleInstallation
import org.autojs.autojs.runtime.api.modules.global.GlobalValues
import org.autojs.autojs.runtime.api.modules.global.GlobalValues.isNullish
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.autojs.autojs.util.RhinoUtils
import org.autojs.autojs.util.RhinoUtils.coerceBoolean
import org.autojs.autojs.util.RhinoUtils.coerceString
import org.mozilla.javascript.Callable
import org.mozilla.javascript.Context
import org.mozilla.javascript.EcmaError
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.VarScope
import java.lang.reflect.InvocationTargetException

/**
 * The `selector` module on the new script module layer (roadmap P3.3): callable as `selector()` (a new `UiSelector`), with
 * `pickup`, `detect`, `existsAll` and `existsOne` on the global object only, and the selector conditions, queries and
 * node actions of `SelectorGlobals.names` as global functions (`text('a')`, `findOne(3000)`, `scrollForward()`), each
 * starting from a new selector. As with the current module, the install hook defines only the names that are not on the
 * global object yet (so `click`, `setText`, ... stay those of `automator`), and the module names are read-only.
 *
 * Arguments follow the documented forms (T4): an argument count or a root, widget, compass or callback of a type that no
 * form of `pickup` / `detect` accepts is a TypeError, and so is a call of a global selector function whose arguments match
 * no condition or method overload (D10; the current module threw IllegalArgumentException after a console warning). The
 * selector, compass and result type of `pickup` / `detect` are checked as the current readers read them ([SelectorForms],
 * appendix C Q66 (2), Q67), and a value a condition refuses is a RangeError without the console warnings (Q68).
 *
 * zh-CN: 新脚本模块层上的 selector 模块 (P3.3): 可调用 (selector() 返回新的 UiSelector); pickup, detect, existsAll 与 existsOne 只在全局对象上;
 * SelectorGlobals.names 中的选择器条件, 查询与节点动作作为全局函数 (text('a'), findOne(3000), scrollForward()), 每次从新的选择器开始. 同现有模块, install 钩子只定义
 * 全局对象上尚未存在的名称 (所以 click, setText 等仍是 automator 的), 模块名称只读. 参数按文档形式 (T4): pickup / detect 的任何形式都不接受的参数个数或 root,
 * widget, compass, callback 类型为 TypeError; 全局选择器函数的参数与任何条件或方法重载都不匹配时也为 TypeError (D10; 现有模块在控制台警告后抛 IllegalArgumentException).
 * pickup / detect 的选择器, 罗盘与结果类型按现有读取方的读法检查 ([SelectorForms], 附录 C Q66 (2), Q67); 条件拒绝的值为 RangeError, 不再输出控制台警告 (Q68).
 */
@ScriptModule(name = "selector", attributes = ScriptAttributes.PERMANENT or ScriptAttributes.READONLY)
class SelectorModule(private val runtime: ScriptRuntime) {

    @ScriptInvoke
    fun invoke(): Scriptable = Context.javaToJS(runtime.selector(), runtime.topLevelScope) as Scriptable

    /** `pickup(root?, selector?, compass?, resultType?, callback?)` in the forms of the current module, told apart by the types of the arguments. */
    @ScriptFunction(global = ScriptGlobal.ONLY)
    fun pickup(vararg args: Any?): Any? {
        if (args.size > 5) throw ScriptArgs.arityError("pickup", 0, 5, args.size)
        val a = args.map { RhinoUtils.unwrap(it) }
        if (a.isEmpty()) return UiSelector.pickup(runtime, null, null, null, UiObject.RESULT_TYPE_WIDGET, null)
        // A UiObject first is the root from two arguments on, and the first of five arguments always is (the current forms).
        // zh-CN: 两个参数起, 首个参数为 UiObject 时它是 root; 五个参数时首个参数总是 root (同现有各形式).
        val root = a.size == 5 || a.size >= 2 && a[0] is UiObject
        val rest = if (root) a.drop(1) else a
        if (root) root("pickup", "root", a[0])
        val selector = rest.getOrNull(0)
        var compass: Any? = UiObject.COMPASS_PASS_ON
        var resultType: Any? = UiObject.RESULT_TYPE_WIDGET
        var callback: Any? = null
        val tail = rest.drop(1)
        when (tail.size) {
            0 -> Unit
            1 -> when {
                tail[0] is Callable -> callback = tail[0]
                SelectorChecks.isCompass(tail[0]) -> compass = tail[0]
                else -> resultType = tail[0]
            }
            2 -> when {
                tail[1] is Callable && SelectorChecks.isCompass(tail[0]) -> { compass = tail[0]; callback = tail[1] }
                tail[1] is Callable -> { resultType = tail[0]; callback = tail[1] }
                else -> { compass = tail[0]; resultType = tail[1] }
            }
            else -> { compass = tail[0]; resultType = tail[1]; callback = tail[2] }
        }
        compass("pickup", compass)
        callback("pickup", callback)
        SelectorForms.selector("pickup", "selector", selector)
        SelectorForms.compass("pickup", "compass", compass)
        SelectorForms.resultType("pickup", "resultType", resultType, compass as? CharSequence, single = false)
        return UiSelector.pickup(runtime, if (root) a[0] as? UiObject else null, selector, compass as? CharSequence, resultType, callback as? Callable)
    }

    /** `detect(widget, compass?, resultType?, callback?)` in the forms of the current module. */
    @ScriptFunction(global = ScriptGlobal.ONLY)
    fun detect(vararg args: Any?): Any? {
        if (args.size !in 2..4) throw ScriptArgs.arityError("detect", 2, 4, args.size)
        val a = args.map { RhinoUtils.unwrap(it) }
        root("detect", "w", a[0])
        var compass: Any? = UiObject.COMPASS_PASS_ON
        var resultType: Any? = UiObject.RESULT_TYPE_WIDGET
        var callback: Any? = null
        when (a.size) {
            2 -> when {
                a[1] is Callable -> callback = a[1]
                SelectorChecks.isCompass(a[1]) -> compass = a[1]
                else -> resultType = a[1]
            }
            3 -> when {
                a[2] is Callable && SelectorChecks.isCompass(a[1]) -> { compass = a[1]; callback = a[2] }
                a[2] is Callable -> { resultType = a[1]; callback = a[2] }
                else -> { compass = a[1]; resultType = a[2] }
            }
            else -> { compass = a[1]; resultType = a[2]; callback = a[3] }
        }
        compass("detect", compass)
        callback("detect", callback)
        SelectorForms.compass("detect", "compass", compass)
        SelectorForms.resultType("detect", "resultType", resultType, compass as? CharSequence, single = true)
        return UiObject.detect(runtime, a[0] as? UiObject, compass as? CharSequence, resultType, callback as? Callable)
    }

    @ScriptFunction(global = ScriptGlobal.ONLY)
    fun existsAll(vararg selectors: Any?): Boolean = selectors.all { coerceBoolean(pickup(it, "?")) }

    @ScriptFunction(global = ScriptGlobal.ONLY)
    fun existsOne(vararg selectors: Any?): Boolean = selectors.any { coerceBoolean(pickup(it, "?")) }

    /**
     * Defines the global selector functions of `SelectorGlobals.names` that are not on the global object yet (nor members
     * of every Java object), in that order, each as its own lazy function, as the current module defined them when it was
     * created.
     *
     * zh-CN: 定义 SelectorGlobals.names 中全局对象上尚未存在 (也不是所有 Java 对象都有的成员) 的全局选择器函数, 顺序照旧, 各自为惰性函数, 同现有模块创建时的定义.
     */
    @ScriptInstall
    fun install(installation: ScriptModuleInstallation) {
        val global = installation.global
        SelectorGlobals.names.filter { it !in JAVA_OBJECT_MEMBERS && it !in SelectorGlobals.excluded && !global.hasProp(it) }.forEach { name ->
            installation.defineLazy(name, ScriptAttributes.EMPTY) {
                installation.function(name, 0) { _, _, _, args -> select(name, args) }
            }
        }
    }

    /** A global selector function: a condition on a new selector, or a query or node action of a new selector. */
    private fun select(name: String, args: Array<out Any?>): Any? {
        val selector = runtime.selector()
        if (SelectorGlobals.isRegistryProperty(name)) {
            val conditionArgs = args.map { arg ->
                when (val unwrapped = arg.jsUnwrapped()) {
                    is Callable -> BooleanFilter.BooleanSupplier { node ->
                        coerceBoolean(RhinoUtils.callFunction(runtime, unwrapped, null as VarScope?, Undefined.SCRIPTABLE_UNDEFINED, arrayOf(node)))
                    }
                    else -> unwrapped
                }
            }
            // A value the condition takes but refuses (Q68): NaN for an integer, an unknown algorithm, a pattern that does not
            // compile; told before applying it where SelectorChecks can (Q69 (2)), else by what applying it throws.
            // zh-CN: 条件接受了类型却拒绝的值 (Q68): 整数处的 NaN, 未知的算法, 无法编译的正则; SelectorChecks 能判定的在施加前判定 (Q69 (2)), 其余看施加时的异常.
            val param = if (args.size == 1) "arguments[0]" else "arguments"
            val value = if (args.size == 1) args[0] else args.toList()
            when (val verdict = SelectorChecks.condition(SelectorRegistry.entry(name)!!, conditionArgs, lenient = false)) {
                SelectorChecks.Verdict.NoOverload -> throw noOverload(name, args)
                is SelectorChecks.Verdict.Rejected -> throw SelectorForms.rejected(name, param, value, verdict.reason)
                else -> Unit
            }
            val outcome = reported(name, args) {
                try {
                    SelectorRegistry.apply(selector, name, conditionArgs)
                } catch (e: IllegalArgumentException) {
                    throw SelectorForms.rejected(name, param, value, e)
                }
            }
            if (outcome != SelectorRegistry.Outcome.APPLIED) throw noOverload(name, args)
            return selector
        }
        val resolved = SelectorMethodResolver.resolve(QUERY_METHODS[name].orEmpty(), args) ?: throw noOverload(name, args)
        return reported(name, args) {
            try {
                resolved.method.invoke(selector, *resolved.arguments)
            } catch (e: InvocationTargetException) {
                throw e.targetException
            }
        }
    }

    /**
     * Runs [block], warning in the console of what fails there with the arguments of the call, as the current module did;
     * an argument error (a script error, D10) is thrown without the warnings, as those of the other modules.
     */
    private inline fun <T> reported(name: String, args: Array<out Any?>, block: () -> T): T = try {
        block()
    } catch (e: Throwable) {
        if (e is EcmaError || ScriptInterruptedException.causedByInterrupt(e)) throw e
        runtime.console.warn("${e.message}\n${e.stackTraceToString()}")
        runtime.console.warn("method: $name, arguments: [${args.joinToString(",") { coerceString(it) }}]")
        throw e
    }

    private fun noOverload(name: String, args: Array<out Any?>) = ScriptArgs.noOverload(name, args, "the conditions or methods UiSelector.$name")

    private fun root(function: String, param: String, value: Any?) {
        if (!isNullish(value) && value !is UiObject) throw GlobalValues.typeError(function, param, "UiObject", value)
    }

    private fun compass(function: String, value: Any?) {
        if (!isNullish(value) && value !is CharSequence) throw GlobalValues.typeError(function, "compass", "string", value)
    }

    private fun callback(function: String, value: Any?) {
        if (!isNullish(value) && value !is Callable) throw GlobalValues.typeError(function, "callback", "function", value)
    }

    private companion object {

        /** The names every Java object has (`equals`, `hashCode`, `toString`), never exported. */
        val JAVA_OBJECT_MEMBERS = setOf("equals", "hashCode", "toString")

        val QUERY_METHODS by lazy { UiSelector::class.java.methods.groupBy { it.name } }
    }

}
