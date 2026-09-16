package org.autojs.autojs.ui.storage

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import org.autojs.autojs.storage.history.HistoryEntities
import org.autojs.autojs.util.FileUtils
import org.autojs.autojs6.databinding.ItemVersionHistoryFileBinding

/**
 * Display row: the entry plus everything the row renders, so [DiffUtil] can detect stat / icon changes.
 * zh-CN: 显示行: 条目加上该行渲染所需的全部数据, 使 [DiffUtil] 能感知统计 / 图标变化.
 */
internal data class VersionHistoryFileRow(
    val entry: HistoryEntities.FileEntry,
    val sizeBytes: Long?,
    val revisionCount: Long?,
    val icon: FileUtils.TYPE.Icon,
)

/**
 * Version history file list backed by [ListAdapter]: submissions are diffed on a background thread
 * and only changed rows are rebound.
 * zh-CN: 基于 [ListAdapter] 的版本历史文件列表: 提交的数据在后台线程 diff, 仅重绑变化的行.
 *
 * Created by JetBrains AI Assistant (GPT-5.2) on Feb 5, 2026.
 * Modified by Claude (Fable 5.1) as of Sep 16, 2026.
 */
internal class VersionHistoryFileAdapter(
    private val onClick: (HistoryEntities.FileEntry) -> Unit,
    private val onLongClick: (HistoryEntities.FileEntry) -> Unit,
) : ListAdapter<VersionHistoryFileRow, VersionHistoryFileViewHolder>(DIFF) {

    fun submit(rows: List<VersionHistoryFileRow>) {
        submitList(rows)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VersionHistoryFileViewHolder {
        val binding = ItemVersionHistoryFileBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VersionHistoryFileViewHolder(binding, onClick, onLongClick)
    }

    override fun onBindViewHolder(holder: VersionHistoryFileViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    companion object {

        private val DIFF = object : DiffUtil.ItemCallback<VersionHistoryFileRow>() {

            override fun areItemsTheSame(oldItem: VersionHistoryFileRow, newItem: VersionHistoryFileRow): Boolean {
                return oldItem.entry.fileId == newItem.entry.fileId
            }

            override fun areContentsTheSame(oldItem: VersionHistoryFileRow, newItem: VersionHistoryFileRow): Boolean {
                return oldItem == newItem
            }
        }
    }
}
