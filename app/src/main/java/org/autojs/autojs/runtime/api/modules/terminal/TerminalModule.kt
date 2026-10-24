package org.autojs.autojs.runtime.api.modules.terminal

import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.terminal.Terminal
import org.autojs.autojs.runtime.api.modules.AugmentMembers
import org.autojs.autojs.runtime.api.modules.ScriptFunction
import org.autojs.autojs.runtime.api.modules.ScriptInstall
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.autojs.autojs.runtime.api.modules.ScriptModuleInstallation
import org.autojs.autojs.runtime.api.modules.ScriptRuntimeFields

/**
 * `terminal` (functions with their `Async` forms; TerminalError and npm as data properties) on the new script module
 * layer (roadmap P4.1): only the mounting layer changes. Every member hands its arguments unchanged to the current
 * `augment/terminal/Terminal`, so values, argument errors and the module's own coded errors stay those of the current
 * module (a coded error is a script error and passes the new layer's error mapping unchanged, T5); the data properties
 * the current module computes at run time are defined on the module object as they are when it is built.
 *
 * zh-CN: 新脚本模块层上的 terminal (函数及其 Async 形式; TerminalError 与 npm 为数据属性) (P4.1): 只换挂载层.
 * 每个成员把参数原样交给现有 augment/terminal/Terminal, 取值, 参数错误与模块自己的带错误码的错误都与现有模块相同 (带错误码的错误是脚本错误,
 * 原样通过新层的错误映射, T5); 现有模块在运行时计算的数据属性在模块对象构建时照原值定义.
 */
@ScriptModule(name = "terminal")
class TerminalModule(private val runtime: ScriptRuntime) {

    private val impl by lazy { Terminal(runtime, ScriptRuntimeFields.terminal(runtime)) }

    @ScriptInstall
    fun install(installation: ScriptModuleInstallation) = installation.onBuilt { AugmentMembers.defineProperties(impl, it) }

    @ScriptFunction
    fun open(vararg args: Any?): Any? = impl.open(runtime, args)

    @ScriptFunction
    fun openAsync(vararg args: Any?): Any? = impl.openAsync(runtime, args)

    @ScriptFunction
    fun sessions(vararg args: Any?): Any? = impl.sessions(runtime, args)

    @ScriptFunction
    fun sessionsAsync(vararg args: Any?): Any? = impl.sessionsAsync(runtime, args)

    @ScriptFunction
    fun session(vararg args: Any?): Any? = impl.session(runtime, args)

    @ScriptFunction
    fun close(vararg args: Any?): Any? = impl.close(runtime, args)

    @ScriptFunction
    fun closeAsync(vararg args: Any?): Any? = impl.closeAsync(runtime, args)

    @ScriptFunction
    fun closeAll(vararg args: Any?): Any? = impl.closeAll(runtime, args)

    @ScriptFunction
    fun closeAllAsync(vararg args: Any?): Any? = impl.closeAllAsync(runtime, args)

    @ScriptFunction
    fun show(vararg args: Any?): Any? = impl.show(runtime, args)

    @ScriptFunction
    fun showAsync(vararg args: Any?): Any? = impl.showAsync(runtime, args)

    @ScriptFunction
    fun state(vararg args: Any?): Any? = impl.state(runtime, args)

    @ScriptFunction
    fun isAvailable(vararg args: Any?): Any? = impl.isAvailable(runtime, args)

    @ScriptFunction
    fun isAvailableAsync(vararg args: Any?): Any? = impl.isAvailableAsync(runtime, args)

    @ScriptFunction
    fun exec(vararg args: Any?): Any? = impl.exec(runtime, args)

    @ScriptFunction
    fun execAsync(vararg args: Any?): Any? = impl.execAsync(runtime, args)

    @ScriptFunction
    fun env(vararg args: Any?): Any? = impl.env(runtime, args)

    @ScriptFunction
    fun envAsync(vararg args: Any?): Any? = impl.envAsync(runtime, args)

}
