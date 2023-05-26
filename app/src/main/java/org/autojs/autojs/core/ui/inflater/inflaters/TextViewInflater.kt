package org.autojs.autojs.core.ui.inflater.inflaters

import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.InputType
import android.text.TextUtils.TruncateAt
import android.text.method.TextKeyListener
import android.text.method.TextKeyListener.Capitalize
import android.text.util.Linkify
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.TextView
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.util.ValueMapper

/**
 * Created by Stardust on 2017/11/3.
 * Modified by SuperMonster003 as of May 26, 2022.
 * Transformed by SuperMonster003 on Apr 12, 2023.
 */
open class TextViewInflater<V : TextView>(resourceParser: ResourceParser) : BaseViewInflater<V>(resourceParser) {

    private var mAutoText = false
    private var mCapitalize: Capitalize? = null
    private var mDrawableBottom: Drawable? = null
    private var mDrawableRight: Drawable? = null
    private var mDrawableTop: Drawable? = null
    private var mDrawableLeft: Drawable? = null
    private var mFontFamily: String? = null
    private var mTextStyle: Int? = null
    private var mTypeface: String? = null

    override fun setAttr(view: V, attrName: String, value: String, parent: ViewGroup?): Boolean {
        when (attrName) {
            "autoText" -> mAutoText = value.toBoolean()
            "capitalize" -> mCapitalize = CAPITALIZE[value]
            "drawableBottom" -> mDrawableBottom = drawables.parse(view, value)
            "drawableTop" -> mDrawableTop = drawables.parse(view, value)
            "drawableLeft" -> mDrawableLeft = drawables.parse(view, value)
            "drawableRight" -> mDrawableRight = drawables.parse(view, value)
            "fontFamily" -> mFontFamily = value
            "textStyle" -> mTextStyle = TEXT_STYLES.split(value)
            "typeface" -> mTypeface = value
            else -> return super.setAttr(view, attrName, value, parent)
        }
        return true
    }

    override fun applyPendingAttributes(view: V, parent: ViewGroup?) {
        setTypeface(view)
        setDrawables(view)
        setKeyListener(view)
    }

    private fun setKeyListener(view: V) {
        mCapitalize?.let { view.keyListener = TextKeyListener.getInstance(mAutoText, it) }
        mCapitalize = null
        mAutoText = false
    }

    private fun setDrawables(view: V) {
        val drawables = view.compoundDrawables
        view.setCompoundDrawables(
            mDrawableLeft ?: drawables[LEFT],
            mDrawableTop ?: drawables[TOP],
            mDrawableRight ?: drawables[RIGHT],
            mDrawableBottom ?: drawables[BOTTOM]
        )
        mDrawableTop = null
        mDrawableRight = null
        mDrawableBottom = null
        mDrawableLeft = null
    }

    private fun setTypeface(view: V) {
        //ignore typeface as android does
        mFontFamily?.let { mTypeface = it }

        if (mTypeface != null) {
            view.typeface = Typeface.create(mTypeface, mTextStyle ?: view.typeface.style)
        } else {
            mTextStyle?.let { view.setTypeface(view.typeface, it) }
        }
        mTypeface = null
        mFontFamily = null
        mTextStyle = null
    }

    companion object {

        const val LEFT = 0
        const val TOP = 1
        const val RIGHT = 2
        const val BOTTOM = 3

        @Suppress("DEPRECATION")
        val AUTO_LINK_MASKS: ValueMapper<Int> = ValueMapper<Int>("autoLink")
            .map("all", Linkify.ALL)
            .map("email", Linkify.EMAIL_ADDRESSES)
            .map("map", Linkify.MAP_ADDRESSES)
            .map("none", 0)
            .map("phone", Linkify.PHONE_NUMBERS)
            .map("web", Linkify.WEB_URLS)

        val ELLIPSIZE: ValueMapper<TruncateAt?> = ValueMapper<TruncateAt?>("ellipsize")
            .map("end", TruncateAt.END)
            .map("marquee", TruncateAt.MARQUEE)
            .map("none", null)
            .map("start", TruncateAt.START)
            .map("middle", TruncateAt.MIDDLE)

        val HYPHENATION_FREQUENCY: ValueMapper<Int> = ValueMapper<Int>("hyphenationFrequency")
            .map("full", 2)
            .map("none", 0)
            .map("normal", 1)

        // TODO: 2017/11/4 IME FLAG
        val IME_OPTIONS: ValueMapper<Int> = ValueMapper<Int>("imeOptions")
            .map("actionDone", EditorInfo.IME_ACTION_DONE)
            .map("actionGo", EditorInfo.IME_ACTION_DONE)
            .map("actionNext", EditorInfo.IME_ACTION_DONE)
            .map("actionNone", EditorInfo.IME_ACTION_DONE)
            .map("actionPrevious", EditorInfo.IME_ACTION_DONE)
            .map("actionSearch", EditorInfo.IME_ACTION_DONE)
            .map("actionSend", EditorInfo.IME_ACTION_DONE)
            .map("actionUnspecified", EditorInfo.IME_ACTION_DONE)

        val INPUT_TYPES: ValueMapper<Int> = ValueMapper<Int>("inputType")
            .map("date", 0x14)
            .map("datetime", 0x4)
            .map("none", 0x0)
            .map("number", 0x2)
            .map("numberDecimal", 0x2002)
            .map("numberPassword", 0x12)
            .map("numberSigned", 0x1002)
            .map("phone", 0x3)
            .map("text", 0x1)
            .map("textAutoComplete", 0x10001)
            .map("textAutoCorrect", 0x8001)
            .map("textCapCharacters", 0x1001)
            .map("textCapSentences", 0x4001)
            .map("textCapWords", 0x2001)
            .map("textEmailAddress", 0x21)
            .map("textEmailSubject", 0x31)
            .map("textFilter", 0xb1)
            .map("textImeMultiLine", 0x40001)
            .map("textLongMessage", 0x51)
            .map("textMultiLine", 0x20001)
            .map("textNoSuggestions", 0x80001)
            .map("textPassword", 0x81)
            .map("textPersonName", 0x61)
            .map("textPhonetic", 0xc1)
            .map("textPostalAddress", 0x71)
            .map("textShortMessage", 0x41)
            .map("textUri", 0x11)
            .map("textVisiblePassword", 0x91)
            .map("textWebEditText", 0xa1)
            .map("textWebEmailAddress", 0xd1)
            .map("textWebPassword", 0xe1)
            .map("time", 0x24)

        val INPUT_TYPE_NUMERIC: ValueMapper<Int> = ValueMapper<Int>("numeric")
            .map("decimal", InputType.TYPE_NUMBER_FLAG_DECIMAL)
            .map("number", InputType.TYPE_CLASS_NUMBER)
            .map("signed", InputType.TYPE_NUMBER_FLAG_SIGNED)

        @JvmField
        val TEXT_STYLES: ValueMapper<Int> = ValueMapper<Int>("textStyle")
            .map("bold", Typeface.BOLD)
            .map("italic", Typeface.ITALIC)
            .map("normal", Typeface.NORMAL)

        val CAPITALIZE: ValueMapper<Capitalize> = ValueMapper<Capitalize>("capitalize")
            .map("characters", Capitalize.CHARACTERS)
            .map("none", Capitalize.NONE)
            .map("sentences", Capitalize.SENTENCES)
            .map("words", Capitalize.WORDS)

    }

}