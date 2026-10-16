package org.autojs.autojs.core.ui.inflater

import android.content.Context
import android.graphics.Typeface
import android.os.Build
import android.os.LocaleList
import android.text.InputFilter
import android.text.method.DigitsKeyListener
import android.util.TypedValue
import android.view.PointerIcon
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TableLayout
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintHelper
import com.google.android.material.shape.ShapeAppearanceModel
import com.google.android.material.shape.Shapeable
import com.google.android.material.textfield.TextInputLayout
import org.autojs.autojs.core.ui.inflater.util.Ids
import org.autojs.autojs.core.ui.inflater.util.Res

/**
 * Which public methods may set an attribute that no handler of the inflater takes (host roadmap
 * `docs/dev/android-xml-attributes-roadmap.md` A2): naming rules tried in order (compared ignoring case), the names
 * that follow no rule, and the attributes a single setter cannot take alone because the platform sets them together
 * with others (line spacing, shadow, auto-size) or not through a setter at all (input filters, scrollbars).
 *
 * zh-CN: 兜底时哪些公开方法可以设置一个属性: 依次尝试的命名规则 (忽略大小写比较), 不符合规则的显式名称, 以及必须与其它值一起设置或不经单一 setter 设置的特例.
 */
internal object AndroidAttributeSetters {

    /** Method names that may set [name] with one argument, most specific first. */
    fun candidates(name: String): List<String> {
        val cap = name.replaceFirstChar { it.uppercaseChar() }
        val names = LinkedHashSet<String>()
        EXPLICIT[name]?.let(names::addAll)
        names += "set$cap"
        if (name.endsWith("Tint")) names += "set${cap}List"
        names += "set${cap}Drawable"
        if (name.length > 3 && name.startsWith("min") && name[3].isUpperCase()) names += "setMinimum" + name.substring(3)
        if (name.length > 2 && name.startsWith("is") && name[2].isUpperCase()) names += "set" + name.substring(2)
        names += "set${cap}Enabled"
        // Material's state colors and radii: trackColorActive -> setTrackActiveTintList, tickRadiusInactive -> setTickInactiveRadius.
        STATE_STYLED.matchEntire(name)?.destructured?.let { (base, kind, state) ->
            names += "set" + base.replaceFirstChar { it.uppercaseChar() } + state + if (kind == "Color") "TintList" else kind
        }
        // ConstraintLayout's Flow and CircularFlow (A9): flow_horizontalGap -> setHorizontalGap.
        HELPER_PREFIXES.firstOrNull { name.startsWith(it) }?.let { prefix ->
            names += "set" + name.removePrefix(prefix).replaceFirstChar { it.uppercaseChar() }
        }
        return names.toList()
    }

    private val STATE_STYLED = Regex("([a-z]\\w*?)(Color|Radius)(Active|Inactive)?")

    private val HELPER_PREFIXES = listOf("circularflow_", "flow_")

