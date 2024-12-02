@file:Suppress("SameParameterValue")

package org.autojs.autojs.core.ui.attribute

import android.content.res.ColorStateList
import android.graphics.PorterDuff
import android.graphics.drawable.Drawable
import android.view.InflateException
import android.view.View
import android.view.ViewGroup
import android.view.ViewGroup.LayoutParams
import android.view.ViewGroup.MarginLayoutParams
import android.widget.CalendarView
import android.widget.DatePicker
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.RelativeLayout
import androidx.annotation.CallSuper
import androidx.core.view.ViewCompat
import org.autojs.autojs.core.internal.Functions.VoidFunc2
import org.autojs.autojs.core.ui.BiMap
import org.autojs.autojs.core.ui.inflater.Exceptions
import org.autojs.autojs.core.ui.inflater.ResourceParser
import org.autojs.autojs.core.ui.inflater.util.Dimensions
import org.autojs.autojs.core.ui.inflater.util.Drawables
import org.autojs.autojs.core.ui.inflater.util.Gravities
import org.autojs.autojs.core.ui.inflater.util.Ids
import org.autojs.autojs.core.ui.inflater.util.Strings
import org.autojs.autojs.core.ui.inflater.util.ValueMapper
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.util.ColorUtils
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

open class ViewAttributes(scriptRuntime: ScriptRuntime, resourceParser: ResourceParser, open val view: View) {

    val drawables: Drawables = resourceParser.drawables

    private val mAttributes: MutableMap<String, Attribute> = HashMap()
    private var mViewAttributeDelegate: ViewAttributeDelegate? = null

    init {
        this.onRegisterAttrs(scriptRuntime)
    }

    interface Getter<T> {
        fun get(): T
    }

    interface Setter<T> {
        fun set(value: T)
    }

    interface Attribute {
        fun get(): String?
        fun set(value: String)
    }

    protected interface AttributeSetter : Setter<String>

    protected interface AttributeGetter : Getter<String>

    protected interface ValueConverter<T> {
        fun convert(value: String): T
    }

    protected class BaseAttribute(private val mAttributeSetter: AttributeSetter) : Attribute {

        private var mValue: String? = null

        override fun get(): String? {
            return mValue
        }

        override fun set(value: String) {
            mValue = value
            mAttributeSetter.set(value)
        }
    }

    protected class MappingAttributeSetter<T>(private val mValueConverter: ValueConverter<T>, private val mSetter: Setter<T>) : AttributeSetter {
        override fun set(value: String) {
            mSetter.set(mValueConverter.convert(value))
        }
    }

    fun setViewAttributeDelegate(viewAttributeDelegate: ViewAttributeDelegate?) {
        mViewAttributeDelegate = viewAttributeDelegate
    }

    operator fun contains(name: String): Boolean {
        return mAttributes.containsKey(name) || mViewAttributeDelegate != null && mViewAttributeDelegate!!.has(name)
    }

    operator fun get(name: String): Attribute? {
        return if (mViewAttributeDelegate != null && mViewAttributeDelegate!!.has(name)) {
            object : Attribute {
                override fun get(): String? {
                    return mViewAttributeDelegate!![view, name, ::getAttrValue]
                }

                override fun set(value: String) {
                    mViewAttributeDelegate!![view, name, value] = ::setAttrValue
                }
            }
        } else mAttributes[name]
    }

    fun getAttrValue(name: String): String? {
        val attribute = mAttributes[name]
        return attribute?.get()
    }

    fun setAttrValue(name: String, value: String) {
        val attribute = mAttributes[name]
        attribute?.set(value)
    }

