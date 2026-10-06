package org.autojs.autojs.theme.app

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.autojs.autojs.theme.app.ColorEntities.LibraryColor
import org.autojs.autojs.theme.app.ColorLibrariesActivity.Companion.PresetColorItem
import org.autojs.autojs.theme.app.ColorLibrariesActivity.Companion.presetColorLibraries
import org.autojs.autojs.theme.intelligent.IntelligentGroup
import org.autojs.autojs.theme.intelligent.IntelligentSnapshot
import org.autojs.autojs6.databinding.MtColorLibraryRecyclerViewItemBinding

/**
 * A result of the search over all color libraries (roadmap P2.4).
 * zh-CN: 全部颜色库搜索的结果 (Roadmap P2.4).
 */
sealed interface GlobalColorSearchItem {

    data class Preset(val item: PresetColorItem) : GlobalColorSearchItem

    data class UserColor(val libraryName: String, val color: LibraryColor) : GlobalColorSearchItem

    /** A group of an intelligent library, shown with all its state colors. zh-CN: 智能库中的一个组, 显示其全部状态的颜色. */
    data class Group(val snapshot: IntelligentSnapshot, val group: IntelligentGroup) : GlobalColorSearchItem

}

/**
 * Shows preset colors, user library colors and intelligent groups in one list; the description ends with the library
 * (its identifier for presets, its name otherwise).
 * zh-CN: 在同一列表中显示预设颜色, 用户库颜色与智能库的组; 描述末尾为所属库 (预设库显示标识, 其他显示库名).
 *
 * Created by Claude (Opus 5.5) on Oct 7, 2026.
 */
@SuppressLint("NotifyDataSetChanged")
class GlobalColorSearchAdapter(
    private val isChecked: (GlobalColorSearchItem) -> Boolean,
    private val onClick: (GlobalColorSearchItem) -> Unit,
) : RecyclerView.Adapter<ColorItemViewHolder>() {

    private var items: List<GlobalColorSearchItem> = emptyList()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ColorItemViewHolder {
        return ColorItemViewHolder(MtColorLibraryRecyclerViewItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))
    }

    override fun onBindViewHolder(holder: ColorItemViewHolder, position: Int) {
        val item = items[position]
        val context = holder.itemView.context
        when (item) {
            is GlobalColorSearchItem.Preset -> holder.bind(
                colors = listOf(context.getColor(item.item.colorRes)),
                name = context.getString(item.item.nameRes),
                identifier = presetColorLibraries.find { it.id == item.item.libraryId }?.let { context.getString(it.identifierRes) },
                checked = isChecked(item),
            )
            is GlobalColorSearchItem.UserColor -> holder.bind(
                color = item.color.color,
                name = item.color.name ?: String.format("#%06X", 0xFFFFFF and item.color.color),
                identifier = shortLibraryName(item.libraryName),
                checked = isChecked(item),
            )
            is GlobalColorSearchItem.Group -> holder.bind(
                colors = IntelligentSummaryFormatter.stateColors(item.snapshot.config, item.group),
                name = IntelligentSummaryFormatter.groupName(item.snapshot.config, item.group),
                identifier = shortLibraryName(item.snapshot.name),
                checked = isChecked(item),
            )
        }
        holder.itemView.setOnClickListener {
            items.getOrNull(holder.bindingAdapterPosition)?.let(onClick)
        }
    }

    override fun getItemCount() = items.size

    fun updateData(newItems: List<GlobalColorSearchItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    /**
     * Shortens long names in the middle: names of copies often differ only at the end, such as "(copy)" and "(2)".
     * zh-CN: 在中间截断过长的名称: 副本的名称往往只在末尾不同, 例如 "(副本)" 与 "(2)".
     */
    private fun shortLibraryName(name: String): String = when {
        name.length <= LIBRARY_NAME_LENGTH -> name
        else -> name.take(LIBRARY_NAME_HEAD).trimEnd() + "..." + name.takeLast(LIBRARY_NAME_LENGTH - LIBRARY_NAME_HEAD - 3).trimStart()
    }

    companion object {
        private const val LIBRARY_NAME_LENGTH = 24
        private const val LIBRARY_NAME_HEAD = 12
    }

}
