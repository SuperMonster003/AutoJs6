package org.autojs.autojs.ui.terminal

import android.graphics.Typeface
import android.os.SystemClock
import android.text.Selection
import android.text.Spannable
import android.util.TypedValue
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ScrollView
import androidx.appcompat.widget.AppCompatTextView
import jackpal.androidterm.emulatorview.TerminalSelectionSnapshot
import org.autojs.autojs.util.ClipboardUtils
import org.autojs.autojs.util.ColorUtils
import org.autojs.autojs6.R

/** Native selection handles on a frozen transcript, restored to the live terminal on dismissal. */
class TerminalTextSelection(private val terminal: TerminalEmulatorView, private val onClosed: () -> Unit) {
    private val context = terminal.context
    private val parent = terminal.parent as FrameLayout
    private val scroll = ScrollView(context)
    private var actionMode: ActionMode? = null
    private var dismissed = false

    fun show(x: Float, y: Float) {
        val session = terminal.termSession ?: return dismiss()
        if (!session.isRunning) return dismiss()
        val snapshot = TerminalSelectionSnapshot.capture(session, terminal.visibleColumns, terminal.transcriptTopLine)
        val text = AppCompatTextView(context).apply {
            id = R.id.terminal_selection
            typeface = Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, terminal.textSizeSp.toFloat())
            setTextColor(context.getColor(R.color.day_night))
            setBackgroundColor(context.getColor(R.color.window_background))
            highlightColor = ColorUtils.applyAlpha(ColorUtils.adjustThemeColorForContrast(context.getColor(R.color.window_background), 4.5), .3)
            includeFontPadding = false
            setHorizontallyScrolling(true)
            setTextIsSelectable(true)
            setText(snapshot.text, android.widget.TextView.BufferType.SPANNABLE)
            customSelectionActionModeCallback = object : ActionMode.Callback {
                override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                    actionMode = mode
                    menu.clear()
                    menu.add(Menu.NONE, android.R.id.copy, 0, R.string.text_copy).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                    menu.add(Menu.NONE, android.R.id.selectAll, 1, R.string.text_select_all).setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                    return true
                }

                override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = false

                override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean = when (item.itemId) {
                    android.R.id.copy -> {
                        ClipboardUtils.setClip(context, snapshot.selectedText(selectionStart, selectionEnd))
                        mode.finish()
                        true
                    }
                    android.R.id.selectAll -> {
                        Selection.setSelection(this@apply.text as Spannable, 0, this@apply.length())
                        mode.invalidate()
                        true
                    }
                    else -> false
                }

                override fun onDestroyActionMode(mode: ActionMode) {
                    actionMode = null
                    post { dismiss() }
                }
            }
        }
        scroll.setBackgroundColor(context.getColor(R.color.window_background))
        scroll.isFillViewport = true
        scroll.addView(text, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        parent.addView(scroll, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        text.post {
            if (dismissed) return@post
            val layout = text.layout ?: return@post
            val top = layout.getLineTop(snapshot.topLine.coerceAtMost(layout.lineCount - 1))
            scroll.scrollTo(0, top)
            text.requestFocus()
            val now = SystemClock.uptimeMillis()
            val localY = (y + scroll.scrollY).coerceIn(0f, (text.height - 1).toFloat())
            for (action in listOf(MotionEvent.ACTION_DOWN, MotionEvent.ACTION_UP)) {
                val event = MotionEvent.obtain(now, now, action, x, localY, 0)
                text.dispatchTouchEvent(event)
                event.recycle()
                // End the synthetic long press with an UP so Android shows its handles and toolbar.
                if (action == MotionEvent.ACTION_DOWN) text.performLongClick()
            }
        }
    }

    fun dismiss() {
        if (dismissed) return
        dismissed = true
        actionMode?.finish()
        parent.removeView(scroll)
        terminal.requestFocus()
        onClosed()
    }
}
