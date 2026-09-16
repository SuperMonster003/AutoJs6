package org.autojs.autojs.ui.storage

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import org.autojs.autojs.storage.history.LineDiff
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.ItemDiffCollapsedBinding
import org.autojs.autojs6.databinding.ItemDiffLineBinding

/**
 * A row of the diff page: one diff line, or a run of unchanged lines folded away.
 * zh-CN: diff 页的一行: 一条 diff 行, 或一段被折叠的未变更行.
 */
sealed class DiffRow {
    data class Line(val line: LineDiff.Line) : DiffRow()

    /** [fromIndex] is the index of the first hidden line in the full result; [count] lines are hidden. */
    data class Collapsed(val fromIndex: Int, val count: Int) : DiffRow()
}

/**
 * Created by Claude (Fable 5.1) on Sep 16, 2026.
 */
internal class DiffLineAdapter(
    private val onExpand: (DiffRow.Collapsed) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var rows: List<DiffRow> = emptyList()

    fun submit(newRows: List<DiffRow>) {
        rows = newRows
        notifyDataSetChanged()
    }

    override fun getItemViewType(position: Int): Int = when (rows[position]) {
        is DiffRow.Line -> TYPE_LINE
        is DiffRow.Collapsed -> TYPE_COLLAPSED
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_LINE -> LineHolder(ItemDiffLineBinding.inflate(inflater, parent, false))
            else -> CollapsedHolder(ItemDiffCollapsedBinding.inflate(inflater, parent, false), onExpand)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is DiffRow.Line -> (holder as LineHolder).bind(row.line)
            is DiffRow.Collapsed -> (holder as CollapsedHolder).bind(row)
        }
    }

    override fun getItemCount(): Int = rows.size

    private class LineHolder(private val binding: ItemDiffLineBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(line: LineDiff.Line) {
            val context = binding.root.context
            binding.leftLineNo.text = if (line.leftLineNo > 0) line.leftLineNo.toString() else ""
            binding.rightLineNo.text = if (line.rightLineNo > 0) line.rightLineNo.toString() else ""
            binding.text.text = line.text
            val (marker, bgRes) = when (line.kind) {
                LineDiff.Kind.INSERT -> "+" to R.color.diff_line_inserted_bg
                LineDiff.Kind.DELETE -> "-" to R.color.diff_line_deleted_bg
                LineDiff.Kind.EQUAL -> " " to android.R.color.transparent
            }
            binding.marker.text = marker
            binding.root.setBackgroundColor(ContextCompat.getColor(context, bgRes))
        }
    }

    private class CollapsedHolder(
        private val binding: ItemDiffCollapsedBinding,
        private val onExpand: (DiffRow.Collapsed) -> Unit,
    ) : RecyclerView.ViewHolder(binding.root) {

        private var bound: DiffRow.Collapsed? = null

        init {
            binding.root.setOnClickListener { bound?.let(onExpand) }
        }

        fun bind(row: DiffRow.Collapsed) {
            bound = row
            binding.text.text = binding.root.context.getString(R.string.text_n_unchanged_lines_hidden, row.count)
        }
    }

    private companion object {
        const val TYPE_LINE = 0
        const val TYPE_COLLAPSED = 1
    }
}
