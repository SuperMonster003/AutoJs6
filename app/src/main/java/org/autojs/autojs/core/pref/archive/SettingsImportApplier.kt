package org.autojs.autojs.core.pref.archive

import org.autojs.autojs.core.pref.SettingEffect
import org.autojs.autojs.core.pref.store.SettingsStore
import org.autojs.autojs.core.pref.store.SettingsStores
import org.autojs.autojs.core.pref.store.StoreId

/**
 * Applies an [ImportPlan] (docs/settings/ROADMAP.md T8 / T12): [write] commits every store of the plan once, its
 * removes and puts in one edit (`commit()`, so that the values are on disk before the effects run or the app
 * restarts), and returns the keys that really changed; [SettingsEffects.run] then applies the changed `APPLY` keys
 * through their entries. [apply] does both on the calling thread. Deployment items are not applied here: an item that
 * succeeds writes its key through a plan of its own put ([ImportPlan.Deployment.toPut], P7.6). Pure JVM (R3).
 *
 * zh-CN: 应用 [ImportPlan] (ROADMAP T8 / T12): [write] 对计划中的每个存储提交一次, 删除与写入在同一次编辑中完成 (`commit()`, 使值在执行
 * 生效入口或重启之前已写盘), 返回真正发生变化的键; 之后由 [SettingsEffects.run] 经各入口应用变化的 `APPLY` 键. [apply] 在调用线程上
 * 依次完成两者. 部署项不在此应用: 部署成功的项以只含其写入的计划写入键 ([ImportPlan.Deployment.toPut], P7.6). 纯 JVM (R3).
 */
object SettingsImportApplier {

    /** What [apply] did: the changed keys, in plan order, and the report of the effects. zh-CN: 变化的键与生效报告. */
    data class Result(val changes: List<SettingsEffects.Change>, val effects: SettingsEffects.Report)

    /**
     * Writes [plan], one commit per store, and returns the keys whose stored value changed, in plan order (removes of
     * a store before its puts); a put of the value already stored is no change.
     * zh-CN: 写入 [plan], 每个存储提交一次, 返回存储值发生变化的键, 按计划顺序 (同一存储先删除后写入); 写入与现值相同的值不算变化.
     */
    @JvmStatic
    @JvmOverloads
    fun write(plan: ImportPlan, open: (StoreId) -> SettingsStore = { SettingsStores[it] }): List<SettingsEffects.Change> {
        val changes = ArrayList<SettingsEffects.Change>()
        plan.stores.forEach { id ->
            val removes = plan.removes.filter { it.store == id }
            val puts = plan.puts.filter { it.store == id }
            val changed = open(id).edit(commit = true) {
                removes.forEach { remove(it.name) }
                puts.forEach { put(it.name, it.value) }
            }
            val effects = LinkedHashMap<String, SettingEffect>()
            removes.forEach { effects[it.name] = it.effect }
            puts.forEach { effects[it.name] = it.effect }
            effects.filterKeys { it in changed }.forEach { (name, effect) -> changes.add(SettingsEffects.Change(id, name, effect)) }
        }
        return changes
    }

    /**
     * [write] and then [SettingsEffects.run] with [actions], both on the calling thread (the main thread in the app,
     * where the entries need it).
     * zh-CN: 先 [write] 再以 [actions] 执行 [SettingsEffects.run], 都在调用线程上 (应用内为入口所需的主线程).
     */
    @JvmStatic
    @JvmOverloads
    fun apply(plan: ImportPlan, actions: (SettingsEffects.Entry) -> SettingsEffects.Action, open: (StoreId) -> SettingsStore = { SettingsStores[it] }): Result {
        val changes = write(plan, open)
        return Result(changes, SettingsEffects.run(changes, actions))
    }
}
