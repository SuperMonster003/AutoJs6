package org.autojs.autojs.ui.common

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.HorizontalScrollView
import android.widget.ScrollView

class NestedOuterScrollView : ScrollView {

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

    constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    constructor(context: Context) : super(context)

    var innerHorizontalScrollView: HorizontalScrollView? = null

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean {
        ev?.let {
            if (innerHorizontalScrollView != null) {
                when (it.action) {
                    MotionEvent.ACTION_DOWN -> {
                        // If the inner HorizontalScrollView height is greater or equal to the NestedOuterScrollView height
                        if (innerHorizontalScrollView!!.measuredHeight <= this.height) {
                            this.parent.requestDisallowInterceptTouchEvent(true)
                        }
                    }
                }
            }
        }
        return super.onInterceptTouchEvent(ev)
    }

    override fun onTouchEvent(ev: MotionEvent?): Boolean {
        ev?.let {
            innerHorizontalScrollView?.dispatchTouchEvent(it)
        }
        return super.onTouchEvent(ev)
    }

}