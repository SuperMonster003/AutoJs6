package org.autojs.autojs.runtime.api.augment.events

import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.core.eventloop.EventEmitter
import org.autojs.autojs.core.looper.MainThreadProxy
import org.autojs.autojs.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.extension.AnyExtensions.jsSpecies
import org.autojs.autojs.extension.FlexibleArray
import org.autojs.autojs.extension.ScriptableExtensions.defineProp
import org.autojs.autojs.extension.ScriptableExtensions.hasProp
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.autojs.util.RhinoUtils.NOT_CONSTRUCTABLE
import org.autojs.autojs.util.RhinoUtils.callFunction
import org.autojs.autojs.util.RhinoUtils.newBaseFunction
import org.autojs.autojs.util.RhinoUtils.newNativeObject
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeJavaMethod
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import java.lang.reflect.Modifier

class Events(scriptRuntime: ScriptRuntime) : Augmentable(scriptRuntime) {

    override val selfAssignmentFunctions = listOf(
        ::__asEmitter__.name,
    )

    companion object : FlexibleArray() {

        @JvmStatic
        @Suppress("FunctionName")
        @RhinoRuntimeFunctionInterface
        fun __asEmitter__(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ScriptableObject = ensureArgumentsAtMost(args, 2) { arguments ->
            var (obj, thread) = arguments
            if (obj.isJsNullish()) {
                obj = newNativeObject()
            }
            require(obj is ScriptableObject) { "Argument obj for events.__asEmitter__ must be a ScriptableObject" }
            val emitter: EventEmitter = when {
                thread.isJsNullish() -> scriptRuntime.events.emitter()
                thread is MainThreadProxy -> scriptRuntime.events.emitter(thread)
                thread is Thread -> scriptRuntime.events.emitter(thread)
                else -> throw WrappedIllegalArgumentException("Argument thread for events.__asEmitter__ must be a MainThreadProxy or Thread instead of ${thread.jsSpecies()}")
            }
            val scope = scriptRuntime.topLevelScope
            emitter.javaClass.declaredMethods.filter {
                !obj.hasProp(it.name) && it.name.matches(Regex("\\w+")) && Modifier.isPublic(it.modifiers)
            }.forEach { method ->
                obj.defineProp(method.name, newBaseFunction(method.name, { argList ->
                    val thisObj = Context.javaToJS(emitter, scope) as Scriptable
                    callFunction(scriptRuntime, NativeJavaMethod(method, method.name), scope, thisObj, arrayOf(*argList))
                }, NOT_CONSTRUCTABLE))
            }
            obj
        }

    }

}