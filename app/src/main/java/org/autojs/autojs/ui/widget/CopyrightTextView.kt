package org.autojs.autojs.ui.widget

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatTextView
import org.autojs.autojs.theme.widget.withWidgetFallbackStyle
import org.autojs.autojs6.R
import java.util.Calendar

class CopyrightTextView : AppCompatTextView {

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context.withWidgetFallbackStyle(defStyleAttr, defStyleRes), attrs, defStyleAttr)

    init {
        text = context.getString(R.string.text_copyright_dynamic, Calendar.getInstance().get(Calendar.YEAR))
    }

}