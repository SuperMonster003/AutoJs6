package org.autojs.autojs.runtime.api.augment.shizuku

import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.rhino.ArgumentGuards
import org.autojs.autojs.rhino.ArgumentGuards.Companion.component1
import org.autojs.autojs.rhino.ArgumentGuards.Companion.component2
import org.autojs.autojs.rhino.extension.ScriptableExtensions.defineProp
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.AbstractShell
import org.autojs.autojs.runtime.api.WrappedShizuku
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.api.augment.Invokable
import org.autojs.autojs.runtime.api.augment.app.App
import org.autojs.autojs.runtime.api.augment.shell.Shell
import org.autojs.autojs.runtime.api.augment.shell.ShellProcess
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
        ::spawn.name,
    )

    override val selfAssignmentGetters = listOf<Pair<String, Supplier<Any?>>>(
        "state" to Supplier {
            newNativeObject(scriptRuntime.topLevelScope).also { o ->
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

        /**
         * `shizuku.spawn(command, options?)`: same as `shell.spawn` with `adb: true`. Options: a boolean (`root`, su
         * inside Shizuku) or `{ root?, env?, cwd? }`.
         * zh-CN: `shizuku.spawn(command, options?)`: 等同于带 `adb: true` 的 `shell.spawn`. 选项: 布尔值 (`root`, 在 Shizuku 中运行 su) 或
         * `{ root?, env?, cwd? }`.
         */
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun spawn(scriptRuntime: ScriptRuntime, args: Array<out Any?>): ShellProcess = ensureArgumentsLengthInRange(args, 1..2) { argList ->
            val (command, options) = argList
            Shell.spawnRhino(scriptRuntime, command, options, forceAdb = true)
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
