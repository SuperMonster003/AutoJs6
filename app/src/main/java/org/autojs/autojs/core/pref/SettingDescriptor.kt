package org.autojs.autojs.core.pref

/**
 * One row of `SettingsRegistry.describe()` (docs/settings/ROADMAP.md T4): plain data with no Android dependency (R3),
 * the only input of export / import, restore defaults, the guard tests and generated documents.
 *
 * [name] is the stored key name (`key_$_night_mode`, not the resource name), [type] the T6 type tag, [store] the
 * [org.autojs.autojs.core.pref.store.StoreId] name ("default" for the default SharedPreferences), [category] the
 * category id (null for a [SettingKind.CACHE], [SettingKind.VALUE_CONSTANT] or [SettingKind.EXCLUDED] row) and
 * [defaultText] the default as text (the source of a computed default, null when there is none). A [family] row
 * describes a [KeyFamily]: [name] is its prefix and every stored name that starts with it is a member (P3).
 *
 * zh-CN: `SettingsRegistry.describe()` 的一行 (ROADMAP T4): 不依赖 Android 的纯数据 (R3), 是导出 / 导入, 恢复默认, 守卫测试与
 * 文档生成的唯一输入. [name] 为存储中的键名 (而非资源名), [type] 为 T6 类型标签, [store] 为存储名 (默认偏好为 "default"),
 * [category] 为分类 id (缓存, 设置值常量与排除项为 null), [defaultText] 为默认值文本 (计算型默认值为其来源, 无默认值为 null).
 * [family] 行描述一个 [KeyFamily]: [name] 为其前缀, 存储中以它开头的名称都是成员 (P3).
 */
data class SettingDescriptor(
    val name: String,
    val type: String,
    val store: String,
    val category: String?,
    val defaultText: String?,
    val effect: SettingEffect,
    val kind: SettingKind,
    val deployment: DeploymentKind? = null,
    val family: Boolean = false,
) {

    /** Only `credentials` holds sensitive entries (D2, T5 c). */
    val sensitive: Boolean
        get() = category == SettingCategory.CREDENTIALS.id
}
