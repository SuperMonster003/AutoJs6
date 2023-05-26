package org.autojs.autojs.core.ui.widget

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView

/**
 * Created by Stardust on 2017/5/15.
 * Transformed by SuperMonster003 on May 20, 2023.
 */
class JsTextView : AppCompatTextView {

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    fun text() = text.toString()

    fun text(text: CharSequence?) = setText(text)

}