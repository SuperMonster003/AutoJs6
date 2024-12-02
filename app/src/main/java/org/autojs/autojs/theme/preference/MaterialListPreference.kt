package org.autojs.autojs.theme.preference

import android.content.Context
import android.os.Bundle
import android.text.TextUtils
import android.util.AttributeSet
import androidx.core.content.res.TypedArrayUtils
import androidx.preference.Preference.SummaryProvider
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.ui.common.NotAskAgainDialog
import org.autojs.autojs.util.StringUtils.key
import org.autojs.autojs6.R

open class MaterialListPreference : MaterialDialogPreference {

    private var mItemKeys: Collection<CharSequence> = emptyList()
    private var mItemValues: Collection<CharSequence> = emptyList()
    private var mItemDisables: Array<Int> = emptyArray()
    private var mItemDefaultKey: CharSequence? = null
    private val mItemDefaultIndex: Int
        get() = getKeyIndex(mItemDefaultKey) ?: 0

    private var itemPrefIndex: Int?
        get() = getKeyIndex(Pref.getStringOrNull(key))
        set(index) {
            index?.let {
                Pref.putString(key, mItemKeys.toList()[it].toString())
            } ?: Pref.remove(key)
        }

    private var mConfirmedPrompt: String? = null

    protected val entry: CharSequence?
        get() = mItemValues.takeIf { it.isNotEmpty() }?.toList()?.get(itemPrefIndex ?: mItemDefaultIndex)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int, bundle: Bundle) : super(context, attrs, defStyleAttr, defStyleRes) {
        obtainStyledAttrs(context, attrs, R.styleable.MaterialListPreference, defStyleAttr, defStyleRes)
            .let { a ->
                getAttrString(a, R.styleable.MaterialListPreference_dialogTitle)?.also { dialogTitle = it }
                getAttrString(a, R.styleable.MaterialListPreference_dialogContent)?.also { dialogContent = it }
                positiveText = getAttrString(a, R.styleable.MaterialListPreference_positiveText) ?: context.getString(R.string.dialog_button_confirm)
                negativeText = getAttrString(a, R.styleable.MaterialListPreference_negativeText) ?: context.getString(R.string.dialog_button_cancel)
                getAttrTextArray(a, R.styleable.MaterialListPreference_itemKeys)?.also { mItemKeys = it.toList() }
                getAttrTextArray(a, R.styleable.MaterialListPreference_itemValues)?.also { mItemValues = it.toList() }
                bundle.getString(key(R.string.key_pref_bundle_default_item), getAttrString(a, R.styleable.MaterialListPreference_itemDefaultKey))?.also { mItemDefaultKey = it }
                getAttrString(a, R.styleable.MaterialListPreference_onConfirmPrompt)?.also { mConfirmedPrompt = it }
                a.recycle()
            }

        bundle.getIntegerArrayList(key(R.string.key_pref_bundle_disabled_items))?.map { context.getString(it) }?.let { disables ->
            mItemKeys.forEachIndexed { index, it -> if (it in disables) mItemDisables += index }
        }

        summaryProvider = SummaryProvider<MaterialListPreference> { preference ->
            preference.entry?.takeUnless { TextUtils.isEmpty(it) }
        }
    }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) : this(context, attrs, defStyleAttr, defStyleRes, Bundle())

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : this(context, attrs, defStyleAttr, 0)

    constructor(context: Context, attrs: AttributeSet?) : this(
        context, attrs, TypedArrayUtils.getAttr(
            context, android.R.attr.dialogPreferenceStyle,
            android.R.attr.dialogPreferenceStyle,
        )
    )

    constructor(context: Context) : this(context, null)

    override fun getBuilder(): MaterialDialog.Builder = super.getBuilder().also { builder ->
        mItemValues.takeIf { it.isNotEmpty() }?.let {
            builder.autoDismiss(false)
            builder.onNegative { d, _ -> d.dismiss() }
            builder.items(it)
            builder.takeIf { mItemDisables.isNotEmpty() }?.itemsDisabledIndices(*mItemDisables)
            builder.itemsCallbackSingleChoice(itemPrefIndex ?: mItemDefaultIndex) { d, _, which, _ ->
                if (itemPrefIndex != which) {
                    itemPrefIndex = which
                    showPrompt() ?: onChangeConfirmed(getDialog())
                } else {
                    d.dismiss()
                }
                return@itemsCallbackSingleChoice true
            }
        }
    }

    private fun getKeyIndex(keyString: CharSequence?): Int? {
        return keyString?.run { mItemKeys.indexOf(this).takeIf { it != -1 } }
    }

    private fun showPrompt() = mConfirmedPrompt?.let { content ->
        NotAskAgainDialog.Builder(prefContext, "prompt_\$_${key}")
            .title(R.string.text_prompt)
            .content(content)
            .positiveText(R.string.text_ok)
            .dismissListener { onChangeConfirmed(getDialog()) }
            .show()
    }

    open fun onChangeConfirmed(dialog: MaterialDialog) {
        notifyChanged()
        getDialog().dismiss()
    }

}