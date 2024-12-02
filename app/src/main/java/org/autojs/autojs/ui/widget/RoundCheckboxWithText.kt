package org.autojs.autojs.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import org.autojs.autojs6.R

/**
 * Created by SuperMonster003 on Nov 24, 2023.
 */
class RoundCheckboxWithText : LinearLayout {

    private var mOnBeingUnavailableListener: OnBeingUnavailableListener? = null

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
            it.setOnClickListener { if (isEnabled) mCheckbox.toggle() }
        }
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : this(context, attrs, defStyleAttr, 0)

    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)

    interface OnBeingUnavailableListener {
        fun onTouch(view: RoundCheckboxWithText)
    }

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
        mTextView.isEnabled = enabled

        super.setEnabled(enabled)
    }

    fun setOnBeingUnavailableListener(l: OnBeingUnavailableListener) {
        mOnBeingUnavailableListener = l
    }

    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean {
        if (!isEnabled && ev != null && ev.action == MotionEvent.ACTION_UP) {
            if (isInside(this, ev)) {
                mOnBeingUnavailableListener?.onTouch(this)
            }
        }
        return super.dispatchTouchEvent(ev)
    }

    private fun isInside(v: View, e: MotionEvent): Boolean {
        return e.x in 0f..v.measuredWidth.toFloat() && e.y in 0f..v.measuredHeight.toFloat()
    }

}