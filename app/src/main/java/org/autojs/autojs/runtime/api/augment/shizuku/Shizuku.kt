package org.autojs.autojs.runtime.api.augment.shizuku

import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.AbstractShell
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.api.augment.Invokable
import org.autojs.autojs.runtime.api.augment.shell.Shell.Companion.getCommandData

@Suppress("unused", "UNUSED_PARAMETER")
class Shizuku(private val scriptRuntime: ScriptRuntime) : Augmentable(scriptRuntime), Invokable {

    override val selfAssignmentFunctions = listOf(
        ::execCommand.name,
        ::getCommand.name,
    )

    override fun invoke(vararg args: Any?): AbstractShell.Result = execCommand(scriptRuntime, args)

    companion object {

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun execCommand(scriptRuntime: ScriptRuntime, args: Array<out Any?>): AbstractShell.Result = ensureArgumentsLengthInRange(args, 1..3) { argList ->
            scriptRuntime.shizuku.execCommand(getCommandData(argList).command)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun getCommand(scriptRuntime: ScriptRuntime, args: Array<out Any?>): String = ensureArgumentsLengthInRange(args, 1..3) { argList ->
            getCommandData(argList).command
        }

    }

}
