package org.autojs.autojs.runtime.api.modules

import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.modules.generated.ScriptModules
import org.mozilla.javascript.ScriptableObject

/**
 * Entry of the new script module layer for `ScriptRuntime` (roadmap D8 / T8). The hidden switch
 * ([Pref.isNewScriptModuleLayerEnabled], off by default, no UI yet) is read once per runtime in `initPrologue`. When it
 * is off nothing here runs and every module comes from the `augment` package as before; when it is on, the modules
 * migrated so far ([MIGRATED]) are mounted from the generated registry at the position of their old augment call, so
 * the order of `Object.keys(global)` does not change, and the other modules still come from the `augment` package.
 *
 * zh-CN: 新脚本模块层在 ScriptRuntime 中的入口 (D8 / T8). 隐藏开关 (默认关, 暂无界面) 在 initPrologue 中每个运行时读取一次. 关闭时这里什么都不执行,
 * 所有模块照旧来自 augment 包; 打开时, 已迁移的模块 ([MIGRATED]) 在原 augment 调用的位置由生成的注册表挂载 (Object.keys(global) 的顺序不变),
 * 其他模块仍来自 augment 包.
 */
object ScriptModuleLayer {

    /** Top-level modules served by the new layer when the switch is on. */
    @JvmField
    val MIGRATED: Set<String> = setOf("base64")

    init {
        // Appendix C Q4: a master switch (off by default) plus a per-module list (all on by default), read on every failure.
        ScriptErrorMapper.debugPrinting = ScriptErrorMapper.DebugPrinting { module ->
            Pref.isScriptModuleDebugPrintEnabled && module !in Pref.scriptModuleDebugPrintDisabledModules
        }
    }

    @JvmStatic
    val isEnabled: Boolean
        get() = Pref.isNewScriptModuleLayerEnabled

    /** A mounter of the host registry for [runtime]'s [global] object, or null when the switch is off. */
    @JvmStatic
    fun mounterOrNull(runtime: ScriptRuntime, global: ScriptableObject): ScriptModuleMounter? =
        if (isEnabled) ScriptModuleMounter(ScriptModules, runtime, runtime.topLevelScope, global) else null

    /** Installs the migrated module [name] with [mounter], or runs [legacy] (the old augment call) without one. */
    @JvmStatic
    inline fun mountOr(mounter: ScriptModuleMounter?, name: String, legacy: () -> Unit) {
        if (mounter == null || name !in MIGRATED || !mounter.install(name)) legacy()
    }

}
