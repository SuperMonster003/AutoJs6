package org.autojs.autojs.core.pref

/**
 * What a registered name is (docs/settings/ROADMAP.md T2, T5 a): a [SETTING] (`SettingKey`), a remembered [STATE]
 * (`StateKey`, always in the `other` category), a [CACHE] (`CacheKey`, never exported), a [VALUE_CONSTANT] (a
 * candidate value such as `key_night_mode_follow_system`, not a stored key) or an [EXCLUDED] name (Bundle argument
 * names, action items, permanently excluded stores). [label] is the spelling used by
 * `tools/settings/classification.json`.
 *
 * zh-CN: 一个登记名称的种类 (ROADMAP T2, T5 a): 设置, 记忆型状态 (固定归 `other`), 缓存 (不可导出), 设置值常量 (候选值,
 * 不是存储键) 或排除项 (Bundle 参数名, 动作条目, 永久排除的存储). [label] 与 `classification.json` 的写法一致.
 */
enum class SettingKind(val label: String) {
    SETTING("Setting"),
    STATE("State"),
    CACHE("Cache"),
    VALUE_CONSTANT("ValueConstant"),
    EXCLUDED("Excluded"),
    ;

    companion object {

        @JvmStatic
        fun ofLabel(label: String): SettingKind = values().firstOrNull { it.label == label }
            ?: throw IllegalArgumentException("Unknown kind \"$label\"")
    }
}
