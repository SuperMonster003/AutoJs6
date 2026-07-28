package org.autojs.autojs.theme.preference

import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import android.widget.TextView
import androidx.preference.PreferenceViewHolder
import org.autojs.autojs.util.DialogUtils
import org.autojs.autojs6.R

open class MaterialPreference :
    androidx.preference.Preference,
    LongClickablePreferenceLike,
    PreferenceTitleColorMutable,
    PreferenceSummaryColorMutable {

    override val prefTitle: CharSequence? = title
    override val prefContext: Context = context
    override var longClickPrompt: CharSequence? = null
    override var longClickPromptMore: CharSequence? = null
    private var availableForCurrentContext = true
    private var unavailableClickHandler: (() -> Unit)? = null

    private var _titleColor: Int? = null
    private var _summaryColor: Int? = null

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

    private fun init(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) {
        obtainStyledAttrs(context, attrs, R.styleable.MaterialPreference, defStyleAttr, defStyleRes).let { a ->
            getAttrString(a, R.styleable.MaterialPreference_longClickPrompt)?.also { longClickPrompt = it }
            getAttrString(a, R.styleable.MaterialPreference_longClickPromptMore)?.also { longClickPromptMore = it }
            a.recycle()
        }
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        holder.itemView.alpha = if (availableForCurrentContext) 1f else UNAVAILABLE_ALPHA
        DialogUtils.applyLongClickability(this, holder)
        val titleView = holder.itemView.findViewById<TextView>(android.R.id.title)
        val summaryView = holder.itemView.findViewById<TextView>(android.R.id.summary)
        titleColor?.let { color -> titleView?.setTextColor(color) }
        summaryColor?.let { color -> summaryView?.setTextColor(color) }
    }

    fun setAvailableForCurrentContext(
        available: Boolean,
        onUnavailableClick: (() -> Unit)? = null,
    ) {
        availableForCurrentContext = available
        unavailableClickHandler = onUnavailableClick
        notifyChanged()
    }

    protected fun handleUnavailableClickIfNeeded(): Boolean {
        if (availableForCurrentContext) {
            return false
        }
        unavailableClickHandler?.invoke()
        return true
    }

    protected fun obtainStyledAttrs(context: Context, set: AttributeSet?, styleableRes: IntArray, defStyleAttr: Int, defStyleRes: Int): TypedArray {
        return context.obtainStyledAttributes(set, styleableRes, defStyleAttr, defStyleRes)
    }

    protected fun getAttrString(a: TypedArray, index: Int): String? = a.getString(index)

    protected fun getAttrTextArray(a: TypedArray, index: Int): Array<CharSequence>? = a.getTextArray(index)

    protected fun getAttrColor(a: TypedArray, index: Int): Int = a.getColor(index, -1)

    companion object {
        private const val UNAVAILABLE_ALPHA = 0.58f
    }

}
