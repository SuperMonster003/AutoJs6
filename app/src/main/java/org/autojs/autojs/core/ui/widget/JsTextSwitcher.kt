package org.autojs.autojs.core.ui.widget

import android.content.Context
import android.util.AttributeSet
import android.view.animation.AnimationUtils
import android.widget.TextSwitcher
import org.autojs.autojs6.R

class JsTextSwitcher : TextSwitcher {

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    val textViews = emptyList<JsTextView>().toMutableList()

    init {

        /* It's necessary to create a TextView for TextSwitcher. */

        addView(JsTextView(context).also { textViews.add(it) })
        addView(JsTextView(context).also { textViews.add(it) })

        inAnimation = AnimationUtils.loadAnimation(context, R.anim.slide_in_micro)
        outAnimation = AnimationUtils.loadAnimation(context, R.anim.slide_out_micro)
    }

}