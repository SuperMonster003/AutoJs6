package org.autojs.autojs.core.ui.widget

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatCheckBox

/**
 * Created by Stardust on Oct 19, 2017.
 * Transformed by SuperMonster003 on May 21, 2023.
 */
class JsCheckBox : AppCompatCheckBox {

    private var mIgnoreCheckedChange = false

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    override fun setOnCheckedChangeListener(listener: OnCheckedChangeListener?) {
        super.setOnCheckedChangeListener { buttonView, isChecked ->
            if (!mIgnoreCheckedChange) {
                listener?.onCheckedChanged(buttonView, isChecked)
            }
        }
    }

    fun setChecked(checked: Boolean, notify: Boolean) {
        mIgnoreCheckedChange = !notify
        super.setChecked(checked)
        mIgnoreCheckedChange = false
    }

    fun toggle(notify: Boolean) = setChecked(!isChecked, notify)

}