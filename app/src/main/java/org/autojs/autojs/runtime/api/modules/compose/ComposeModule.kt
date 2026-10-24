package org.autojs.autojs.runtime.api.modules.compose

import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.compose.Compose
import org.autojs.autojs.runtime.api.augment.compose.ComposeJsErrors
import org.autojs.autojs.runtime.api.modules.AugmentMembers
import org.autojs.autojs.runtime.api.modules.ScriptGetter
import org.autojs.autojs.runtime.api.modules.ScriptGlobal
import org.autojs.autojs.runtime.api.modules.ScriptInstall
import org.autojs.autojs.runtime.api.modules.ScriptInstallKind
import org.autojs.autojs.runtime.api.modules.ScriptInstallNames
import org.autojs.autojs.runtime.api.modules.ScriptInvoke
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.autojs.autojs.runtime.api.modules.ScriptModuleInstallation
import org.autojs.autojs.runtime.api.modules.ScriptRuntimeFields
import org.mozilla.javascript.ScriptableObject

/**
 * `compose` on the new script module layer (roadmap P4.1): only the mounting layer changes. `compose(...)` is the
 * current `augment/compose/Compose.invoke` (mount), the getters `version` / `sessions` and the data properties (the
 * catalog functions, `ComposeError`) are the current module's own, and the global `ComposeError` is the same
 * constructor; argument errors and the module's ComposeError instances stay those of the current module (a ComposeError
 * is a script error and passes the new layer's error mapping unchanged, T5).
 *
 * The current `Compose` instance is created when the module is installed, on the thread that starts the runtime, as the
 * old augment call did: it binds its owner thread then (mutations from other threads must go through `compose.post`),
 * installs the localizer of ComposeError messages and is held by the host (`ScriptRuntime.compose`: `ui.tsx`, replacing
 * the ui content, closing at exit). The module object (the catalog functions) is still built on first use.
 *
 * zh-CN: 新脚本模块层上的 compose (P4.1): 只换挂载层. compose(...) 即现有 augment/compose/Compose.invoke (挂载), getter version / sessions 与数据属性
 * (目录函数, ComposeError) 均为现有模块自身的, 全局 ComposeError 为同一构造函数; 参数错误与模块的 ComposeError 实例与现有模块相同 (ComposeError 是脚本
 * 错误, 原样通过新层的错误映射, T5). 现有 Compose 实例在模块安装时于启动运行时的线程上创建, 同原 augment 调用: 此时绑定所属线程 (其他线程的修改须经
 * compose.post), 安装 ComposeError 消息的本地化, 并由宿主持有 (ScriptRuntime.compose: ui.tsx, 替换 ui 内容, 结束时关闭). 模块对象 (目录函数)
 * 仍在首次使用时构建.
 */
@ScriptModule(name = "compose")
class ComposeModule(private val runtime: ScriptRuntime) {

    // A runtime holding one already (tests give one with their own platform) keeps it.
    // zh-CN: 已持有实例的运行时沿用之 (测试给出带自己平台的实例).
    private val compose = ScriptRuntimeFields.compose(runtime) ?: Compose(runtime).also { ScriptRuntimeFields.setCompose(runtime, it) }

    @ScriptInstall(
        names = [
            ScriptInstallNames(["ComposeError"], global = ScriptGlobal.ALSO),
            ScriptInstallNames(["isAvailable", "modifier", "ref"], kind = ScriptInstallKind.FUNCTION, arity = [0]),
            ScriptInstallNames(["state", "batch"], kind = ScriptInstallKind.FUNCTION, arity = [1]),
            ScriptInstallNames(["createElement"], kind = ScriptInstallKind.FUNCTION, arity = [1, -1]),
            ScriptInstallNames(["mount", "floaty", "dialog", "post"], kind = ScriptInstallKind.FUNCTION, arity = [1, 2]),
            ScriptInstallNames(["memo", "attach"], kind = ScriptInstallKind.FUNCTION, arity = [2, 3]),
            ScriptInstallNames(["theme"], kind = ScriptInstallKind.FUNCTION, arity = [0, 1]),
        ],
        computed = "the component functions of the Compose catalog (ComposeUiV2.interopCatalog, NODE components)",
    )
    fun install(installation: ScriptModuleInstallation) {
        installation.defineLazy(COMPOSE_ERROR, ScriptableObject.PERMANENT) { ComposeJsErrors.constructor(runtime.topLevelScope) }
        installation.onBuilt { AugmentMembers.defineProperties(compose, it) }
    }

    @ScriptInvoke(arity = [1, 2])
    fun invoke(vararg args: Any?): Any? = compose.invoke(*args)

    @ScriptGetter
    fun version(): Any? = AugmentMembers.getter(compose, "version")

    @ScriptGetter
    fun sessions(): Any? = AugmentMembers.getter(compose, "sessions")

    private companion object {
        const val COMPOSE_ERROR = "ComposeError"
    }

}
