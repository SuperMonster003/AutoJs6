package org.autojs.autojs.ui.settings

import android.annotation.SuppressLint
import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import io.noties.markwon.Markwon
import org.autojs.autojs.ui.settings.VersionHistoryAdapter.VersionHistoryViewHolder
import org.autojs.autojs.ui.settings.VersionHistoryRepository.Companion.Category
import org.autojs.autojs.ui.settings.VersionHistoryRepository.Companion.DEFAULT_FILTER
import org.autojs.autojs.ui.settings.VersionHistoryRepository.Companion.compareVersion
import org.autojs.autojs.util.ProcessLogger
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.ItemVersionHistoryBinding

@SuppressLint("NotifyDataSetChanged")
class VersionHistoryAdapter(private val context: Context, private val markwon: Markwon) : RecyclerView.Adapter<VersionHistoryViewHolder>() {

    private val mData = mutableListOf<VersionHistoryItem>()
    private var mLayoutManager: RecyclerView.LayoutManager? = null
    private val mCategoryFilter: MutableSet<Category> = DEFAULT_FILTER.clone()

    fun submit(list: MutableList<VersionHistoryItem>) {
        mData.clear()
        mData += list
        notifyDataSetChanged()
    }

    fun add(item: VersionHistoryItem) {
        mData += item
        notifyItemInserted(mData.lastIndex)
    }

    fun addAll(list: List<VersionHistoryItem>) {
        val start = mData.size
        mData += list
        notifyItemRangeInserted(start, list.size)
    }

    fun updateFilter(filter: Set<Category>) {
        mCategoryFilter.clear()
        mCategoryFilter.addAll(filter)
        notifyDataSetChanged()
    }