    @CallSuper
    protected open fun onRegisterAttrs(scriptRuntime: ScriptRuntime) {
        registerAttr("id") { view.id = Ids.parse(it) }
        registerAttr("gravity", Gravities::parse, ::setGravity)
        registerAttrs(arrayOf("width", "w", "layout_width", "layoutWidth"), { parseDimension(it, true) }, ::setWidth)
        registerAttrs(arrayOf("height", "h", "layout_height", "layoutHeight"), { parseDimension(it, false) }, ::setHeight)
        registerAttrs(arrayOf("background", "bg")) { drawables.setupWithViewBackground(view, it) }
        registerAttrs(arrayOf("backgroundColor", "bgColor")) { view.setBackgroundColor(ColorUtils.parse(view, it)) }
        registerAttrs(arrayOf("backgroundTint", "bgTint")) { setBackgroundTint(ColorUtils.parse(view, it)) }
        registerAttrs(arrayOf("backgroundTintMode", "bgTintMode")) { ViewCompat.setBackgroundTintMode(view, TINT_MODES[it]) }
        registerAttrs(arrayOf("foreground", "fg")) { setForeground(drawables.parse(view, it)) }
        registerAttrs(arrayOf("foregroundGravity", "fgGravity"), Gravities::parse, ::setForegroundGravity)
        registerAttrs(arrayOf("foregroundTint", "fgTint")) { view.foregroundTintList = ColorUtils.toColorStateList(view, it) }
        registerAttrs(arrayOf("foregroundTintMode", "fgTintMode"), { TINT_MODES[it] }, ::setForegroundTintMode)
        registerAttrs(arrayOf("layout_gravity", "layoutGravity"), Gravities::parse, ::setLayoutGravity)
        registerAttrs(arrayOf("layout_weight", "layoutWeight"), String::toFloat, ::setLayoutWeight)
        registerAttrs(arrayOf("layout_margin", "layoutMargin"), ::setMargin)
        registerAttrs(arrayOf("layout_marginLeft", "layoutMarginLeft"), { parseDimension(it, true) }, ::setMarginLeft)
        registerAttrs(arrayOf("layout_marginRight", "layoutMarginRight"), { parseDimension(it, true) }, ::setMarginRight)
        registerAttrs(arrayOf("layout_marginTop", "layoutMarginTop"), { parseDimension(it, false) }, ::setMarginTop)
        registerAttrs(arrayOf("layout_marginBottom", "layoutMarginBottom"), { parseDimension(it, false) }, ::setMarginBottom)
        registerAttrs(arrayOf("layout_marginStart", "layoutMarginStart"), { parseDimension(it, true) }, ::setMarginStart)
        registerAttrs(arrayOf("layout_marginEnd", "layoutMarginEnd"), { parseDimension(it, true) }, ::setMarginEnd)
        registerAttrs(arrayOf("layout_marginVertical", "layoutMarginVertical"), { parseDimension(it, true) }, ::setMarginVertical)
        registerAttrs(arrayOf("layout_marginHorizontal", "layoutMarginHorizontal"), { parseDimension(it, true) }, ::setMarginHorizontal)
        registerAttrs(arrayOf("layout_alignParentBottom", "layoutAlignParentBottom")) { setLayoutRule(RelativeLayout.ALIGN_PARENT_BOTTOM, false, it) }
        registerAttrs(arrayOf("layout_alignParentTop", "layoutAlignParentTop")) { setLayoutRule(RelativeLayout.ALIGN_PARENT_TOP, false, it) }
        registerAttrs(arrayOf("layout_alignParentLeft", "layoutAlignParentLeft")) { setLayoutRule(RelativeLayout.ALIGN_PARENT_LEFT, false, it) }
        registerAttrs(arrayOf("layout_alignParentStart", "layoutAlignParentStart")) { setLayoutRule(RelativeLayout.ALIGN_PARENT_START, false, it) }
        registerAttrs(arrayOf("layout_alignParentRight", "layoutAlignParentRight")) { setLayoutRule(RelativeLayout.ALIGN_PARENT_RIGHT, false, it) }
        registerAttrs(arrayOf("layout_alignParentEnd", "layoutAlignParentEnd")) { setLayoutRule(RelativeLayout.ALIGN_PARENT_END, false, it) }
        registerAttrs(arrayOf("layout_centerHorizontal", "layoutCenterHorizontal")) { setLayoutRule(RelativeLayout.CENTER_HORIZONTAL, false, it) }
        registerAttrs(arrayOf("layout_centerVertical", "layoutCenterVertical")) { setLayoutRule(RelativeLayout.CENTER_VERTICAL, false, it) }
        registerAttrs(arrayOf("layout_centerInParent", "layoutCenterInParent")) { setLayoutRule(RelativeLayout.CENTER_IN_PARENT, false, it) }
        registerAttrs(arrayOf("layout_below", "layoutBelow")) { setLayoutRule(RelativeLayout.BELOW, true, it) }
        registerAttrs(arrayOf("layout_above", "layoutAbove")) { setLayoutRule(RelativeLayout.ABOVE, true, it) }
        registerAttrs(arrayOf("layout_toLeftOf", "layoutToLeftOf")) { setLayoutRule(RelativeLayout.LEFT_OF, true, it) }
        registerAttrs(arrayOf("layout_toRightOf", "layoutToRightOf")) { setLayoutRule(RelativeLayout.RIGHT_OF, true, it) }
        registerAttrs(arrayOf("layout_alignBottom", "layoutAlignBottom")) { setLayoutRule(RelativeLayout.ALIGN_BOTTOM, true, it) }
        registerAttrs(arrayOf("layout_alignTop", "layoutAlignTop")) { setLayoutRule(RelativeLayout.ALIGN_TOP, true, it) }
        registerAttrs(arrayOf("layout_alignLeft", "layoutAlignLeft")) { setLayoutRule(RelativeLayout.ALIGN_LEFT, true, it) }
        registerAttrs(arrayOf("layout_alignStart", "layoutAlignStart")) { setLayoutRule(RelativeLayout.ALIGN_START, true, it) }
        registerAttrs(arrayOf("layout_alignRight", "layoutAlignRight")) { setLayoutRule(RelativeLayout.ALIGN_RIGHT, true, it) }
        registerAttrs(arrayOf("layout_alignEnd", "layoutAlignEnd")) { setLayoutRule(RelativeLayout.ALIGN_END, true, it) }
        registerAttr("padding", ::setPadding)
        registerAttr("paddingLeft", { parseDimension(it, true) }, ::setPaddingLeft)
        registerAttr("paddingRight", { parseDimension(it, true) }, ::setPaddingRight)
        registerAttr("paddingTop", { parseDimension(it, false) }, ::setPaddingTop)
        registerAttr("paddingBottom", { parseDimension(it, false) }, ::setPaddingBottom)
        registerAttr("paddingStart", { parseDimension(it, true) }, ::setPaddingStart)
        registerAttr("paddingEnd", { parseDimension(it, true) }, ::setPaddingEnd)
        registerAttr("paddingVertical", { parseDimension(it, true) }, ::setPaddingVertical)
        registerAttr("paddingHorizontal", { parseDimension(it, true) }, ::setPaddingHorizontal)
        registerAttr("alpha") { view.alpha = it.toFloat() }
        registerAttrs(arrayOf("isClickable", "clickable")) { view.isClickable = it.toBoolean() }
        registerAttr("contentDescription") { view.contentDescription = parseString(it) }
        registerAttrs(arrayOf("isContextClickable", "contextClickable")) { setContextClickable(it.toBoolean()) }
        registerAttrs(arrayOf("isDuplicateParentStateEnabled", "duplicateParentStateEnabled", "duplicateParentState", "enableDuplicateParentState")) { view.isDuplicateParentStateEnabled = it.toBoolean() }
        registerAttr("elevation", ::parseDimensionToIntPixel, ::setElevation)
        registerAttrs(arrayOf("isScrollbarFadingEnabled", "scrollbarFadingEnabled", "fadeScrollbars", "enableFadeScrollbars")) { view.isScrollbarFadingEnabled = it.toBoolean() }
        registerAttr("fadingEdgeLength", ::parseDimensionToIntPixel) { view.setFadingEdgeLength(it) }
        registerAttr("filterTouchesWhenObscured") { view.filterTouchesWhenObscured = it.toBoolean() }
        registerAttr("fitsSystemWindows") { view.fitsSystemWindows = it.toBoolean() }
        registerAttrs(arrayOf("isFocusable", "focusable")) { view.isFocusable = it.toBoolean() }
        registerAttrs(arrayOf("isFocusableInTouchMode", "focusableInTouchMode")) { view.isFocusableInTouchMode = it.toBoolean() }
        registerAttr("forceHasOverlappingRendering") { forceHasOverlappingRendering(it.toBoolean()) }
        registerAttrs(arrayOf("isHapticFeedbackEnabled", "hapticFeedbackEnabled", "enableHapticFeedback")) { view.isHapticFeedbackEnabled = it.toBoolean() }
        registerAttr("importantForAccessibility") { view.importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY[it] }
        registerAttr("isScrollContainer") { view.isScrollContainer = it.toBoolean() }
        registerAttr("keepScreenOn") { view.keepScreenOn = it.toBoolean() }
        registerAttr("layoutDirection") { view.layoutDirection = LAYOUT_DIRECTIONS[it] }
        registerAttrs(arrayOf("isLongClickable", "longClickable")) { view.isLongClickable = it.toBoolean() }
        registerAttr("minHeight") { view.minimumHeight = parseDimensionToIntPixel(it) }
        registerAttr("minWidth") { view.minimumWidth = parseDimensionToIntPixel(it) }
        registerAttr("requiresFadingEdge", ::requiresFadingEdge)
        registerAttr("rotation") { view.rotation = it.toFloat() }
        registerAttr("rotationX") { view.rotationX = it.toFloat() }
        registerAttr("rotationY") { view.rotationY = it.toFloat() }
        registerAttrs(arrayOf("isSaveEnabled", "saveEnabled", "enableSave")) { view.isSaveEnabled = it.toBoolean() }
        registerAttr("scaleX") { view.scaleX = it.toFloat() }
        registerAttr("scaleY") { view.scaleY = it.toFloat() }
        registerAttr("scrollIndicators") { ViewCompat.setScrollIndicators(view, SCROLL_INDICATORS[it]) }
        registerAttr("scrollX") { view.scrollX = parseDimensionToIntPixel(it) }
        registerAttr("scrollY") { view.scrollY = parseDimensionToIntPixel(it) }
        registerAttr("scrollbarDefaultDelayBeforeFade") { view.scrollBarDefaultDelayBeforeFade = it.toInt() }
        registerAttr("scrollbarFadeDuration") { view.scrollBarFadeDuration = it.toInt() }
        registerAttr("scrollbarSize") { view.scrollBarSize = parseDimensionToIntPixel(it) }
        registerAttr("scrollbarStyle") { view.scrollBarStyle = SCROLLBARS_STYLES[it] }
        registerAttr("scrollbars", ::setScrollbars)
        registerAttrs(arrayOf("isSoundEffectsEnabled", "soundEffectsEnabled", "enableSoundEffects")) { view.isSoundEffectsEnabled = it.toBoolean() }
        registerAttr("tag") { view.tag = parseString(it) }
        registerAttr("textAlignment") { view.textAlignment = TEXT_ALIGNMENTS[it] }
        registerAttr("textDirection") { view.textDirection = TEXT_DIRECTIONS[it] }
        registerAttr("transformPivotX") { view.pivotX = parseDimensionToPixel(it) }
        registerAttr("transformPivotY") { view.pivotY = parseDimensionToPixel(it) }
        registerAttr("translationX") { view.translationX = parseDimensionToPixel(it) }
        registerAttr("translationY") { view.translationY = parseDimensionToPixel(it) }
        registerAttr("translationZ") { ViewCompat.setTranslationZ(view, parseDimensionToPixel(it)) }
        registerAttr("transitionName") { ViewCompat.setTransitionName(view, parseString(it)) }
        registerAttr("visibility") { view.visibility = VISIBILITY[it] }

        @Suppress("DEPRECATION")
        registerAttr("drawingCacheQuality") { view.drawingCacheQuality = DRAWABLE_CACHE_QUALITIES[it] }

        registerAttrUnsupported(
            arrayOf(
                "accessibilityLiveRegion",
                "accessibilityTraversalAfter",
                "accessibilityTraversalBefore",
                "autofillHints",
                "autofilledHighlight",
                "defaultFocusHighlightEnabled",
                "focusedByDefault",
                "importantForAutofill",
                "keyboardNavigationCluster",
                "layerType",
                "nextClusterForward",
                "nextFocusDown",
                "nextFocusForward",
                "nextFocusLeft",
                "nextFocusRight",
                "nextFocusUp",
                "onClick",
                "paddingHorizontal",
                "paddingVertical",
                "scrollbarAlwaysDrawHorizontalTrack",
                "scrollbarAlwaysDrawVerticalTrack",
                "scrollbarThumbHorizontal",
                "scrollbarThumbVertical",
                "scrollbarTrackHorizontal",
                "scrollbarTrackVertical",
                "stateListAnimator",
                "tooltipText",
            )
        )
    }

