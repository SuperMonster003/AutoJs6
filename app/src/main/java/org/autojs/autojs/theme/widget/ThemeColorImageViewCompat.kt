package org.autojs.autojs.theme.widget

import android.content.Context
import android.graphics.PorterDuff
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatImageView
import org.autojs.autojs.theme.ThemeColor
import org.autojs.autojs.theme.ThemeColorHelper
import org.autojs.autojs.theme.ThemeColorManager.add
import org.autojs.autojs.theme.ThemeColorMutable
import org.autojs.autojs.util.ColorUtils
import org.autojs.autojs6.R

/**
 * Created by Stardust on May 10, 2017.
 * Transformed by SuperMonster003 on May 15, 2023.
 */
class ThemeColorImageViewCompat : AppCompatImageView, ThemeColorMutable {

    private var mColor = 0

    constructor(context: Context) : super(context) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init()
    }

    private fun init() {
        add(this)
    }

    override fun setThemeColor(color: ThemeColor) {
        if (mColor != color.colorPrimary) {
            mColor = color.colorPrimary
            ThemeColorHelper.setThemeColorPrimary(this, true)
        }
    }

    override fun setImageResource(resId: Int) {
        super.setImageResource(resId)
        if (mColor != 0) {
            ThemeColorHelper.setThemeColorPrimary(this, true)
        }
    }

}