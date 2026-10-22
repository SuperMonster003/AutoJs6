package org.autojs.autojs.core.pref.archive

import org.autojs.autojs.core.pref.KeyFamily
import org.autojs.autojs.core.pref.PrefKey
import org.autojs.autojs.core.pref.SettingCategory
import org.autojs.autojs.core.pref.SettingEffect
import org.autojs.autojs.core.pref.SettingsRegistry
import org.autojs.autojs.core.pref.store.SettingsStore
import org.autojs.autojs.core.pref.store.SettingsStores
import org.autojs.autojs.core.pref.store.StoreId

/**
 * Plans "Restore default settings..." (docs/settings/ROADMAP.md D11, P8.1) with the import planner (T8): resetting a
 * category is importing it from an empty category file, which removes every stored declared key and key family member
 * of the category (D8); a named store whose declarations all belong to the selected categories is cleared as a whole
 * (D11), so names no declaration knows (left by older versions) go as well. `other` (the remembered states, T2) and the
 * data categories are never reset ([resettable]); the default store is never cleared as a whole. Pure JVM (R3).
 *
 * zh-CN: 以导入计划器 (T8) 规划 "恢复默认设置..." (ROADMAP D11, P8.1): 恢复某分类即从空的分类文件导入它, 从而删除该分类全部已存储的
 * 声明键与键族成员 (D8); 声明全部属于所选分类的命名存储整份清除 (D11), 因此旧版本留下的未声明名称也一并删除. `other` (记忆型状态, T2)
 * 与数据分类永不恢复默认 ([resettable]); 默认存储不会整份清除. 纯 JVM (R3).
 */
object SettingsResetPlanner {

    /** The categories a reset can tick. zh-CN: 恢复默认可勾选的分类. */
    @JvmStatic
    @JvmOverloads
    fun resettable(categories: List<SettingCategory> = SettingCategory.ALL): List<SettingCategory> =
        categories.filter { !it.isData && it != SettingCategory.OTHER }

    /**
     * The plan of resetting [selected] (category ids; the ones a reset cannot tick are left out): only removes, every
     * stored name of the selected categories.
     * zh-CN: 恢复 [selected] (分类 id; 不可勾选的被略去) 的计划: 只有删除, 即所选分类的全部已存储名称.
     */
    @JvmStatic
    @JvmOverloads
    fun plan(
        selected: Collection<String>,
        keys: List<PrefKey<*>> = SettingsRegistry.keys,
        families: List<KeyFamily<*, *>> = SettingsRegistry.families,
        open: (StoreId) -> SettingsStore = { SettingsStores[it] },
    ): ImportPlan {
        val ids = resettable().map { it.id }.filter { it in selected }
        val empty = ids.map { SettingsSnapshotCodec.Decoded(SettingsSnapshot.Category(it, emptyMap()), emptyList()) }
        val planned = SettingsImportPlanner.plan(empty, ids, ImportEnvironment(), withDeployments = false, keys = keys, families = families, open = open, plugins = emptyList())
        val owners = LinkedHashMap<StoreId, MutableList<String?>>()
        keys.forEach { owners.getOrPut(it.store) { ArrayList() }.add(it.category?.id) }
        families.forEach { owners.getOrPut(it.template.store) { ArrayList() }.add(it.category?.id) }
        val whole = owners.filter { (store, categories) ->
            !store.isDefault && !store.excluded && store.backend == StoreId.Backend.SHARED_PREFERENCES && categories.all { it != null && it in ids }
        }
        val removed = planned.removes.map { it.store to it.name }.toHashSet()
        val leftovers = whole.flatMap { (store, categories) ->
            open(store).all().keys.sorted().filter { (store to it) !in removed }.map { ImportPlan.Remove(categories.first()!!, store, it, SettingEffect.NONE) }
        }
        return planned.copy(removes = planned.removes + leftovers)
    }
}