    private fun setForegroundTintMode(mode: PorterDuff.Mode?) {
        view.foregroundTintMode = mode
    }

    protected fun setForegroundGravity(g: Int) {
        view.foregroundGravity = g
    }

    protected fun setForeground(foreground: Drawable?) {
        view.foreground = foreground
    }

    private fun forceHasOverlappingRendering(b: Boolean) {
        view.forceHasOverlappingRendering(b)
    }

    protected fun setElevation(e: Int) {
        ViewCompat.setElevation(view, e.toFloat())
    }

    private fun setScrollbars(scrollbars: String) {
        scrollbars.split("\\|".toRegex()).forEach { str ->
            when (str) {
                "horizontal" -> view.isHorizontalScrollBarEnabled = true
                "vertical" -> view.isVerticalScrollBarEnabled = true
            }
        }
    }

    private fun requiresFadingEdge(fadingEdge: String) {
        fadingEdge.split("\\|".toRegex()).forEach { str ->
            when (str) {
                "horizontal" -> view.isHorizontalFadingEdgeEnabled = true
                "vertical" -> view.isVerticalFadingEdgeEnabled = true
            }
        }
    }

    protected fun parseDimensionToPixel(value: String): Float {
        return Dimensions.parseToPixel(view, value)
    }

    private fun parseDimensionToIntPixel(value: String): Int {
        return Dimensions.parseToIntPixel(value, view)
    }

