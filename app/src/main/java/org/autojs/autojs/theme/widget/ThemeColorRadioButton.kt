package org.autojs.autojs.theme.widget

import android.content.Context
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatRadioButton
import org.autojs.autojs.theme.ThemeColor
import org.autojs.autojs.theme.ThemeColorHelper
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.theme.ThemeColorMutable

/**
 * Created by SuperMonster003 on May 6, 2025.
 */
class ThemeColorRadioButton : AppCompatRadioButton, ThemeColorMutable {

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
        ThemeColorHelper.setColorPrimary(this, color.colorPrimary, true)
    }

}
