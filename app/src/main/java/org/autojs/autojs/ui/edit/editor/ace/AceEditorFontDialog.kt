package org.autojs.autojs.ui.edit.editor.ace

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.AppCompatRadioButton
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.theme.ThemeColorHelper
import org.autojs.autojs6.R

object AceEditorFontDialog {

    fun build(
        context: Context,
        currentFont: AceEditorFont,
        onConfirmed: (AceEditorFont) -> Unit,
    ): MaterialDialog {
        val adapter = Adapter(context, AceEditorFont.entries.toList(), currentFont)
        return MaterialDialog.Builder(context)
            .title(R.string.text_ace_editor_font)
            .adapter(adapter, null)
            .negativeText(R.string.dialog_button_cancel)
            .negativeColorRes(R.color.dialog_button_default)
            .positiveText(R.string.dialog_button_confirm)
            .positiveColorRes(R.color.dialog_button_attraction)
            .onPositive { _, _ -> onConfirmed(adapter.selectedFont) }
            .build()
    }

    private class Adapter(
        private val context: Context,
        private val fonts: List<AceEditorFont>,
        currentFont: AceEditorFont,
    ) : RecyclerView.Adapter<Adapter.ViewHolder>() {

        private val choiceTint = ThemeColorHelper.getThemeColorStateList(context)

        var selectedIndex: Int = fonts.indexOf(currentFont).coerceAtLeast(0)
            private set

        val selectedFont: AceEditorFont
            get() = fonts[selectedIndex]

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.dialog_ace_editor_font_item, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val font = fonts[position]
            holder.title.text = font.displayName(context)
            holder.subtitle.text = font.author
            holder.radioButton.buttonTintList = choiceTint
            holder.radioButton.isChecked = position == selectedIndex
            holder.itemView.setOnClickListener {
                select(position)
            }
        }

        override fun getItemCount(): Int = fonts.size

        private fun select(position: Int) {
            if (position == selectedIndex) {
                return
            }
            val previousIndex = selectedIndex
            selectedIndex = position
            notifyItemChanged(previousIndex)
            notifyItemChanged(selectedIndex)
        }

        private class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val radioButton: AppCompatRadioButton = itemView.findViewById(R.id.radio_button)
            val title: TextView = itemView.findViewById(R.id.title)
            val subtitle: TextView = itemView.findViewById(R.id.subtitle)
        }
    }
}
