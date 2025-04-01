package org.autojs.autojs.theme.app

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import org.autojs.autojs6.R

@SuppressLint("NotifyDataSetChanged")
class ColorLibraryAdapter(
    private var libraries: List<ColorLibrariesActivity.Companion.PresetColorLibrary>,
    private val onItemClick: (ColorLibrariesActivity.Companion.PresetColorLibrary) -> Unit,
) : RecyclerView.Adapter<ColorLibraryViewHolder>() {

    fun updateData(newLibraries: List<ColorLibrariesActivity.Companion.PresetColorLibrary>) {
        libraries = newLibraries
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ColorLibraryViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.mt_color_libraries_recycler_view_item, parent, false)
        return ColorLibraryViewHolder(view)
    }

    override fun onBindViewHolder(holder: ColorLibraryViewHolder, position: Int) {
        val library = libraries[position]
        holder.bind(library)
        holder.itemView.setOnClickListener { onItemClick(library) }
    }

    override fun getItemCount() = libraries.size

}