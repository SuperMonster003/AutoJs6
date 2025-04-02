package org.autojs.autojs.theme.app

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.theme.app.ColorLibrariesActivity.Companion.PresetColorLibrary
import org.autojs.autojs6.R

@SuppressLint("NotifyDataSetChanged")
class ColorLibraryAdapter(
    private var libraries: List<PresetColorLibrary>,
    private val onItemClick: (PresetColorLibrary) -> Unit,
) : RecyclerView.Adapter<ColorLibraryViewHolder>() {

    fun updateData(newLibraries: List<PresetColorLibrary>) {
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
        holder.itemView.setOnClickListener {
            when {
                library.isIntelligent -> MaterialDialog.Builder(holder.itemView.context)
                    .title(R.string.text_under_development_title)
                    .content(R.string.text_under_development_content)
                    .positiveText(R.string.dialog_button_dismiss)
                    .positiveColorRes(R.color.dialog_button_default)
                    .show()
                else -> onItemClick(library)
            }
        }
    }

    override fun getItemCount() = libraries.size

}