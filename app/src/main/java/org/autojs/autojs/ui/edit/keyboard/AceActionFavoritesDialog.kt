package org.autojs.autojs.ui.edit.keyboard

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.graphics.drawable.DrawableCompat
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.util.DialogUtils
import org.autojs.autojs.util.DisplayUtils
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R
import kotlin.math.roundToInt

object AceActionFavoritesDialog {

    fun show(
        context: Context,
        onActionSelected: (AceActionHistoryStore.Action) -> Unit,
    ) {
        val entries = AceActionHistoryStore.getRankedEntries(context)
        if (entries.isEmpty()) {
            ViewUtils.showToast(context, R.string.text_ace_action_history_empty)
            return
        }

        val recyclerView = RecyclerView(context).apply {
            layoutManager = LinearLayoutManager(context)
            overScrollMode = View.OVER_SCROLL_IF_CONTENT_SCROLLS
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dialogListHeight(context, entries),
            )
        }
        val adapter = FavoritesAdapter(context, entries.toMutableList(), onActionSelected)
        recyclerView.adapter = adapter
        val dialogContent = createDialogContent(context, recyclerView)

        val dialog = DialogUtils.buildAndShowAdaptive {
            MaterialDialog.Builder(context)
                .customView(dialogContent, false)
                .neutralText(R.string.dialog_button_clear_items)
                .neutralColorRes(R.color.dialog_button_caution)
                .onNeutral { d, _ -> confirmClear(context, d) }
                .negativeText(R.string.dialog_button_cancel)
                .negativeColorRes(R.color.dialog_button_default)
                .onNegative { d, _ -> d.dismiss() }
                .autoDismiss(false)
                .build()
        }
        adapter.dialog = dialog
        attachTouchHelper(context, recyclerView, adapter)
    }

    private fun createDialogContent(context: Context, recyclerView: RecyclerView): View {
        val horizontalPadding = DisplayUtils.dpToPx(24f).roundToInt()
        val titleRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(horizontalPadding, DisplayUtils.dpToPx(18f).roundToInt(), DisplayUtils.dpToPx(8f).roundToInt(), DisplayUtils.dpToPx(6f).roundToInt())
        }
        val title = TextView(context).apply {
            text = context.getString(R.string.text_ace_action_favorites)
            textSize = 20f
            setTextColor(context.getColor(R.color.day_night))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        val help = TextView(context).apply {
            text = context.getString(R.string.text_usage_instructions)
            textSize = 14f
            setTextColor(ThemeColorManager.colorPrimary)
            gravity = android.view.Gravity.CENTER
            minHeight = DisplayUtils.dpToPx(40f).roundToInt()
            setPadding(DisplayUtils.dpToPx(12f).roundToInt(), 0, DisplayUtils.dpToPx(12f).roundToInt(), 0)
            setBackgroundResource(selectableItemBackgroundRes(context))
            setOnClickListener { showUsageInstructions(context) }
        }
        titleRow.addView(title)
        titleRow.addView(help)
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            addView(titleRow)
            addView(recyclerView)
        }
    }

    private fun selectableItemBackgroundRes(context: Context): Int {
        return TypedValue().also {
            context.theme.resolveAttribute(android.R.attr.selectableItemBackground, it, true)
        }.resourceId
    }

    private fun showUsageInstructions(context: Context) {
        DialogUtils.buildAndShowAdaptive {
            MaterialDialog.Builder(context)
                .title(R.string.text_usage_instructions)
                .content(R.string.description_ace_action_favorites)
                .positiveText(R.string.dialog_button_confirm)
                .positiveColorRes(R.color.dialog_button_attraction)
                .build()
        }
    }

    private fun attachTouchHelper(
        context: Context,
        recyclerView: RecyclerView,
        adapter: FavoritesAdapter,
    ) {
        val helper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT,
        ) {
            private val swipePainter = SwipePainter(context, adapter)
            private var moved = false

            override fun isLongPressDragEnabled(): Boolean = true

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder,
            ): Boolean {
                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                if (from == RecyclerView.NO_POSITION || to == RecyclerView.NO_POSITION) {
                    return false
                }
                moved = adapter.move(from, to) || moved
                return moved
            }

            override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(recyclerView, viewHolder)
                if (moved) {
                    adapter.finishDrag()
                    moved = false
                }
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                if (position == RecyclerView.NO_POSITION) {
                    return
                }
                when (direction) {
                    ItemTouchHelper.LEFT -> adapter.confirmDelete(position)
                    ItemTouchHelper.RIGHT -> adapter.togglePinned(position)
                    else -> adapter.notifyItemChanged(position)
                }
            }

            override fun onChildDraw(
                c: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean,
            ) {
                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE) {
                    swipePainter.draw(c, viewHolder, dX)
                }
                super.onChildDraw(c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive)
            }
        })
        helper.attachToRecyclerView(recyclerView)
    }

    private fun confirmClear(context: Context, parentDialog: MaterialDialog) {
        DialogUtils.buildAndShowAdaptive {
            MaterialDialog.Builder(context)
                .title(R.string.text_prompt)
                .content(R.string.text_ace_action_clear_confirm)
                .positiveText(R.string.dialog_button_confirm)
                .positiveColorRes(R.color.dialog_button_caution)
                .onPositive { d, _ ->
                    AceActionHistoryStore.clear(context)
                    d.dismiss()
                    parentDialog.dismiss()
                }
                .negativeText(R.string.dialog_button_cancel)
                .negativeColorRes(R.color.dialog_button_default)
                .build()
        }
    }

    private fun dialogListHeight(context: Context, entries: List<AceActionHistoryStore.Entry>): Int {
        val itemCount = entries.size
        val rowHeightDp = if (entries.any { !it.payloadSummary.isNullOrBlank() }) 58f else 48f
        val rowHeight = DisplayUtils.dpToPx(rowHeightDp).roundToInt()
        val maxHeight = (context.resources.displayMetrics.heightPixels * 0.52f).roundToInt()
        return (rowHeight * itemCount.coerceAtMost(10)).coerceAtMost(maxHeight).coerceAtLeast(rowHeight)
    }

    private class FavoritesAdapter(
        private val context: Context,
        private val entries: MutableList<AceActionHistoryStore.Entry>,
        private val onActionSelected: (AceActionHistoryStore.Action) -> Unit,
    ) : RecyclerView.Adapter<FavoritesAdapter.VH>() {

        var dialog: MaterialDialog? = null
        private var pinnedCount = entries.count { it.pinned }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            return VH(
                LayoutInflater.from(parent.context)
                    .inflate(R.layout.operation_dialog_item, parent, false),
            )
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val entry = entries[position]
            val verticalPadding = DisplayUtils.dpToPx(9f).roundToInt()
            holder.itemView.setPadding(
                holder.itemView.paddingLeft,
                verticalPadding,
                holder.itemView.paddingRight,
                verticalPadding,
            )
            holder.title.text = entry.label
            holder.usageCount.visibility = View.VISIBLE
            holder.usageCount.text = "<${entry.usageCount}>"
            val payloadSummary = entry.payloadSummary
            if (payloadSummary.isNullOrBlank()) {
                holder.subtitle.visibility = View.GONE
            } else {
                holder.subtitle.visibility = View.VISIBLE
                holder.subtitle.text = payloadSummary
            }
            holder.icon.setImageResource(
                if (entry.pinned) R.drawable.ic_thumbtacks else R.drawable.ic_ali_fx,
            )
            holder.icon.imageTintList = ColorStateList.valueOf(context.getColor(R.color.day_night))
            holder.itemView.setOnClickListener {
                onActionSelected(
                    AceActionHistoryStore.Action(
                        id = entry.id,
                        label = entry.label,
                        mutatesText = entry.mutatesText,
                        payloadJson = entry.payloadJson,
                        payloadSummary = entry.payloadSummary,
                    ),
                )
                dialog?.dismiss()
            }
        }

        override fun getItemCount(): Int = entries.size

        fun entryAt(position: Int): AceActionHistoryStore.Entry? = entries.getOrNull(position)

        fun move(from: Int, to: Int): Boolean {
            if (from !in entries.indices || to !in entries.indices || from == to) {
                return false
            }
            val fromPinned = from < pinnedCount
            val toPinned = to < pinnedCount
            if (fromPinned && !toPinned) {
                pinnedCount = (pinnedCount - 1).coerceAtLeast(0)
            } else if (!fromPinned && toPinned) {
                pinnedCount = (pinnedCount + 1).coerceAtMost(entries.size)
            }
            val moved = entries.removeAt(from)
            entries.add(to, moved)
            syncPinnedState()
            notifyItemMoved(from, to)
            return true
        }

        fun persistOrder() {
            AceActionHistoryStore.applyFavoritesOrder(
                context,
                entries.map { it.id },
                pinnedCount,
            )
        }

        fun finishDrag() {
            persistOrder()
            replace(AceActionHistoryStore.getRankedEntries(context))
        }

        fun togglePinned(position: Int) {
            val entry = entries.getOrNull(position) ?: return
            notifyItemChanged(position)
            persistOrder()
            AceActionHistoryStore.setPinned(context, entry.id, !entry.pinned)
            replace(AceActionHistoryStore.getRankedEntries(context))
        }

        fun confirmDelete(position: Int) {
            val entry = entries.getOrNull(position) ?: return
            notifyItemChanged(position)
            DialogUtils.buildAndShowAdaptive {
                MaterialDialog.Builder(context)
                    .title(R.string.text_prompt)
                    .content(context.getString(R.string.text_ace_action_delete_confirm, entry.label))
                    .positiveText(R.string.dialog_button_confirm)
                    .positiveColorRes(R.color.dialog_button_caution)
                    .onPositive { d, _ ->
                        AceActionHistoryStore.delete(context, entry.id)
                        replace(AceActionHistoryStore.getRankedEntries(context))
                        if (entries.isEmpty()) {
                            dialog?.dismiss()
                        }
                        d.dismiss()
                    }
                    .negativeText(R.string.dialog_button_cancel)
                    .negativeColorRes(R.color.dialog_button_default)
                    .build()
            }
        }

        private fun replace(next: List<AceActionHistoryStore.Entry>) {
            entries.clear()
            entries += next
            pinnedCount = entries.count { it.pinned }
            notifyDataSetChanged()
        }

        private fun syncPinnedState() {
            val now = System.currentTimeMillis()
            for (index in entries.indices) {
                val entry = entries[index]
                val pinned = index < pinnedCount
                entries[index] = entry.copy(
                    pinned = pinned,
                    pinnedAt = if (pinned) entry.pinnedAt.takeIf { it > 0L } ?: now else 0L,
                )
            }
        }

        class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val icon: ImageView = itemView.findViewById(R.id.icon)
            val title: TextView = itemView.findViewById(R.id.title)
            val usageCount: TextView = itemView.findViewById(R.id.usage_count)
            val subtitle: TextView = itemView.findViewById(R.id.subtitle)
        }
    }

    private class SwipePainter(
        private val context: Context,
        private val adapter: FavoritesAdapter,
    ) {
        private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            textSize = DisplayUtils.dpToPx(14f)
        }
        private val iconBounds = Rect()

        fun draw(canvas: Canvas, viewHolder: RecyclerView.ViewHolder, dX: Float) {
            val itemView = viewHolder.itemView
            if (dX > 0f) {
                drawAction(
                    canvas = canvas,
                    itemView = itemView,
                    left = itemView.left,
                    right = itemView.left + dX.toInt(),
                    iconRes = R.drawable.ic_thumbtacks,
                    label = pinLabel(viewHolder.bindingAdapterPosition),
                    color = Color.rgb(46, 125, 50),
                    alignStart = true,
                )
            } else if (dX < 0f) {
                drawAction(
                    canvas = canvas,
                    itemView = itemView,
                    left = itemView.right + dX.toInt(),
                    right = itemView.right,
                    iconRes = R.drawable.ic_key_store_delete,
                    label = context.getString(R.string.text_delete),
                    color = Color.rgb(198, 40, 40),
                    alignStart = false,
                )
            }
        }

        private fun drawAction(
            canvas: Canvas,
            itemView: View,
            left: Int,
            right: Int,
            iconRes: Int,
            label: String,
            color: Int,
            alignStart: Boolean,
        ) {
            backgroundPaint.color = color
            canvas.drawRect(left.toFloat(), itemView.top.toFloat(), right.toFloat(), itemView.bottom.toFloat(), backgroundPaint)

            val iconSize = DisplayUtils.dpToPx(22f).roundToInt()
            val iconTop = itemView.top + (itemView.height - iconSize) / 2
            val iconLeft = if (alignStart) {
                itemView.left + DisplayUtils.dpToPx(20f).roundToInt()
            } else {
                itemView.right - DisplayUtils.dpToPx(20f).roundToInt() - iconSize
            }
            iconBounds.set(iconLeft, iconTop, iconLeft + iconSize, iconTop + iconSize)
            context.getDrawable(iconRes)?.mutate()?.let { drawable ->
                DrawableCompat.setTint(drawable, Color.WHITE)
                drawable.bounds = iconBounds
                drawable.draw(canvas)
            }

            val textY = itemView.top + itemView.height / 2f - (textPaint.descent() + textPaint.ascent()) / 2f
            val textX = if (alignStart) {
                iconBounds.right + DisplayUtils.dpToPx(12f)
            } else {
                iconBounds.left - DisplayUtils.dpToPx(12f) - textPaint.measureText(label)
            }
            canvas.drawText(label, textX, textY, textPaint)
        }

        private fun pinLabel(position: Int): String {
            val entry = adapter.entryAt(position)
            return context.getString(
                if (entry?.pinned == true) R.string.text_ace_action_unpin else R.string.text_ace_action_pin,
            )
        }
    }
}
