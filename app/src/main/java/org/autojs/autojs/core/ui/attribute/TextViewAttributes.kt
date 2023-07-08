package org.autojs.autojs.core.ui.attribute

import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.InputFilter
import android.text.InputFilter.LengthFilter
import android.text.InputType
import android.text.method.DigitsKeyListener
import android.text.method.TextKeyListener
import android.util.TypedValue
import android.view.View
import android.widget.TextView
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.inflaters.TextViewInflater
import org.autojs.autojs.core.ui.inflater.util.Dimensions
import org.autojs.autojs.core.ui.inflater.util.Gravities
import org.autojs.autojs.core.ui.inflater.util.Res
import org.autojs.autojs.core.ui.inflater.util.Strings
import org.autojs.autojs.util.ColorUtils.parse

/**
 * Created by SuperMonster003 on Apr 12, 2023.
 * Transformed by SuperMonster003 on Apr 12, 2023.
 */
open class TextViewAttributes(resourceParser: ResourceParser, view: View) : ViewAttributes(resourceParser, view) {

    private var mDrawableLeft: Drawable? = null
    private var mDrawableTop: Drawable? = null
    private var mDrawableRight: Drawable? = null
    private var mDrawableBottom: Drawable? = null
    private var mCapitalize: TextKeyListener.Capitalize? = null
    private var mAutoText = false
    private var mFontFamily: String? = null
    private var mTypeface: String? = null
    private var mTextStyle: Int? = null

    override val view = super.view as TextView

    override fun onRegisterAttrs() {
        super.onRegisterAttrs()

        registerAttr("autoLink") { view.autoLinkMask = TextViewInflater.AUTO_LINK_MASKS[it] }
        registerAttr("autoText") { mAutoText = it.toBoolean(); setKeyListener() }
        registerAttr("capitalize") { mCapitalize = TextViewInflater.CAPITALIZE[it]; setKeyListener() }
        registerAttr("digit") { setDigit(it) }
        registerAttr("drawableBottom") { mDrawableBottom = drawables.parse(view, it); setDrawables() }
        registerAttr("drawableLeft") { mDrawableLeft = drawables.parse(view, it); setDrawables() }
        registerAttr("drawablePadding") { view.compoundDrawablePadding = Dimensions.parseToIntPixel(it, view) }
        registerAttr("drawableRight") { mDrawableRight = drawables.parse(view, it); setDrawables() }
        registerAttr("drawableTop") { mDrawableTop = drawables.parse(view, it); setDrawables() }
        registerAttr("drawables") { setDrawables(it) }
        registerAttr("ellipsize") { it -> TextViewInflater.ELLIPSIZE[it]?.let { view.ellipsize = it } }
        registerAttr("ems") { view.setEms(it.toInt()) }
        registerAttr("fontFamily") { mFontFamily = it; setTypeface() }
        registerAttr("fontFeatureSettings") { view.fontFeatureSettings = Strings.parse(view, it) }
        registerAttr("freezesText") { view.freezesText = it.toBoolean() }
        registerAttr("gravity") { view.gravity = Gravities.parse(it) }
        registerAttr("hint") { view.hint = Strings.parse(view, it) }
        registerAttr("hyphenationFrequency") { view.hyphenationFrequency = TextViewInflater.HYPHENATION_FREQUENCY[it] }
        registerAttr("imeActionId") { view.setImeActionLabel(view.imeActionLabel, it.toInt()) }
        registerAttr("imeActionLabel") { view.setImeActionLabel(it, view.imeActionId) }
        registerAttr("imeOptions") { view.imeOptions = TextViewInflater.IME_OPTIONS.split(it) }
        registerAttr("includeFontPadding") { view.includeFontPadding = it.toBoolean() }
        registerAttr("inputType") { view.inputType = TextViewInflater.INPUT_TYPES.split(it) }
        registerAttr("letterSpacing") { view.letterSpacing = it.toFloat() }
        registerAttr("lineSpacingExtra") { view.setLineSpacing(Dimensions.parseToIntPixel(it, view).toFloat(), view.lineSpacingMultiplier) }
        registerAttr("lineSpacingMultiplier") { view.setLineSpacing(view.lineSpacingExtra, Dimensions.parseToIntPixel(it, view).toFloat()) }
        registerAttr("lines") { view.setLines(it.toInt()) }
        registerAttr("linksClickable") { view.linksClickable = it.toBoolean() }
        registerAttr("marqueeRepeatLimit") { view.marqueeRepeatLimit = if (it == "marquee_forever") Int.MAX_VALUE else it.toInt() }
        registerAttr("maxEms") { view.maxEms = it.toInt() }
        registerAttr("maxHeight") { view.maxHeight = Dimensions.parseToIntPixel(it, view) }
        registerAttr("maxLength") { view.filters = arrayOf<InputFilter>(LengthFilter(it.toInt())) }
        registerAttr("maxLines") { view.maxLines = it.toInt() }
        registerAttr("maxWidth") { view.maxWidth = Dimensions.parseToIntPixel(it, view) }
        registerAttr("minEms") { view.minEms = it.toInt() }
        registerAttr("minHeight") { view.minHeight = Dimensions.parseToIntPixel(it, view) }
        registerAttr("minLines") { view.minLines = it.toInt() }
        registerAttr("minWidth") { view.minWidth = Dimensions.parseToIntPixel(it, view) }
        registerAttr("numeric") { view.inputType = TextViewInflater.INPUT_TYPE_NUMERIC.split(it) or InputType.TYPE_CLASS_NUMBER }
        registerAttr("password") { if (it == "true") view.inputType = view.inputType or InputType.TYPE_TEXT_VARIATION_PASSWORD }
        registerAttr("phoneNumber") { if (it == "true") view.inputType = view.inputType or InputType.TYPE_TEXT_VARIATION_PHONETIC }
        registerAttr("privateImeOptions") { view.privateImeOptions = Strings.parse(view, it) }
        registerAttr("scrollHorizontally") { view.setHorizontallyScrolling(it.toBoolean()) }
        registerAttr("selectAllOnFocus") { view.setSelectAllOnFocus(it.toBoolean()) }
        registerAttr("shadowColor") { view.setShadowLayer(view.shadowRadius, view.shadowDx, view.shadowDy, parse(view, it)) }
        registerAttr("shadowDx") { view.setShadowLayer(view.shadowRadius, Dimensions.parseToPixel(it, view), view.shadowDy, view.shadowColor) }
        registerAttr("shadowDy") { view.setShadowLayer(view.shadowRadius, view.shadowDx, Dimensions.parseToPixel(it, view), view.shadowColor) }
        registerAttr("shadowRadius") { view.setShadowLayer(Dimensions.parseToPixel(it, view), view.shadowDx, view.shadowDy, view.shadowColor) }
        registerAttr("text") { view.text = Strings.parse(view, it) }
        registerAttr("textAppearance") { view.setTextAppearance(Res.parseStyle(view, it)) }
        registerAttr("textIsSelectable") { view.setTextIsSelectable(it.toBoolean()) }
        registerAttr("textScaleX") { view.textScaleX = Dimensions.parseToPixel(it, view) }
        registerAttr("textStyle") { mTextStyle = TextViewInflater.TEXT_STYLES.split(it); setTypeface() }
        registerAttr("typeface") { mTypeface = it; setTypeface() }
        registerAttrs(arrayOf("highlightTextColor", "textColorHighlight")) { view.highlightColor = parse(view, it) }
        registerAttrs(arrayOf("hintTextColor", "textColorHint")) { view.setHintTextColor(parse(view, it)) }
        registerAttrs(arrayOf("isAllCaps", "allCaps", "textAllCaps")) { view.isAllCaps = it.toBoolean() }
        registerAttrs(arrayOf("isCursorVisible", "cursorVisible")) { view.isCursorVisible = it.toBoolean() }
        registerAttrs(arrayOf("isElegantTextHeight", "elegantTextHeight")) { view.isElegantTextHeight = it.toBoolean() }
        registerAttrs(arrayOf("isSingleLine", "singleLine")) { view.isSingleLine = it.toBoolean() }
        registerAttrs(arrayOf("linkTextColor", "textColorLink")) { view.setLinkTextColor(parse(view, it)) }
        registerAttrs(arrayOf("textColor", "color")) { view.setTextColor(parse(view.context, it)) }
        registerAttrs(arrayOf("textSize", "size")) { view.setTextSize(TypedValue.COMPLEX_UNIT_PX, Dimensions.parseToPixel(it, view)) }

        registerAttrUnsupported(
            arrayOf(
                "drawableStart",
                "drawableEnd",
                "editable",
                "editorExtras",
                "inputMethod",
            )
        )

    }

