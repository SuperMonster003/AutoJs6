package org.autojs.autojs.ui.edit.editor.encoding

import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.AppCompatRadioButton
import androidx.core.widget.ImageViewCompat
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.theme.ThemeColorHelper
import org.autojs.autojs6.R

object EditorEncodingDialog {

    fun build(
        context: Context,
        candidates: List<EditorEncodingCandidate>,
        onSelected: (EditorEncodingCandidate) -> Unit,
    ): MaterialDialog {
        var dialog: MaterialDialog? = null
        val adapter = Adapter(context, candidates) { candidate ->
            dialog?.dismiss()
            onSelected(candidate)
        }
        return MaterialDialog.Builder(context)
            .title(R.string.text_file_encoding)
            .adapter(adapter, null)
            .negativeText(R.string.dialog_button_cancel)
            .negativeColorRes(R.color.dialog_button_default)
            .build()
            .also { dialog = it }
    }

    private class Adapter(
        private val context: Context,
        private val candidates: List<EditorEncodingCandidate>,
        private val onSelected: (EditorEncodingCandidate) -> Unit,
    ) : RecyclerView.Adapter<Adapter.ViewHolder>() {

        private val choiceTint = ThemeColorHelper.getThemeColorStateList(context)
        private val warningTint = ColorStateList.valueOf(context.getColor(R.color.editor_encoding_risk_warning))
        private val errorTint = ColorStateList.valueOf(context.getColor(R.color.editor_encoding_risk_error))

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.dialog_editor_encoding_item, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val candidate = candidates[position]
            val subtitle = subtitle(candidate)
            holder.title.text = candidate.charset.name()
            holder.subtitle.text = subtitle
            holder.radio.buttonTintList = choiceTint
            holder.radio.isChecked = candidate.isCurrent
            bindRisk(holder.riskIcon, candidate.listRisk)
            holder.itemView.contentDescription = listOfNotNull(
                candidate.charset.name(),
                subtitle,
                when (candidate.listRisk) {
                    EditorEncodingRisk.SAFE -> null
                    EditorEncodingRisk.WARNING -> context.getString(R.string.text_encoding_risk_warning)
                    EditorEncodingRisk.ERROR -> context.getString(R.string.text_encoding_risk_error)
                },
            ).joinToString(". ")
            holder.itemView.setOnClickListener { onSelected(candidate) }
        }

        override fun getItemCount(): Int = candidates.size

        private fun subtitle(candidate: EditorEncodingCandidate): String = buildList {
            if (candidate.isCurrent) add(context.getString(R.string.text_current))
            candidate.detectionConfidence?.let { confidence ->
                add(context.getString(R.string.text_encoding_detected_with_confidence, confidence))
            }
            add(
                context.getString(
                    when {
                        candidate.convertRisk == EditorEncodingRisk.ERROR -> R.string.text_encoding_cannot_represent_current_text
                        candidate.reloadRisk == EditorEncodingRisk.ERROR -> R.string.text_encoding_cannot_decode_file
                        candidate.listRisk == EditorEncodingRisk.WARNING -> R.string.text_encoding_may_change_content
                        else -> R.string.text_encoding_compatible
                    },
                ),
            )
        }.joinToString(" · ")

        private fun bindRisk(icon: ImageView, risk: EditorEncodingRisk) {
            when (risk) {
                EditorEncodingRisk.SAFE -> {
                    icon.visibility = View.INVISIBLE
                }
                EditorEncodingRisk.WARNING -> {
                    icon.visibility = View.VISIBLE
                    icon.setImageResource(R.drawable.ic_warning_black_48dp)
                    ImageViewCompat.setImageTintList(icon, warningTint)
                }
                EditorEncodingRisk.ERROR -> {
                    icon.visibility = View.VISIBLE
                    icon.setImageResource(R.drawable.ic_error_black_48dp)
                    ImageViewCompat.setImageTintList(icon, errorTint)
                }
            }
            icon.contentDescription = null
        }

        private class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val riskIcon: ImageView = view.findViewById(R.id.risk_icon)
            val title: TextView = view.findViewById(R.id.title)
            val subtitle: TextView = view.findViewById(R.id.subtitle)
            val radio: AppCompatRadioButton = view.findViewById(R.id.radio_button)
        }
    }
}
