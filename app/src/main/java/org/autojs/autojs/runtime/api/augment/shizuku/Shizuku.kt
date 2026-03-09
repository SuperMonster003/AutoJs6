package org.autojs.autojs.runtime.api.augment.shizuku

import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.rhino.ArgumentGuards
import org.autojs.autojs.rhino.extension.ScriptableExtensions.defineProp
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.AbstractShell
import org.autojs.autojs.runtime.api.WrappedShizuku
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.api.augment.Invokable
import org.autojs.autojs.runtime.api.augment.app.App
import org.autojs.autojs.runtime.api.augment.shell.Shell
import org.autojs.autojs.util.RhinoUtils.newNativeObject
import java.util.function.Supplier

@Suppress("unused", "UNUSED_PARAMETER")
class Shizuku(private val scriptRuntime: ScriptRuntime) : Augmentable(scriptRuntime), Invokable {

    override val selfAssignmentFunctions = listOf(
        ::execCommand.name,
        ::getCommand.name,
        ::kill.name,
        ::currentPackage.name,
        ::currentActivity.name,
        ::currentComponent.name,
    )

    override val selfAssignmentGetters = listOf<Pair<String, Supplier<Any?>>>(
        "state" to Supplier {
            newNativeObject().also { o ->
                o.defineProp("isInstalled", WrappedShizuku.isInstalled(globalContext))
                o.defineProp("hasService", WrappedShizuku.hasService())
                o.defineProp("isRunning", WrappedShizuku.isRunning())
                o.defineProp("hasPermission", WrappedShizuku.hasPermission())
                o.defineProp("isOperational", WrappedShizuku.isOperational())
            }
        }
    )

    override fun invoke(vararg args: Any?): AbstractShell.Result = execCommand(scriptRuntime, args)

    companion object : ArgumentGuards() {

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun execCommand(scriptRuntime: ScriptRuntime, args: Array<out Any?>): AbstractShell.Result = ensureArgumentsLengthInRange(args, 1..3) { argList ->
            scriptRuntime.shizuku.execCommand(Shell.getCommandData(argList).command)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun getCommand(scriptRuntime: ScriptRuntime, args: Array<out Any?>): String = ensureArgumentsLengthInRange(args, 1..3) { argList ->
            Shell.getCommandData(argList).command
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun kill(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsOnlyOne(args) { app ->
            if (!WrappedShizuku.isOperational()) return@ensureArgumentsOnlyOne false
            when (val packageName = App.getPackageName(scriptRuntime, arrayOf(app))) {
                null -> false
                else -> execCommand(scriptRuntime, arrayOf("am force-stop $packageName")).code == 0
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun currentPackage(scriptRuntime: ScriptRuntime, args: Array<out Any?>): String = ensureArgumentsIsEmpty(args) {
            when {
                !WrappedShizuku.isOperational() -> ""
                else -> WrappedShizuku.service?.currentPackage() ?: ""
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun currentActivity(scriptRuntime: ScriptRuntime, args: Array<out Any?>): String = ensureArgumentsIsEmpty(args) {
            when {
                !WrappedShizuku.isOperational() -> ""
                else -> WrappedShizuku.service?.currentActivity() ?: ""
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun currentComponent(scriptRuntime: ScriptRuntime, args: Array<out Any?>): String = ensureArgumentsIsEmpty(args) {
            when {
                !WrappedShizuku.isOperational() -> ""
                else -> WrappedShizuku.service?.currentComponent() ?: ""
            }
        }

    }

}
