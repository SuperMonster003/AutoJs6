package org.autojs.autojs.ui.storage

import android.view.View
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import org.autojs.autojs.pio.PFiles
import org.autojs.autojs.storage.history.TrashEntities
import org.autojs.autojs.storage.history.TrashExpiry
import org.autojs.autojs.storage.history.TrashPreviewController
import org.autojs.autojs.util.StringUtils.normalizeTrailingSlash
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.ItemTrashBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Created by JetBrains AI Assistant (GPT-5.2) on Feb 4, 2026.
 * Modified by Claude (Fable 5.1) as of Sep 16, 2026.
 */
internal class TrashViewHolder(
    private val binding: ItemTrashBinding,
    private val onClick: (TrashEntities.TrashItem) -> Unit,
    private val onLongClick: (TrashEntities.TrashItem) -> Unit,
) : RecyclerView.ViewHolder(binding.root) {

    private var boundItem: TrashEntities.TrashItem? = null

    fun bind(item: TrashEntities.TrashItem, selectionMode: Boolean, selected: Boolean, expiryMaxDays: Int, now: Long, missing: Boolean) {
        boundItem = item
        val context = binding.root.context

        binding.icon.setImageResource(if (item.isDirectory) R.drawable.ic_simple_folder else R.drawable.ic_simple_file)
        binding.title.text = File(item.originalPath).name

        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val timeText = fmt.format(Date(item.trashedAt))
        val sizeText = PFiles.formatSizeWithUnit(item.sizeBytes)

        val remaining = TrashExpiry.expiresAtOrNull(item.trashedAt, expiryMaxDays)?.let { TrashExpiry.remaining(it, now) }
        binding.subtitle.text = buildString {
            append(timeText)
            append("  |  ")
            append(sizeText)
            if (missing) {
                append("  |  ")
                append(context.getString(R.string.text_trash_data_missing))
            } else if (remaining != null) {
                append("  |  ")
                append(TrashPreviewController.formatRemaining(context, remaining))
            }
            append('\n')
            append(item.originalPath.normalizeTrailingSlash(item.isDirectory))
        }
        // Broken items and items expiring within a day are highlighted.
        // zh-CN: 失效条目与一天内到期的条目高亮.
        val subtitleColor = if (missing || remaining?.withinOneDay == true) R.color.trash_expiring_soon else R.color.day_night_alpha_70
        binding.subtitle.setTextColor(ContextCompat.getColor(context, subtitleColor))

        binding.checkbox.visibility = if (selectionMode) View.VISIBLE else View.GONE
        binding.checkbox.isChecked = selected
        binding.root.isActivated = selected

        itemView.setOnClickListener {
            boundItem?.let(onClick)
        }
        itemView.setOnLongClickListener {
            boundItem?.let(onLongClick)
            true
        }
    }
}