    /** Attributes whose setter follows no naming rule. */
    private val EXPLICIT: Map<String, List<String>> = mapOf(
        "src" to listOf("setImageDrawable"),
        "tint" to listOf("setImageTintList"),
        "tintMode" to listOf("setImageTintMode"),
        "textColorHint" to listOf("setHintTextColor"),
        "textColorLink" to listOf("setLinkTextColor"),
        "textColorHighlight" to listOf("setHighlightColor"),
        "textAllCaps" to listOf("setAllCaps"),
        "drawableTint" to listOf("setCompoundDrawableTintList"),
        "drawableTintMode" to listOf("setCompoundDrawableTintMode"),
        "drawablePadding" to listOf("setCompoundDrawablePadding"),
        "autoSizeTextType" to listOf("setAutoSizeTextTypeWithDefaults"),
        "fadeScrollbars" to listOf("setScrollbarFadingEnabled"),
        "transformPivotX" to listOf("setPivotX"),
        "transformPivotY" to listOf("setPivotY"),
        "splitMotionEvents" to listOf("setMotionEventSplittingEnabled"),
        "listSelector" to listOf("setSelector"),
        "nextFocusDown" to listOf("setNextFocusDownId"),
        "nextFocusUp" to listOf("setNextFocusUpId"),
        "nextFocusLeft" to listOf("setNextFocusLeftId"),
        "nextFocusRight" to listOf("setNextFocusRightId"),
        "nextFocusForward" to listOf("setNextFocusForwardId"),
        "nextClusterForward" to listOf("setNextClusterForwardId"),
        "scrollbarThumbVertical" to listOf("setVerticalScrollbarThumbDrawable"),
        "scrollbarThumbHorizontal" to listOf("setHorizontalScrollbarThumbDrawable"),
        "scrollbarTrackVertical" to listOf("setVerticalScrollbarTrackDrawable"),
        "scrollbarTrackHorizontal" to listOf("setHorizontalScrollbarTrackDrawable"),
        "checkMarkTint" to listOf("setCheckMarkTintList"),
        "buttonTint" to listOf("setButtonTintList"),
        "thumbTint" to listOf("setThumbTintList"),
        "trackTint" to listOf("setTrackTintList"),
        // Found by the coverage matrix (A5).
        "completionThreshold" to listOf("setThreshold"),
        "tabStripEnabled" to listOf("setStripEnabled"),
        "tabStripLeft" to listOf("setLeftStripDrawable"),
        "tabStripRight" to listOf("setRightStripDrawable"),
        "tabIndicator" to listOf("setSelectedTabIndicator"),
        "tabInlineLabel" to listOf("setInlineLabel"),
        "tabUnboundedRipple" to listOf("setUnboundedRipple"),
        "swipeRefreshLayoutProgressSpinnerBackgroundColor" to listOf("setProgressBackgroundColorSchemeColor"),
        "titleMaxLines" to listOf("setMaxLines"),
        "titleTextEllipsize" to listOf("setTitleEllipsize"),
        "helperTextTextColor" to listOf("setHelperTextColor"),
        "pressedTranslationZ" to listOf("setCompatPressedTranslationZ"),
        "hoveredFocusedTranslationZ" to listOf("setCompatHoveredFocusedTranslationZ"),
        // ConstraintLayout's own attribute and its Barrier (A9).
        "layout_optimizationLevel" to listOf("setOptimizationLevel"),
        "barrierDirection" to listOf("setType"),
        "barrierMargin" to listOf("setMargin"),
        "barrierAllowsGoneWidgets" to listOf("setAllowsGoneWidget"),
    )

