package org.autojs.autojs.runtime.api.modules.events

import org.autojs.autojs.core.eventloop.EventEmitter
import org.autojs.autojs.core.looper.MainThreadProxy
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.modules.ScriptAttributes
import org.autojs.autojs.runtime.api.modules.ScriptFunction
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.autojs.autojs.runtime.api.modules.ScriptPrototype
import org.autojs.autojs.runtime.api.modules.global.GlobalValues
import org.autojs.autojs.runtime.api.modules.global.GlobalValues.isNullish
import org.autojs.autojs.util.RhinoUtils
import org.mozilla.javascript.Context
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Wrapper
import java.lang.reflect.Modifier

/**
 * The `events` module on the new script module layer (roadmap P3.3): the module object's prototype is the runtime's Java
 * `Events` (`ScriptRuntime.events`: `emitter`, `observeKey`, `onKeyDown`, `observeNotification` and the rest), as with the
 * current module, plus the hidden `__asEmitter__(obj?, thread?)` (READONLY, DONTENUM) that the obsolete JS of other
 * modules used to turn an object into an event emitter.
 *
 * `__asEmitter__` binds every public method of a new emitter (of the script thread, or of [thread] when it is a `Thread` or
 * the main thread proxy) that [obj] does not have yet, and returns [obj] (a new object when nullish). Arguments follow the
 * real signature (T4): an object that is not a script object or a thread of another kind is a TypeError (D10; the current
 * module threw IllegalArgumentException).
 *
 * zh-CN: 新脚本模块层上的 events 模块 (P3.3): 模块对象的原型是运行时的 Java Events (ScriptRuntime.events: emitter, observeKey, onKeyDown,
 * observeNotification 等), 同现有模块; 另有隐藏的 __asEmitter__(obj?, thread?) (READONLY, DONTENUM), 旧 JS 曾用它把其他模块的对象变成事件发射器.
 * __asEmitter__ 把一个新发射器 (脚本线程的, 或 thread 为 Thread 或主线程代理时属于它的) 中 obj 尚未拥有的公有方法逐个绑定到 obj 上, 返回 obj
 * (null / undefined 时为新对象). 参数按真实签名 (T4): obj 不是脚本对象, 或 thread 为其他类型时为 TypeError (D10; 现有模块抛 IllegalArgumentException).
 */
@ScriptModule(name = "events")
class EventsModule(private val runtime: ScriptRuntime) {

    @ScriptPrototype
    val prototype: Any
        get() = runtime.events

    @ScriptFunction(name = "__asEmitter__", attributes = ScriptAttributes.READONLY or ScriptAttributes.DONTENUM or ScriptAttributes.PERMANENT)
    fun asEmitter(obj: Any? = null, thread: Any? = null): ScriptableObject {
        val scope = runtime.topLevelScope
        val target = when {
            isNullish(obj) -> RhinoUtils.newNativeObject(scope)
            obj is ScriptableObject -> obj
            else -> throw GlobalValues.typeError(FUNCTION, "obj", "Object", obj)
        }
        val emitter: EventEmitter = when (val t = if (thread is Wrapper) thread.unwrap() else thread) {
            null, is org.mozilla.javascript.Undefined -> runtime.events.emitter()
            is MainThreadProxy -> runtime.events.emitter(t)
            is Thread -> runtime.events.emitter(t)
            else -> throw GlobalValues.typeError(FUNCTION, "thread", "Thread", thread)
        }
        emitter.javaClass.declaredMethods.filter { Modifier.isPublic(it.modifiers) && isWord(it.name) && !ScriptableObject.hasProperty(target, it.name) }.forEach { method ->
            val function = RhinoUtils.newBaseFunction(scope, method.name, { args ->
                val thisObj = Context.javaToJS(emitter, scope) as Scriptable
                RhinoUtils.callFunction(runtime, RhinoUtils.newNativeJavaMethod(scope, method, method.name), scope, thisObj, arrayOf(*args))
            }, RhinoUtils.NOT_CONSTRUCTABLE)
            ScriptableObject.defineProperty(target, method.name, function, ScriptableObject.EMPTY)
        }
        return target
    }

    /** The `\w+` test of the current module without a regular expression: ASCII letters, digits and `_`. */
    private fun isWord(name: String) = name.isNotEmpty() && name.all { it in 'a'..'z' || it in 'A'..'Z' || it in '0'..'9' || it == '_' }

    private companion object {
        const val FUNCTION = "events.__asEmitter__"
    }

}
