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

    private var column: LinearLayout

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
        layoutDirection = View.LAYOUT_DIRECTION_LTR
        gravity = Gravity.CENTER_VERTICAL
        val symbols = LinearLayout(context).apply { orientation = HORIZONTAL }
        addView(HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            isFillViewport = true
            addView(symbols, ViewGroup.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        }, LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f))
        column = newColumn(symbols)
        keyButton(Key.ESC)
        keyButton(Key.TAB)
        column = newColumn(symbols)
        textButton("/")
        ctrlButton = button("CTRL") { listener?.onToggleCtrl() }
        column = newColumn(symbols)
        textButton("|")
        altButton = button("ALT") { listener?.onToggleAlt() }
        listOf("-" to "`", "~" to "\\", "$" to "_", "=" to ":", "'" to "\"", "(" to ")", "[" to "]", "{" to "}", "&" to ";", "*" to "?").forEach { (top, bottom) ->
            column = newColumn(symbols)
            textButton(top)
            textButton(bottom)
        }
        column = newColumn(symbols)
        keyButton(Key.DELETE)
        textButton("!")
        addView(View(context).apply { setBackgroundColor(context.getColor(R.color.divider)) }, LayoutParams(dp(1), LayoutParams.MATCH_PARENT))
        val navigation = LinearLayout(context).apply { orientation = HORIZONTAL }
        addView(navigation, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
        listOf(Key.HOME to Key.LEFT, Key.UP to Key.DOWN, Key.END to Key.RIGHT, Key.PAGE_UP to Key.PAGE_DOWN).forEach { (top, bottom) ->
            column = newColumn(navigation)
            keyButton(top)
            keyButton(bottom)
        }
        setModifiers(ctrl = false, alt = false)
    }

    private fun newColumn(parent: LinearLayout) = LinearLayout(context).apply {
        orientation = VERTICAL
        gravity = Gravity.CENTER_VERTICAL
        parent.addView(this, LayoutParams(dp(44), LayoutParams.WRAP_CONTENT))
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

    private fun keyButton(key: Key) = button(key.label.uppercase(java.util.Locale.ROOT)) { listener?.onKey(key) }

    private fun textButton(text: String) = button(text) { listener?.onText(text) }

    private fun button(label: String, onClick: () -> Unit): TextView = TextView(context).apply {
        text = label
        gravity = Gravity.CENTER
        isSingleLine = true
        isClickable = true
        isFocusable = false
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        androidx.core.widget.TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(this, 9, 12, 1, TypedValue.COMPLEX_UNIT_SP)
        setTextColor(context.getColor(R.color.day_night))
        setBackgroundResource(idleBackground)
        contentDescription = label
        minWidth = dp(40)
        val horizontal = dp(2)
        setPadding(horizontal, 0, horizontal, 0)
        layoutParams = LinearLayout.LayoutParams(dp(44), dp(40))
        setOnClickListener { onClick() }
        column.addView(this)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

}