    /**
     * Attributes set together with others or without a single setter; each returns false when it does not apply to
     * the view, so the generic rules still get their turn.
     */
    val SPECIAL: Map<String, (AndroidAttributeFallback.Values, View, String) -> Boolean> = mapOf(
        "lineSpacingExtra" to { values, view, value ->
            (view as? TextView)?.let { it.setLineSpacing(values.dimension(value), it.lineSpacingMultiplier); true } ?: false
        },
        "lineSpacingMultiplier" to { values, view, value ->
            (view as? TextView)?.let { it.setLineSpacing(it.lineSpacingExtra, values.float(value)); true } ?: false
        },
        "shadowColor" to { values, view, value ->
            (view as? TextView)?.let { it.setShadowLayer(it.shadowRadius, it.shadowDx, it.shadowDy, values.color(value)); true } ?: false
        },
        "shadowDx" to { values, view, value ->
            (view as? TextView)?.let { it.setShadowLayer(it.shadowRadius, values.float(value), it.shadowDy, it.shadowColor); true } ?: false
        },
        "shadowDy" to { values, view, value ->
            (view as? TextView)?.let { it.setShadowLayer(it.shadowRadius, it.shadowDx, values.float(value), it.shadowColor); true } ?: false
        },
        "shadowRadius" to { values, view, value ->
            (view as? TextView)?.let { it.setShadowLayer(values.float(value), it.shadowDx, it.shadowDy, it.shadowColor); true } ?: false
        },
        "autoSizeMinTextSize" to { values, view, value -> autoSize(view, min = values.dimension(value)) },
        "autoSizeMaxTextSize" to { values, view, value -> autoSize(view, max = values.dimension(value)) },
        "autoSizeStepGranularity" to { values, view, value -> autoSize(view, step = values.dimension(value)) },
        "maxLength" to { values, view, value ->
            (view as? TextView)?.let { text ->
                text.filters = text.filters.filterNot { it is InputFilter.LengthFilter }.toTypedArray() + InputFilter.LengthFilter(values.int(value))
                true
            } ?: false
        },
        "drawableStart" to { values, view, value -> relativeDrawable(view, values, value, 0) },
        "drawableEnd" to { values, view, value -> relativeDrawable(view, values, value, 2) },
        "scrollbars" to { values, view, value ->
            val flags = values.flagNames(value)
            view.isHorizontalScrollBarEnabled = "horizontal" in flags
            view.isVerticalScrollBarEnabled = "vertical" in flags
            true
        },
        "requiresFadingEdge" to { values, view, value -> fadingEdges(values, view, value) },
        // Found by the coverage matrix (A5): two-argument setters, values that need a conversion of their own, and
        // setters of several classes that share no interface.
        "fadingEdge" to { values, view, value -> fadingEdges(values, view, value) },
        "layerType" to { values, view, value -> view.setLayerType(values.int(value), null); true },
        "outlineProvider" to { _, view, value -> view.outlineProvider = outlineProvider(value); true },
        "autofillHints" to { values, view, value ->
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && run {
                view.setAutofillHints(*values.scalar(value).split(',').map { it.trim() }.filter { it.isNotEmpty() }.toTypedArray())
                true
            }
        },
        "pointerIcon" to { values, view, value -> view.pointerIcon = PointerIcon.getSystemIcon(view.context, values.int(value)); true },
        "digits" to { values, view, value -> (view as? TextView)?.let { it.keyListener = DigitsKeyListener.getInstance(values.scalar(value)); true } ?: false },
        "textFontWeight" to { values, view, value ->
            if (view !is TextView || Build.VERSION.SDK_INT < Build.VERSION_CODES.P) false else {
                val base = view.typeface ?: Typeface.DEFAULT
                view.typeface = Typeface.create(base, values.int(value), base.isItalic)
                true
            }
        },
        "textLocale" to { values, view, value -> (view as? TextView)?.let { it.textLocales = LocaleList.forLanguageTags(values.scalar(value)); true } ?: false },
        "entries" to { values, view, value ->
            (view as? ListView)?.let { it.adapter = ArrayAdapter(view.context, android.R.layout.simple_list_item_1, values.scalar(value).split('|')); true } ?: false
        },
        "stretchColumns" to { values, view, value -> tableColumns(view, values.scalar(value), TableLayout::setStretchAllColumns, TableLayout::setColumnStretchable) },
        "shrinkColumns" to { values, view, value -> tableColumns(view, values.scalar(value), TableLayout::setShrinkAllColumns, TableLayout::setColumnShrinkable) },
        "collapseColumns" to { values, view, value -> tableColumns(view, values.scalar(value), null, TableLayout::setColumnCollapsed) },
        "contentInsetStart" to { values, view, value -> isToolbar(view) && callInts(view, "setContentInsetsRelative", values.int(value), getInt(view, "getContentInsetEnd")) },
        "contentInsetEnd" to { values, view, value -> isToolbar(view) && callInts(view, "setContentInsetsRelative", getInt(view, "getContentInsetStart"), values.int(value)) },
        "contentInsetLeft" to { values, view, value -> isToolbar(view) && callInts(view, "setContentInsetsAbsolute", values.int(value), getInt(view, "getContentInsetRight")) },
        "contentInsetRight" to { values, view, value -> isToolbar(view) && callInts(view, "setContentInsetsAbsolute", getInt(view, "getContentInsetLeft"), values.int(value)) },
        "titleMargin" to { values, view, value -> isToolbar(view) && values.int(value).let { callInts(view, "setTitleMargin", it, it, it, it) } },
        "titleMargins" to { values, view, value -> isToolbar(view) && values.int(value).let { callInts(view, "setTitleMargin", it, it, it, it) } },
        "expandedTitleMargin" to { values, view, value -> values.int(value).let { callInts(view, "setExpandedTitleMargin", it, it, it, it) } },
        "titleTextAppearance" to { _, view, value -> textAppearance(view, "setTitleTextAppearance", value) },
        "subtitleTextAppearance" to { _, view, value -> textAppearance(view, "setSubtitleTextAppearance", value) },
        "switchTextAppearance" to { _, view, value -> textAppearance(view, "setSwitchTextAppearance", value) },
        "boxCornerRadiusTopStart" to { values, view, value -> boxCorners(view) { it[0] = values.dimension(value) } },
        "boxCornerRadiusTopEnd" to { values, view, value -> boxCorners(view) { it[1] = values.dimension(value) } },
        "boxCornerRadiusBottomStart" to { values, view, value -> boxCorners(view) { it[2] = values.dimension(value) } },
        "boxCornerRadiusBottomEnd" to { values, view, value -> boxCorners(view) { it[3] = values.dimension(value) } },
        "indicatorColor" to { values, view, value ->
            view.javaClass.methods.firstOrNull { it.name == "setIndicatorColor" && it.parameterTypes.contentEquals(arrayOf(IntArray::class.java)) }
                ?.let { it.invoke(view, intArrayOf(values.color(value))); true } ?: false
        },
        // A ConstraintLayout helper's views by id name (A9): "a,b" -> the ids of @id/a and @id/b.
        "constraint_referenced_ids" to { values, view, value ->
            (view as? ConstraintHelper)?.let { helper ->
                helper.referencedIds = values.scalar(value).split(',').map { it.trim() }.filter { it.isNotEmpty() }
                    .map { Ids.parse(if (it.startsWith("@")) it else "@id/$it") }.toIntArray()
                true
            } ?: false
        },
        "shapeAppearance" to { _, view, value -> shapeAppearance(view, value, overlay = false) },
        "shapeAppearanceOverlay" to { _, view, value -> shapeAppearance(view, value, overlay = true) },
    )

