package org.autojs.autojs.theme.preference

import android.content.Context
import android.content.res.TypedArray
import android.util.AttributeSet
import androidx.core.content.res.TypedArrayUtils
import androidx.preference.PreferenceViewHolder
import org.autojs.autojs.app.DialogUtils
import org.autojs.autojs6.R

open class MaterialPreference : androidx.preference.Preference, LongClickablePreferenceLike {

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
            context, androidx.preference.R.attr.preferenceStyle, android.R.attr.preferenceStyle
        )
    )

    constructor(context: Context) : this(context, null)

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)
        DialogUtils.applyLongClickability(this, holder)
    }

    protected fun obtainStyledAttrs(context: Context, set: AttributeSet?, styleableRes: IntArray, defStyleAttr: Int, defStyleRes: Int): TypedArray {
        return context.obtainStyledAttributes(set, styleableRes, defStyleAttr, defStyleRes)
    }

    protected fun getAttrString(a: TypedArray, index: Int): String? = TypedArrayUtils.getString(a, index, index)

    protected fun getAttrTextArray(a: TypedArray, index: Int): Array<CharSequence>? = TypedArrayUtils.getTextArray(a, index, index)

}
