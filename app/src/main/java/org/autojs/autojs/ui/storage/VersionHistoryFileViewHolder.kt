package org.autojs.autojs.ui.storage

import android.text.TextUtils.TruncateAt
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.autojs.autojs.pio.PFiles
import org.autojs.autojs.storage.history.HistoryEntities
import org.autojs.autojs6.R
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Created by JetBrains AI Assistant (GPT-5.2) on Feb 5, 2026.
 * Modified by JetBrains AI Assistant (GPT-5.2) as of Feb 6, 2026.
 * Modified by SuperMonster003 as of Feb 7, 2026.
 */
internal class VersionHistoryFileViewHolder(
    itemView: View,
    private val onClick: (HistoryEntities.FileEntry) -> Unit,
    private val onLongClick: (HistoryEntities.FileEntry) -> Unit,
) : RecyclerView.ViewHolder(itemView) {

    private val titleView: TextView = (itemView as LinearLayout).getChildAt(0) as TextView
    private val subtitleView: TextView = (itemView as LinearLayout).getChildAt(1) as TextView

    private var boundItem: HistoryEntities.FileEntry? = null

    fun bind(
        item: HistoryEntities.FileEntry,
        totalSizeBytesOrNull: Long?,
        revisionCountOrNull: Long?,
    ) {
        boundItem = item

        titleView.text = File(item.displayPath).name

        val context = itemView.context

        val fmt = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val timeText = fmt.format(Date(item.lastSeenAt))

        val sizeText = totalSizeBytesOrNull
            ?.coerceAtLeast(0L)?.let { PFiles.formatSizeWithUnit(it) }
            ?: context.getString(R.string.text_half_ellipsis)

        val countText = revisionCountOrNull
            ?.coerceAtLeast(0L)?.toString()
            ?: context.getString(R.string.text_half_ellipsis)

        subtitleView.text = buildString {
            append(timeText)
            append("  |  ")
            append("< $countText >")
            append("  |  ")
            append(sizeText)
            append("\n")
            append(item.displayPath)
        }

        itemView.setOnClickListener {
            boundItem?.let(onClick)
        }
        itemView.setOnLongClickListener {
            boundItem?.let(onLongClick)
            true
        }
    }

    companion object {

        fun create(
            parent: ViewGroup,
            onClick: (HistoryEntities.FileEntry) -> Unit,
            onLongClick: (HistoryEntities.FileEntry) -> Unit,
        ): VersionHistoryFileViewHolder {
            val ctx = parent.context

            val root = LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(32, 24, 32, 24)
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
                gravity = Gravity.CENTER_VERTICAL

                // Use theme selectable background to show press/ripple feedback.
                // zh-CN: 使用主题的可选择背景, 显示按压态或 ripple 点击反馈.
                with(ctx.theme) {
                    val typedValue = TypedValue()
                    resolveAttribute(android.R.attr.selectableItemBackground, typedValue, true)
                    setBackgroundResource(typedValue.resourceId)
                }

                // Ensure this view can show pressed state.
                // zh-CN: 确保该 view 能显示 pressed 状态.
                isClickable = true
                isFocusable = true
                isLongClickable = true
            }

            val title = TextView(ctx).apply {
                textSize = 16f
                setTextColor(context.getColor(R.color.day_night))
                maxLines = 1
                ellipsize = TruncateAt.MIDDLE
            }

            val subtitle = TextView(ctx).apply {
                textSize = 12f
                setTextColor(context.getColor(R.color.day_night_alpha_70))
                maxLines = 3
                ellipsize = TruncateAt.END
                alpha = 0.75f
            }

            root.addView(title)
            root.addView(subtitle)

            return VersionHistoryFileViewHolder(root, onClick, onLongClick)
        }
    }
}