    private fun fadingEdges(values: AndroidAttributeFallback.Values, view: View, value: String): Boolean {
        val flags = values.flagNames(value)
        view.isHorizontalFadingEdgeEnabled = "horizontal" in flags
        view.isVerticalFadingEdgeEnabled = "vertical" in flags
        return true
    }

    private fun outlineProvider(value: String): ViewOutlineProvider? = when (value.trim()) {
        "background" -> ViewOutlineProvider.BACKGROUND
        "bounds" -> ViewOutlineProvider.BOUNDS
        "paddedBounds" -> ViewOutlineProvider.PADDED_BOUNDS
        "none" -> null
        else -> throw IllegalArgumentException("\"$value\" is not an outline provider")
    }

    /** `*` (when [all] is given) or comma-separated column indices of a TableLayout. */
    private fun tableColumns(view: View, value: String, all: ((TableLayout, Boolean) -> Unit)?, one: (TableLayout, Int, Boolean) -> Unit): Boolean {
        if (view !is TableLayout) return false
        val text = value.trim()
        if (text == "*" && all != null) {
            all(view, true)
            return true
        }
        text.split(',').map { it.trim() }.filter { it.isNotEmpty() }.forEach { column ->
            one(view, column.toIntOrNull() ?: throw IllegalArgumentException("\"$column\" is not a column index"), true)
        }
        return true
    }

