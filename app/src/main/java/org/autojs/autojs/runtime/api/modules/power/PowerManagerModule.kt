package org.autojs.autojs.runtime.api.modules.power

import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.power.PowerManager
import org.autojs.autojs.runtime.api.modules.ScriptFunction
import org.autojs.autojs.runtime.api.modules.ScriptModule

/**
 * The `powerManager` module (alias `power_manager`, both with `$` forms) on the new script module layer (roadmap P3.4):
 * the battery optimization queries of the current module, served by its implementation (`augment/power/PowerManager`; to
 * be moved out before P5.2). `packageName` is a string (the current application when omitted or nullish), `forceRequest`
 * a boolean as documented (T4; the current module took the truthiness).
 *
 * zh-CN: 新脚本模块层上的 powerManager 模块 (别名 power_manager, 均有 $ 形式, P3.4): 现有模块的电池优化查询, 由其实现完成 (augment/power/PowerManager; P5.2
 * 前迁出). packageName 为字符串 (省略或为 null / undefined 时为当前应用), forceRequest 按文档为布尔值 (T4; 现有模块取真值).
 */
@ScriptModule(name = "powerManager", aliases = ["power_manager"])
class PowerManagerModule(private val runtime: ScriptRuntime) {

    @ScriptFunction
    fun isIgnoringBatteryOptimizations(packageName: String? = null): Boolean =
        PowerManager.isIgnoringBatteryOptimizations(runtime, arrayOf(packageName))

    @ScriptFunction
    fun requestIgnoreBatteryOptimizations(forceRequest: Boolean? = null, packageName: String? = null) {
        PowerManager.requestIgnoreBatteryOptimizations(runtime, arrayOf<Any?>(forceRequest, packageName))
    }

}
