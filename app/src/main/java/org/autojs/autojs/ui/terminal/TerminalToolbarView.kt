package org.autojs.autojs.ui.terminal

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import org.autojs.autojs.core.terminal.TerminalKeySequences.Key
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.util.ColorUtils
import org.autojs.autojs6.R

/**
 * Horizontal key bar above the soft keyboard: Esc, Tab, a few shell punctuation keys, sticky
 * Ctrl / Alt, cursor and editing keys. Modifier highlighting follows the theme color.
 *
 * zh-CN: 软键盘上方的横向按键栏: Esc, Tab, 常用 shell 标点, 粘滞 Ctrl / Alt, 光标与编辑键. 修饰键高亮跟随主题色.
 */
class TerminalToolbarView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
) : HorizontalScrollView(context, attrs) {

    interface Listener {
        fun onKey(key: Key)
        fun onText(text: String)
        fun onToggleCtrl()
        fun onToggleAlt()
    }

    var listener: Listener? = null

    private val container = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
    }

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
        isHorizontalScrollBarEnabled = false
        isFillViewport = true
        addView(container, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        keyButton(Key.ESC)
        keyButton(Key.TAB)
        textButton("-")
        textButton("/")
        textButton("|")
        ctrlButton = button("Ctrl") { listener?.onToggleCtrl() }
        altButton = button("Alt") { listener?.onToggleAlt() }
        keyButton(Key.UP)
        keyButton(Key.LEFT)
        keyButton(Key.DOWN)
        keyButton(Key.RIGHT)
        keyButton(Key.HOME)
        keyButton(Key.END)
        keyButton(Key.PAGE_UP)
        keyButton(Key.PAGE_DOWN)
        keyButton(Key.DELETE)
        setModifiers(ctrl = false, alt = false)
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
        minWidth = dp(44)
        val horizontal = dp(10)
        setPadding(horizontal, 0, horizontal, 0)
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(40))
        setOnClickListener { onClick() }
        container.addView(this)
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density + 0.5f).toInt()

}
