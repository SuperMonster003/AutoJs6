package org.autojs.autojs.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import org.autojs.autojs6.R

/**
 * Created by SuperMonster003 on Nov 24, 2023.
 */
open class RoundCheckboxWithText : LinearLayout {

    private var mTextView: TextView
    private var mCheckbox: CheckBox
    private var mWrapper: LinearLayout

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
        inflate(context, R.layout.round_checkbox_with_text, this)

        val a = context.obtainStyledAttributes(attrs, R.styleable.RoundCheckboxWithText)

        val text = a.getString(R.styleable.RoundCheckboxWithText_text)
        val isChecked = a.getBoolean(R.styleable.RoundCheckboxWithText_checked, false)

        a.recycle()

        mCheckbox = findViewById<CheckBox>(R.id.checkbox).also { it.isChecked = isChecked }
        mTextView = findViewById<TextView>(R.id.text).also { it.text = text }
        mWrapper = findViewById<LinearLayout?>(R.id.wrapper).also {
            it.setOnClickListener { mCheckbox.toggle() }
        }
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : this(context, attrs, defStyleAttr, 0)

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)

    fun setChecked(checked: Boolean) {
        mCheckbox.isChecked = checked
    }

    fun isChecked() = mCheckbox.isChecked

    fun setText(text: String?) {
        mTextView.text = text
    }

    fun getText(): CharSequence? = mTextView.text

    override fun setEnabled(enabled: Boolean) {
        mCheckbox.isEnabled = enabled
        mTextView.alpha = if (enabled) 1.0f else 0.7f
        super.setEnabled(enabled)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent?) = !isEnabled

}