    private fun parseDimension(dim: String, isHorizontal: Boolean): Int {
        return when (dim) {
            "wrap_content" -> LayoutParams.WRAP_CONTENT
            "fill_parent", "match_parent" -> LayoutParams.MATCH_PARENT
            else -> Dimensions.parseToPixel(dim, view, view.parent as? ViewGroup, isHorizontal)
        }
    }

    protected fun registerAttr(name: String, attribute: Attribute) {
        mAttributes[name] = attribute
    }

    protected fun <V> registerAttr(name: String, getter: Getter<V>, setter: Setter<V?>, biMap: BiMap<String?, V>) {
        mAttributes[name] = object : Attribute {
            override fun get() = biMap.getKey(getter.get())

            override fun set(value: String) = setter.set(biMap[value])
        }
    }

    protected fun registerAttr(name: String, getter: AttributeGetter, setter: AttributeSetter) {
        mAttributes[name] = object : Attribute {
            override fun get() = getter.get()

            override fun set(value: String) = setter.set(value)
        }
    }

    protected fun registerAttr(name: String, setter: (String) -> Unit) {
        mAttributes[name] = BaseAttribute(object : AttributeSetter {
            override fun set(value: String) {
                setter(value)
            }
        })
    }

    protected fun registerAttrUnsupported(names: Array<String>) {
        names.forEach { registerAttr(it) { value -> Exceptions.unsupported(view, it, value) } }
    }

