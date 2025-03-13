package org.autojs.autojs.ui.widget

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.theme.ThemeColorManagerCompat
import org.autojs.autojs.util.ColorUtils
import org.autojs.autojs.util.FileUtils
import org.autojs.autojs6.R
import kotlin.math.roundToInt

class FirstCharView : TextView {

    constructor(context: Context?) : super(context) {
        init()
    }

    constructor(context: Context?, attrs: AttributeSet?) : super(context, attrs) {
        init()
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init()
    }

    constructor(context: Context?, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
        init()
    }

    private fun init() {
        gravity = Gravity.CENTER
        setLineSpacing(0f, 1f)
    }

    private val strokeWidth: Int
        get() = context.resources.getDimensionPixelSize(R.dimen.first_char_view_stroke_width)

    private var privateBackground: GradientDrawable? = null

    private fun background(): GradientDrawable {
        if (privateBackground == null) {
            privateBackground = background as GradientDrawable
        }
        return privateBackground!!
    }

    fun setIconText(text: CharSequence?) = also { setText(text) }

    fun setIcon(icon: FileUtils.TYPE.Icon): FirstCharView {
        setIconText(icon.text)
        icon.textSize?.let { setTextSize(TypedValue.COMPLEX_UNIT_SP, it.toFloat()) }
        icon.textPadding?.let { padding ->
            val (start, top, end, bottom) = padding.map {
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, it.toFloat(), resources.displayMetrics).roundToInt()
            }
            setPadding(start, top, end, bottom)
        }
        icon.rotation?.let { rotation = it.toFloat() }
        icon.includeFontPadding?.let { includeFontPadding = it }
        return this
    }

    fun setIconTextColor(@ColorInt color: Int) = also { setTextColor(color) }

    fun setIconTextColorRes(@ColorRes colorRes: Int) = also { setTextColor(context.getColor(colorRes)) }

    fun setIconTextColorByThemeColorLuminance() = also { setIconTextColorRes(ThemeColorManager.getDayOrNightColorResByLuminance()) }

    fun setIconTextThemeColor() = setIconTextColor(ThemeColorManagerCompat.getColorPrimary())

    fun setIconTextColorDayNight() = setIconTextColorRes(COLOR_RES_DAY_NIGHT)

    fun setIconTextColorNightDay() = setIconTextColorRes(COLOR_RES_NIGHT_DAY)

    fun setStrokeColor(@ColorInt color: Int) = also { background().setStroke(strokeWidth, color) }

    fun setStrokeColorRes(@ColorRes colorRes: Int): FirstCharView = setStrokeColor(context.getColor(colorRes))

    fun setStrokeThemeColor() = setStrokeColor(ThemeColorManagerCompat.getColorPrimary())

    fun setStrokeColorDayNight() = setStrokeColorRes(COLOR_RES_DAY_NIGHT)

    fun setStrokeColorNightDay() = setStrokeColorRes(COLOR_RES_NIGHT_DAY)

    fun setFillColor(@ColorInt color: Int) = also { background().setColor(color) }

    fun setFillColorRes(@ColorRes colorRes: Int) = setFillColor(context.getColor(colorRes))

    fun setFillThemeColor() = setFillColor(ThemeColorManagerCompat.getColorPrimary())

    fun setFillTransparent() = setFillColor(COLOR_TRANSPARENT)

    fun setFillColorDayNight() = setFillColorRes(COLOR_RES_DAY_NIGHT)

    fun setFillColorNightDay() = setFillColorRes(COLOR_RES_NIGHT_DAY)

    companion object {

        private val COLOR_RES_DAY_NIGHT = R.color.day_night
        private val COLOR_RES_NIGHT_DAY = R.color.night_day
        private const val COLOR_TRANSPARENT = Color.TRANSPARENT

    }

}
