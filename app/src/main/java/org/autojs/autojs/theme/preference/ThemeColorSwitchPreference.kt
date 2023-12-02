package org.autojs.autojs.theme.preference

import android.content.Context
import android.content.res.Resources
import android.content.res.TypedArray
import android.graphics.Color
import android.util.AttributeSet
import android.view.View
import android.widget.Switch
import androidx.appcompat.widget.SwitchCompat
import androidx.core.content.res.TypedArrayUtils
import androidx.preference.PreferenceViewHolder
import androidx.preference.SwitchPreference
import org.autojs.autojs.app.DialogUtils
import org.autojs.autojs.theme.ThemeColor
import org.autojs.autojs.theme.ThemeColorHelper
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.theme.ThemeColorMutable
import org.autojs.autojs6.R

/**
 * Created by Stardust on Mar 5, 2017.
 */
class ThemeColorSwitchPreference : SwitchPreference, ThemeColorMutable, LongClickablePreferenceLike {

    private var mCheckableView: View? = null
    private var mColor = Color.TRANSPARENT

    override val prefTitle: CharSequence? = title
    override val prefContext: Context = context
    override var longClickPrompt: CharSequence? = null
    override var longClickPromptMore: CharSequence? = null

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
        obtainStyledAttrs(context, attrs, R.styleable.MaterialPreference, defStyleAttr, defStyleRes)
            .let { a ->
                getAttrString(a, R.styleable.MaterialPreference_longClickPrompt)?.also { longClickPrompt = it }
                getAttrString(a, R.styleable.MaterialPreference_longClickPromptMore)?.also { longClickPromptMore = it }
                a.recycle()
            }
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : this(context, attrs, defStyleAttr, 0)

    constructor(context: Context, attrs: AttributeSet?) : this(
        context, attrs, TypedArrayUtils.getAttr(
            context, androidx.preference.R.attr.switchPreferenceStyle, android.R.attr.switchPreferenceStyle
        )
    )

    constructor(context: Context) : this(context, null)

    init {
        ThemeColorManager.add(this)
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        mCheckableView = holder.findViewById(Resources.getSystem().getIdentifier("switch_widget", "id", "android"))
        if (mColor != Color.TRANSPARENT) {
            applyColor()
        }
        DialogUtils.applyLongClickability(this, holder)
    }

    override fun setThemeColor(color: ThemeColor) {
        mColor = color.colorPrimary
        applyColor()
    }

    private fun applyColor() {
        if (mCheckableView is Switch) {
            ThemeColorHelper.setColorPrimary(mCheckableView as Switch?, mColor)
        }
        if (mCheckableView is SwitchCompat) {
            ThemeColorHelper.setColorPrimary(mCheckableView as SwitchCompat?, mColor)
        }
    }

    private fun obtainStyledAttrs(context: Context, set: AttributeSet?, styleableRes: IntArray, defStyleAttr: Int, defStyleRes: Int): TypedArray {
        return context.obtainStyledAttributes(set, styleableRes, defStyleAttr, defStyleRes)
    }

    private fun getAttrString(a: TypedArray, index: Int): String? = TypedArrayUtils.getString(a, index, index)

}