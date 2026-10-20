package org.autojs.autojs.core.plugin.batchrename

import org.autojs.autojs.core.pref.keys.ExplorerKeys

/**
 * Settings of the rename history page (plugin roadmap D39): the batches kept for scripts and for each plugin, 20, 50 or
 * 100.
 * zh-CN: 重命名历史页面的设置 (插件路线图 D39): 脚本与每个插件各保留的批数, 20, 50 或 100.
 *
 * The key moved to [ExplorerKeys] (docs/settings/ROADMAP.md P2.2, T17); this object forwards to it until the old
 * entries are removed (P5.2 / P14.1).
 * zh-CN: 该键已并入 [ExplorerKeys] (ROADMAP P2.2, T17); 本对象在旧入口删除 (P5.2 / P14.1) 前转发到它.
 */
@Deprecated("Use ExplorerKeys.batchRenameHistoryRetention (docs/settings/ROADMAP.md P2.2)", ReplaceWith("ExplorerKeys", "org.autojs.autojs.core.pref.keys.ExplorerKeys"))
object BatchRenameHistoryPrefs {

    fun retention(): Int = ExplorerKeys.batchRenameHistoryRetention()

    fun setRetention(value: Int) = ExplorerKeys.setBatchRenameHistoryRetention(value)
}
