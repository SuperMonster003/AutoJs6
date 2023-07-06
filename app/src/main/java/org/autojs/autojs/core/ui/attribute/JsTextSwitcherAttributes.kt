package org.autojs.autojs.core.ui.attribute

import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.text.InputFilter
import android.text.InputType
import android.text.method.DigitsKeyListener
import android.text.method.TextKeyListener
import android.util.TypedValue
import android.view.View
import android.view.animation.AnimationUtils
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.inflaters.TextViewInflater
import org.autojs.autojs.core.ui.inflater.util.Dimensions
import org.autojs.autojs.core.ui.inflater.util.Gravities
import org.autojs.autojs.core.ui.inflater.util.Res
import org.autojs.autojs.core.ui.inflater.util.Strings
import org.autojs.autojs.core.ui.widget.JsTextSwitcher
import org.autojs.autojs.core.ui.widget.JsTextView
import org.autojs.autojs.util.ColorUtils
import org.autojs.autojs6.R
import kotlin.text.RegexOption.IGNORE_CASE

class JsTextSwitcherAttributes(resourceParser: ResourceParser, view: View) : TextSwitcherAttributes(resourceParser, view) {

    override val view = super.view as JsTextSwitcher

    private var mDrawableLeft: Drawable? = null
    private var mDrawableTop: Drawable? = null
    private var mDrawableRight: Drawable? = null
    private var mDrawableBottom: Drawable? = null
    private var mCapitalize: TextKeyListener.Capitalize? = null
    private var mAutoText = false
    private var mFontFamily: String? = null
    private var mTypeface: String? = null
    private var mTextStyle: Int? = null

