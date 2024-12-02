package org.autojs.autojs.ui.widget

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatEditText
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.R

/**
 * Created by SuperMonster003 on Nov 18, 2024.
 */
class PackageNameEditTextView : AppCompatEditText {

    constructor(context: Context) : super(context)

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(context, attrs, defStyleAttr)

    init {
        val view = this@PackageNameEditTextView
        val allowedChars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ1234567890_."

        view.addTextChangedListener(object : TextWatcher {

            private var currentText: String = ""

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {
                currentText = s?.toString() ?: ""
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val newText = s?.toString() ?: ""
                if (newText != currentText) {
                    val (cleanText, invalidChars) = filterText(newText)
                    if (cleanText != newText) {
                        setText(cleanText)
                        setSelection(cleanText.length)
                        if (invalidChars.isNotEmpty()) {
                            val invalidCharString = invalidChars.joinToString(", ")
                            val message = context.getString(R.string.text_invalid_character_is_removed) + ": [ $invalidCharString ]"
                            ViewUtils.showSnack(view, message, true)
                        }
                    }
                }
            }

            override fun afterTextChanged(s: Editable?) = Unit

            private fun filterText(text: String): Pair<String, List<Char>> {
                val cleanText = StringBuilder()
                val invalidChars = mutableListOf<Char>()
                for (char in text) {
                    if (char in allowedChars) {
                        cleanText.append(char)
                    } else {
                        invalidChars.add(char)
                    }
                }
                return Pair(cleanText.toString(), invalidChars)
            }

        })
    }

}