package org.autojs.autojs.core.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.widget.FrameLayout
import androidx.annotation.AttrRes
import androidx.annotation.StyleRes
import org.autojs.autojs.core.ui.JsViewHelper

/**
 * Created by Stardust on May 14, 2017.
 * Transformed by SuperMonster003 on May 22, 2023.
 */
class JsFrameLayout : FrameLayout {

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, @AttrRes defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    constructor(context: Context, attrs: AttributeSet?, @AttrRes defStyleAttr: Int, @StyleRes defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

    fun id(id: String?) = JsViewHelper.findViewByStringId(this, id)

}