    override fun onRegisterAttrs() {
        super.onRegisterAttrs()

        val textViews by lazy { view.textViews }

        registerAttr("autoLink") { value -> textViews.forEach { it.autoLinkMask = TextViewInflater.AUTO_LINK_MASKS[value] } }
        registerAttr("autoText") { value -> mAutoText = value.toBoolean(); setKeyListener(textViews) }
        registerAttr("capitalize") { value -> mCapitalize = TextViewInflater.CAPITALIZE[value]; setKeyListener(textViews) }
        registerAttr("digit") { value -> setDigit(textViews, value) }
        registerAttr("drawableBottom") { value -> mDrawableBottom = drawables.parse(view, value); setDrawables(textViews) }
        registerAttr("drawableLeft") { value -> mDrawableLeft = drawables.parse(view, value); setDrawables(textViews) }
        registerAttr("drawablePadding") { value -> textViews.forEach { it.compoundDrawablePadding = Dimensions.parseToIntPixel(value, view) } }
        registerAttr("drawableRight") { value -> mDrawableRight = drawables.parse(view, value); setDrawables(textViews) }
        registerAttr("drawableTop") { value -> mDrawableTop = drawables.parse(view, value); setDrawables(textViews) }
        registerAttr("drawables") { value -> setDrawables(textViews, value) }
        registerAttr("ellipsize") { value -> TextViewInflater.ELLIPSIZE[value]?.let { ellipsize -> textViews.forEach { it.ellipsize = ellipsize } } }
        registerAttr("ems") { value -> textViews.forEach { it.setEms(value.toInt()) } }
        registerAttr("fontFamily") { value -> mFontFamily = value; textViews.forEach { setTypeface(it) } }
        registerAttr("fontFeatureSettings") { value -> textViews.forEach { it.fontFeatureSettings = Strings.parse(view, value) } }
        registerAttr("freezesText") { value -> textViews.forEach { it.freezesText = value.toBoolean() } }
        registerAttr("gravity") { value -> textViews.forEach { it.gravity = Gravities.parse(value) } }
        registerAttr("hint") { value -> textViews.forEach { it.hint = Strings.parse(view, value) } }
        registerAttr("hyphenationFrequency") { value -> textViews.forEach { it.hyphenationFrequency = TextViewInflater.HYPHENATION_FREQUENCY[value] } }
        registerAttr("imeActionId") { value -> textViews.forEach { it.setImeActionLabel(it.imeActionLabel, value.toInt()) } }
        registerAttr("imeActionLabel") { value -> textViews.forEach { it.setImeActionLabel(value, it.imeActionId) } }
        registerAttr("imeOptions") { value -> textViews.forEach { it.imeOptions = TextViewInflater.IME_OPTIONS.split(value) } }
        registerAttr("includeFontPadding") { value -> textViews.forEach { it.includeFontPadding = value.toBoolean() } }
        registerAttr("inputType") { value -> textViews.forEach { it.inputType = TextViewInflater.INPUT_TYPES.split(value) } }
        registerAttr("letterSpacing") { value -> textViews.forEach { it.letterSpacing = value.toFloat() } }
        registerAttr("lineSpacingExtra") { value -> textViews.forEach { it.setLineSpacing(Dimensions.parseToIntPixel(value, it).toFloat(), it.lineSpacingMultiplier) } }
        registerAttr("lineSpacingMultiplier") { value -> textViews.forEach { it.setLineSpacing(it.lineSpacingExtra, Dimensions.parseToIntPixel(value, it).toFloat()) } }
        registerAttr("lines") { value -> textViews.forEach { it.setLines(value.toInt()) } }
        registerAttr("linksClickable") { value -> textViews.forEach { it.linksClickable = value.toBoolean() } }
        registerAttr("marqueeRepeatLimit") { value -> textViews.forEach { it.marqueeRepeatLimit = if (value == "marquee_forever") Int.MAX_VALUE else value.toInt() } }
        registerAttr("maxEms") { value -> textViews.forEach { it.maxEms = value.toInt() } }
        registerAttr("maxHeight") { value -> textViews.forEach { it.maxHeight = Dimensions.parseToIntPixel(value, it) } }
        registerAttr("maxLength") { value -> textViews.forEach { it.filters = arrayOf<InputFilter>(InputFilter.LengthFilter(value.toInt())) } }
        registerAttr("maxLines") { value -> textViews.forEach { it.maxLines = value.toInt() } }
        registerAttr("maxWidth") { value -> textViews.forEach { it.maxWidth = Dimensions.parseToIntPixel(value, view) } }
        registerAttr("minEms") { value -> textViews.forEach { it.minEms = value.toInt() } }
        registerAttr("minHeight") { value -> textViews.forEach { it.minHeight = Dimensions.parseToIntPixel(value, it) } }
        registerAttr("minLines") { value -> textViews.forEach { it.minLines = value.toInt() } }
        registerAttr("minWidth") { value -> textViews.forEach { it.minWidth = Dimensions.parseToIntPixel(value, view) } }
        registerAttr("numeric") { value -> textViews.forEach { it.inputType = TextViewInflater.INPUT_TYPE_NUMERIC.split(value) or InputType.TYPE_CLASS_NUMBER } }
        registerAttr("password") { value -> if (value == "true") textViews.forEach { it.inputType = it.inputType or InputType.TYPE_TEXT_VARIATION_PASSWORD } }
        registerAttr("phoneNumber") { value -> if (value == "true") textViews.forEach { it.inputType = it.inputType or InputType.TYPE_TEXT_VARIATION_PHONETIC } }
        registerAttr("privateImeOptions") { value -> textViews.forEach { it.privateImeOptions = Strings.parse(view, value) } }
        registerAttr("scrollHorizontally") { value -> textViews.forEach { it.setHorizontallyScrolling(value.toBoolean()) } }
        registerAttr("selectAllOnFocus") { value -> textViews.forEach { it.setSelectAllOnFocus(value.toBoolean()) } }
        registerAttr("shadowColor") { value -> textViews.forEach { it.setShadowLayer(it.shadowRadius, it.shadowDx, it.shadowDy, ColorUtils.parse(it, value)) } }
        registerAttr("shadowDx") { value -> textViews.forEach { it.setShadowLayer(it.shadowRadius, Dimensions.parseToPixel(value, it), it.shadowDy, it.shadowColor) } }
        registerAttr("shadowDy") { value -> textViews.forEach { it.setShadowLayer(it.shadowRadius, it.shadowDx, Dimensions.parseToPixel(value, it), it.shadowColor) } }
        registerAttr("shadowRadius") { value -> textViews.forEach { it.setShadowLayer(Dimensions.parseToPixel(value, it), it.shadowDx, it.shadowDy, it.shadowColor) } }
        registerAttr("text") { value -> textViews.forEach { it.text = Strings.parse(it, value) } }
        registerAttr("textAppearance") { value -> textViews.forEach { it.setTextAppearance(Res.parseStyle(it, value)) } }
        registerAttr("textIsSelectable") { value -> textViews.forEach { it.setTextIsSelectable(value.toBoolean()) } }
        registerAttr("textScaleX") { value -> textViews.forEach { it.textScaleX = Dimensions.parseToPixel(value, it) } }
        registerAttr("textStyle") { value -> mTextStyle = TextViewInflater.TEXT_STYLES.split(value); textViews.forEach { setTypeface(it) } }
        registerAttr("typeface") { value -> mTypeface = value; textViews.forEach { setTypeface(it) } }
        registerAttrs(arrayOf("highlightTextColor", "textColorHighlight")) { value -> textViews.forEach { it.highlightColor = ColorUtils.parse(view, value) } }
        registerAttrs(arrayOf("hintTextColor", "textColorHint")) { value -> textViews.forEach { it.setHintTextColor(ColorUtils.parse(view, value)) } }
        registerAttrs(arrayOf("isAllCaps", "allCaps", "textAllCaps")) { value -> textViews.forEach { it.isAllCaps = value.toBoolean() } }
        registerAttrs(arrayOf("isCursorVisible", "cursorVisible")) { value -> textViews.forEach { it.isCursorVisible = value.toBoolean() } }
        registerAttrs(arrayOf("isElegantTextHeight", "elegantTextHeight")) { value -> textViews.forEach { it.isElegantTextHeight = value.toBoolean() } }
        registerAttrs(arrayOf("isSingleLine", "singleLine")) { value -> textViews.forEach { it.isSingleLine = value.toBoolean() } }
        registerAttrs(arrayOf("linkTextColor", "textColorLink")) { value -> textViews.forEach { it.setLinkTextColor(ColorUtils.parse(view, value)) } }
        registerAttrs(arrayOf("textColor", "color")) { value -> textViews.forEach { it.setTextColor(ColorUtils.parse(it.context, value)) } }
        registerAttrs(arrayOf("textSize", "size")) { value -> textViews.forEach { it.setTextSize(TypedValue.COMPLEX_UNIT_PX, Dimensions.parseToPixel(value, it)) } }

        registerAttrs(arrayOf("anim", "animation")) { setAnimations(it) }
        registerAttrs(arrayOf("animIn", "inAnim", "inAnimation")) { setInAnimation(Strings.parseAnimation(view, it)) }
        registerAttrs(arrayOf("animOut", "outAnim", "outAnimation")) { setOutAnimation(Strings.parseAnimation(view, it)) }

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

    private fun setAnimations(value: String) {
        mapOf(
            Regex("from.*left|(to.*)?right|from.*left.*to.*right", IGNORE_CASE) to { setAnimations(R.anim.slide_in_left, R.anim.slide_out_right) },
            Regex("from.*(top|up)|(to.*)?(bottom|down)|from.*(top|up).*to.*(bottom|down)", IGNORE_CASE) to { setAnimations(R.anim.slide_in_top, R.anim.slide_out_bottom) },
            Regex("from.*right|(to.*)?left|from.*right.*to.*left", IGNORE_CASE) to { setAnimations(R.anim.slide_in_right, R.anim.slide_out_left) },
            Regex("from.*(bottom|down)|(to.*)?(top|up)|from.*(bottom|down).*to.*(top|up)", IGNORE_CASE) to { setAnimations(R.anim.slide_in_bottom, R.anim.slide_out_top) },
            Regex("micro", IGNORE_CASE) to { setAnimations(R.anim.slide_in_micro, R.anim.slide_out_micro) },
            Regex("fade", IGNORE_CASE) to { setAnimations(R.anim.fade_in, R.anim.fade_out) },
            Regex("fast.*Fade|fade.*Fast", IGNORE_CASE) to { setAnimations(R.anim.fast_fade_in, R.anim.fast_fade_out) },
            Regex("shrink", IGNORE_CASE) to { setAnimations(R.anim.grow_fade_in, R.anim.shrink_fade_out) },
        ).forEach { entry ->
            if (entry.key.matches(value)) {
                entry.value.invoke().also { return }
            }
        }
        throw Exception("Can't parse animations for $value")
    }

    private fun setAnimations(`in`: Int, out: Int) {
        setInAnimation(`in`)
        setOutAnimation(out)
    }

    private fun setInAnimation(`in`: Int) {
        view.inAnimation = AnimationUtils.loadAnimation(view.context, `in`)
    }

    private fun setOutAnimation(out: Int) {
        view.outAnimation = AnimationUtils.loadAnimation(view.context, out)
    }

    private fun setDigit(textViews: MutableList<JsTextView>, value: String) {
        if (value == "true") {
            @Suppress("DEPRECATION")
            textViews.forEach { it.keyListener = DigitsKeyListener.getInstance() }
        } else if (value != "false") {
            textViews.forEach { it.keyListener = DigitsKeyListener.getInstance(value) }
        }
    }

    private fun setDrawables(textViews: MutableList<JsTextView>, value: String) {
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
        setDrawables(textViews)
    }

    private fun setKeyListener(textViews: MutableList<JsTextView>) {
        mCapitalize?.let { value -> textViews.forEach { it.keyListener = TextKeyListener.getInstance(mAutoText, value) } }
    }

    private fun setDrawables(textViews: MutableList<JsTextView>) {
        textViews.forEach { view ->
            view.compoundDrawables.let {
                view.setCompoundDrawables(
                    mDrawableLeft ?: it[TextViewInflater.LEFT],
                    mDrawableTop ?: it[TextViewInflater.TOP],
                    mDrawableRight ?: it[TextViewInflater.RIGHT],
                    mDrawableBottom ?: it[TextViewInflater.BOTTOM],
                )
            }
        }
    }

    private fun setTypeface(view: JsTextView) {
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