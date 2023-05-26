package org.autojs.autojs.core.ui.widget

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Resources
import android.util.AttributeSet
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import androidx.appcompat.widget.AppCompatSpinner
import androidx.core.view.children

class JsSpinner : AppCompatSpinner {

    private var mTextSize = -1f
    private var mTextStyle = -1
    private var mTextColor = 0

    var entryTextSize = -1f
    var entryTextStyle = -1
    var entryTextColor = 0

    constructor(context: Context) : super(context)
    constructor(context: Context, mode: Int) : super(context, mode)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, mode: Int) : super(context, attrs, defStyleAttr, mode)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, mode: Int, popupTheme: Resources.Theme?) : super(context, attrs, defStyleAttr, mode, popupTheme)

    var textSize: Float
        get() = mTextSize
        set(textSize) {
            mTextSize = textSize
            children.forEach {
                if (it is TextView) {
                    it.setTextSize(TypedValue.COMPLEX_UNIT_PX, mTextSize)
                }
            }
        }
    var textStyle: Int
        get() = mTextStyle
        @SuppressLint("WrongConstant")
        set(textStyle) {
            mTextStyle = textStyle
            children.forEach {
                if (it is TextView) {
                    it.setTypeface(it.typeface, mTextStyle)
                }
            }
        }
    var textColor: Int
        get() = mTextColor
        set(textColor) {
            mTextColor = textColor
            children.forEach {
                if (it is TextView) {
                    it.setTextColor(mTextColor)
                }
            }
        }

    inner class Adapter(context: Context, resource: Int, objects: Array<String?>) : ArrayAdapter<String?>(context, resource, objects) {

        @SuppressLint("WrongConstant")
        override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = super.getView(position, convertView, parent)
            if (view is TextView) {
                if (mTextColor != 0) {
                    view.setTextColor(mTextColor)
                }
                if (mTextSize != -1f) {
                    view.setTextSize(TypedValue.COMPLEX_UNIT_PX, mTextSize)
                }
                if (mTextStyle != -1) {
                    view.setTypeface(view.typeface, mTextStyle)
                }
            }
            return view
        }

        override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
            val view = super.getDropDownView(position, convertView, parent)
            if (view is TextView) {
                if (entryTextColor != 0) {
                    view.setTextColor(entryTextColor)
                }
                if (entryTextSize != -1f) {
                    view.setTextSize(TypedValue.COMPLEX_UNIT_PX, entryTextSize)
                }
                if (entryTextStyle != -1) {
                    view.setTypeface(view.typeface, entryTextStyle)
                }
            }
            return view
        }
    }
}