    private fun <T> registerAttr(name: String, converter: (String) -> T, applier: (T) -> Unit) {
        val attributeSetter = MappingAttributeSetter(
            object : ValueConverter<T> {
                override fun convert(value: String) = converter(value)
            },
            object : Setter<T> {
                override fun set(value: T) = applier(value)
            },
        )
        mAttributes[name] = BaseAttribute(attributeSetter)
    }

    private fun <T> registerAttrs(names: Array<String>, converter: (String) -> T, applier: (T) -> Unit) {
        val attributeSetter = MappingAttributeSetter(
            object : ValueConverter<T> {
                override fun convert(value: String) = converter(value)
            },
            object : Setter<T> {
                override fun set(value: T) = applier(value)
            },
        )
        registerAttrs(names, attributeSetter::set)
    }

    protected fun registerAttrs(names: Array<String>, setter: (String) -> Unit) {
        registerAttrs(names, BaseAttribute(object : AttributeSetter {
            override fun set(value: String) = setter(value)
        }))
    }

    private fun registerAttrs(names: Array<String>, attribute: Attribute) {
        names.forEach { mAttributes[it] = attribute }
    }

    protected fun registerPixelAttr(name: String, applier: (Float) -> Unit) {
        registerAttr(name) { applier(parseDimensionToPixel(it)) }
    }

    protected fun registerIntPixelAttr(name: String, applier: (Int) -> Unit) {
        registerAttr(name) { applier(parseDimensionToIntPixel(it)) }
    }

    protected fun registerIdAttr(name: String, applier: (Int) -> Unit) {
        registerAttr(name) { applier(Ids.parse(it)) }
    }

    protected fun registerBooleanAttr(name: String, applier: (Boolean) -> Unit) {
        registerAttr(name) { applier(it.toBoolean()) }
    }

    protected fun parseDrawable(value: String): Drawable? = drawables.parse(view, value)

