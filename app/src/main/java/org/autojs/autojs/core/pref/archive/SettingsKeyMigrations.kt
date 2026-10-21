package org.autojs.autojs.core.pref.archive

import org.autojs.autojs.core.pref.TypedValue

/**
 * The key rename table of imports (docs/settings/ROADMAP.md D9 / R1, P7.1): key names never change (R1), and when one
 * has to, the old name is read through a [Rename] here, so that an archive exported by an older host still imports
 * the value under its new name. The first phase has no rename ([RENAMES] is empty); the table only renames entries of
 * a category file before the import plan (P7.2) sees them. Pure JVM (R3).
 *
 * zh-CN: 导入的键改名迁移表 (ROADMAP D9 / R1, P7.1): 键名不改 (R1), 确需改名时旧名经此处的 [Rename] 读取, 使旧版本宿主导出的
 * 归档仍能以新名导入其值. 第一期没有改名 ([RENAMES] 为空); 迁移表只在导入计划 (P7.2) 之前改写分类文件中的条目名. 纯 JVM (R3).
 */
object SettingsKeyMigrations {

    /**
     * [from] of [store] is named [to] from the host `versionCode` [sinceVersionCode] on: an archive exported by an
     * older host has its [from] entry read as [to]. When the archive holds both names, [to] wins and [from] is dropped.
     * zh-CN: 自宿主 `versionCode` [sinceVersionCode] 起, [store] 中的 [from] 改名为 [to]: 较旧宿主导出的归档中的 [from] 条目按 [to]
     * 读取. 归档同时含两个名称时以 [to] 为准, 丢弃 [from].
     */
    data class Rename(val store: String, val from: String, val to: String, val sinceVersionCode: Int) {
        init {
            require(from != to) { "A rename needs two names, got $from twice" }
        }
    }

    /** The renames of every version, oldest first; empty in the first phase (D9). zh-CN: 各版本的改名, 按时间先后; 第一期为空. */
    @JvmField
    val RENAMES: List<Rename> = emptyList()

    /**
     * [decoded] with the renames that apply to an archive exported by the host `versionCode` [exportedBy], applied by
     * [Rename.sinceVersionCode] (so that a name renamed twice ends up with its latest name). Entries keep their order.
     * zh-CN: 对宿主 `versionCode` 为 [exportedBy] 时导出的归档, 按 [Rename.sinceVersionCode] 先后应用适用的改名后的 [decoded]
     * (改名两次的名称得到最新的名称). 条目顺序不变.
     */
    @JvmStatic
    @JvmOverloads
    fun migrate(decoded: SettingsSnapshotCodec.Decoded, exportedBy: Int, renames: List<Rename> = RENAMES): SettingsSnapshotCodec.Decoded {
        val applicable = renames.filter { exportedBy < it.sinceVersionCode }.sortedBy { it.sinceVersionCode }
        if (applicable.isEmpty()) return decoded
        val stores = decoded.category.stores.mapValuesTo(LinkedHashMap()) { (store, entries) ->
            applicable.filter { it.store == store }.fold(entries) { current, rename -> renamed(current, rename) }
        }
        return decoded.copy(category = decoded.category.copy(stores = stores))
    }

    private fun renamed(entries: Map<String, TypedValue>, rename: Rename): Map<String, TypedValue> {
        val value = entries[rename.from] ?: return entries
        val result = LinkedHashMap<String, TypedValue>()
        entries.forEach { (name, v) ->
            when (name) {
                rename.from -> if (rename.to !in entries) result[rename.to] = value
                else -> result[name] = v
            }
        }
        return result
    }
}
