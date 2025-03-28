package org.autojs.autojs.theme.preference

import android.content.Context
import android.util.AttributeSet
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs6.R

open class MaterialDialogPreference : MaterialPreference {

    private var mBuilder: MaterialDialog.Builder? = null
    private var mContext: Context? = null

    private lateinit var mDialog: MaterialDialog

    protected var dialogTitle: CharSequence? = null
    protected var dialogContent: CharSequence? = null
    protected var neutralText: CharSequence? = null
    protected var negativeText: CharSequence? = null
    protected var neutralTextShort: CharSequence? = null
    protected var positiveText: CharSequence? = null

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

    override fun onClick() {

        // @Hint by SuperMonster003 on Mar 27, 2025.
        //  ! Avoid rapid consecutive clicks to prevent the app from crashing.
        //  ! zh-CN: 避免连续快速点击可能导致应用崩溃.
        //  # java.lang.IllegalArgumentException:
        //  # LayoutManager androidx.recyclerview.widget.LinearLayoutManager@xxx
        //  # is already attached to a RecyclerView: androidx.recyclerview.widget.RecyclerView{...}
        if (::mDialog.isInitialized && mDialog.isShowing) return

        getBuilder().build().also { mDialog = it }.show()
        super.onClick()
    }

    protected fun getDialog() = mDialog

    protected open fun getBuilder(): MaterialDialog.Builder = mBuilder!!
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