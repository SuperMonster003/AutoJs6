package org.autojs.autojs.runtime.api.modules.mail

import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.mail.Mail
import org.autojs.autojs.runtime.api.modules.AugmentMembers
import org.autojs.autojs.runtime.api.modules.ScriptFunction
import org.autojs.autojs.runtime.api.modules.ScriptGetter
import org.autojs.autojs.runtime.api.modules.ScriptInstall
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.autojs.autojs.runtime.api.modules.ScriptModuleInstallation
import org.autojs.autojs.runtime.api.modules.ScriptRuntimeFields

/**
 * `mail` (functions, getter `default`; MailError, providers, accounts and the client methods forwarded to the default
 * client with their `Async` forms as data properties) on the new script module layer (roadmap P4.1): only the mounting
 * layer changes. Every member hands its arguments unchanged to the current `augment/mail/Mail`, so values, argument
 * errors and the module's own coded errors stay those of the current module (a coded error is a script error and passes
 * the new layer's error mapping unchanged, T5); the data properties the current module computes at run time are defined
 * on the module object as they are when it is built.
 *
 * zh-CN: 新脚本模块层上的 mail (函数, getter default; MailError, providers, accounts
 * 以及转发到默认客户端的客户端方法与其 Async 形式为数据属性) (P4.1): 只换挂载层. 每个成员把参数原样交给现有 augment/mail/Mail, 取值,
 * 参数错误与模块自己的带错误码的错误都与现有模块相同 (带错误码的错误是脚本错误, 原样通过新层的错误映射, T5); 现有模块在运行时计算的数据属性在模块对象构建时照原值定义.
 */
@ScriptModule(name = "mail")
class MailModule(private val runtime: ScriptRuntime) {

    private val impl by lazy { Mail(runtime, ScriptRuntimeFields.mail(runtime)) }

    @ScriptInstall
    fun install(installation: ScriptModuleInstallation) = installation.onBuilt { AugmentMembers.defineProperties(impl, it) }

    @ScriptFunction
    fun connect(vararg args: Any?): Any? = impl.connect(runtime, args)

    @ScriptFunction
    fun connectAsync(vararg args: Any?): Any? = impl.connectAsync(runtime, args)

    @ScriptFunction
    fun setDefault(vararg args: Any?): Any? = impl.setDefault(runtime, args)

    @ScriptFunction
    fun close(vararg args: Any?): Any? = impl.close(runtime, args)

    @ScriptGetter(name = "default")
    fun defaultValue(): Any? = AugmentMembers.getter(impl, "default")

}
