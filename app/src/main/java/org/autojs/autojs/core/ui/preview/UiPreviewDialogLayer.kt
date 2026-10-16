package org.autojs.autojs.core.ui.preview

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.view.ContextThemeWrapper
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import com.afollestad.materialdialogs.MaterialDialog
import com.afollestad.materialdialogs.MaterialDialogPresenter
import com.afollestad.materialdialogs.internal.MDRootLayout

/**
 * The dialogs of an interactive-preview run, shown inside the session's root (preview contract v4, 3-Block Compose
 * roadmap P11.5): a run's `dialogs.*` and `dialogs.build` dialogs are built with [UiPreviewDialogContext], whose
 * [MaterialDialogPresenter] moves each dialog's view into this layer instead of opening a window (an overlay window
 * would need a permission and would cover the client's app outside the preview). The layer stacks the dialogs over a
 * scrim, sizes them like a dialog window of the session's size, cancels the top one on an outside touch or the back
 * key when it allows that, and drops them all when the run ends. Everything runs on the main thread.
 *
 * zh-CN: 交互预览运行的对话框, 显示在会话根容器之内 (预览契约 v4, 3-Block Compose 路线图 P11.5): 运行的 `dialogs.*` 与 `dialogs.build` 对话框以
 * [UiPreviewDialogContext] 构建, 其 [MaterialDialogPresenter] 把对话框的视图移入本层而不是打开窗口 (悬浮窗需要权限, 且会遮住预览之外的客户端界面).
 * 本层在遮罩上叠放对话框, 按会话尺寸的对话框窗口确定大小, 在顶层对话框允许时于点击外部或返回键时取消它, 并在运行结束时全部移除. 全部在主线程运行.
 */
@SuppressLint("ViewConstructor")
class UiPreviewDialogLayer(context: Context) : FrameLayout(context), MaterialDialogPresenter {

    private class Entry(val dialog: MaterialDialog, val card: Card)

    private val entries = ArrayList<Entry>()
    private val maxWidth = resources.getDimensionPixelSize(com.afollestad.materialdialogs.R.dimen.md_dialog_max_width)
    private val horizontalMargin = resources.getDimensionPixelSize(com.afollestad.materialdialogs.R.dimen.md_dialog_horizontal_margin)
    private val verticalMargin = resources.getDimensionPixelSize(com.afollestad.materialdialogs.R.dimen.md_dialog_vertical_margin)

    init {
        visibility = GONE
        background = ColorDrawable(SCRIM_COLOR)
    }

    /** The dialogs shown, bottom first. */
    val dialogs: List<MaterialDialog>
        get() = entries.map { it.dialog }

    override fun presentDialog(dialog: MaterialDialog): Boolean {
        if (entries.any { it.dialog === dialog }) return true
        val view = dialog.view ?: return false
        (view.parent as? ViewGroup)?.removeView(view)
        val card = Card(context, dialog)
        card.background = windowBackgroundOf(dialog)
        card.addView(view, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(card, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER))
        entries.add(Entry(dialog, card))
        visibility = VISIBLE
        return true
    }

    override fun dismissPresentedDialog(dialog: MaterialDialog) {
        val entry = entries.firstOrNull { it.dialog === dialog } ?: return
        entries.remove(entry)
        entry.card.removeAllViews()
        removeView(entry.card)
        if (entries.isEmpty()) visibility = GONE
    }

    /** The back key: the top dialog takes it (and is cancelled when it allows that); false without a dialog. */
    fun onBackPressed(): Boolean {
        val top = entries.lastOrNull()?.dialog ?: return false
        if (top.cancelsOnBack()) top.cancel()
        return true
    }

    /** Drops every dialog without notifying anyone, like the windows of a finished Activity; the run is gone. */
    fun clear() {
        entries.forEach { it.card.removeAllViews() }
        entries.clear()
        removeAllViews()
        visibility = GONE
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        // A touch that reaches the layer itself is outside every card.
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            entries.lastOrNull()?.dialog?.takeIf { it.cancelsOnTouchOutside() }?.cancel()
        }
        return true
    }

    /** The background the dialog gave its window (its color with rounded corners), or the theme's floating color. */
    private fun windowBackgroundOf(dialog: MaterialDialog): Drawable {
        dialog.window?.peekDecorView()?.background?.constantState?.newDrawable(resources)?.mutate()?.let { return it }
        val attrs = dialog.context.obtainStyledAttributes(intArrayOf(android.R.attr.colorBackgroundFloating))
        return try {
            ColorDrawable(attrs.getColor(0, Color.WHITE))
        } finally {
            attrs.recycle()
        }
    }

    /** Holds one dialog's view at the width a dialog window of this size gets, and caps its height the same way. */
    private inner class Card(context: Context, private val dialog: MaterialDialog) : FrameLayout(context) {

        init {
            isClickable = true
            clipToOutline = true
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val available = MeasureSpec.getSize(widthMeasureSpec)
            val availableHeight = MeasureSpec.getSize(heightMeasureSpec)
            val width = minOf(maxWidth, available - horizontalMargin * 2).coerceAtLeast(minOf(available, 1))
            val height = (availableHeight - verticalMargin * 2).coerceAtLeast(minOf(availableHeight, 1))
            (dialog.view as? MDRootLayout)?.setMaxHeight(height)
            super.onMeasure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(height, MeasureSpec.AT_MOST))
        }
    }

    companion object {
        /** The dim of a dialog window (`backgroundDimAmount` 0.6 of the platform's dialog themes). */
        private val SCRIM_COLOR = Color.argb(153, 0, 0, 0)
    }
}

/**
 * The context the dialogs of a preview run are built with: the session's configuration (density, night mode, locale,
 * font scale) under the light AppCompat theme `dialogs` uses, presenting through the session's [UiPreviewDialogLayer].
 */
class UiPreviewDialogContext(base: Context, private val layer: UiPreviewDialogLayer) :
    ContextThemeWrapper(base, androidx.appcompat.R.style.Theme_AppCompat_Light), MaterialDialogPresenter {

    override fun presentDialog(dialog: MaterialDialog): Boolean = layer.presentDialog(dialog)

    override fun dismissPresentedDialog(dialog: MaterialDialog) = layer.dismissPresentedDialog(dialog)
}
