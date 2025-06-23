package org.autojs.autojs.theme.preference

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.AttributeSet
import android.widget.TextView
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceViewHolder
import org.autojs.autojs.theme.ThemeColor
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.theme.ThemeColorMutable
import org.autojs.autojs.util.ColorUtils
import org.autojs.autojs6.R

/**
 * Created by Stardust on Aug 8, 2016.
 * Transformed by SuperMonster003 on Sep 25, 2022.
 */
class ThemeColorPreferenceCategory : PreferenceCategory, ThemeColorMutable {

    private var mTitleTextView: TextView? = null
    private var mColor = Color.TRANSPARENT

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context) : super(context)

    init {
        ThemeColorManager.add(this)
    }

    fun setTitleTextColor(titleTextColor: Int) = setThemeColor(ThemeColor(titleTextColor))

    override fun setThemeColor(color: ThemeColor) {
        ColorUtils.adjustColorForContrast(context.getColor(R.color.window_background), color.colorPrimary, 3.2).let { contrastColor ->
            mColor = contrastColor
            mTitleTextView?.setTextColor(contrastColor)
        }
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        (holder.findViewById(android.R.id.title) as TextView).let {
            it.takeIf { mColor != Color.TRANSPARENT }?.setTextColor(mColor)
            it.setTypeface(null, Typeface.BOLD)
            mTitleTextView = it
        }
    }
}