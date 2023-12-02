@file:Suppress("unused")

package org.autojs.autojs.core.ui.widget

import android.annotation.SuppressLint
import android.content.Context
import android.util.AttributeSet
import android.widget.Button
import android.widget.CompoundButton

/**
 * Created by Stardust on May 15, 2017.
 * Transformed by SuperMonster003 on May 18, 2023.
 */
@SuppressLint("AppCompatCustomView")
class JsButton : Button {

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

    fun text() = text?.toString() ?: ""

    fun text(text: CharSequence?) = setText(text)

}