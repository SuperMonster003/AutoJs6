package org.autojs.autojs.runtime.api.modules.selector

import org.autojs.autojs.core.accessibility.UiSelector
import org.autojs.autojs.core.automator.filter.BooleanFilter
import org.autojs.autojs.core.automator.filter.PatternPrecheck
import org.autojs.autojs.core.automator.pick.PickResultAdapter
import org.autojs.autojs.core.automator.selector.SelectorRegistry
import org.autojs.autojs.core.automator.selector.SelectorResolution
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
import org.autojs.autojs.runtime.api.modules.ScriptMessages
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.autojs.autojs.runtime.api.modules.ScriptModuleInstallation
import org.autojs.autojs.runtime.api.modules.util.UtilValues
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
import org.mozilla.javascript.ScriptRuntime as RhinoScriptRuntime

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
 * arguments of `pickup` / `detect` are told by the normalizer of the core (`PickArguments`, docs/selector/ROADMAP.md P1.1,
 * P1.4; formerly the copies `SelectorForms` / `SelectorChecks` / `SelectorPatterns` of this package, appendix C Q66 (2),
 * Q67), and a value a condition refuses is a RangeError without the console warnings (Q68).
 *
 * zh-CN: 新脚本模块层上的 selector 模块 (P3.3): 可调用 (selector() 返回新的 UiSelector); pickup, detect, existsAll 与 existsOne 只在全局对象上;
 * SelectorGlobals.names 中的选择器条件, 查询与节点动作作为全局函数 (text('a'), findOne(3000), scrollForward()), 每次从新的选择器开始. 同现有模块, install 钩子只定义
 * 全局对象上尚未存在的名称 (所以 click, setText 等仍是 automator 的), 模块名称只读. 参数按文档形式 (T4): pickup / detect 的任何形式都不接受的参数个数或 root,
 * widget, compass, callback 类型为 TypeError; 全局选择器函数的参数与任何条件或方法重载都不匹配时也为 TypeError (D10; 现有模块在控制台警告后抛 IllegalArgumentException).
 * pickup / detect 的参数由 core 的归一化器判定 (PickArguments, selector P1.1 / P1.4; 原为本包的副本 SelectorForms / SelectorChecks / SelectorPatterns,
 * 附录 C Q66 (2), Q67); 条件拒绝的值为 RangeError, 不再输出控制台警告 (Q68).
 */
@ScriptModule(name = "selector", attributes = ScriptAttributes.PERMANENT or ScriptAttributes.READONLY)
class SelectorModule(private val runtime: ScriptRuntime) {

    @ScriptInvoke
    fun invoke(): Scriptable = Context.javaToJS(runtime.selector(), runtime.topLevelScope) as Scriptable

    /** `pickup(root?, selector?, compass?, resultType?, callback?)` in the forms of the current module, told apart by the types of the arguments. */
    @ScriptFunction(global = ScriptGlobal.ONLY, arity = [0, 5])
    fun pickup(vararg args: Any?): Any? = PickResultAdapter.pickup(runtime, Array(args.size) { RhinoUtils.unwrap(args[it]) })

    /** `detect(widget, compass?, resultType?, callback?)` in the forms of the current module. */
    @ScriptFunction(global = ScriptGlobal.ONLY, arity = [2, 4])
    fun detect(vararg args: Any?): Any? = PickResultAdapter.detect(runtime, Array(args.size) { RhinoUtils.unwrap(args[it]) })

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
    @ScriptInstall(computed = "the global selector functions: SelectorGlobals.names not on the global object yet, nor members of every Java object, nor SelectorGlobals.excluded")
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
            // compile; told before applying it by the registry and the pattern precheck (Q69 (2), selector P1.4), else by what
            // applying it throws.
            // zh-CN: 条件接受了类型却拒绝的值 (Q68): 整数处的 NaN, 未知的算法, 无法编译的正则; 由注册表与正则预检在施加前判定 (Q69 (2), selector P1.4), 其余看施加时的异常.
            val param = if (args.size == 1) "arguments[0]" else "arguments"
            val value = if (args.size == 1) args[0] else args.toList()
            when (val resolution = SelectorRegistry.entry(name)!!.resolve(conditionArgs, lenient = false)) {
                SelectorResolution.NoOverload -> throw noOverload(name, args)
                is SelectorResolution.Rejected -> throw rejected(name, param, value, resolution.reason)
                is SelectorResolution.Resolved -> resolution.patterns.firstNotNullOfOrNull { PatternPrecheck.refusal(it) }?.let { throw rejected(name, param, value, it) }
            }
            val outcome = reported(name, args) {
                try {
                    SelectorRegistry.apply(selector, name, conditionArgs)
                } catch (e: IllegalArgumentException) {
                    throw rejected(name, param, value, e.message?.lineSequence()?.firstOrNull()?.takeIf { it.isNotBlank() } ?: e.javaClass.simpleName)
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

    /** A value a condition takes but refuses, for [reason] (NaN for an integer, an unknown algorithm, a pattern that does not compile). */
    private fun rejected(function: String, param: String, value: Any?, reason: String): RuntimeException =
        RhinoScriptRuntime.rangeError(ScriptMessages.failedToCall(function, ScriptMessages.argumentCannotBe(param, "${show(value)} ($reason)")))

    private fun show(value: Any?): String = if (value is List<*>) value.joinToString(", ", "[", "]") { show(it) } else UtilValues.quote(value)

    private companion object {

        /** The names every Java object has (`equals`, `hashCode`, `toString`), never exported. */
        val JAVA_OBJECT_MEMBERS = setOf("equals", "hashCode", "toString")

        val QUERY_METHODS by lazy { UiSelector::class.java.methods.groupBy { it.name } }
    }

}
