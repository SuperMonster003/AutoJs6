package org.autojs.autojs.runtime.api.augment.mediainfo

import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.rhino.ArgumentGuards
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.api.augment.Invokable
import org.mozilla.javascript.NativeObject

@Suppress("unused", "UNUSED_PARAMETER")
class Mediainfo(private val scriptRuntime: ScriptRuntime) : Augmentable(scriptRuntime), Invokable {

    override val selfAssignmentFunctions = listOf(
        ::read.name,
    )

    override fun invoke(vararg args: Any?): NativeObject = ensureArgumentsOnlyOne(args) { path ->
        read(scriptRuntime, arrayOf(path))
    }

    companion object : ArgumentGuards() {

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun read(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeObject = ensureArgumentsOnlyOne(args) { path ->
            MediainfoNativeObject(scriptRuntime, path)
        }

    }

}