    /** The platform Toolbar and AppCompat's share their method names but no type. */
    private fun isToolbar(view: View) = view is android.widget.Toolbar || view is androidx.appcompat.widget.Toolbar

    /** Calls the public method [name] of [view] that takes exactly [args].size ints; false when it has none. */
    private fun callInts(view: View, name: String, vararg args: Int): Boolean {
        val method = view.javaClass.methods.firstOrNull { method ->
            method.name == name && method.parameterTypes.size == args.size && method.parameterTypes.all { it == Int::class.javaPrimitiveType }
        } ?: return false
        method.invoke(view, *args.toTypedArray())
        return true
    }

    private fun getInt(view: View, name: String): Int = view.javaClass.getMethod(name).invoke(view) as Int

    /** `set*TextAppearance(Context, int)` with a compiled style (`@style/...`, `@android:style/...` or `?attr`). */
    private fun textAppearance(view: View, name: String, value: String): Boolean {
        val method = view.javaClass.methods.firstOrNull { it.name == name && it.parameterTypes.contentEquals(arrayOf(Context::class.java, Int::class.javaPrimitiveType)) }
            ?: return false
        method.invoke(view, view.context, style(view, value))
        return true
    }

    private fun style(view: View, value: String): Int =
        Res.parseStyle(view.context, value.trim()).takeIf { it != 0 } ?: throw IllegalArgumentException("\"$value\" is not a style")

    /** Sets one of the four box corner radii of a TextInputLayout (top start, top end, bottom start, bottom end). */
    private fun boxCorners(view: View, change: (FloatArray) -> Unit): Boolean {
        if (view !is TextInputLayout) return false
        val radii = floatArrayOf(view.boxCornerRadiusTopStart, view.boxCornerRadiusTopEnd, view.boxCornerRadiusBottomStart, view.boxCornerRadiusBottomEnd)
        change(radii)
        view.setBoxCornerRadii(radii[0], radii[1], radii[2], radii[3])
        return true
    }

    /** A Material shape from a compiled ShapeAppearance style; an overlay applies over the default shape. */
    private fun shapeAppearance(view: View, value: String, overlay: Boolean): Boolean {
        if (view !is Shapeable) return false
        val style = style(view, value)
        view.shapeAppearanceModel = ShapeAppearanceModel.builder(view.context, if (overlay) 0 else style, if (overlay) style else 0).build()
        return true
    }

    /** Uniform auto-size with the given bounds; the others keep their current values or the platform defaults. */
    private fun autoSize(view: View, min: Float? = null, max: Float? = null, step: Float? = null): Boolean {
        if (view !is TextView || Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return false
        val metrics = view.resources.displayMetrics
        val currentMin = view.autoSizeMinTextSize.takeIf { it > 0 }?.toFloat() ?: TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 12f, metrics)
        val currentMax = view.autoSizeMaxTextSize.takeIf { it > 0 }?.toFloat() ?: TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, 112f, metrics)
        val currentStep = view.autoSizeStepGranularity.takeIf { it > 0 }?.toFloat() ?: 1f
        view.setAutoSizeTextTypeUniformWithConfiguration(
            (min ?: currentMin).toInt(), (max ?: currentMax).toInt(), (step ?: currentStep).toInt().coerceAtLeast(1), TypedValue.COMPLEX_UNIT_PX,
        )
        return true
    }

    /** Sets the relative compound drawable at [index] (0 start, 2 end) and keeps the other three. */
    private fun relativeDrawable(view: View, values: AndroidAttributeFallback.Values, value: String, index: Int): Boolean {
        if (view !is TextView) return false
        val drawables = view.compoundDrawablesRelative.copyOf()
        drawables[index] = values.drawable(value)
        view.setCompoundDrawablesRelativeWithIntrinsicBounds(drawables[0], drawables[1], drawables[2], drawables[3])
        return true
    }
}