    fun addOrUpdate(item: VersionHistoryItem) {
        when (val idx = mData.indexOfFirst { compareVersion(it.version, item.version) == 0 }) {
            -1 -> {
                ProcessLogger.i("${item.version}: ${context.getString(R.string.logger_ver_history_insert_new_entries)}")
                val insertPos = mData.indexOfFirst { compareVersion(it.version, item.version) < 0 }
                val realPos = if (insertPos == -1) mData.size else insertPos
                mData.add(realPos, item)
                notifyItemInserted(realPos)
                mLayoutManager?.scrollToPosition(0)
            }
            else -> {
                fun sameDate(local: VersionHistoryItem) = local.date == item.date
                fun sameLines(local: VersionHistoryItem) = local.lines.joinToString(",") { it.trim() } == item.lines.joinToString(",") { it.trim() }
                val local = mData[idx]
                if (sameDate(local) && sameLines(local)) {
                    ProcessLogger.i("${item.version}: ${context.getString(R.string.logger_ver_history_no_processing_needed)}")
                } else {
                    if (!sameDate(local)) {
                        ProcessLogger.i("${item.version}: ${context.getString(R.string.logger_ver_history_overwrite_date)}")
                        Log.d(TAG, "item date: ${item.date}")
                        Log.d(TAG, "local data: ${local.date}")
                    }
                    if (!sameLines(local)) {
                        ProcessLogger.i("${item.version}: ${context.getString(R.string.logger_ver_history_overwrite_update_record)}")
                        Log.d(TAG, "item lines: ${item.lines.joinToString(",")}")
                        Log.d(TAG, "local lines: ${local.lines.joinToString(",")}")
                    }
                    mData[idx] = item
                    notifyItemChanged(idx)
                }
            }
        }
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        mLayoutManager = recyclerView.layoutManager
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VersionHistoryViewHolder {
        val binding = ItemVersionHistoryBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VersionHistoryViewHolder(parent.context, binding)
    }

    override fun getItemCount() = mData.size

    override fun onBindViewHolder(holder: VersionHistoryViewHolder, position: Int) {
        holder.bind(mData[position], animate = false)
    }

    override fun onBindViewHolder(holder: VersionHistoryViewHolder, position: Int, payloads: MutableList<Any>) {
        val animate = payloads.contains(PAYLOAD_EXPAND_STATE_CHANGED)
        holder.bind(mData[position], animate)
    }

    fun expandAll() {
        mData.forEach { it.expanded = true }
        notifyItemRangeChanged(0, itemCount, PAYLOAD_EXPAND_STATE_CHANGED)
    }

    fun collapseAll() {
        mData.forEach { it.expanded = false }
        notifyItemRangeChanged(0, itemCount, PAYLOAD_EXPAND_STATE_CHANGED)
    }

    inner class VersionHistoryViewHolder(context: Context, binding: ItemVersionHistoryBinding) : RecyclerView.ViewHolder(binding.root) {

        private val chevron = binding.chevron

        private val tvTitle = binding.tvTitle
        private val tvDate = binding.tvDate
        private val tvLines = binding.tvLines

        private val tvFeatureCount = binding.tvFeatureCount
        private val tvFixCount = binding.tvFixCount
        private val tvImprovementCount = binding.tvImprovementCount

        private val tvFeatureCountContainer = binding.tvFeatureCountContainer
        private val tvFixCountContainer = binding.tvFixCountContainer
        private val tvImprovementCountContainer = binding.tvImprovementCountContainer

        private val changelogLabelHint = "`${context.getString(R.string.changelog_label_hint)}`"
        private val changelogLabelFeature = "`${context.getString(R.string.changelog_label_feature)}`"
        private val changelogLabelFix = "`${context.getString(R.string.changelog_label_fix)}`"
        private val changelogLabelImprovement = "`${context.getString(R.string.changelog_label_improvement)}`"
        private val changelogLabelDependency = "`${context.getString(R.string.changelog_label_dependency)}`"

        fun bind(item: VersionHistoryItem, animate: Boolean) {
            val rotationAngle = if (item.expanded) 180f else 0f
            chevron.animate().cancel()
            when (animate) {
                true -> chevron.animate().rotation(rotationAngle).setDuration(200).start()
                else -> chevron.rotation = rotationAngle
            }

            tvTitle.text = item.version
            tvDate.text = item.date
            tvLines.run {
                markwon.setMarkdown(this, applyCategoryFilter(item.lines).joinToString("\n"))
                visibility = if (item.expanded) View.VISIBLE else View.GONE
            }

            when {
                mCategoryFilter.contains(Category.FEATURE) -> {
                    tvFeatureCount.text = item.lines.count { it.contains(changelogLabelFeature) }.toString()
                    tvFeatureCountContainer.visibility = View.VISIBLE
                }
                else -> {
                    tvFeatureCountContainer.visibility = View.GONE
                }
            }
            when {
                mCategoryFilter.contains(Category.FIX) -> {
                    tvFixCount.text = item.lines.count { it.contains(changelogLabelFix) }.toString()
                    tvFixCountContainer.visibility = View.VISIBLE
                }
                else -> {
                    tvFixCountContainer.visibility = View.GONE
                }
            }
            when {
                mCategoryFilter.contains(Category.IMPROVEMENT) -> {
                    tvImprovementCount.text = item.lines.count { it.contains(changelogLabelImprovement) }.toString()
                    tvImprovementCountContainer.visibility = View.VISIBLE
                }
                else -> {
                    tvImprovementCountContainer.visibility = View.GONE
                }
            }

            itemView.setOnClickListener {
                item.expanded = !item.expanded
                notifyItemChanged(absoluteAdapterPosition, PAYLOAD_EXPAND_STATE_CHANGED)
            }
        }

        private fun applyCategoryFilter(lines: List<String>) = lines.filter {
            when {
                it.contains(changelogLabelHint) && !mCategoryFilter.contains(Category.HINT) -> false
                it.contains(changelogLabelFeature) && !mCategoryFilter.contains(Category.FEATURE) -> false
                it.contains(changelogLabelFix) && !mCategoryFilter.contains(Category.FIX) -> false
                it.contains(changelogLabelImprovement) && !mCategoryFilter.contains(Category.IMPROVEMENT) -> false
                it.contains(changelogLabelDependency) && !mCategoryFilter.contains(Category.DEPENDENCY) -> false
                else -> true
            }
        }

    }

    companion object {

        private val TAG = VersionHistoryAdapter::class.java.simpleName

        private const val PAYLOAD_EXPAND_STATE_CHANGED = 1

    }

}