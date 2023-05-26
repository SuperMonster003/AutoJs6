package org.autojs.autojs.core.ui.widget

import android.content.Context
import android.util.AttributeSet
import androidx.appcompatlegacy.widget.AppCompatTextView

/**
 * Created by SuperMonster003 on Mar 20, 2022.
 * Transformed by SuperMonster003 on May 22, 2023.
 */
// @Reference to TonyJiangWJ/Auto.js on Mar 20, 2022
class JsTextViewLegacy : AppCompatTextView {

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    fun text() = text.toString()

    fun text(text: CharSequence?) = setText(text)

}