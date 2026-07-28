package org.autojs.autojs.theme.preference

import android.content.Context
import android.content.res.Resources
import android.content.res.TypedArray
import android.graphics.Color
import android.util.AttributeSet
import android.view.View
import android.widget.CompoundButton
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.preference.PreferenceViewHolder
import androidx.preference.SwitchPreference
import org.autojs.autojs.theme.ThemeColor
import org.autojs.autojs.theme.ThemeColorHelper
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.theme.ThemeColorMutable
import org.autojs.autojs.util.DialogUtils
import org.autojs.autojs6.R

/**
 * Created by Stardust on Mar 5, 2017.
 * Modified by SuperMonster003 as of Mar 26, 2026.
 */
open class ThemeColorSwitchPreference :
    SwitchPreference,
    ThemeColorMutable,
    LongClickablePreferenceLike,
    PreferenceTitleColorMutable,
    PreferenceSummaryColorMutable,
    PreferenceButtonColorMutable {

    private var mCheckableView: View? = null

    override val prefTitle: CharSequence? = title
    override val prefContext: Context = context
    override var longClickPrompt: CharSequence? = null
    override var longClickPromptMore: CharSequence? = null

    private var _titleColor: Int? = null
    private var _summaryColor: Int? = null
    private var _buttonColor = Color.TRANSPARENT
    private var buttonColorContrastMatters = true
    private var availableForCurrentContext = true
    private var unavailableChecked = false
    private var unavailableClickHandler: (() -> Unit)? = null

    override var titleColor: Int?
        get() = _titleColor
        set(value) {
            if (_titleColor == value) return
            _titleColor = value
            notifyChanged()
        }

    override var summaryColor: Int?
        get() = _summaryColor
        set(value) {
            if (_summaryColor == value) return
            _summaryColor = value
            notifyChanged()
        }

    override var buttonColor: Int
        get() = _buttonColor
        set(value) {
            setButtonColor(value, contrastMatters = true)
        }

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
        holder.itemView.alpha = if (availableForCurrentContext && isEnabled) 1f else UNAVAILABLE_ALPHA
        mCheckableView?.isEnabled = isEnabled && availableForCurrentContext
        if (!availableForCurrentContext) {
            setCheckableViewCheckedWithoutPreferenceCallback(unavailableChecked)
        }
        applyColor()
        DialogUtils.applyLongClickability(this, holder)
        titleColor?.let { color ->
            holder.itemView.findViewById<TextView>(android.R.id.title)?.setTextColor(color)
        }
        summaryColor?.let { color ->
            holder.itemView.findViewById<TextView>(android.R.id.summary)?.setTextColor(color)
        }
    }

    override fun onClick() {
        if (!availableForCurrentContext) {
            unavailableClickHandler?.invoke()
            return
        }
        super.onClick()
    }

    fun setAvailableForCurrentContext(
        available: Boolean,
        unavailableChecked: Boolean = false,
        onUnavailableClick: (() -> Unit)? = null,
    ) {
        availableForCurrentContext = available
        this.unavailableChecked = unavailableChecked
        unavailableClickHandler = onUnavailableClick
        notifyChanged()
    }

    override fun setThemeColor(color: ThemeColor) {
        buttonColor = color.colorPrimary
        applyColor()
    }

    fun setButtonColor(color: Int, contrastMatters: Boolean) {
        if (_buttonColor == color && buttonColorContrastMatters == contrastMatters) return
        _buttonColor = color
        buttonColorContrastMatters = contrastMatters
        applyColor()
        notifyChanged()
    }

    private fun applyColor() {
        if (mCheckableView is Switch && buttonColor != Color.TRANSPARENT) {
            ThemeColorHelper.setColorPrimary(
                mCheckableView as Switch,
                buttonColor,
                buttonColorContrastMatters,
            )
        }
        if (mCheckableView is SwitchCompat && buttonColor != Color.TRANSPARENT) {
            ThemeColorHelper.setColorPrimary(
                mCheckableView as SwitchCompat,
                buttonColor,
                buttonColorContrastMatters,
            )
        }
    }

    protected fun obtainStyledAttrs(context: Context, set: AttributeSet?, styleableRes: IntArray, defStyleAttr: Int, defStyleRes: Int): TypedArray {
        return context.obtainStyledAttributes(set, styleableRes, defStyleAttr, defStyleRes)
    }

    protected fun getAttrString(a: TypedArray, index: Int): String? = a.getString(index)

    private fun setCheckableViewCheckedWithoutPreferenceCallback(checked: Boolean) {
        (mCheckableView as? CompoundButton)?.let { view ->
            view.setOnCheckedChangeListener(null)
            view.isChecked = checked
        }
    }

    companion object {
        private const val UNAVAILABLE_ALPHA = 0.58f
    }

}
