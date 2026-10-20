package org.autojs.autojs.core.pref

import org.autojs.autojs.core.pref.store.StoreId

/**
 * Declaration of a remembered state (docs/settings/ROADMAP.md T2, P4): the same model as [SettingKey] (name, store,
 * [SettingType], default), always in the `other` category (not selected by default, never reset to defaults, D7 /
 * D11), with no effect, no deployment item and never sensitive. Build one with the factories named after the value
 * types ([bool], [int], [long], [float], [str], [strSet], [json]) or with the constructor for any [SettingType].
 *
 * zh-CN: 记忆型状态的声明 (ROADMAP T2, P4): 与 [SettingKey] 同模型 (键名, 存储, 值类型, 默认值), 固定归 `other` 分类 (默认
 * 不勾选, 不参与恢复默认, D7 / D11), 无生效方式, 无部署项, 不是凭据. 用按值类型命名的工厂函数或构造函数创建.
 */
class StateKey<T : Any> @JvmOverloads constructor(
    keyRes: Int = 0,
    type: SettingType<T>,
    default: SettingDefault<T>,
    store: StoreId = StoreId.DEFAULT,
    name: String? = null,
) : PrefKey<T>(keyRes, name, store, type, default) {

    override val kind: SettingKind
        get() = SettingKind.STATE

    override val category: SettingCategory
        get() = SettingCategory.OTHER

    companion object {

        @JvmStatic
        @JvmOverloads
        fun bool(keyRes: Int = 0, default: SettingDefault<Boolean>, store: StoreId = StoreId.DEFAULT, name: String? = null) =
            StateKey(keyRes, SettingType.Bool, default, store, name)

        @JvmStatic
        @JvmOverloads
        fun int(keyRes: Int = 0, default: SettingDefault<Int>, store: StoreId = StoreId.DEFAULT, name: String? = null) =
            StateKey(keyRes, SettingType.Int, default, store, name)

        @JvmStatic
        @JvmOverloads
        fun long(keyRes: Int = 0, default: SettingDefault<Long>, store: StoreId = StoreId.DEFAULT, name: String? = null) =
            StateKey(keyRes, SettingType.Long, default, store, name)

        @JvmStatic
        @JvmOverloads
        fun float(keyRes: Int = 0, default: SettingDefault<Float>, store: StoreId = StoreId.DEFAULT, name: String? = null) =
            StateKey(keyRes, SettingType.Float, default, store, name)

        @JvmStatic
        @JvmOverloads
        fun str(keyRes: Int = 0, default: SettingDefault<String>, store: StoreId = StoreId.DEFAULT, name: String? = null) =
            StateKey(keyRes, SettingType.Str, default, store, name)

        @JvmStatic
        @JvmOverloads
        fun strSet(keyRes: Int = 0, default: SettingDefault<Set<String>>, store: StoreId = StoreId.DEFAULT, name: String? = null) =
            StateKey(keyRes, SettingType.StrSet, default, store, name)

        @JvmStatic
        @JvmOverloads
        fun <V : Any> json(keyRes: Int = 0, type: SettingType.Json<V>, default: SettingDefault<V>, store: StoreId = StoreId.DEFAULT, name: String? = null) =
            StateKey(keyRes, type, default, store, name)
    }
}
