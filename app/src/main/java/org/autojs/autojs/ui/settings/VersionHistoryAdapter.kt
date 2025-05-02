package org.autojs.autojs.ui.settings

import android.annotation.SuppressLint
import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.noties.markwon.Markwon
import org.autojs.autojs.ui.settings.VersionHistoryAdapter.VersionHistoryViewHolder
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.ItemVersionHistoryBinding

class VersionHistoryAdapter(private val context: Context, private val markwon: Markwon) : RecyclerView.Adapter<VersionHistoryViewHolder>() {

    private val data = mutableListOf<VersionHistoryItem>()

    @SuppressLint("NotifyDataSetChanged")
    fun submit(list: List<VersionHistoryItem>) {
        data.clear()
        data += list
        notifyDataSetChanged()
    }

    fun add(item: VersionHistoryItem) {
        data += item
        notifyItemInserted(data.lastIndex)
    }

    fun addAll(list: List<VersionHistoryItem>) {
        val start = data.size
        data += list
        notifyItemRangeInserted(start, list.size)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VersionHistoryViewHolder {
        val binding = ItemVersionHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VersionHistoryViewHolder(binding)
    }

    override fun getItemCount() = data.size

    override fun onBindViewHolder(holder: VersionHistoryViewHolder, position: Int) {
        holder.bind(context, data[position], animate = false)
    }

    override fun onBindViewHolder(holder: VersionHistoryViewHolder, position: Int, payloads: MutableList<Any>) {
        val animate = payloads.contains(PAYLOAD_EXPAND_STATE_CHANGED)
        holder.bind(context, data[position], animate)
    }

    fun expandAll() {
        data.forEach { it.expanded = true }
        notifyItemRangeChanged(0, itemCount, PAYLOAD_EXPAND_STATE_CHANGED)
    }

    fun collapseAll() {
        data.forEach { it.expanded = false }
        notifyItemRangeChanged(0, itemCount, PAYLOAD_EXPAND_STATE_CHANGED)
    }

    inner class VersionHistoryViewHolder(binding: ItemVersionHistoryBinding) : RecyclerView.ViewHolder(binding.root) {

        private val chevron = binding.chevron
        private val tvTitle = binding.tvTitle
        private val tvDate = binding.tvDate
        private val tvBody = binding.tvBody

        private val tvFeature = binding.tvFeatureCount
        private val tvFix = binding.tvFixCount
        private val tvImprovement = binding.tvImprovementCount

        fun bind(context: Context, item: VersionHistoryItem, animate: Boolean) {
            val rotationAngle = if (item.expanded) 180f else 0f
            chevron.animate().cancel()
            when (animate) {
                true -> chevron.animate().rotation(rotationAngle).setDuration(200).start()
                else -> chevron.rotation = rotationAngle
            }

            item.lines.count { it.contains("`${context.getString(R.string.changelog_label_feature)}`") }.let {
                tvFeature.text = "$it"
            }
            item.lines.count { it.contains("`${context.getString(R.string.changelog_label_fix)}`") }.let {
                tvFix.text = "$it"
            }
            item.lines.count { it.contains("`${context.getString(R.string.changelog_label_improvement)}`") }.let {
                tvImprovement.text = "$it"
            }

            tvTitle.text = item.version
            tvDate.text = item.date
            tvBody.run {
                markwon.setMarkdown(this, item.lines.joinToString("\n"))
                visibility = if (item.expanded) View.VISIBLE else View.GONE
            }

            itemView.setOnClickListener {
                item.expanded = !item.expanded
                notifyItemChanged(absoluteAdapterPosition, PAYLOAD_EXPAND_STATE_CHANGED)
            }
        }

    }

    companion object {

        private const val PAYLOAD_EXPAND_STATE_CHANGED = 1

    }

}