    protected fun setGravity(g: Int) = try {
        val setGravity = view.javaClass.getMethod("setGravity", Int::class.javaPrimitiveType)
        setGravity.invoke(view, g)
        true
    } catch (e: Exception) {
        false.also { e.printStackTrace() }
    }

    private fun setMargin(margin: String) {
        (view.layoutParams as? MarginLayoutParams)?.let { params ->
            val (left, top, right, bottom) = Dimensions.parseToIntPixelArray(view, margin)
            view.layoutParams = params.apply {
                setMargins(left, top, right, bottom)
                marginStart = left
                marginEnd = right
            }
        }
    }

    private fun setLayoutRule(rule: Int, isLayoutTarget: Boolean, attrValue: String) {
        if (view.parent is RelativeLayout) {
            if (isLayoutTarget) {
                (view.layoutParams as RelativeLayout.LayoutParams).addRule(rule, Ids.parse(attrValue))
            } else if (attrValue == "true") {
                (view.layoutParams as RelativeLayout.LayoutParams).addRule(rule)
            }
        }
    }

    private fun setMarginLeft(margin: Int) {
        (view.layoutParams as? MarginLayoutParams)?.let { it.leftMargin = margin }
    }

    private fun setMarginRight(margin: Int) {
        (view.layoutParams as? MarginLayoutParams)?.let { it.rightMargin = margin }
    }

    private fun setMarginTop(margin: Int) {
        (view.layoutParams as? MarginLayoutParams)?.let { it.topMargin = margin }
    }

    private fun setMarginBottom(margin: Int) {
        (view.layoutParams as? MarginLayoutParams)?.let { it.bottomMargin = margin }
    }

    protected fun setMarginStart(margin: Int) {
        (view.layoutParams as? MarginLayoutParams)?.let { it.marginStart = margin }
    }

    protected fun setMarginEnd(margin: Int) {
        (view.layoutParams as? MarginLayoutParams)?.let { it.marginEnd = margin }
    }

    protected fun setMarginVertical(margin: Int) {
        (view.layoutParams as? MarginLayoutParams)?.let {
            it.topMargin = margin
            it.bottomMargin = margin
        }
    }

    protected fun setMarginHorizontal(margin: Int) {
        (view.layoutParams as? MarginLayoutParams)?.let {
            it.marginStart = margin
            it.marginEnd = margin
        }
    }

    protected fun setPadding(padding: String) {
        val (left, top, right, bottom) = Dimensions.parseToIntPixelArray(view, padding)
        view.setPadding(left, top, right, bottom)
    }

    private fun setPaddingLeft(padding: Int) {
        view.setPadding(padding, view.paddingTop, view.paddingRight, view.paddingBottom)
    }

    private fun setPaddingTop(padding: Int) {
        view.setPadding(view.paddingLeft, padding, view.paddingRight, view.paddingBottom)
    }

    private fun setPaddingRight(padding: Int) {
        view.setPadding(view.paddingLeft, view.paddingTop, padding, view.paddingBottom)
    }

    private fun setPaddingBottom(padding: Int) {
        view.setPadding(view.paddingLeft, view.paddingTop, view.paddingRight, padding)
    }

    private fun setPaddingStart(padding: Int) {
        view.setPaddingRelative(padding, view.paddingTop, view.paddingEnd, view.paddingBottom)
    }

    private fun setPaddingEnd(padding: Int) {
        view.setPaddingRelative(view.paddingStart, view.paddingTop, padding, view.paddingBottom)
    }

    private fun setPaddingVertical(padding: Int) {
        view.setPaddingRelative(view.paddingStart, padding, view.paddingEnd, padding)
    }

    private fun setPaddingHorizontal(padding: Int) {
        view.setPaddingRelative(padding, view.paddingTop, padding, view.paddingBottom)
    }

    protected fun setBackgroundTint(color: Int) {
        ViewCompat.setBackgroundTintList(view, ColorStateList.valueOf(color))
    }

    protected fun setContextClickable(clickable: Boolean) {
        view.isContextClickable = clickable
    }

