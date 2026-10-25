package org.autojs.autojs.runtime.api.modules.compose

import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.modules.ScriptModuleLayer
import org.autojs.autojs.runtime.api.modules.ScriptModuleMounter
import org.autojs.autojs.runtime.api.modules.ScriptRuntimeFields
import org.autojs.autojs.runtime.api.modules.generated.ScriptModules

/**
 * Mounts [compose] (created on [runtime], with the platform of the test) as the `compose` module of the runtime's scope
 * through the script module layer, as `ScriptRuntime.augment()` does: the module keeps the Compose the runtime holds.
 * Until P5.2 the tests mounted it with the old `Compose.augment`.
 *
 * zh-CN: 经脚本模块层把 [compose] (在 [runtime] 上以测试的平台创建) 挂载为运行时作用域的 compose 模块, 同 ScriptRuntime.augment(): 模块沿用运行时持有的
 * Compose. P5.2 之前测试以旧的 Compose.augment 挂载.
 */
internal fun mountCompose(runtime: ScriptRuntime, compose: Compose): Compose {
    ScriptRuntimeFields.setCompose(runtime, compose)
    val scope = runtime.topLevelScope
    check(ScriptModuleMounter(ScriptModules, runtime, scope, scope.globalThis, order = ScriptModuleLayer.ORDER).install("compose"))
    return compose
}
