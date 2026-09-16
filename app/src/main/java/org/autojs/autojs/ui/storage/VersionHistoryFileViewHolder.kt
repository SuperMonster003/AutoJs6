package org.autojs.autojs.ui.storage

import androidx.recyclerview.widget.RecyclerView
import org.autojs.autojs.pio.PFiles
import org.autojs.autojs.storage.history.HistoryEntities
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.ItemVersionHistoryFileBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Row of the version history file list: file-type icon (same [org.autojs.autojs.ui.widget.FirstCharView]
 * as the explorer), name, and "time | < revisions > | size" plus the path.
 * zh-CN: 版本历史文件列表的行: 与资源管理器相同的文件类型图标, 名称, 以及 "时间 | < 版本数 > | 大小" 与路径.
 *
 * Created by JetBrains AI Assistant (GPT-5.2) on Feb 5, 2026.
 * Modified by JetBrains AI Assistant (GPT-5.2) as of Feb 6, 2026.
 * Modified by SuperMonster003 as of Feb 7, 2026.
 * Modified by Claude (Fable 5.1) as of Sep 16, 2026.
 */
internal class VersionHistoryFileViewHolder(
    private val binding: ItemVersionHistoryFileBinding,
    private val onClick: (HistoryEntities.FileEntry) -> Unit,
    private val onLongClick: (HistoryEntities.FileEntry) -> Unit,
) : RecyclerView.ViewHolder(binding.root) {

    private var boundItem: HistoryEntities.FileEntry? = null

    fun bind(row: VersionHistoryFileRow) {
        val item = row.entry
        boundItem = item
        val context = binding.root.context

        binding.firstChar.setIcon(row.icon)
        binding.firstChar.setIconTextColorDayNight()
        binding.firstChar.setStrokeColorDayNight()
        binding.firstChar.setFillTransparent()

        binding.title.text = File(item.displayPath).name

        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val timeText = fmt.format(Date(item.lastSeenAt))

        val sizeText = row.sizeBytes
            ?.coerceAtLeast(0L)?.let { PFiles.formatSizeWithUnit(it) }
            ?: context.getString(R.string.text_half_ellipsis)

        val countText = row.revisionCount
            ?.coerceAtLeast(0L)?.toString()
            ?: context.getString(R.string.text_half_ellipsis)

        binding.subtitle.text = buildString {
            append(timeText)
            append("  |  ")
            append("< $countText >")
            append("  |  ")
            append(sizeText)
            append("\n")
            append(item.displayPath)
        }

        itemView.setOnClickListener {
            boundItem?.let(onClick)
        }
        itemView.setOnLongClickListener {
            boundItem?.let(onLongClick)
            true
        }
    }
}
