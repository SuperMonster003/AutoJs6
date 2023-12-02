package org.autojs.autojs.ui.edit

import android.content.Context
import android.text.Editable
import android.text.TextUtils
import android.text.TextWatcher
import android.view.LayoutInflater
import android.widget.CheckBox
import androidx.appcompat.widget.AppCompatEditText
import androidx.preference.PreferenceManager
import com.afollestad.materialdialogs.MaterialDialog
import org.autojs.autojs.ui.edit.editor.CodeEditor.CheckedPatternSyntaxException
import org.autojs.autojs6.R
import org.autojs.autojs6.databinding.DialogFindOrReplaceBinding

/**
 * Created by Stardust on Sep 28, 2017.
 * Modified by SuperMonster003 as of Dec 1, 2021.
 * Transformed by SuperMonster003 on May 12, 2023.
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
        mReplaceCheckBox = binding.checkboxReplace
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
        positiveText(R.string.text_ok)
        negativeText(R.string.text_cancel)
    }

    private fun storeState() {
        PreferenceManager.getDefaultSharedPreferences(getContext()).edit()
            .putString(KEY_KEYWORDS, mKeywordsEditText.text.toString())
            .apply()
    }

    private fun restoreState() {
        PreferenceManager.getDefaultSharedPreferences(getContext())
            .getString(KEY_KEYWORDS, "")
            .let { mKeywordsEditText.setText(it) }

    }

    private fun syncWithReplaceCheckBox() {
        if (mReplaceAllCheckBox.isChecked && !mReplaceCheckBox.isChecked) {
            mReplaceCheckBox.isChecked = true
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
                mEditorView.find(keywords, usingRegex)
            } else {
                val replacement = mReplacementEditText.text.toString()
                if (mReplaceAllCheckBox.isChecked) {
                    mEditorView.replaceAll(keywords, replacement, usingRegex)
                } else {
                    mEditorView.replace(keywords, replacement, usingRegex)
                }
            }
            dialog.dismiss()
        } catch (e: CheckedPatternSyntaxException) {
            e.printStackTrace()
            mKeywordsEditText.error = getContext().getString(R.string.error_pattern_syntax)
        }
    }

    fun setQueryIfNotEmpty(s: String?): FindOrReplaceDialogBuilder {
        if (!TextUtils.isEmpty(s)) mKeywordsEditText.setText(s)
        return this
    }

    companion object {

        private const val KEY_KEYWORDS = "..."

    }

}