package org.autojs.autojs.ui.storage

import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.autojs.autojs.pio.PFiles
import org.autojs.autojs.storage.history.TrashEntities
import org.autojs.autojs.util.StringUtils.normalizeTrailingSlash
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.ItemTrashBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Created by JetBrains AI Assistant (GPT-5.2) on Feb 4, 2026.
 */
internal class TrashViewHolder(
    binding: ItemTrashBinding,
    private val onClick: (TrashEntities.TrashItem) -> Unit,
    private val onLongClick: (TrashEntities.TrashItem) -> Unit,
) : RecyclerView.ViewHolder(binding.root) {

    private val iconView: ImageView = binding.icon
    private val titleView: TextView = binding.title
    private val subtitleView: TextView = binding.subtitle

    private var boundItem: TrashEntities.TrashItem? = null

    fun bind(item: TrashEntities.TrashItem) {
        boundItem = item

        iconView.setImageResource(if (item.isDirectory) R.drawable.ic_simple_folder else R.drawable.ic_simple_file)
        titleView.text = File(item.originalPath).name

        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val timeText = fmt.format(Date(item.trashedAt))
        val sizeText = PFiles.formatSizeWithUnit(item.sizeBytes)

        subtitleView.text = buildString {
            append(timeText)
            append("  |  ")
            append(sizeText)
            append("\n")
            append(item.originalPath.normalizeTrailingSlash(item.isDirectory))
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
