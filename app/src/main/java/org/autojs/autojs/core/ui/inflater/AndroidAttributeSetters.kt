package org.autojs.autojs.core.ui.inflater

import android.os.Build
import android.text.InputFilter
import android.util.TypedValue
import android.view.View
import android.widget.TextView

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
        return names.toList()
    }

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
        "requiresFadingEdge" to { values, view, value ->
            val flags = values.flagNames(value)
            view.isHorizontalFadingEdgeEnabled = "horizontal" in flags
            view.isVerticalFadingEdgeEnabled = "vertical" in flags
            true
        },
    )

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
