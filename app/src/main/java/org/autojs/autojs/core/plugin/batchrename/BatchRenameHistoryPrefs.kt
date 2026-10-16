package org.autojs.autojs.core.plugin.batchrename

import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs6.R

/**
 * Settings of the rename history page (plugin roadmap D39): the batches kept for scripts and for each plugin, 20, 50 or
 * 100.
 * zh-CN: 重命名历史页面的设置 (插件路线图 D39): 脚本与每个插件各保留的批数, 20, 50 或 100.
 */
object BatchRenameHistoryPrefs {

    fun retention(): Int = Pref.getInt(R.string.key_batch_rename_history_retention, BatchRenameJournalStore.MAX_BATCHES)
        .takeIf { it in BatchRenameJournalStore.RETENTION_CHOICES } ?: BatchRenameJournalStore.MAX_BATCHES

    fun setRetention(value: Int) {
        require(value in BatchRenameJournalStore.RETENTION_CHOICES) { "Unsupported retention $value" }
        Pref.putInt(R.string.key_batch_rename_history_retention, value)
    }
}
