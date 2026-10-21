package org.autojs.autojs.core.pref.keys

import org.autojs.autojs.core.pref.store.SettingsStores
import org.autojs.autojs.core.pref.store.StoreId

/**
 * The named SharedPreferences files of the host (docs/settings/ROADMAP.md P3, T3): every file opened with
 * `getSharedPreferences` is declared here with its file name unchanged (R1) and registered in [SettingsStores], which
 * is what guard (b) of T5 checks the sources against. Keys and key families of a store declare it as their `store`
 * in the domain objects; a store without keys of its own is one of the permanent exclusions of appendix A.2
 * ([StoreId.excluded], P3.5), whose code keeps opening it as before. A name with `*` stands for the files whose names
 * are built at run time from that pattern.
 *
 * zh-CN: 宿主的命名 SharedPreferences 文件 (ROADMAP P3, T3): 每个经 `getSharedPreferences` 打开的文件都在这里以原文件名 (R1)
 * 声明并登记到 [SettingsStores], T5 守卫 (b) 以此核对源码. 存储中的键与键族在领域对象里把它写作 `store`; 没有键的存储是附录
 * A.2 的永久排除项 ([StoreId.excluded], P3.5), 其代码照旧打开文件. 名称中的 `*` 代表运行时按该模式拼出的一组文件名.
 */
object NamedStores {

    private fun excluded(name: String) = SettingsStores.register(StoreId(name, excluded = true))

    // Permanent exclusions of appendix A.2 (P3.5): registered only, their readers are unchanged.
    // zh-CN: 附录 A.2 的永久排除项 (P3.5): 只登记, 读写方不变.

    /** Device identity (`DeviceIdProvider`); importing it on another device would break its uniqueness. */
    @JvmField
    val DEVICE_ID_PREFS = excluded("device_id_prefs")

    /** Data of the `storages` module of scripts (`LocalStorage`, `Storages`): one file per storage name. */
    @JvmField
    val SCRIPT_LOCAL_STORAGES = excluded("autojs.localstorage.*")

    /** Data of the storage bridge of Node.js scripts (`NodeBridgeProtocol.storagePreferencesName`, appendix C Q13 e). */
    @JvmField
    val NODE_SCRIPT_STORAGES = excluded("autojs.node.storage.*.*.*")

    /** Data of the storages of Lua scripts (`LuaHostStorage`). */
    @JvmField
    val LUA_SCRIPT_STORAGES = excluded("lua-runtime-storage-v1-*")

    /** Backups of privileged edits, pointing to files of this device (`PrivilegedBackupManager`). */
    @JvmField
    val PRIVILEGED_EDIT_BACKUPS = excluded("privileged_edit_backups")

    /** Intents of running tasks, valid for the current processes only (`RuntimeTasksBridge`). */
    @JvmField
    val RUNTIME_TASK_INTENTS = excluded("runtime_task_intents")

    /** Desktop widget ids assigned by the launcher of this device (`ScriptWidgets`). */
    @JvmField
    val SCRIPT_WIDGETS = excluded("ScriptWidgets")

    /** Playback positions of media files of this device (`ExplorerPlaybackHistoryStore`). */
    @JvmField
    val EXPLORER_PLAYBACK_HISTORY = excluded("explorer_playback_history")

    /** Every declared named store, in declaration order. */
    @JvmField
    val ALL: List<StoreId> = listOf(
        DEVICE_ID_PREFS, SCRIPT_LOCAL_STORAGES, NODE_SCRIPT_STORAGES, LUA_SCRIPT_STORAGES, PRIVILEGED_EDIT_BACKUPS,
        RUNTIME_TASK_INTENTS, SCRIPT_WIDGETS, EXPLORER_PLAYBACK_HISTORY,
    )
}
