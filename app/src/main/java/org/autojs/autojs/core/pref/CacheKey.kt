package org.autojs.autojs.core.pref

import org.autojs.autojs.core.pref.store.StoreId

/**
 * Declaration of a cached value (docs/settings/ROADMAP.md T2, appendix A.2), for example `key_root_manager_apps_cache`:
 * the same model as [SettingKey] but in no category, so it is never exported, imported or reset; it is declared only
 * so that the guard tests (T5) know the name. Factories as in [StateKey].
 *
 * zh-CN: 缓存值的声明 (ROADMAP T2, 附录 A.2), 例如 `key_root_manager_apps_cache`: 与 [SettingKey] 同模型但不属于任何分类,
 * 因此不导出, 不导入, 不恢复默认; 登记只为让守卫测试 (T5) 认识该名称. 工厂函数同 [StateKey].
 */
class CacheKey<T : Any> @JvmOverloads constructor(
    keyRes: Int = 0,
    type: SettingType<T>,
    default: SettingDefault<T>,
    store: StoreId = StoreId.DEFAULT,
    name: String? = null,
) : PrefKey<T>(keyRes, name, store, type, default) {

    override val kind: SettingKind
        get() = SettingKind.CACHE

    override val category: SettingCategory?
        get() = null

    companion object {

        @JvmStatic
        @JvmOverloads
        fun bool(keyRes: Int = 0, default: SettingDefault<Boolean>, store: StoreId = StoreId.DEFAULT, name: String? = null) =
            CacheKey(keyRes, SettingType.Bool, default, store, name)

        @JvmStatic
        @JvmOverloads
        fun int(keyRes: Int = 0, default: SettingDefault<Int>, store: StoreId = StoreId.DEFAULT, name: String? = null) =
            CacheKey(keyRes, SettingType.Int, default, store, name)

        @JvmStatic
        @JvmOverloads
        fun long(keyRes: Int = 0, default: SettingDefault<Long>, store: StoreId = StoreId.DEFAULT, name: String? = null) =
            CacheKey(keyRes, SettingType.Long, default, store, name)

        @JvmStatic
        @JvmOverloads
        fun float(keyRes: Int = 0, default: SettingDefault<Float>, store: StoreId = StoreId.DEFAULT, name: String? = null) =
            CacheKey(keyRes, SettingType.Float, default, store, name)

        @JvmStatic
        @JvmOverloads
        fun str(keyRes: Int = 0, default: SettingDefault<String>, store: StoreId = StoreId.DEFAULT, name: String? = null) =
            CacheKey(keyRes, SettingType.Str, default, store, name)

        @JvmStatic
        @JvmOverloads
        fun strSet(keyRes: Int = 0, default: SettingDefault<Set<String>>, store: StoreId = StoreId.DEFAULT, name: String? = null) =
            CacheKey(keyRes, SettingType.StrSet, default, store, name)

        @JvmStatic
        @JvmOverloads
        fun <V : Any> json(keyRes: Int = 0, type: SettingType.Json<V>, default: SettingDefault<V>, store: StoreId = StoreId.DEFAULT, name: String? = null) =
            CacheKey(keyRes, type, default, store, name)
    }
}
