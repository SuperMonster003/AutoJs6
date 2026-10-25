package org.autojs.autojs.runtime.api.modules.settings

import org.autojs.autojs.core.pref.SettingCategory
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.modules.ScriptFunction
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.autojs.autojs.runtime.api.modules.global.GlobalValues
import org.autojs.autojs.runtime.api.modules.global.GlobalValues.isNullish
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Wrapper

/**
 * The `settings` module on the new script module layer (roadmap P3.4): `isEnabled(key)` and `setEnabled(key, enabled)`
 * over the current implementation (`augment/settings/Settings`: compatible aliases, the stores and the declared defaults of
 * the settings layer; to be moved out before P5.2). A key is a string (other values by their string form, as before), a
 * nullish key a TypeError; `enabled` is a boolean as documented (T4; the current module took the truthiness and refused
 * null with IllegalArgumentException).
 *
 * `export(path?, categories?)` and `categories()` (docs/settings/ROADMAP.md P12.1) are those of the current module; here a
 * path that is not a string, or categories that are not a string or an array of strings, are a TypeError, a blank path or
 * an unknown category a RangeError (the current module throws IllegalArgumentException for all of them).
 *
 * zh-CN: 新脚本模块层上的 settings 模块 (P3.4): isEnabled(key) 与 setEnabled(key, enabled), 由现有实现完成 (augment/settings/Settings: 兼容别名, 设置层的
 * 存储与声明默认值; P5.2 前迁出). 键为字符串 (其他值与以前一样按字符串形式), null / undefined 为 TypeError; enabled 按文档为布尔值 (T4; 现有模块取真值, 并以
 * IllegalArgumentException 拒绝 null). export(path?, categories?) 与 categories() (P12.1) 与现有模块相同; 这里 path 不是字符串, 或 categories 不是字符串
 * 或字符串数组时为 TypeError, path 为空白或分类未知时为 RangeError (现有模块一律抛 IllegalArgumentException).
 */
@ScriptModule(name = "settings")
class SettingsModule(private val runtime: ScriptRuntime) {

    @ScriptFunction
    fun isEnabled(key: String): Boolean = Settings.isEnabled(runtime, arrayOf(key))

    @ScriptFunction
    fun setEnabled(key: String, enabled: Boolean) {
        Settings.setEnabled(runtime, arrayOf<Any?>(key, enabled))
    }

    @ScriptFunction
    fun export(path: Any? = null, categories: Any? = null): NativeObject {
        if (!isNullish(path)) {
            val text = unwrap(path) as? CharSequence ?: throw GlobalValues.typeError(EXPORT, "path", "string", path)
            if (text.isBlank()) throw GlobalValues.rangeError(EXPORT, "path", "a path", path)
        }
        val ids = try {
            SettingsScriptExport.categoryIds(categories)
        } catch (_: IllegalArgumentException) {
            throw GlobalValues.typeError(EXPORT, "categories", "string | string[]", categories)
        }
        ids?.firstOrNull { SettingCategory.ofIdOrNull(it) == null }?.let {
            throw GlobalValues.rangeError(EXPORT, "categories", SettingCategory.ALL.joinToString(" | ") { category -> category.id }, it)
        }
        return Settings.export(runtime, arrayOf(path, categories))
    }

    @ScriptFunction
    fun categories(): NativeArray = Settings.categories(runtime, emptyArray())

    private fun unwrap(value: Any?): Any? = if (value is Wrapper) value.unwrap() else value

    private companion object {
        const val EXPORT = "settings.export"
    }

}
