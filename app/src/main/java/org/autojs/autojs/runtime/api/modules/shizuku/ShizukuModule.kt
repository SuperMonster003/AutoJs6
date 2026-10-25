package org.autojs.autojs.runtime.api.modules.shizuku

import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.AbstractShell
import org.autojs.autojs.runtime.api.WrappedShizuku
import org.autojs.autojs.runtime.api.modules.ScriptFunction
import org.autojs.autojs.runtime.api.modules.ScriptGetter
import org.autojs.autojs.runtime.api.modules.ScriptInvoke
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.autojs.autojs.runtime.api.modules.ScriptPrototype
import org.autojs.autojs.runtime.api.modules.shell.ShellArgs
import org.autojs.autojs.runtime.api.modules.shell.ShellArgs.ABSENT
import org.autojs.autojs.runtime.api.modules.shell.ShellProcess
import org.autojs.autojs.util.RhinoUtils
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.ScriptableObject

/**
 * The `shizuku` module on the new script module layer (roadmap P3.4): callable as `execCommand`, the module object's
 * prototype is the runtime's `WrappedShizuku` (`ScriptRuntime.shizuku`), with the functions of the current module in its
 * order and the `state` getter, served by the current implementation (`augment/shizuku/Shizuku` and the shell module's
 * command parsing; to be moved out before P5.2). Arguments as for `shell` ([ShellArgs], D10; issue #310: a command may be
 * an array of strings).
 *
 * zh-CN: 新脚本模块层上的 shizuku 模块 (P3.4): 可调用 (同 execCommand), 模块对象的原型是运行时的 WrappedShizuku (ScriptRuntime.shizuku), 函数及顺序同现有
 * 模块, 另有 getter state, 由现有实现完成 (augment/shizuku/Shizuku 与 shell 模块的命令解析; P5.2 前迁出). 参数同 shell (ShellArgs, D10; issue #310:
 * 命令可为字符串数组).
 */
@ScriptModule(name = "shizuku")
class ShizukuModule(private val runtime: ScriptRuntime) {

    @ScriptPrototype
    val prototype: Any
        get() = runtime.shizuku

    @ScriptInvoke
    fun invoke(command: Any?, arguments: Any? = ABSENT, withRoot: Any? = ABSENT): AbstractShell.Result =
        Shizuku.execCommand(runtime, ShellArgs.command("shizuku", command, arguments, withRoot))

    @ScriptFunction
    fun execCommand(command: Any?, arguments: Any? = ABSENT, withRoot: Any? = ABSENT): AbstractShell.Result =
        Shizuku.execCommand(runtime, ShellArgs.command("shizuku.execCommand", command, arguments, withRoot))

    @ScriptFunction
    fun getCommand(command: Any?, arguments: Any? = ABSENT, withRoot: Any? = ABSENT): String =
        Shizuku.getCommand(runtime, ShellArgs.command("shizuku.getCommand", command, arguments, withRoot))

    @ScriptFunction
    fun kill(app: Any?): Boolean = Shizuku.kill(runtime, arrayOf(app))

    @ScriptFunction
    fun currentPackage(): String = Shizuku.currentPackage(runtime, emptyArray())

    @ScriptFunction
    fun currentActivity(): String = Shizuku.currentActivity(runtime, emptyArray())

    @ScriptFunction
    fun currentComponent(): String = Shizuku.currentComponent(runtime, emptyArray())

    @ScriptFunction
    fun spawn(command: Any?, options: Any? = ABSENT): ShellProcess = Shizuku.spawn(runtime, ShellArgs.spawn("shizuku.spawn", command, options))

    /** Whether Shizuku is installed, has its service, runs, grants AutoJs6 and is operational, read on every access. */
    @ScriptGetter
    val state: NativeObject
        get() = RhinoUtils.newNativeObject(runtime.topLevelScope).also { o ->
            o.defineProperty("isInstalled", WrappedShizuku.isInstalled(GlobalAppContext.get()), ScriptableObject.EMPTY)
            o.defineProperty("hasService", WrappedShizuku.hasService(), ScriptableObject.EMPTY)
            o.defineProperty("isRunning", WrappedShizuku.isRunning(), ScriptableObject.EMPTY)
            o.defineProperty("hasPermission", WrappedShizuku.hasPermission(), ScriptableObject.EMPTY)
            o.defineProperty("isOperational", WrappedShizuku.isOperational(), ScriptableObject.EMPTY)
        }

}
