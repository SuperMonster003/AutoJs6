package org.autojs.autojs.ui.terminal

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import org.autojs.autojs.core.terminal.TerminalKeySequences.Key
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.util.ColorUtils
import org.autojs.autojs6.R

/** Two rows of shell keys with a fixed navigation pad, matching the editor keyboard. */
class TerminalToolbarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : LinearLayout(context, attrs) {

    interface Listener {
        fun onKey(key: Key)
        fun onText(text: String)
        fun onToggleCtrl()
        fun onToggleAlt()
    }

    var listener: Listener? = null

    private var row: LinearLayout

    private val ctrlButton: TextView
    private val altButton: TextView
    private val idleBackground: Int = context.obtainStyledAttributes(intArrayOf(android.R.attr.selectableItemBackgroundBorderless)).let { array ->
        try {
            array.getResourceId(0, 0)
        } finally {
            // TypedArray only implements AutoCloseable since API 31, so recycle by hand.
            // zh-CN: TypedArray 自 API 31 才实现 AutoCloseable, 因此手动回收.
            array.recycle()
        }
    }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val symbols = LinearLayout(context).apply { orientation = VERTICAL }
        addView(HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            isFillViewport = true
            addView(symbols, ViewGroup.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        row = newRow(symbols)
        ctrlButton = button("Ctrl") { listener?.onToggleCtrl() }
        altButton = button("Alt") { listener?.onToggleAlt() }
        listOf("-", "/", "|", "~", "$", "_", "=", ":").forEach(::textButton)
        row = newRow(symbols)
        listOf(Key.HOME, Key.END, Key.PAGE_UP, Key.PAGE_DOWN, Key.DELETE).forEach(::keyButton)
        listOf("\\", "'", "\"", "(", ")").forEach(::textButton)
        addView(View(context).apply { setBackgroundColor(context.getColor(R.color.divider)) }, LayoutParams(dp(1), LayoutParams.MATCH_PARENT))
        val navigation = LinearLayout(context).apply { orientation = VERTICAL }
        addView(navigation, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        row = newRow(navigation)
        listOf(Key.ESC, Key.UP, Key.TAB).forEach(::keyButton)
        row = newRow(navigation)
        listOf(Key.LEFT, Key.DOWN, Key.RIGHT).forEach(::keyButton)
        setModifiers(ctrl = false, alt = false)
    }

    private fun newRow(parent: LinearLayout) = LinearLayout(context).apply {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        parent.addView(this, LayoutParams(LayoutParams.WRAP_CONTENT, dp(40)))
    }

    fun setModifiers(ctrl: Boolean, alt: Boolean) {
        applyModifierState(ctrlButton, ctrl)
        applyModifierState(altButton, alt)
    }

    private fun applyModifierState(button: TextView, active: Boolean) {
        button.isSelected = active
        if (active) {
            val accent = ThemeColorManager.colorPrimary
            button.background = ColorDrawable(ColorUtils.applyAlpha(accent, 0.22))
            button.setTextColor(ColorUtils.adjustThemeColorForContrast(context.getColor(R.color.window_background), 4.5))
            button.setTypeface(null, Typeface.BOLD)
        } else {
            button.setBackgroundResource(idleBackground)
            button.setTextColor(context.getColor(R.color.day_night))
            button.setTypeface(null, Typeface.NORMAL)
        }
    }

    private fun keyButton(key: Key) = button(key.label) { listener?.onKey(key) }

    private fun textButton(text: String) = button(text) { listener?.onText(text) }

    private fun button(label: String, onClick: () -> Unit): TextView = TextView(context).apply {
        text = label
        gravity = Gravity.CENTER
        isSingleLine = true
        isClickable = true
        isFocusable = false
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setTextColor(context.getColor(R.color.day_night))
        setBackgroundResource(idleBackground)
        contentDescription = label
        minWidth = dp(40)
        val horizontal = dp(4)
        setPadding(horizontal, 0, horizontal, 0)
        layoutParams = LinearLayout.LayoutParams(dp(44), dp(40))
        setOnClickListener { onClick() }
        row.addView(this)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

}
