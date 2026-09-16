package org.autojs.autojs.ui.storage

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import org.autojs.autojs.pio.PFiles
import org.autojs.autojs.storage.history.VersionHistoryController
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.ItemRevisionBinding
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Created by Claude (Fable 5.1) on Sep 16, 2026.
 */
internal class RevisionViewHolder(
    private val binding: ItemRevisionBinding,
    private val onClick: (RevisionRow) -> Unit,
    private val onLongClick: (RevisionRow) -> Unit,
) : RecyclerView.ViewHolder(binding.root) {

    private var boundRow: RevisionRow? = null

    fun bind(row: RevisionRow, selectionMode: Boolean, selected: Boolean) {
        boundRow = row
        val context = binding.root.context
        val rev = row.rev

        val timeText = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(rev.createdAt))
        binding.title.text = buildString {
            append(timeText)
            rev.label?.takeIf { it.isNotBlank() }?.let { append("  ·  ").append(it) }
            if (row.isCurrent) append("  ·  ").append(context.getString(R.string.text_current))
        }

        binding.subtitle.text = buildString {
            append(PFiles.formatSizeWithUnit(rev.sizeBytes))
            append("  |  ")
            append(VersionHistoryController.opLabel(context, rev.op))
            append("  |  ")
            append(rev.encoding)
            if (rev.hadBom) append(" (BOM)")
            if (rev.pinned) append("  |  ").append(context.getString(R.string.text_pinned))
        }

        binding.checkbox.visibility = if (selectionMode) View.VISIBLE else View.GONE
        binding.checkbox.isChecked = selected
        binding.root.isActivated = selected

        binding.root.setOnClickListener { boundRow?.let(onClick) }
        binding.root.setOnLongClickListener {
            boundRow?.let(onLongClick)
            true
        }
    }
}
