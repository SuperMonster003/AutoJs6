package org.autojs.autojs.core.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.widget.CompoundButton
import androidx.appcompat.widget.SwitchCompat

/**
 * Created by Stardust on 2017/9/18.
 * Transformed by SuperMonster003 on May 21, 2023.
 */
open class JsSwitch : SwitchCompat {

    private var mIgnoreCheckedChange = false

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    override fun setOnCheckedChangeListener(listener: OnCheckedChangeListener?) {
        super.setOnCheckedChangeListener { buttonView: CompoundButton?, isChecked: Boolean ->
            if (!mIgnoreCheckedChange) {
                listener?.onCheckedChanged(buttonView, isChecked)
            }
        }
    }

    open fun setChecked(checked: Boolean, notify: Boolean) {
        mIgnoreCheckedChange = !notify
        super.setChecked(checked)
        mIgnoreCheckedChange = false
    }

    fun toggle(notify: Boolean) = setChecked(!isChecked, notify)

}