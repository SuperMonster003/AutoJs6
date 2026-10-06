package org.autojs.autojs.theme.app

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.autojs.autojs.theme.intelligent.IntelligentColorEngine
import org.autojs.autojs.theme.intelligent.IntelligentGroup
import org.autojs.autojs.theme.intelligent.IntelligentSnapshot
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.MtColorLibrariesRecyclerViewItemBinding
import org.autojs.autojs6.databinding.MtColorLibraryRecyclerViewItemBinding

/**
 * Rows of the intelligent libraries page: section headers, libraries and an empty hint.
 * zh-CN: 智能库页面的行: 分区标题, 智能库与空状态提示.
 */
sealed interface IntelligentLibraryRow {
    data class Header(val title: String) : IntelligentLibraryRow
    data class Library(val snapshot: IntelligentSnapshot, val isPreset: Boolean) : IntelligentLibraryRow
    data class Empty(val text: String) : IntelligentLibraryRow
}

/**
 * Created by Claude (Opus 5.5) on Oct 6, 2026.
 */
@SuppressLint("NotifyDataSetChanged")
class IntelligentLibraryAdapter(
    private val onClick: (IntelligentLibraryRow.Library) -> Unit,
    private val onLongClick: (IntelligentLibraryRow.Library) -> Unit,
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    private var rows: List<IntelligentLibraryRow> = emptyList()

    fun submit(newRows: List<IntelligentLibraryRow>) {
        rows = newRows
        notifyDataSetChanged()
    }

    override fun getItemCount() = rows.size

    override fun getItemViewType(position: Int) = when (rows[position]) {
        is IntelligentLibraryRow.Header -> TYPE_HEADER
        is IntelligentLibraryRow.Library -> TYPE_LIBRARY
        is IntelligentLibraryRow.Empty -> TYPE_EMPTY
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_LIBRARY -> LibraryViewHolder(MtColorLibrariesRecyclerViewItemBinding.inflate(inflater, parent, false))
            else -> TextViewHolder(inflater.inflate(R.layout.mt_section_header_item, parent, false) as TextView, viewType == TYPE_EMPTY)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val row = rows[position]) {
            is IntelligentLibraryRow.Header -> (holder as TextViewHolder).view.text = row.title
            is IntelligentLibraryRow.Empty -> (holder as TextViewHolder).view.text = row.text
            is IntelligentLibraryRow.Library -> (holder as LibraryViewHolder).bind(row)
        }
    }

    private class TextViewHolder(val view: TextView, isEmptyHint: Boolean) : RecyclerView.ViewHolder(view) {
        init {
            if (isEmptyHint) {
                val padding = (16 * view.resources.displayMetrics.density).toInt()
                view.setPadding(padding, padding, padding, padding)
                view.textSize = 14f
            }
        }
    }

    private inner class LibraryViewHolder(private val binding: MtColorLibrariesRecyclerViewItemBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(row: IntelligentLibraryRow.Library) {
            val context = binding.root.context
            val snapshot = row.snapshot
            val isActive = IntelligentColorEngine.activeSnapshot?.libraryId == snapshot.libraryId
            val shownGroup = when {
                isActive -> snapshot.group(IntelligentColorEngine.shownGroupId)
                else -> null
            } ?: snapshot.groups.firstOrNull { it.enabled && it.hasColor } ?: snapshot.groups.firstOrNull()

            binding.libraryItem.imageTintList = null
            binding.libraryItem.setImageDrawable(shownGroup?.let { SegmentedCircleDrawable(IntelligentSummaryFormatter.stateColors(snapshot.config, it)) })
            binding.name.text = snapshot.name
            val summary = IntelligentSummaryFormatter.summary(context, snapshot.config, snapshot.groups)
            binding.description.text = when {
                isActive && shownGroup != null -> "$summary\n${context.getString(R.string.text_in_use)}: ${IntelligentSummaryFormatter.groupName(snapshot.config, shownGroup)}"
                else -> summary
            }
            binding.root.setOnClickListener { onClick(row) }
            binding.root.setOnLongClickListener { true.also { onLongClick(row) } }
        }

    }

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_LIBRARY = 1
        private const val TYPE_EMPTY = 2
    }

}

/**
 * Groups of one intelligent library, each with its state colors as a segmented swatch.
 * zh-CN: 单个智能库中的组, 每组以分段色块显示其各状态的颜色.
 */
@SuppressLint("NotifyDataSetChanged")
class IntelligentGroupAdapter(private val onClick: (IntelligentGroup) -> Unit) : RecyclerView.Adapter<ColorItemViewHolder>() {

    private var snapshot: IntelligentSnapshot? = null
    private var checkedGroupId: Long? = null

    val groups: List<IntelligentGroup>
        get() = snapshot?.groups.orEmpty()

    fun submit(snapshot: IntelligentSnapshot, checkedGroupId: Long?) {
        this.snapshot = snapshot
        this.checkedGroupId = checkedGroupId
        notifyDataSetChanged()
    }

    override fun getItemCount() = groups.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ColorItemViewHolder {
        return ColorItemViewHolder(MtColorLibraryRecyclerViewItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: ColorItemViewHolder, position: Int) {
        val config = snapshot?.config ?: return
        val group = groups[position]
        val context = holder.itemView.context
        val available = group.enabled && group.hasColor
        holder.bind(
            colors = IntelligentSummaryFormatter.stateColors(config, group).ifEmpty { listOf(context.getColor(R.color.window_background)) },
            name = IntelligentSummaryFormatter.groupName(config, group),
            identifier = if (group.enabled) null else context.getString(R.string.text_group_disabled),
            checked = group.id == checkedGroupId,
        )
        holder.itemView.alpha = if (available) 1f else 0.5f
        holder.itemView.setOnClickListener { groups.getOrNull(holder.bindingAdapterPosition)?.let(onClick) }
    }

}
