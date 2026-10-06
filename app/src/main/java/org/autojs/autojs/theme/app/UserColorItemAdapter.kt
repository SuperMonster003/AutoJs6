package org.autojs.autojs.theme.app

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.autojs.autojs.theme.app.ColorEntities.LibraryColor
import org.autojs.autojs6.databinding.MtColorLibraryRecyclerViewItemBinding

/**
 * Colors of a user library. While [isReordering] is on, long presses are left to the drag helper.
 * zh-CN: 用户库中的颜色. [isReordering] 开启时, 长按交给拖拽辅助处理.
 *
 * Created by Claude (Opus 5.5) on Oct 6, 2026.
 */
@SuppressLint("NotifyDataSetChanged")
class UserColorItemAdapter(
    private val onClick: (LibraryColor) -> Unit,
    private val onLongClick: (LibraryColor) -> Unit,
) : RecyclerView.Adapter<ColorItemViewHolder>() {

    val items: MutableList<LibraryColor> = mutableListOf()

    var selectedColorId: Long? = null

    var isReordering = false
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ColorItemViewHolder {
        val binding = MtColorLibraryRecyclerViewItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return ColorItemViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ColorItemViewHolder, position: Int) {
        val item = items[position]
        holder.bind(
            color = item.color,
            name = item.name ?: String.format("#%06X", 0xFFFFFF and item.color),
            identifier = null,
            checked = item.id == selectedColorId,
        )
        holder.itemView.setOnClickListener { items.getOrNull(holder.bindingAdapterPosition)?.let(onClick) }
        when {
            isReordering -> holder.itemView.setOnLongClickListener(null)
            else -> holder.itemView.setOnLongClickListener { true.also { items.getOrNull(holder.bindingAdapterPosition)?.let(onLongClick) } }
        }
    }

    override fun getItemCount() = items.size

    fun submit(newItems: List<LibraryColor>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    fun move(from: Int, to: Int) {
        if (from !in items.indices || to !in items.indices) return
        items.add(to, items.removeAt(from))
        notifyItemMoved(from, to)
    }

}