    private fun setDigit(value: String) {
        if (value == "true") {
            @Suppress("DEPRECATION")
            view.keyListener = DigitsKeyListener.getInstance()
        } else if (value != "false") {
            view.keyListener = DigitsKeyListener.getInstance(value)
        }
    }

    private fun setDrawables(value: String) {
        val values = parseAttrValue(value)
        when (values.size) {
            1 -> {
                mDrawableLeft = drawables.parse(view, values[0])
                mDrawableTop = drawables.parse(view, values[0])
                mDrawableRight = drawables.parse(view, values[0])
                mDrawableBottom = drawables.parse(view, values[0])
            }
            2 -> {
                mDrawableLeft = drawables.parse(view, values[0])
                mDrawableTop = drawables.parse(view, values[1])
                mDrawableRight = drawables.parse(view, values[0])
                mDrawableBottom = drawables.parse(view, values[1])
            }
            3 -> {
                mDrawableLeft = drawables.parse(view, values[0])
                mDrawableTop = drawables.parse(view, values[1])
                mDrawableRight = drawables.parse(view, values[2])
                mDrawableBottom = drawables.parse(view, values[1])
            }
            4 -> {
                mDrawableLeft = drawables.parse(view, values[0])
                mDrawableTop = drawables.parse(view, values[1])
                mDrawableRight = drawables.parse(view, values[2])
                mDrawableBottom = drawables.parse(view, values[3])
            }
        }
        setDrawables()
    }

    private fun setKeyListener() {
        mCapitalize?.let { view.keyListener = TextKeyListener.getInstance(mAutoText, it) }
    }

    private fun setDrawables() {
        view.compoundDrawables.let {
            view.setCompoundDrawables(
                mDrawableLeft ?: it[TextViewInflater.LEFT],
                mDrawableTop ?: it[TextViewInflater.TOP],
                mDrawableRight ?: it[TextViewInflater.RIGHT],
                mDrawableBottom ?: it[TextViewInflater.BOTTOM],
            )
        }
    }

    private fun setTypeface() {
        if (mFontFamily != null) {
            //ignore typeface as android does
            mTypeface = mFontFamily
        }
        if (mTypeface != null) {
            view.typeface = Typeface.create(mTypeface, mTextStyle ?: view.typeface.style)
        } else {
            mTextStyle?.let { view.setTypeface(view.typeface, it) }
        }
    }

}