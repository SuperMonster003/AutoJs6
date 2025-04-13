package org.autojs.autojs.theme.preference

import android.content.Context
import android.content.res.Resources
import android.content.res.TypedArray
import android.graphics.Color
import android.util.AttributeSet
import android.view.View
import android.widget.Switch
import androidx.appcompat.widget.SwitchCompat
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
open class ThemeColorSwitchPreference : SwitchPreference, ThemeColorMutable, LongClickablePreferenceLike {

    private var mCheckableView: View? = null
    private var mColor = Color.TRANSPARENT

    override val prefTitle: CharSequence? = title
    override val prefContext: Context = context
    override var longClickPrompt: CharSequence? = null
    override var longClickPromptMore: CharSequence? = null

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
        init(context, attrs, defStyleAttr, defStyleRes)
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr) {
        init(context, attrs, defStyleAttr, 0)
    }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        init(context, attrs, 0, 0)
    }

    constructor(context: Context) : super(context) {
        init(context, null, 0, 0)
    }

    init {
        ThemeColorManager.add(this)
    }

    private fun init(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) {
        obtainStyledAttrs(context, attrs, R.styleable.MaterialPreference, defStyleAttr, defStyleRes).let { a ->
            getAttrString(a, R.styleable.MaterialPreference_longClickPrompt)?.also { longClickPrompt = it }
            getAttrString(a, R.styleable.MaterialPreference_longClickPromptMore)?.also { longClickPromptMore = it }
            a.recycle()
        }
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
            ThemeColorHelper.setColorPrimary(mCheckableView as Switch, mColor, true)
        }
        if (mCheckableView is SwitchCompat) {
            ThemeColorHelper.setColorPrimary(mCheckableView as SwitchCompat, mColor, true)
        }
    }

    protected fun obtainStyledAttrs(context: Context, set: AttributeSet?, styleableRes: IntArray, defStyleAttr: Int, defStyleRes: Int): TypedArray {
        return context.obtainStyledAttributes(set, styleableRes, defStyleAttr, defStyleRes)
    }

    protected fun getAttrString(a: TypedArray, index: Int): String? = a.getString(index)

}