package org.autojs.autojs.runtime.api.modules

import org.autojs.autojs.runtime.ScriptRuntime
import org.mozilla.javascript.LambdaFunction
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.SerializableCallable
import org.mozilla.javascript.VarScope
import org.mozilla.javascript.Wrapper
import org.mozilla.javascript.ScriptRuntime as RhinoScriptRuntime

/**
 * Extends JavaScript built-in objects with the modules of a [ScriptModuleRegistry] that declare `@ScriptModule(builtIn)`
 * (roadmap P2.1: the "extend JavaScript built-in objects" option and the `jsox` module, formerly
 * `JsBuildInObjectExtensible`). Extending module `Mathx` (built-in `Math`) binds its data properties declaring
 * `ScriptBuiltIn.STATIC` / `GLOBAL`, then its functions declaring `STATIC` on `Math`, `PROTOTYPE` on `Math.prototype`
 * and `GLOBAL` on the global object, in declaration order and with their declared attributes (PERMANENT by default),
 * as the old extension did. A prototype function passes its receiver as the first argument, unwrapped to the primitive
 * for `Number` / `String` / `Boolean` objects, so `(5).clamp(1, 3)` calls `Numberx.clamp(5, 1, 3)`. Every function is a
 * new function object (`Math.sum !== Mathx.sum`, as before), and extending again replaces them. Errors go through
 * [ScriptErrorMapper] like those of the mounted module objects (the old prototype functions exited the script instead).
 *
 * zh-CN: 用注册表中声明了 `@ScriptModule(builtIn)` 的模块扩展 JavaScript 内置对象 (P2.1: "JavaScript 内置对象扩展" 设置与 jsox 模块, 原
 * JsBuildInObjectExtensible). 以 Mathx (内置对象 Math) 为例, 依次绑定声明 STATIC / GLOBAL 的数据属性, 以及声明 STATIC (Math 上), PROTOTYPE (Math.prototype 上)
 * 与 GLOBAL (全局对象上) 的函数, 按声明顺序与声明的特性 (默认 PERMANENT), 与旧扩展一致. 原型函数把接收者作为第一个参数传入, Number / String / Boolean 对象取其原始值.
 * 每个函数都是新的函数对象, 再次扩展会替换它们. 错误与模块对象的成员一样经 ScriptErrorMapper 处理 (旧的原型函数改为直接退出脚本).
 */
class ScriptBuiltInExtender(
    private val registry: ScriptModuleRegistry,
    private val runtime: ScriptRuntime?,
    private val scope: VarScope,
    private val global: ScriptableObject,
    private val debugConsole: ScriptErrorMapper.DebugConsole? = null,
) {

    /** Names of the top-level modules that extend a built-in object, in registry order. */
    val names: List<String>
        get() = registry.bindings.filter { it.spec.parent == null && it.spec.builtIn != null }.map { it.spec.name }

    /** Extends the built-in object of the module named [name] (its primary name); false when there is no such module. */
    fun extend(name: String): Boolean {
        val binding = registry.bindings.firstOrNull { it.spec.parent == null && it.spec.builtIn != null && it.spec.name == name } ?: return false
        @Suppress("UNCHECKED_CAST")
        extend(binding as ScriptModuleBinding<Any>)
        return true
    }

    private fun extend(binding: ScriptModuleBinding<Any>) {
        val spec = binding.spec
        val builtIn = checkNotNull(spec.builtIn)
        val module = guarded(spec.name, spec.name) { binding.create(runtime) }
        val target = ScriptableObject.getProperty(global, builtIn) as? ScriptableObject
            ?: throw IllegalStateException("Built-in object $builtIn extended by script module ${spec.name} is not an object")
        val prototype by lazy {
            ScriptableObject.getProperty(target, "prototype") as? ScriptableObject
                ?: throw IllegalStateException("Built-in object $builtIn extended by script module ${spec.name} has no prototype object")
        }
        spec.accessors.filter { !it.getter && (it.onBuiltIn || it.onBuiltInGlobal) }.forEach { a ->
            val value = guarded("$builtIn.${a.name}", spec.name) { binding.get(module, a.id, scope) }
            if (a.onBuiltIn) target.defineProperty(a.name, value, a.attributes)
            if (a.onBuiltInGlobal) global.defineProperty(a.name, value, a.attributes)
        }
        spec.functions.filter { it.onBuiltIn }.forEach { f ->
            (listOf(f.name) + f.aliases).forEach { name -> target.defineProperty(name, function(binding, module, f, name, "$builtIn.$name", receiver = false), f.attributes) }
        }
        spec.functions.filter { it.onBuiltInPrototype }.forEach { f ->
            prototype.defineProperty(f.name, function(binding, module, f, f.name, "$builtIn.prototype.${f.name}", receiver = true), f.attributes)
        }
        spec.functions.filter { it.onBuiltInGlobal }.forEach { f ->
            (listOf(f.name) + f.aliases).forEach { name -> global.defineProperty(name, function(binding, module, f, name, name, receiver = false), f.attributes) }
        }
    }

    private fun function(binding: ScriptModuleBinding<Any>, module: Any, f: ScriptFunctionSpec, name: String, member: String, receiver: Boolean): LambdaFunction {
        val topLevel = binding.spec.name
        val length = if (receiver) maxOf(0, f.length - 1) else f.length
        return LambdaFunction(scope, name, length, SerializableCallable { cx, s, thisObj, args ->
            guarded(member, topLevel) {
                val actual = if (receiver) arrayOf(receiverOf(thisObj), *args) else args
                binding.call(module, f.id, cx, s, thisObj, actual)
            }
        }, false)
    }

    /** Runs [block] for [member] of the top-level module [module], mapping what it throws (T5). */
    private inline fun <T> guarded(member: String, module: String, block: () -> T): T = try {
        block()
    } catch (e: Throwable) {
        throw ScriptErrorMapper.map(e, member, module, debugConsole)
    }

    private companion object {

        /** The receiver as a module function takes it: the primitive value of a Number, String or Boolean object. */
        fun receiverOf(thisObj: Any?): Any? = when {
            thisObj !is ScriptableObject || thisObj is Wrapper -> thisObj
            thisObj.className == "Number" -> RhinoScriptRuntime.toNumber(thisObj)
            thisObj.className == "String" -> RhinoScriptRuntime.toString(thisObj)
            thisObj.className == "Boolean" -> RhinoScriptRuntime.toBoolean(thisObj)
            else -> thisObj
        }

    }

}