    private fun setLayoutGravity(gravity: Int) {
        val layoutParams = view.layoutParams
        when (view.parent) {
            is LinearLayout -> (layoutParams as LinearLayout.LayoutParams).gravity = gravity
            is FrameLayout -> (layoutParams as FrameLayout.LayoutParams).gravity = gravity
            else -> try {
                val field = layoutParams.javaClass.getField("gravity")
                field[layoutParams] = gravity
            } catch (e: Exception) {
                e.printStackTrace().also { return }
            }
        }
        view.layoutParams = layoutParams
    }

    private fun setLayoutWeight(weight: Float) {
        if (view.parent is LinearLayout) {
            view.layoutParams = view.layoutParams.also { params ->
                (params as LinearLayout.LayoutParams).weight = weight
            }
        }
    }

    protected fun setWidth(width: Int) {
        view.layoutParams = view.layoutParams.also { it.width = width }
    }

    protected fun setHeight(height: Int) {
        view.layoutParams = view.layoutParams.also { it.height = height }
    }

    private fun parseString(value: String?) = Strings.parse(view, value)

    companion object {

        protected fun <T1, T2> bind(func2: VoidFunc2<T1, T2>, t1: T1) = object : Setter<T2> {
            override fun set(value: T2) = func2.call(t1, value)
        }

        @JvmStatic
        fun parseAttrValue(value: String): List<String> {
            val splitter = Regex("\\s*[|,;\\s/]\\s*")
            val result = mutableListOf<String>()
            var invalidIndex = -1
            val split = value.split(splitter)

            split.forEachIndexed { index, it ->
                if (it.isEmpty() || index == invalidIndex) {
                    return@forEachIndexed
                }
                if (value.startsWith("@")) {

                    // @Hint by SuperMonster003 on May 22, 2023.
                    //  ! Conditions like "@color/xxx", "@dimen/xxx" and so forth.
                    //  ! zh-CN: 类似 "@color/xxx", "@dimen/xxx" 等情形.

                    if (index + 1 < split.size) {
                        result.add("$it/${split[index + 1]}")
                        invalidIndex = index + 1
                        return@forEachIndexed
                    }
                }
                result.add(it)
            }
            return result
        }

        @JvmStatic
        fun parseDayOfWeek(value: String) = when {
            value.matches(Regex("MON(DAY)?", RegexOption.IGNORE_CASE)) -> Calendar.MONDAY
            value.matches(Regex("TUE(SDAY)?", RegexOption.IGNORE_CASE)) -> Calendar.TUESDAY
            value.matches(Regex("WED(NESDAY)?", RegexOption.IGNORE_CASE)) -> Calendar.WEDNESDAY
            value.matches(Regex("THU(RSDAY)?", RegexOption.IGNORE_CASE)) -> Calendar.THURSDAY
            value.matches(Regex("FRI(DAY)?", RegexOption.IGNORE_CASE)) -> Calendar.FRIDAY
            value.matches(Regex("SAT(URDAY)?", RegexOption.IGNORE_CASE)) -> Calendar.SATURDAY
            value.matches(Regex("SUN(DAY)?", RegexOption.IGNORE_CASE)) -> Calendar.SUNDAY
            else -> {
                // @Caution by SuperMonster003 on May 19, 2023.
                //  ! Calendar.XXX is not as same as JavaScript Date.
                //  ! Take Tuesday as an example,
                //  ! for Java, Calendar.TUESDAY is 3,
                //  ! for JavaScript, Date#getDay() is 2.
                //  ! zh-CN:
                //  ! Calendar.XXX 与 JavaScript Date 不同.
                //  ! 以 "周二" (Tuesday) 为例,
                //  ! 对于 Java, Calendar.TUESDAY 是 3,
                //  ! 对于 JavaScript, Date#getDay() 是 2.

                // @Hint by SuperMonster003 on May 19, 2023.
                //  ! Compatibility for 0 is not necessary.
                //  # (value.toInt() + 6).mod(7) + 1
                //  ! zh-CN:
                //  ! 0 值兼容不再必要.
                //  # (value.toInt() + 6).mod(7) + 1

                value.toInt()
            }
        }

        fun setMaxDate(view: CalendarView, value: String) {
            try {
                parseDate(value)?.time?.let { view.maxDate = it }
            } catch (e: ParseException) {
                throw InflateException(e)
            }
        }

        fun setMaxDate(view: DatePicker, value: String) {
            try {
                parseDate(value)?.time?.let { view.maxDate = it }
            } catch (e: ParseException) {
                throw InflateException(e)
            }
        }

        fun setMinDate(view: CalendarView, value: String) {
            try {
                parseDate(value)?.time?.let { view.minDate = it }
            } catch (e: ParseException) {
                throw InflateException(e)
            }
        }

        fun setMinDate(view: DatePicker, value: String) {
            try {
                parseDate(value)?.time?.let { view.minDate = it }
            } catch (e: ParseException) {
                throw InflateException(e)
            }
        }

        fun parseDate(value: String): Date? = when {
            value.matches(Regex("^\\d{4}/.+")) -> {
                SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).parse(value)
            }
            else -> {
                SimpleDateFormat("MM/dd/yyyy", Locale.getDefault()).parse(value)
            }
        }

