package org.autojs.autojs.runtime.api.modules.installer

import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.modules.AugmentMembers
import org.autojs.autojs.runtime.api.modules.ScriptFunction
import org.autojs.autojs.runtime.api.modules.ScriptGetter
import org.autojs.autojs.runtime.api.modules.ScriptInstall
import org.autojs.autojs.runtime.api.modules.ScriptInstallNames
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.autojs.autojs.runtime.api.modules.ScriptModuleInstallation
import org.autojs.autojs.runtime.api.modules.ScriptRuntimeFields

/**
 * `installer` (functions, getter `status`; InstallerError and authorizer as data properties) on the new script module
 * layer (roadmap P4.1): only the mounting layer changes. Every member hands its arguments unchanged to the current
 * `augment/installer/Installer`, so values, argument errors and the module's own coded errors stay those of the current
 * module (a coded error is a script error and passes the new layer's error mapping unchanged, T5); the data properties
 * the current module computes at run time are defined on the module object as they are when it is built.
 *
 * zh-CN: 新脚本模块层上的 installer (函数, getter status; InstallerError 与 authorizer 为数据属性) (P4.1):
 * 只换挂载层. 每个成员把参数原样交给现有 augment/installer/Installer, 取值, 参数错误与模块自己的带错误码的错误都与现有模块相同
 * (带错误码的错误是脚本错误, 原样通过新层的错误映射, T5); 现有模块在运行时计算的数据属性在模块对象构建时照原值定义.
 */
@ScriptModule(name = "installer")
class InstallerModule(private val runtime: ScriptRuntime) {

    private val impl by lazy { Installer(runtime, ScriptRuntimeFields.installer(runtime)) }

    @ScriptInstall(names = [ScriptInstallNames(["InstallerError", "authorizer"])])
    fun install(installation: ScriptModuleInstallation) = installation.onBuilt { AugmentMembers.defineProperties(impl.scriptProperties, it) }

    @ScriptFunction(arity = [1, 2])
    fun install(vararg args: Any?): Any? = impl.install(runtime, args)

    @ScriptFunction(arity = [1, 2])
    fun installAsync(vararg args: Any?): Any? = impl.installAsync(runtime, args)

    @ScriptFunction(arity = [1, 2])
    fun session(vararg args: Any?): Any? = impl.session(runtime, args)

    @ScriptFunction(arity = [1, 2])
    fun uninstall(vararg args: Any?): Any? = impl.uninstall(runtime, args)

    @ScriptFunction(arity = [1, 2])
    fun uninstallAsync(vararg args: Any?): Any? = impl.uninstallAsync(runtime, args)

    @ScriptFunction(arity = [1])
    fun inspect(vararg args: Any?): Any? = impl.inspect(runtime, args)

    @ScriptFunction(arity = [1])
    fun inspectAsync(vararg args: Any?): Any? = impl.inspectAsync(runtime, args)

    @ScriptFunction(arity = [0])
    fun isDefault(vararg args: Any?): Any? = impl.isDefault(runtime, args)

    @ScriptFunction(arity = [0, 2])
    fun setDefault(vararg args: Any?): Any? = impl.setDefault(runtime, args)

    @ScriptFunction(arity = [0, 2])
    fun setDefaultAsync(vararg args: Any?): Any? = impl.setDefaultAsync(runtime, args)

    @ScriptFunction(arity = [0])
    fun users(vararg args: Any?): Any? = impl.users(runtime, args)

    @ScriptFunction(arity = [0])
    fun usersAsync(vararg args: Any?): Any? = impl.usersAsync(runtime, args)

    @ScriptFunction(arity = [0])
    fun isAvailable(vararg args: Any?): Any? = impl.isAvailable(runtime, args)

    @ScriptGetter(name = "status")
    fun status(): Any? = impl.statusValue()

}
