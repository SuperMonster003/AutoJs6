package org.autojs.autojs.ui.storage

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import org.autojs.autojs.core.plugin.batchrename.BatchRenameBatchState
import org.autojs.autojs.core.plugin.batchrename.BatchRenameEntryState
import org.autojs.autojs.core.plugin.batchrename.BatchRenameJournalEntry
import org.autojs.autojs.core.plugin.batchrename.BatchRenameJournalRecord
import org.autojs.autojs.core.plugin.batchrename.BatchRenameSource
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.ItemTrashBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Rows of the rename history page, one per batch, in the row layout of the Trash page.
 * zh-CN: 重命名历史页面的列表, 每批一行, 使用回收站页面的行布局.
 */
internal class BatchRenameHistoryAdapter(
    private val onClick: (BatchRenameJournalRecord) -> Unit,
) : ListAdapter<BatchRenameJournalRecord, BatchRenameHistoryAdapter.ViewHolder>(DIFF) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder =
        ViewHolder(ItemTrashBinding.inflate(LayoutInflater.from(parent.context), parent, false), onClick)

    override fun onBindViewHolder(holder: ViewHolder, position: Int) = holder.bind(getItem(position))

    internal class ViewHolder(
        private val binding: ItemTrashBinding,
        private val onClick: (BatchRenameJournalRecord) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {

        private var bound: BatchRenameJournalRecord? = null

        init {
            itemView.setOnClickListener { bound?.let(onClick) }
        }

        fun bind(record: BatchRenameJournalRecord) {
            bound = record
            val context = binding.root.context
            val directory = record.entries.firstOrNull()?.directory == true
            binding.icon.setImageResource(if (directory) R.drawable.ic_simple_folder else R.drawable.ic_simple_file)
            binding.title.text = BatchRenameHistoryTexts.title(record)
            binding.subtitle.text = BatchRenameHistoryTexts.summary(context, record)
            // A batch whose undo is incomplete is highlighted. zh-CN: 撤销未完成的批次高亮.
            val color = if (record.state == BatchRenameBatchState.RECOVERY_REQUIRED) R.color.trash_expiring_soon else R.color.day_night_alpha_70
            binding.subtitle.setTextColor(ContextCompat.getColor(context, color))
            binding.checkbox.visibility = View.GONE
        }
    }

    private companion object {

        val DIFF = object : DiffUtil.ItemCallback<BatchRenameJournalRecord>() {
            override fun areItemsTheSame(oldItem: BatchRenameJournalRecord, newItem: BatchRenameJournalRecord) = oldItem.batchId == newItem.batchId
            override fun areContentsTheSame(oldItem: BatchRenameJournalRecord, newItem: BatchRenameJournalRecord) = oldItem == newItem
        }
    }
}

/**
 * Texts of the rename history page: the row of a batch and its details.
 * zh-CN: 重命名历史页面的文本: 批次所在行与批次明细.
 */
internal object BatchRenameHistoryTexts {

    /** Entries listed in the details; the rest are counted. zh-CN: 明细中列出的条目数; 其余只计数. */
    const val MAX_DETAIL_ENTRIES = 200

    private const val SEPARATOR = "  |  "

    fun time(millis: Long): String = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(millis))

    fun source(context: Context, source: BatchRenameSource): String = context.getString(
        when (source) {
            BatchRenameSource.SCRIPT -> R.string.text_rename_history_source_script
            BatchRenameSource.EXPLORER -> R.string.text_rename_history_source_explorer
        },
    )

    fun state(context: Context, state: BatchRenameBatchState): String = context.getString(
        when (state) {
            BatchRenameBatchState.COMMITTED -> R.string.text_rename_history_state_committed
            BatchRenameBatchState.PARTIAL -> R.string.text_rename_history_state_partial
            BatchRenameBatchState.REJECTED -> R.string.text_rename_history_state_rejected
            BatchRenameBatchState.RESTORED -> R.string.text_rename_history_state_restored
            BatchRenameBatchState.RECOVERY_REQUIRED -> R.string.text_rename_history_state_recovery_required
        },
    )

    fun entryState(context: Context, state: BatchRenameEntryState): String = context.getString(
        when (state) {
            BatchRenameEntryState.RENAMED -> R.string.text_rename_history_entry_renamed
            BatchRenameEntryState.NOT_RENAMED -> R.string.text_rename_history_entry_not_renamed
            BatchRenameEntryState.STALE -> R.string.text_rename_history_entry_stale
            BatchRenameEntryState.RESTORED -> R.string.text_rename_history_entry_restored
            BatchRenameEntryState.RECOVERY_REQUIRED -> R.string.text_rename_history_entry_not_restored
        },
    )

    /**
     * The folder of a batch: the session folder of a file manager batch, otherwise the folder of its first entry.
     * zh-CN: 批次所在文件夹: 文件管理器批次为会话所在文件夹, 否则为第一个条目所在文件夹.
     */
    fun folder(record: BatchRenameJournalRecord): String? = record.scope ?: record.entries.firstOrNull()?.let { File(it.oldPath).parent }

    /** `old -> new` of the first entry. zh-CN: 第一个条目的 `旧名 -> 新名`. */
    fun title(record: BatchRenameJournalRecord): String = record.entries.firstOrNull()?.let { entry ->
        "${name(entry.oldPath, entry)} -> ${name(entry.newPath, entry)}"
    }.orEmpty()

    /** Time, source and state; then the entry count and the folder. zh-CN: 时间, 来源与状态; 然后是条目数与文件夹. */
    fun summary(context: Context, record: BatchRenameJournalRecord): String = buildString {
        append(time(record.createdAt))
        append(SEPARATOR).append(source(context, record.source))
        append(SEPARATOR).append(state(context, record.state))
        append('\n')
        append(context.resources.getQuantityString(R.plurals.text_rename_history_items, record.entries.size, record.entries.size))
        folder(record)?.let { append(SEPARATOR).append(it) }
    }

    /**
     * The details of a batch: source, state, entry count and folder, then `old -> new (state)` per entry, old paths
     * relative to the folder.
     * zh-CN: 批次明细: 来源, 状态, 条目数与文件夹, 然后每个条目一行 `旧名 -> 新名 (状态)`, 旧路径相对于该文件夹.
     */
    fun details(context: Context, record: BatchRenameJournalRecord): String = buildString {
        val folder = folder(record)
        val prefix = folder?.plus(File.separator)
        append(source(context, record.source))
        append(SEPARATOR).append(state(context, record.state))
        append(SEPARATOR).append(context.resources.getQuantityString(R.plurals.text_rename_history_items, record.entries.size, record.entries.size))
        folder?.let { append('\n').append(it) }
        append('\n')
        record.entries.take(MAX_DETAIL_ENTRIES).forEach { entry ->
            val old = if (prefix != null && entry.oldPath.startsWith(prefix)) entry.oldPath.substring(prefix.length) else entry.oldPath
            append('\n')
            append(if (entry.directory) "$old/" else old)
            append(" -> ").append(name(entry.newPath, entry))
            append("  (").append(entryState(context, entry.state)).append(')')
        }
        val more = record.entries.size - MAX_DETAIL_ENTRIES
        if (more > 0) append('\n').append(context.getString(R.string.text_rename_history_more_entries, more))
    }

    private fun name(path: String, entry: BatchRenameJournalEntry): String = File(path).name.let { if (entry.directory) "$it/" else it }
}
