package org.autojs.autojs.theme.preference

import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import androidx.preference.PreferenceViewHolder
import org.autojs.autojs.app.DialogUtils
import org.autojs.autojs6.R

open class MaterialPreference : androidx.preference.Preference, LongClickablePreferenceLike {

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

    private fun init(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) {
        obtainStyledAttrs(context, attrs, R.styleable.MaterialPreference, defStyleAttr, defStyleRes).let { a ->
            getAttrString(a, R.styleable.MaterialPreference_longClickPrompt)?.also { longClickPrompt = it }
            getAttrString(a, R.styleable.MaterialPreference_longClickPromptMore)?.also { longClickPromptMore = it }
            a.recycle()
        }
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        DialogUtils.applyLongClickability(this, holder)
    }

    protected fun obtainStyledAttrs(context: Context, set: AttributeSet?, styleableRes: IntArray, defStyleAttr: Int, defStyleRes: Int): TypedArray {
        return context.obtainStyledAttributes(set, styleableRes, defStyleAttr, defStyleRes)
    }

    protected fun getAttrString(a: TypedArray, index: Int): String? = a.getString(index)

    protected fun getAttrTextArray(a: TypedArray, index: Int): Array<CharSequence>? = a.getTextArray(index)

}
