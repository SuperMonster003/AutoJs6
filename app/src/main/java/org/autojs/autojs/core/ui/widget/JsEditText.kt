package org.autojs.autojs.core.ui.widget

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.widget.EditText

/**
 * Created by Stardust on 2017/5/15.
 * Transformed by SuperMonster003 on May 20, 2023.
 */
@SuppressLint("AppCompatCustomView")
class JsEditText : EditText {

    constructor(context: Context?) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        showSoftInputOnFocus = true
    }

    fun text() = text?.toString() ?: ""

    fun text(text: CharSequence?) = setText(text)

}