package org.autojs.autojs.runtime.api.modules.mail

import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.modules.AugmentMembers
import org.autojs.autojs.runtime.api.modules.ScriptFunction
import org.autojs.autojs.runtime.api.modules.ScriptGetter
import org.autojs.autojs.runtime.api.modules.ScriptInstall
import org.autojs.autojs.runtime.api.modules.ScriptInstallKind
import org.autojs.autojs.runtime.api.modules.ScriptInstallNames
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

    @ScriptInstall(
        names = [
            ScriptInstallNames(["MailError", "providers", "accounts"]),
            // The client methods of the default client (appendix A.3 of the mail roadmap), by the counts the client checks.
            // zh-CN: 默认客户端的方法 (邮件路线图附录 A.3), 按客户端检查的参数个数分组.
            ScriptInstallNames(["test", "testAsync"], kind = ScriptInstallKind.FUNCTION, arity = [0]),
            ScriptInstallNames(
                names = ["folder", "folderStatus", "folderStatusAsync", "createFolder", "createFolderAsync", "deleteFolder", "deleteFolderAsync"],
                kind = ScriptInstallKind.FUNCTION,
                arity = [1],
            ),
            ScriptInstallNames(["renameFolder", "renameFolderAsync"], kind = ScriptInstallKind.FUNCTION, arity = [2]),
            ScriptInstallNames(["folders", "foldersAsync", "fetch", "fetchAsync", "expunge", "expungeAsync"], kind = ScriptInstallKind.FUNCTION, arity = [0, 1]),
            ScriptInstallNames(
                names = [
                    "send", "sendAsync", "search", "searchAsync", "get", "getAsync", "fetchBody", "fetchBodyAsync", "markRead",
                    "markReadAsync", "markUnread", "markUnreadAsync", "flag", "flagAsync", "unflag", "unflagAsync", "delete", "deleteAsync",
                ],
                kind = ScriptInstallKind.FUNCTION,
                arity = [1, 2],
            ),
            ScriptInstallNames(["download", "downloadAsync", "raw", "rawAsync"], kind = ScriptInstallKind.FUNCTION, arity = [1, 3]),
            ScriptInstallNames(["move", "moveAsync", "copy", "copyAsync", "append", "appendAsync"], kind = ScriptInstallKind.FUNCTION, arity = [2, 3]),
            ScriptInstallNames(["setFlags", "setFlagsAsync"], kind = ScriptInstallKind.FUNCTION, arity = [2, 4]),
            ScriptInstallNames(["watch"], kind = ScriptInstallKind.FUNCTION, arity = [0, 2]),
        ],
    )
    fun install(installation: ScriptModuleInstallation) = installation.onBuilt { AugmentMembers.defineProperties(impl.scriptProperties, it) }

    @ScriptFunction(arity = [1])
    fun connect(vararg args: Any?): Any? = impl.connect(runtime, args)

    @ScriptFunction(arity = [1])
    fun connectAsync(vararg args: Any?): Any? = impl.connectAsync(runtime, args)

    @ScriptFunction(arity = [1])
    fun setDefault(vararg args: Any?): Any? = impl.setDefault(runtime, args)

    @ScriptFunction(arity = [0])
    fun close(vararg args: Any?): Any? = impl.close(runtime, args)

    @ScriptGetter(name = "default")
    fun defaultValue(): Any? = impl.defaultClient

}
