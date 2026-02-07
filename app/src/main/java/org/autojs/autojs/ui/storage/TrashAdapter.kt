package org.autojs.autojs.ui.storage

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.autojs.autojs.storage.history.TrashEntities
import org.autojs.autojs6.databinding.ItemTrashBinding

/**
 * Created by JetBrains AI Assistant (GPT-5.2) on Feb 4, 2026.
 */
internal class TrashAdapter(
    private val onClick: (TrashEntities.TrashItem) -> Unit,
    private val onLongClick: (TrashEntities.TrashItem) -> Unit,
) : RecyclerView.Adapter<TrashViewHolder>() {

    private var items: List<TrashEntities.TrashItem> = emptyList()

    fun submit(newItems: List<TrashEntities.TrashItem>) {
        items = newItems
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TrashViewHolder {
        val binding = ItemTrashBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TrashViewHolder(binding, onClick, onLongClick)
    }

    override fun onBindViewHolder(holder: TrashViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size
}
