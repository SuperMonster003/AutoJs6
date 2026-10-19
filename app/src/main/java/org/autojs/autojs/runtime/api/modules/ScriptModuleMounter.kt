package org.autojs.autojs.runtime.api.modules

import org.autojs.autojs.runtime.ScriptRuntime
import org.mozilla.javascript.Context
import org.mozilla.javascript.LambdaConstructor
import org.mozilla.javascript.LambdaFunction
import org.mozilla.javascript.NativeJavaClass
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.SerializableCallable
import org.mozilla.javascript.SerializableConstructable
import org.mozilla.javascript.VarScope
import java.util.function.Consumer
import java.util.function.Supplier
import org.mozilla.javascript.ScriptRuntime as RhinoScriptRuntime

/**
 * Builds a module object from a generated [ScriptModuleBinding] and binds it on a target object (P1.1 prototype).
 *
 * Mounting is eager here; P1.2 replaces this with the on-demand accessors of roadmap T3 (the module object built on
 * first access, then redefined as a permanent data property) and wires it into `ScriptRuntime` behind the T8 switch.
 * Aliases of a function share one function object.
 *
 * zh-CN: 由生成的绑定构建模块对象并挂到目标对象上 (P1.1 原型). 这里是急切挂载; P1.2 改为 T3 的按需访问器 (首次访问时构建, 再重定义为永久数据属性),
 * 并在 T8 开关后接入 ScriptRuntime. 函数的别名共用同一个函数对象.
 */
object ScriptModuleMounter {

    @JvmStatic
    fun <M : Any> mount(
        binding: ScriptModuleBinding<M>,
        runtime: ScriptRuntime?,
        target: ScriptableObject,
        global: ScriptableObject,
        scope: VarScope,
        cx: Context = Context.getCurrentContext(),
    ): ScriptableObject {
        val module = binding.create(runtime)
        val spec = binding.spec
        val moduleObject = moduleObject(binding, module, spec, cx, scope)

        binding.prototype(module)?.let { proto ->
            val prototype = proto as? Scriptable ?: RhinoScriptRuntime.toObject(scope, proto)
            prototype.prototype = moduleObject.prototype
            moduleObject.prototype = prototype
        }

        spec.functions.forEach { f ->
            val function = LambdaFunction(scope, f.name, f.length, SerializableCallable { c, s, thisObj, args ->
                binding.call(module, f.id, c, s, thisObj, args)
            }, false)
            (listOf(f.name) + f.aliases).forEach { name ->
                if (f.onModule) moduleObject.defineProperty(name, function, f.attributes)
                if (f.onGlobal) global.defineProperty(name, function, f.attributes)
            }
        }

        spec.accessors.forEach { a ->
            val holders = listOfNotNull(moduleObject.takeIf { a.onModule }, global.takeIf { a.onGlobal })
            if (a.getter) {
                val getter = Supplier<Any?> { binding.get(module, a.id, scope) }
                val setter = if (a.writable) Consumer<Any?> { value -> binding.set(module, a.id, value) } else null
                holders.forEach { it.defineProperty(a.name, getter, setter, a.attributes) }
            } else {
                val value = binding.get(module, a.id, scope)
                holders.forEach { it.defineProperty(a.name, value, a.attributes) }
            }
        }

        spec.javaClasses.forEach { j ->
            val javaClass = NativeJavaClass(scope, j.type)
            if (j.onModule) moduleObject.defineProperty(j.name, javaClass, j.attributes)
            if (j.onGlobal) global.defineProperty(j.name, javaClass, j.attributes)
        }

        spec.names.forEach { name -> target.defineProperty(name, moduleObject, ScriptableObject.PERMANENT) }
        return moduleObject
    }

    private fun <M : Any> moduleObject(binding: ScriptModuleBinding<M>, module: M, spec: ScriptModuleSpec, cx: Context, scope: VarScope): ScriptableObject {
        val invoke = SerializableCallable { c, s, thisObj, args -> binding.call(module, ScriptModuleBinding.INVOKE, c, s, thisObj, args) }
        val construct = SerializableConstructable { c, _, s, args -> binding.call(module, ScriptModuleBinding.CONSTRUCT, c, s, null, args) as Scriptable }
        return when {
            spec.invokeLength >= 0 && spec.constructLength >= 0 -> LambdaConstructor(scope, spec.name, spec.invokeLength, invoke, construct)
            spec.constructLength >= 0 -> LambdaConstructor(scope, spec.name, spec.constructLength, construct)
            spec.invokeLength >= 0 -> LambdaFunction(scope, spec.name, spec.invokeLength, invoke, false)
            else -> cx.newObject(scope) as ScriptableObject
        }
    }

}