        @JvmField
        val TINT_MODES: ValueMapper<PorterDuff.Mode> = ValueMapper<PorterDuff.Mode>("tintMode")
            .map("add", PorterDuff.Mode.ADD)
            .map("multiply", PorterDuff.Mode.MULTIPLY)
            .map("screen", PorterDuff.Mode.SCREEN)
            .map("src_atop", PorterDuff.Mode.SRC_ATOP)
            .map("src_in", PorterDuff.Mode.SRC_IN)
            .map("src_over", PorterDuff.Mode.SRC_OVER)

        @Suppress("DEPRECATION")
        val DRAWABLE_CACHE_QUALITIES: ValueMapper<Int> = ValueMapper<Int>("drawingCacheQuality")
            .map("auto", View.DRAWING_CACHE_QUALITY_AUTO)
            .map("high", View.DRAWING_CACHE_QUALITY_HIGH)
            .map("low", View.DRAWING_CACHE_QUALITY_LOW)

        val IMPORTANT_FOR_ACCESSIBILITY: ValueMapper<Int> = ValueMapper<Int>("importantForAccessibility")
            .map("auto", 0) // View.IMPORTANT_FOR_ACCESSIBILITY_AUTO
            .map("no", 2) // View.IMPORTANT_FOR_ACCESSIBILITY_NO
            .map("noHideDescendants", 4) // View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            .map("yes", 1) // View.IMPORTANT_FOR_ACCESSIBILITY_YES

        val LAYOUT_DIRECTIONS: ValueMapper<Int> = ValueMapper<Int>("layoutDirection")
            .map("inherit", 2)
            .map("locale", 3)
            .map("ltr", 0)
            .map("rtl", 1)

        val SCROLLBARS_STYLES: ValueMapper<Int> = ValueMapper<Int>("scrollbarStyle")
            .map("insideInset", View.SCROLLBARS_INSIDE_INSET)
            .map("insideOverlay", View.SCROLLBARS_INSIDE_OVERLAY)
            .map("outsideInset", View.SCROLLBARS_OUTSIDE_INSET)
            .map("outsideOverlay", View.SCROLLBARS_OUTSIDE_OVERLAY)

        val SCROLL_INDICATORS: ValueMapper<Int> = ValueMapper<Int>("scrollIndicators")
            .map("bottom", 2) // View.SCROLL_INDICATOR_BOTTOM)
            .map("end", 20) // View.SCROLL_INDICATOR_END)
            .map("left", 4) // View.SCROLL_INDICATOR_LEFT)
            .map("none", 0)
            .map("right", 8) // View.SCROLL_INDICATOR_RIGHT)
            .map("start", 10) // View.SCROLL_INDICATOR_START)
            .map("top", 1) // View.SCROLL_INDICATOR_TOP)

        val VISIBILITY: ValueMapper<Int> = ValueMapper<Int>("visibility")
            .map("visible", View.VISIBLE)
            .map("invisible", View.INVISIBLE)
            .map("gone", View.GONE)

        val TEXT_DIRECTIONS: ValueMapper<Int> = ValueMapper<Int>("textDirection")
            .map("anyRtl", 2)
            .map("firstStrong", 1)
            .map("firstStrongLtr", 6)
            .map("firstStrongRtl", 7)
            .map("inherit", 0)
            .map("locale", 5)
            .map("ltr", 3)
            .map("rtl", 4)

        val TEXT_ALIGNMENTS: ValueMapper<Int> = ValueMapper<Int>("textAlignment")
            .map("center", 4)
            .map("gravity", 1)
            .map("inherit", 0)
            .map("textEnd", 3)
            .map("textStart", 2)
            .map("viewEnd", 6)
            .map("viewStart", 5)

    }

}