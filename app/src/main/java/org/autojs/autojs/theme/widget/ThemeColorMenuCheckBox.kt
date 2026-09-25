package org.autojs.autojs.theme.widget

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatCheckBox
import org.autojs.autojs.theme.ThemeColor
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.theme.ThemeColorMutable
import org.autojs.autojs.util.ColorUtils
import org.autojs.autojs6.R

class ThemeColorMenuCheckBox : AppCompatCheckBox, ThemeColorMutable {

    constructor(context: Context) : super(context) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context.withWidgetFallbackStyle(defStyleAttr, defStyleRes), attrs, defStyleAttr) {
        init()
    }

    private fun init() {
        ThemeColorManager.add(this)
    }

    override fun setThemeColor(color: ThemeColor) {
        val checkedColor = ColorUtils.adjustColorForContrast(
            context.getColor(R.color.window_background),
            color.colorPrimary,
            2.3,
        )
        val uncheckedColor = context.getColor(R.color.day_night_alpha_40)
        buttonTintList = ColorStateList(
            arrayOf(
                intArrayOf(android.R.attr.state_checked),
                intArrayOf(-android.R.attr.state_checked),
            ),
            intArrayOf(checkedColor, uncheckedColor),
        )
    }

}
