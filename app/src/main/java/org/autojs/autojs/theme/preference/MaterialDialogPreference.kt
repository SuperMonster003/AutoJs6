package org.autojs.autojs.theme.preference

import android.content.Context
import android.util.AttributeSet
import androidx.core.content.res.TypedArrayUtils
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs6.R

open class MaterialDialogPreference : MaterialPreference {

    private val mBuilder: MaterialDialog.Builder
    private val mContext: Context

    private lateinit var mDialog: MaterialDialog

    protected var dialogTitle: CharSequence?
    protected var dialogContent: CharSequence?
    protected var neutralText: CharSequence?
    protected var negativeText: CharSequence?
    protected var neutralTextShort: CharSequence?
    protected var positiveText: CharSequence?

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes) {
        mContext = context
        mBuilder = MaterialDialog.Builder(context)

        obtainStyledAttrs(context, attrs, R.styleable.MaterialDialogPreference, defStyleAttr, defStyleRes)
            .let { a ->
                dialogTitle = getAttrString(a, R.styleable.MaterialDialogPreference_dialogTitle) ?: prefTitle
                dialogContent = getAttrString(a, R.styleable.MaterialDialogPreference_dialogContent)
                neutralText = getAttrString(a, R.styleable.MaterialDialogPreference_neutralText)
                neutralTextShort = getAttrString(a, R.styleable.MaterialDialogPreference_neutralTextShort)
                negativeText = getAttrString(a, R.styleable.MaterialDialogPreference_negativeText)
                positiveText = getAttrString(a, R.styleable.MaterialDialogPreference_positiveText)
                a.recycle()
            }
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : this(context, attrs, defStyleAttr, 0)

    constructor(context: Context, attrs: AttributeSet?) : this(
        context, attrs, TypedArrayUtils.getAttr(
            context, android.R.attr.dialogPreferenceStyle,
            android.R.attr.dialogPreferenceStyle,
        )
    )

    constructor(context: Context) : this(context, null)

    override fun onClick() {
        getBuilder().build().also { mDialog = it }.show()
        super.onClick()
    }

    protected fun getDialog() = mDialog

    protected open fun getBuilder(): MaterialDialog.Builder = mBuilder
        .neutralColorRes(R.color.dialog_button_hint)
        .dismissListener { mDialog.recyclerView?.layoutManager = null }
        .onNeutral { _, _ -> onNeutral() }
        .also { builder ->
            dialogTitle?.let { builder.title(it) }
            dialogContent?.let { builder.content(it) }
            setNeutralTextIfNeeded(builder)
            negativeText?.let { builder.negativeText(it) }
            positiveText?.let { builder.positiveText(it) }
        }

    private fun setNeutralTextIfNeeded(builder: MaterialDialog.Builder) {
        if (neutralTextShort != null && mayBeStack()) {
            builder.neutralText(neutralTextShort!!)
        } else {
            neutralText?.let { builder.neutralText(it) }
        }
    }

    open fun onNeutral() {}

    private fun mayBeStack(): Boolean {
        val density = prefContext.resources.displayMetrics.density
        val maxChars: Int = (32 * density - 54).toInt()
        val positiveTextLength = 5.coerceAtLeast((positiveText ?: prefContext.getString(android.R.string.ok)).length)
        val negativeTextLength = 5.coerceAtLeast((negativeText ?: prefContext.getString(android.R.string.cancel)).length)
        val neutralTextLength = 5.coerceAtLeast(neutralText!!.length)
        return positiveTextLength + negativeTextLength + neutralTextLength > maxChars
    }

}