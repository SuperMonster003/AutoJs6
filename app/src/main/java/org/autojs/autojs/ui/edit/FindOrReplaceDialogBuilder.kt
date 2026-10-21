package org.autojs.autojs.ui.edit

import android.content.Context
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.LayoutInflater
import android.widget.CheckBox
import androidx.appcompat.widget.AppCompatEditText
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.core.pref.keys.MiscStateKeys
import org.autojs.autojs.core.pref.store.SettingsStores
import org.autojs.autojs.core.pref.store.set
import org.autojs.autojs.ui.edit.editor.engine.EditorSearchSyntaxException
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.DialogFindOrReplaceBinding

/**
 * Created by Stardust on Sep 28, 2017.
 * Modified by SuperMonster003 as of Dec 1, 2021.
 * Transformed by SuperMonster003 on May 12, 2023.
 * Modified by JetBrains AI Assistant (GPT-5.2) as of Feb 8, 2026.
 */
class FindOrReplaceDialogBuilder(context: Context, private val mEditorView: EditorView) : MaterialDialog.Builder(context) {

    private lateinit var mRegexCheckBox: CheckBox
    private lateinit var mReplaceCheckBox: CheckBox
    private lateinit var mReplaceAllCheckBox: CheckBox
    private lateinit var mKeywordsEditText: AppCompatEditText
    private lateinit var mReplacementEditText: AppCompatEditText

    init {
        setupViews()
        restoreState()
        autoDismiss(false)
        onNegative { dialog, _ -> dialog.dismiss() }
        onPositive { dialog, _ ->
            storeState()
            findOrReplace(dialog)
        }
    }

    private fun setupViews() {
        val binding = DialogFindOrReplaceBinding.inflate(LayoutInflater.from(context))

        mRegexCheckBox = binding.checkboxRegex
        mReplaceCheckBox = binding.checkboxReplace.apply {
            setOnCheckedChangeListener { _, _ -> syncWithReplaceAllCheckBox() }
        }
        mKeywordsEditText = binding.keywords
        mReplaceAllCheckBox = binding.checkboxReplaceAll.apply {
            setOnCheckedChangeListener { _, _ -> syncWithReplaceCheckBox() }
        }
        mReplacementEditText = binding.replacement.apply {
            addTextChangedListener(object : TextWatcher {
                override fun beforeTextChanged(s: CharSequence, start: Int, count: Int, after: Int) {}

                override fun onTextChanged(s: CharSequence, start: Int, before: Int, count: Int) {
                    if (mReplacementEditText.text?.isNotEmpty() == true) {
                        mReplaceCheckBox.isChecked = true
                    }
                }

                override fun afterTextChanged(s: Editable) {}
            })
        }

        title(R.string.text_find_or_replace)
        customView(binding.root, true)
        positiveText(R.string.dialog_button_confirm)
        negativeText(R.string.dialog_button_cancel)
    }

    private fun storeState() {
        SettingsStores.default.edit {
            this[MiscStateKeys.FIND_OR_REPLACE_KEYWORDS] = mKeywordsEditText.text.toString()
            this[MiscStateKeys.FIND_OR_REPLACE_USING_REGEX] = mRegexCheckBox.isChecked
        }
    }

    private fun restoreState() {
        mKeywordsEditText.setText(MiscStateKeys.FIND_OR_REPLACE_KEYWORDS.get())
        mRegexCheckBox.isChecked = MiscStateKeys.FIND_OR_REPLACE_USING_REGEX.get()
    }

    fun setUsingRegex(usingRegex: Boolean): FindOrReplaceDialogBuilder {
        mRegexCheckBox.isChecked = usingRegex
        return this
    }

    private fun syncWithReplaceCheckBox() {
        if (mReplaceAllCheckBox.isChecked && !mReplaceCheckBox.isChecked) {
            mReplaceCheckBox.isChecked = true
        }
    }

    private fun syncWithReplaceAllCheckBox() {
        if (!mReplaceCheckBox.isChecked && mReplaceAllCheckBox.isChecked) {
            mReplaceAllCheckBox.isChecked = false
        }
    }

    private fun findOrReplace(dialog: MaterialDialog) {
        val keywords = mKeywordsEditText.text.toString()
        if (keywords.isEmpty()) {
            return
        }
        try {
            val usingRegex = mRegexCheckBox.isChecked
            if (!mReplaceCheckBox.isChecked) {
                val ok = mEditorView.tryFind(keywords, usingRegex)
                if (ok) {
                    mEditorView.recordAceFindOrReplaceHistory(
                        keywords = keywords,
                        replacement = "",
                        usingRegex = usingRegex,
                        replace = false,
                        replaceAll = false,
                    )
                    dialog.dismiss()
                }
            } else {
                val replacement = mReplacementEditText.text.toString()
                if (mReplaceAllCheckBox.isChecked) {
                    mEditorView.replaceAll(keywords, replacement, usingRegex)
                    mEditorView.recordAceFindOrReplaceHistory(
                        keywords = keywords,
                        replacement = replacement,
                        usingRegex = usingRegex,
                        replace = true,
                        replaceAll = true,
                    )
                    dialog.dismiss()
                } else {
                    val ok = mEditorView.tryReplace(keywords, replacement, usingRegex)
                    if (ok) {
                        mEditorView.recordAceFindOrReplaceHistory(
                            keywords = keywords,
                            replacement = replacement,
                            usingRegex = usingRegex,
                            replace = true,
                            replaceAll = false,
                        )
                        dialog.dismiss()
                    }
                }
            }
        } catch (e: EditorSearchSyntaxException) {
            e.printStackTrace()
            mKeywordsEditText.error = getContext().getString(R.string.error_pattern_syntax)
        }
    }

    fun setQueryIfNotEmpty(s: String?): FindOrReplaceDialogBuilder {
        if (!TextUtils.isEmpty(s)) mKeywordsEditText.setText(s)
        return this
    }

    fun setReplacement(s: String?): FindOrReplaceDialogBuilder {
        mReplacementEditText.setText(s.orEmpty())
        return this
    }

    fun setReplaceMode(replace: Boolean, replaceAll: Boolean): FindOrReplaceDialogBuilder {
        mReplaceCheckBox.isChecked = replace || replaceAll
        mReplaceAllCheckBox.isChecked = replaceAll
        return this
    }

}
