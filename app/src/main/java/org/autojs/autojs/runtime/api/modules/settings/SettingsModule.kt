package org.autojs.autojs.runtime.api.modules.settings

import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.settings.Settings
import org.autojs.autojs.runtime.api.modules.ScriptFunction
import org.autojs.autojs.runtime.api.modules.ScriptModule

/**
 * The `settings` module on the new script module layer (roadmap P3.4): `isEnabled(key)` and `setEnabled(key, enabled)`
 * over the current implementation (`augment/settings/Settings`: compatible aliases, the stores and the declared defaults of
 * the settings layer; to be moved out before P5.2). A key is a string (other values by their string form, as before), a
 * nullish key a TypeError; `enabled` is a boolean as documented (T4; the current module took the truthiness and refused
 * null with IllegalArgumentException).
 *
 * zh-CN: 新脚本模块层上的 settings 模块 (P3.4): isEnabled(key) 与 setEnabled(key, enabled), 由现有实现完成 (augment/settings/Settings: 兼容别名, 设置层的
 * 存储与声明默认值; P5.2 前迁出). 键为字符串 (其他值与以前一样按字符串形式), null / undefined 为 TypeError; enabled 按文档为布尔值 (T4; 现有模块取真值, 并以
 * IllegalArgumentException 拒绝 null).
 */
@ScriptModule(name = "settings")
class SettingsModule(private val runtime: ScriptRuntime) {

    @ScriptFunction
    fun isEnabled(key: String): Boolean = Settings.isEnabled(runtime, arrayOf(key))

    @ScriptFunction
    fun setEnabled(key: String, enabled: Boolean) {
        Settings.setEnabled(runtime, arrayOf<Any?>(key, enabled))
    }

}
