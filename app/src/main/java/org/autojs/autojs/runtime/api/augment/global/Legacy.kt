package org.autojs.autojs.runtime.api.augment.global

import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.api.augment.util.Util

@Suppress("unused", "UNUSED_PARAMETER")
class Legacy(scriptRuntime: ScriptRuntime) : Augmentable(scriptRuntime) {

    override val selfAssignmentFunctions = listOf(
        ::isObject.name,
        ::isObjectSpecies.name,
    )

    companion object {

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun isObject(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsLength(args, 1) {
            Util.isObject(it)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun isObjectSpecies(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsLength(args, 1) {
            Util.isObject(it)
        }

    }

}