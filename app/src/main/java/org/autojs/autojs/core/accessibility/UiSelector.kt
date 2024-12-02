@file:Suppress("unused", "MemberVisibilityCanBePrivate")

package org.autojs.autojs.core.accessibility

import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.FloatRange
import org.autojs.autojs.annotation.ScriptInterface
import org.autojs.autojs.concurrent.VolatileBox
import org.autojs.autojs.core.automator.ActionArgument
import org.autojs.autojs.core.automator.UiObject
import org.autojs.autojs.core.automator.UiObject.Companion.COMPASS_PASS_ON
import org.autojs.autojs.core.automator.UiObject.Companion.Detector
import org.autojs.autojs.core.automator.UiObject.Companion.Detector.Result
import org.autojs.autojs.core.automator.UiObject.Companion.RESULT_TYPE_WIDGET
import org.autojs.autojs.core.automator.UiObjectActions
import org.autojs.autojs.core.automator.UiObjectCollection
import org.autojs.autojs.core.automator.UiObjectCollection.Companion.EMPTY
import org.autojs.autojs.core.automator.UiObjectCollection.Companion.of
import org.autojs.autojs.core.automator.filter.ActionFilter
import org.autojs.autojs.core.automator.filter.AppFilter
import org.autojs.autojs.core.automator.filter.BooleanFilter
import org.autojs.autojs.core.automator.filter.BoundsFilter
import org.autojs.autojs.core.automator.filter.ClassNameFilter
import org.autojs.autojs.core.automator.filter.ContentFilter
import org.autojs.autojs.core.automator.filter.DescFilter
import org.autojs.autojs.core.automator.filter.DoubleMinFilter
import org.autojs.autojs.core.automator.filter.Filter
import org.autojs.autojs.core.automator.filter.IdFilter
import org.autojs.autojs.core.automator.filter.IdHexFilter
import org.autojs.autojs.core.automator.filter.IntFilter
import org.autojs.autojs.core.automator.filter.MaxIntFilter
import org.autojs.autojs.core.automator.filter.MetricsFilter
import org.autojs.autojs.core.automator.filter.MetricsMaxFilter
import org.autojs.autojs.core.automator.filter.MetricsMinFilter
import org.autojs.autojs.core.automator.filter.MetricsRangeFilter
import org.autojs.autojs.core.automator.filter.MinIntFilter
import org.autojs.autojs.core.automator.filter.PackageNameFilter
import org.autojs.autojs.core.automator.filter.Selector
import org.autojs.autojs.core.automator.filter.TextFilter
import org.autojs.autojs.core.automator.filter.ToleranceFilter
import org.autojs.autojs.core.automator.search.BFS
import org.autojs.autojs.core.automator.search.DFS
import org.autojs.autojs.core.automator.search.SearchAlgorithm
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.StringReadable
import org.autojs.autojs.runtime.api.augment.util.Inspect
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.autojs.autojs.util.App
import org.autojs.autojs.util.RhinoUtils
import org.autojs.autojs.util.RhinoUtils.isMainThread
import org.autojs.autojs.util.StringUtils.convertRegex
import org.autojs.autojs.util.StringUtils.str
import org.autojs.autojs6.R
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeJavaMethod
import org.mozilla.javascript.regexp.NativeRegExp
import java.math.BigInteger

/**
 * Created by Stardust on Mar 9, 2017.
 * Modified by SuperMonster003 as of Jun 11, 2022.
 */
open class UiSelector : UiObjectActions, StringReadable {

    internal var selector = Selector()

    internal var searchAlgorithm: SearchAlgorithm = DFS

    private val mAccessibilityBridge: AccessibilityBridge?

    private val mAllocator: AccessibilityNodeInfoAllocator?

    private val mA11yTool = AccessibilityTool()

    constructor() : this(AccessibilityService.bridge)

    constructor(accessibilityBridge: AccessibilityBridge?) : this(accessibilityBridge, null)

    constructor(accessibilityBridge: AccessibilityBridge?, allocator: AccessibilityNodeInfoAllocator?) {
        mAccessibilityBridge = accessibilityBridge
        mAllocator = allocator
    }

    @ScriptInterface
    fun id(str: String) = also { addFilter(IdFilter.equals(str)) }

    @ScriptInterface
    fun idStartsWith(prefix: String) = also { addFilter(IdFilter.startsWith(prefix)) }

    @ScriptInterface
    fun idEndsWith(suffix: String) = also { addFilter(IdFilter.endsWith(suffix)) }

    @ScriptInterface
    fun idContains(str: String) = also { addFilter(IdFilter.contains(str)) }

    @ScriptInterface
    @Deprecated("Deprecated in Java", ReplaceWith("idMatch(regex)"))
    fun idMatches(regex: String) = also { addFilter(IdFilter.matches(convertRegex(regex))) }

    @ScriptInterface
    @Suppress("DEPRECATION")
    @Deprecated("Deprecated in Java", ReplaceWith("idMatch(regex)"))
    fun idMatches(regex: NativeRegExp) = idMatches(regex.toString())

    @ScriptInterface
    fun idMatch(regex: String) = also { addFilter(IdFilter.match(regex)) }

    @ScriptInterface
    fun idMatch(regex: NativeRegExp) = idMatch(convertRegex(regex.toString()))

    @ScriptInterface
    fun idHex(str: String) = also { addFilter(IdHexFilter.equals(str)) }

    @ScriptInterface
    fun text(str: String) = also { addFilter(TextFilter.equals(str)) }

    @ScriptInterface
    fun textStartsWith(prefix: String) = also { addFilter(TextFilter.startsWith(prefix)) }

    @ScriptInterface
    fun textEndsWith(suffix: String) = also { addFilter(TextFilter.endsWith(suffix)) }

    @ScriptInterface
    fun textContains(str: String) = also { addFilter(TextFilter.contains(str)) }

    @ScriptInterface
    @Deprecated("Deprecated in Java", ReplaceWith("textMatch(regex)"))
    fun textMatches(regex: String) = also { addFilter(TextFilter.matches(convertRegex(regex))) }

    @ScriptInterface
    @Suppress("DEPRECATION")
    @Deprecated("Deprecated in Java", ReplaceWith("textMatch(regex)"))
    fun textMatches(regex: NativeRegExp) = textMatches(regex.toString())

    @ScriptInterface
    fun textMatch(regex: String) = also { addFilter(TextFilter.match(regex)) }

    @ScriptInterface
    fun textMatch(regex: NativeRegExp) = textMatch(convertRegex(regex.toString()))

    @ScriptInterface
    fun desc(str: String) = also { addFilter(DescFilter.equals(str)) }

    @ScriptInterface
    fun descStartsWith(prefix: String) = also { addFilter(DescFilter.startsWith(prefix)) }

    @ScriptInterface
    fun descEndsWith(suffix: String) = also { addFilter(DescFilter.endsWith(suffix)) }

    @ScriptInterface
    fun descContains(str: String) = also { addFilter(DescFilter.contains(str)) }

    @ScriptInterface
    @Deprecated("Deprecated in Java", ReplaceWith("descMatch(regex)"))
    fun descMatches(regex: String) = also { addFilter(DescFilter.matches(convertRegex(regex))) }

    @ScriptInterface
    @Suppress("DEPRECATION")
    @Deprecated("Deprecated in Java", ReplaceWith("descMatch(regex)"))
    fun descMatches(regex: NativeRegExp) = descMatches(regex.toString())

    @ScriptInterface
    fun descMatch(regex: String) = also { addFilter(DescFilter.match(regex)) }

    @ScriptInterface
    fun descMatch(regex: NativeRegExp) = descMatch(convertRegex(regex.toString()))

    @ScriptInterface
    fun content(str: String) = also { addFilter(ContentFilter.equals(str)) }

    @ScriptInterface
    fun contentStartsWith(prefix: String) = also { addFilter(ContentFilter.startsWith(prefix)) }

    @ScriptInterface
    fun contentEndsWith(suffix: String) = also { addFilter(ContentFilter.endsWith(suffix)) }

    @ScriptInterface
    fun contentContains(str: String) = also { addFilter(ContentFilter.contains(str)) }

    @ScriptInterface
    @Deprecated("Deprecated in Java", ReplaceWith("contentMatch(regex)"))
    fun contentMatches(regex: String) = also { addFilter(ContentFilter.matches(convertRegex(regex))) }

    @ScriptInterface
    @Suppress("DEPRECATION")
    @Deprecated("Deprecated in Java", ReplaceWith("contentMatch(regex)"))
    fun contentMatches(regex: NativeRegExp) = contentMatches(regex.toString())

    @ScriptInterface
    fun contentMatch(regex: String) = also { addFilter(ContentFilter.match(regex)) }

    @ScriptInterface
    fun contentMatch(regex: NativeRegExp) = contentMatch(convertRegex(regex.toString()))

    @ScriptInterface
    fun className(str: String) = also { addFilter(ClassNameFilter.equals(str)) }

    @ScriptInterface
    fun classNameStartsWith(prefix: String) = also { addFilter(ClassNameFilter.startsWith(prefix)) }

    @ScriptInterface
    fun classNameEndsWith(suffix: String) = also { addFilter(ClassNameFilter.endsWith(suffix)) }

    @ScriptInterface
    fun classNameContains(str: String) = also { addFilter(ClassNameFilter.contains(str)) }

    @ScriptInterface
    @Deprecated("Deprecated in Java", ReplaceWith("classNameMatch(regex)"))
    fun classNameMatches(regex: String) = also { addFilter(ClassNameFilter.matches(convertRegex(regex))) }

    @ScriptInterface
    @Suppress("DEPRECATION")
    @Deprecated("Deprecated in Java", ReplaceWith("classNameMatch(regex)"))
    fun classNameMatches(regex: NativeRegExp) = classNameMatches(regex.toString())

    @ScriptInterface
    fun classNameMatch(regex: String) = also { addFilter(ClassNameFilter.match(regex)) }

    @ScriptInterface
    fun classNameMatch(regex: NativeRegExp) = classNameMatch(convertRegex(regex.toString()))

    @ScriptInterface
    fun packageName(str: String) = also { addFilter(PackageNameFilter.equals(str)) }

    @ScriptInterface
    fun packageName(app: App) = also { addFilter(PackageNameFilter.equals(app.packageName)) }

    @ScriptInterface
    fun packageNameStartsWith(prefix: String) = also { addFilter(PackageNameFilter.startsWith(prefix)) }

    @ScriptInterface
    fun packageNameEndsWith(suffix: String) = also { addFilter(PackageNameFilter.endsWith(suffix)) }

    @ScriptInterface
    fun packageNameContains(str: String) = also { addFilter(PackageNameFilter.contains(str)) }

    @ScriptInterface
    @Deprecated("Deprecated in Java", ReplaceWith("packageNameMatch(regex)"))
    fun packageNameMatches(regex: String) = also { addFilter(PackageNameFilter.matches(convertRegex(regex))) }

    @ScriptInterface
    @Suppress("DEPRECATION")
    @Deprecated("Deprecated in Java", ReplaceWith("packageNameMatch(regex)"))
    fun packageNameMatches(regex: NativeRegExp) = packageNameMatches(regex.toString())

    @ScriptInterface
    fun packageNameMatch(regex: String) = also { addFilter(PackageNameFilter.match(regex)) }

    @ScriptInterface
    fun packageNameMatch(regex: NativeRegExp) = packageNameMatch(convertRegex(regex.toString()))

    @ScriptInterface
    fun currentApp(app: App) = also { addFilter(AppFilter(app)) }

    @ScriptInterface
    fun currentApp(name: String) = also { addFilter(AppFilter(name)) }

    @ScriptInterface
    fun bounds(l: Double, t: Double, r: Double, b: Double) = also { addFilter(BoundsFilter(l, t, r, b, BoundsFilter.TYPE_EQUALS)) }

    @ScriptInterface
    fun boundsInside(l: Double, t: Double, r: Double, b: Double) = also { addFilter(BoundsFilter(l, t, r, b, BoundsFilter.TYPE_INSIDE)) }

    @ScriptInterface
    fun boundsContains(l: Double, t: Double, r: Double, b: Double) = also { addFilter(BoundsFilter(l, t, r, b, BoundsFilter.TYPE_CONTAINS)) }

    @ScriptInterface
    fun boundsLeft(value: Double) = also { addFilter(MetricsFilter(MetricsFilter.BOUNDS_LEFT, value)) }

    @ScriptInterface
    fun boundsLeft(min: Double, max: Double) = also { addFilter(MetricsRangeFilter(MetricsRangeFilter.BOUNDS_LEFT, min, max)) }

    @ScriptInterface
    fun boundsTop(value: Double) = also { addFilter(MetricsFilter(MetricsFilter.BOUNDS_TOP, value)) }

    @ScriptInterface
    fun boundsTop(min: Double, max: Double) = also { addFilter(MetricsRangeFilter(MetricsRangeFilter.BOUNDS_TOP, min, max)) }

    @ScriptInterface
    fun boundsRight(value: Double) = also { addFilter(MetricsFilter(MetricsFilter.BOUNDS_RIGHT, value)) }

    @ScriptInterface
    fun boundsRight(min: Double, max: Double) = also { addFilter(MetricsRangeFilter(MetricsRangeFilter.BOUNDS_RIGHT, min, max)) }

    @ScriptInterface
    fun boundsBottom(value: Double) = also { addFilter(MetricsFilter(MetricsFilter.BOUNDS_BOTTOM, value)) }

    @ScriptInterface
    fun boundsBottom(min: Double, max: Double) = also { addFilter(MetricsRangeFilter(MetricsRangeFilter.BOUNDS_BOTTOM, min, max)) }

    @ScriptInterface
    fun boundsWidth(value: Double) = also { addFilter(MetricsFilter(MetricsFilter.BOUNDS_WIDTH, value)) }

    @ScriptInterface
    fun boundsWidth(min: Double, max: Double) = also { addFilter(MetricsRangeFilter(MetricsRangeFilter.BOUNDS_WIDTH, min, max)) }

    @ScriptInterface
    fun boundsHeight(value: Double) = also { addFilter(MetricsFilter(MetricsFilter.BOUNDS_HEIGHT, value)) }

    @ScriptInterface
    fun boundsHeight(min: Double, max: Double) = also { addFilter(MetricsRangeFilter(MetricsRangeFilter.BOUNDS_HEIGHT, min, max)) }

    @ScriptInterface
    fun boundsCenterX(value: Double) = also { addFilter(MetricsFilter(MetricsFilter.BOUNDS_CENTER_X, value)) }

    @ScriptInterface
    fun boundsCenterX(min: Double, max: Double) = also { addFilter(MetricsRangeFilter(MetricsRangeFilter.BOUNDS_CENTER_X, min, max)) }

    @ScriptInterface
    fun boundsCenterY(value: Double) = also { addFilter(MetricsFilter(MetricsFilter.BOUNDS_CENTER_Y, value)) }

    @ScriptInterface
    fun boundsCenterY(min: Double, max: Double) = also { addFilter(MetricsRangeFilter(MetricsRangeFilter.BOUNDS_CENTER_Y, min, max)) }

    @ScriptInterface
    fun boundsMinLeft(min: Double) = also { addFilter(MetricsMinFilter(MetricsMinFilter.BOUNDS_LEFT, min)) }

    @ScriptInterface
    fun boundsMinTop(min: Double) = also { addFilter(MetricsMinFilter(MetricsMinFilter.BOUNDS_TOP, min)) }

    @ScriptInterface
    fun boundsMinRight(min: Double) = also { addFilter(MetricsMinFilter(MetricsMinFilter.BOUNDS_RIGHT, min)) }

    @ScriptInterface
    fun boundsMinBottom(min: Double) = also { addFilter(MetricsMinFilter(MetricsMinFilter.BOUNDS_BOTTOM, min)) }

    @ScriptInterface
    fun boundsMinWidth(min: Double) = also { addFilter(MetricsMinFilter(MetricsMinFilter.BOUNDS_WIDTH, min)) }

    @ScriptInterface
    fun boundsMinHeight(min: Double) = also { addFilter(MetricsMinFilter(MetricsMinFilter.BOUNDS_HEIGHT, min)) }

    @ScriptInterface
    fun boundsMinCenterX(min: Double) = also { addFilter(MetricsMinFilter(MetricsMinFilter.BOUNDS_CENTER_X, min)) }

    @ScriptInterface
    fun boundsMinCenterY(min: Double) = also { addFilter(MetricsMinFilter(MetricsMinFilter.BOUNDS_CENTER_Y, min)) }

    @ScriptInterface
    fun boundsMaxLeft(max: Double) = also { addFilter(MetricsMaxFilter(MetricsMaxFilter.BOUNDS_LEFT, max)) }

    @ScriptInterface
    fun boundsMaxTop(max: Double) = also { addFilter(MetricsMaxFilter(MetricsMaxFilter.BOUNDS_TOP, max)) }

    @ScriptInterface
    fun boundsMaxRight(max: Double) = also { addFilter(MetricsMaxFilter(MetricsMaxFilter.BOUNDS_RIGHT, max)) }

    @ScriptInterface
    fun boundsMaxBottom(max: Double) = also { addFilter(MetricsMaxFilter(MetricsMaxFilter.BOUNDS_BOTTOM, max)) }

    @ScriptInterface
    fun boundsMaxWidth(max: Double) = also { addFilter(MetricsMaxFilter(MetricsMaxFilter.BOUNDS_WIDTH, max)) }

    @ScriptInterface
    fun boundsMaxHeight(max: Double) = also { addFilter(MetricsMaxFilter(MetricsMaxFilter.BOUNDS_HEIGHT, max)) }

    @ScriptInterface
    fun boundsMaxCenterX(max: Double) = also { addFilter(MetricsMaxFilter(MetricsMaxFilter.BOUNDS_CENTER_X, max)) }

    @ScriptInterface
    fun boundsMaxCenterY(max: Double) = also { addFilter(MetricsMaxFilter(MetricsMaxFilter.BOUNDS_CENTER_Y, max)) }

    @ScriptInterface
    fun left(value: Double) = also { addFilter(MetricsFilter(MetricsFilter.LEFT, value)) }

    @ScriptInterface
    fun left(min: Double, max: Double) = also { addFilter(MetricsRangeFilter(MetricsRangeFilter.LEFT, min, max)) }

    @ScriptInterface
    fun top(value: Double) = also { addFilter(MetricsFilter(MetricsFilter.TOP, value)) }

    @ScriptInterface
    fun top(min: Double, max: Double) = also { addFilter(MetricsRangeFilter(MetricsRangeFilter.TOP, min, max)) }

    @ScriptInterface
    fun right(value: Double) = also { addFilter(MetricsFilter(MetricsFilter.RIGHT, value)) }

    @ScriptInterface
    fun right(min: Double, max: Double) = also { addFilter(MetricsRangeFilter(MetricsRangeFilter.RIGHT, min, max)) }

    @ScriptInterface
    fun bottom(value: Double) = also { addFilter(MetricsFilter(MetricsFilter.BOTTOM, value)) }

    @ScriptInterface
    fun bottom(min: Double, max: Double) = also { addFilter(MetricsRangeFilter(MetricsRangeFilter.BOTTOM, min, max)) }

    @ScriptInterface
    fun width(value: Double) = also { addFilter(MetricsFilter(MetricsFilter.WIDTH, value)) }

    @ScriptInterface
    fun width(min: Double, max: Double) = also { addFilter(MetricsRangeFilter(MetricsRangeFilter.WIDTH, min, max)) }

    @ScriptInterface
    fun height(value: Double) = also { addFilter(MetricsFilter(MetricsFilter.HEIGHT, value)) }

    @ScriptInterface
    fun height(min: Double, max: Double) = also { addFilter(MetricsRangeFilter(MetricsRangeFilter.HEIGHT, min, max)) }

    @ScriptInterface
    fun centerX(value: Double) = also { addFilter(MetricsFilter(MetricsFilter.CENTER_X, value)) }

    @ScriptInterface
    fun centerX(min: Double, max: Double) = also { addFilter(MetricsRangeFilter(MetricsRangeFilter.CENTER_X, min, max)) }

    @ScriptInterface
    fun centerY(value: Double) = also { addFilter(MetricsFilter(MetricsFilter.CENTER_Y, value)) }

    @ScriptInterface
    fun centerY(min: Double, max: Double) = also { addFilter(MetricsRangeFilter(MetricsRangeFilter.CENTER_Y, min, max)) }

    @ScriptInterface
    fun minLeft(value: Double) = also { addFilter(MetricsMinFilter(MetricsMinFilter.LEFT, value)) }

    @ScriptInterface
    fun minTop(value: Double) = also { addFilter(MetricsMinFilter(MetricsMinFilter.TOP, value)) }

    @ScriptInterface
    fun minRight(value: Double) = also { addFilter(MetricsMinFilter(MetricsMinFilter.RIGHT, value)) }

    @ScriptInterface
    fun minBottom(value: Double) = also { addFilter(MetricsMinFilter(MetricsMinFilter.BOTTOM, value)) }

    @ScriptInterface
    fun minWidth(value: Double) = also { addFilter(MetricsMinFilter(MetricsMinFilter.WIDTH, value)) }

    @ScriptInterface
    fun minHeight(value: Double) = also { addFilter(MetricsMinFilter(MetricsMinFilter.HEIGHT, value)) }

    @ScriptInterface
    fun minCenterX(value: Double) = also { addFilter(MetricsMinFilter(MetricsMinFilter.CENTER_X, value)) }

    @ScriptInterface
    fun minCenterY(value: Double) = also { addFilter(MetricsMinFilter(MetricsMinFilter.CENTER_Y, value)) }

    @ScriptInterface
    fun maxLeft(value: Double) = also { addFilter(MetricsMaxFilter(MetricsMaxFilter.LEFT, value)) }

    @ScriptInterface
    fun maxTop(value: Double) = also { addFilter(MetricsMaxFilter(MetricsMaxFilter.TOP, value)) }

    @ScriptInterface
    fun maxRight(value: Double) = also { addFilter(MetricsMaxFilter(MetricsMaxFilter.RIGHT, value)) }

    @ScriptInterface
    fun maxBottom(value: Double) = also { addFilter(MetricsMaxFilter(MetricsMaxFilter.BOTTOM, value)) }

    @ScriptInterface
    fun maxWidth(value: Double) = also { addFilter(MetricsMaxFilter(MetricsMaxFilter.WIDTH, value)) }

    @ScriptInterface
    fun maxHeight(value: Double) = also { addFilter(MetricsMaxFilter(MetricsMaxFilter.HEIGHT, value)) }

    @ScriptInterface
    fun maxCenterX(value: Double) = also { addFilter(MetricsMaxFilter(MetricsMaxFilter.CENTER_X, value)) }

    @ScriptInterface
    fun maxCenterY(value: Double) = also { addFilter(MetricsMaxFilter(MetricsMaxFilter.CENTER_Y, value)) }

    @ScriptInterface
    fun screenCenterX(b: Boolean, @FloatRange(0.0, 0.5) tolerance: Double) = also { addFilter(ToleranceFilter(ToleranceFilter.SCREEN_CENTER_X, b, tolerance)) }

    @ScriptInterface
    fun screenCenterX(b: Boolean) = also { addFilter(ToleranceFilter(ToleranceFilter.SCREEN_CENTER_X, b)) }

    @ScriptInterface
    fun screenCenterX(@FloatRange(0.0, 0.5) tolerance: Double) = also { addFilter(ToleranceFilter(ToleranceFilter.SCREEN_CENTER_X, null, tolerance)) }

    @ScriptInterface
    fun screenCenterX() = also { addFilter(ToleranceFilter(ToleranceFilter.SCREEN_CENTER_X)) }

    @ScriptInterface
    fun screenCenterY(b: Boolean, @FloatRange(0.0, 0.5) tolerance: Double) = also { addFilter(ToleranceFilter(ToleranceFilter.SCREEN_CENTER_Y, b, tolerance)) }

    @ScriptInterface
    fun screenCenterY(b: Boolean) = also { addFilter(ToleranceFilter(ToleranceFilter.SCREEN_CENTER_Y, b)) }

    @ScriptInterface
    fun screenCenterY(@FloatRange(0.0, 0.5) tolerance: Double) = also { addFilter(ToleranceFilter(ToleranceFilter.SCREEN_CENTER_Y, null, tolerance)) }

    @ScriptInterface
    fun screenCenterY() = also { addFilter(ToleranceFilter(ToleranceFilter.SCREEN_CENTER_Y)) }

    @ScriptInterface
    fun screenCoverage(@FloatRange(0.0, 1.0) min: Double) = also { addFilter(DoubleMinFilter(DoubleMinFilter.SCREEN_COVERAGE, min)) }

    @ScriptInterface
    fun screenCoverage() = also { addFilter(DoubleMinFilter(DoubleMinFilter.SCREEN_COVERAGE)) }

    @ScriptInterface
    fun algorithm(str: String) = when {
        str.equals("BFS", true) -> also { searchAlgorithm = BFS }
        str.equals("DFS", true) -> also { searchAlgorithm = DFS }
        else -> throw IllegalArgumentException(str(R.string.error_unknown_algorithm_selector_param, str))
    }

    @ScriptInterface
    fun action(vararg actions: Any) = also { addFilter(ActionFilter(actions)) }

    @ScriptInterface
    fun filter(filter: BooleanFilter.BooleanSupplier) = also {
        addFilter(object : Filter {
            override fun filter(node: UiObject) = filter[node]
        })
    }

    @ScriptInterface
    fun hasChildren() = also { addFilter(BooleanFilter(BooleanFilter.HAS_CHILDREN)) }

    @ScriptInterface
    fun hasChildren(b: Boolean) = also { addFilter(BooleanFilter(BooleanFilter.HAS_CHILDREN, b)) }

    @ScriptInterface
    fun checkable(b: Boolean) = also { addFilter(BooleanFilter(BooleanFilter.CHECKABLE, b)) }

    @ScriptInterface
    fun checkable() = also { addFilter(BooleanFilter(BooleanFilter.CHECKABLE)) }

    @ScriptInterface
    fun checked(b: Boolean) = also { addFilter(BooleanFilter(BooleanFilter.CHECKED, b)) }

    @ScriptInterface
    fun checked() = also { addFilter(BooleanFilter(BooleanFilter.CHECKED)) }

    @ScriptInterface
    fun focusable(b: Boolean) = also { addFilter(BooleanFilter(BooleanFilter.FOCUSABLE, b)) }

    @ScriptInterface
    fun focusable() = also { addFilter(BooleanFilter(BooleanFilter.FOCUSABLE)) }

    @ScriptInterface
    fun focused(b: Boolean) = also { addFilter(BooleanFilter(BooleanFilter.FOCUSED, b)) }

    @ScriptInterface
    fun focused() = also { addFilter(BooleanFilter(BooleanFilter.FOCUSED)) }

    @ScriptInterface
    fun visibleToUser(b: Boolean) = also { addFilter(BooleanFilter(BooleanFilter.VISIBLE_TO_USER, b)) }

    @ScriptInterface
    fun visibleToUser() = also { addFilter(BooleanFilter(BooleanFilter.VISIBLE_TO_USER)) }

    @ScriptInterface
    fun accessibilityFocused(b: Boolean) = also { addFilter(BooleanFilter(BooleanFilter.ACCESSIBILITY_FOCUSED, b)) }

    @ScriptInterface
    fun accessibilityFocused() = also { addFilter(BooleanFilter(BooleanFilter.ACCESSIBILITY_FOCUSED)) }

    @ScriptInterface
    fun selected(b: Boolean) = also { addFilter(BooleanFilter(BooleanFilter.SELECTED, b)) }

    @ScriptInterface
    fun selected() = also { addFilter(BooleanFilter(BooleanFilter.SELECTED)) }

    @ScriptInterface
    fun clickable(b: Boolean) = also { addFilter(BooleanFilter(BooleanFilter.CLICKABLE, b)) }

    @ScriptInterface
    fun clickable() = also { addFilter(BooleanFilter(BooleanFilter.CLICKABLE)) }

    @ScriptInterface
    fun longClickable(b: Boolean) = also { addFilter(BooleanFilter(BooleanFilter.LONG_CLICKABLE, b)) }

    @ScriptInterface
    fun longClickable() = also { addFilter(BooleanFilter(BooleanFilter.LONG_CLICKABLE)) }

    @ScriptInterface
    fun enabled(b: Boolean) = also { addFilter(BooleanFilter(BooleanFilter.ENABLED, b)) }

    @ScriptInterface
    fun enabled() = also { addFilter(BooleanFilter(BooleanFilter.ENABLED)) }

    @ScriptInterface
    fun password(b: Boolean) = also { addFilter(BooleanFilter(BooleanFilter.PASSWORD, b)) }

    @ScriptInterface
    fun password() = also { addFilter(BooleanFilter(BooleanFilter.PASSWORD)) }

    @ScriptInterface
    fun scrollable(b: Boolean) = also { addFilter(BooleanFilter(BooleanFilter.SCROLLABLE, b)) }

    @ScriptInterface
    fun scrollable() = also { addFilter(BooleanFilter(BooleanFilter.SCROLLABLE)) }

    @ScriptInterface
    fun editable(b: Boolean) = also { addFilter(BooleanFilter(BooleanFilter.EDITABLE, b)) }

    @ScriptInterface
    fun editable() = also { addFilter(BooleanFilter(BooleanFilter.EDITABLE)) }

    @ScriptInterface
    fun contentInvalid(b: Boolean) = also { addFilter(BooleanFilter(BooleanFilter.CONTENT_INVALID, b)) }

    @ScriptInterface
    fun contentInvalid() = also { addFilter(BooleanFilter(BooleanFilter.CONTENT_INVALID)) }

    @ScriptInterface
    fun contextClickable(b: Boolean) = also { addFilter(BooleanFilter(BooleanFilter.CONTEXT_CLICKABLE, b)) }

    @ScriptInterface
    fun contextClickable() = also { addFilter(BooleanFilter(BooleanFilter.CONTEXT_CLICKABLE)) }

    @ScriptInterface
    fun multiLine(b: Boolean) = also { addFilter(BooleanFilter(BooleanFilter.MULTI_LINE, b)) }

    @ScriptInterface
    fun multiLine() = also { addFilter(BooleanFilter(BooleanFilter.MULTI_LINE)) }

    @ScriptInterface
    fun dismissable(b: Boolean) = also { addFilter(BooleanFilter(BooleanFilter.DISMISSABLE, b)) }

    @ScriptInterface
    fun dismissable() = also { addFilter(BooleanFilter(BooleanFilter.DISMISSABLE)) }

    @ScriptInterface
    fun depth(d: Int) = also { addFilter(IntFilter(IntFilter.DEPTH, d)) }

    @ScriptInterface
    fun row(d: Int) = also { addFilter(IntFilter(IntFilter.ROW, d)) }

    @ScriptInterface
    fun rowCount(d: Int) = also { addFilter(IntFilter(IntFilter.ROW_COUNT, d)) }

    @ScriptInterface
    fun rowSpan(d: Int) = also { addFilter(IntFilter(IntFilter.ROW_SPAN, d)) }

    @ScriptInterface
    fun column(d: Int) = also { addFilter(IntFilter(IntFilter.COLUMN, d)) }

    @ScriptInterface
    fun columnCount(d: Int) = also { addFilter(IntFilter(IntFilter.COLUMN_COUNT, d)) }

    @ScriptInterface
    fun columnSpan(d: Int) = also { addFilter(IntFilter(IntFilter.COLUMN_SPAN, d)) }

    @ScriptInterface
    fun drawingOrder(order: Int) = also { addFilter(IntFilter(IntFilter.DRAWING_ORDER, order)) }

    @ScriptInterface
    fun indexInParent(index: Int) = also { addFilter(IntFilter(IntFilter.INDEX_IN_PARENT, index)) }

    @ScriptInterface
    fun childCount(count: Int) = also { addFilter(IntFilter(IntFilter.CHILD_COUNT, count)) }

    @ScriptInterface
    fun minChildCount(min: Int) = also { addFilter(MinIntFilter(MinIntFilter.CHILD_COUNT, min)) }

    @ScriptInterface
    fun maxChildCount(max: Int) = also { addFilter(MaxIntFilter(MaxIntFilter.CHILD_COUNT, max)) }

    @ScriptInterface
    fun findOnce(): UiObject? = findOnce(0)

    @ScriptInterface
    fun findOnce(index: Int): UiObject? = find(index + 1).takeIf { index < it.size() }?.get(index)

    @ScriptInterface
    fun exists() = findOnce() != null

    @ScriptInterface
    fun find(): UiObjectCollection = find(Int.MAX_VALUE)

    @ScriptInterface
    fun findOne(timeout: Long): UiObject? {
        if (isMainThread()) {
            throw IllegalThreadStateException(str(R.string.error_function_called_in_ui_thread, "findOne"))
        }
        val start = SystemClock.uptimeMillis()
        fun timedOut() = timeout > 0 && SystemClock.uptimeMillis() - start > timeout
        do {
            find(1).takeIf { it.isNotEmpty() }?.let { return it[0] }
            if (timedOut()) return null else intermission()
        } while (true)
    }

    @ScriptInterface
    @Deprecated("Deprecated in Java", ReplaceWith("untilFindOne()"))
    fun findOne(): UiObject = findOne(-1)!!

    @ScriptInterface
    fun untilFindOne(): UiObject {
        if (isMainThread()) {
            throw IllegalThreadStateException(str(R.string.error_function_called_in_ui_thread, "untilFindOne"))
        }
        return findOne(-1)!!
    }

    @ScriptInterface
    fun untilFind(): UiObjectCollection {
        if (isMainThread()) {
            throw IllegalThreadStateException(str(R.string.error_function_called_in_ui_thread, "untilFind"))
        }
        do {
            find().takeIf { it.isNotEmpty() }?.let { return it } ?: intermission()
        } while (true)
    }

    @ScriptInterface
    @Deprecated("Deprecated in Java", ReplaceWith("untilFind()"))
    fun waitFor(): UiObjectCollection {
        if (isMainThread()) {
            throw IllegalThreadStateException(str(R.string.error_function_called_in_ui_thread, "waitFor"))
        }
        return untilFind()
    }

    @ScriptInterface
    fun performAction(action: Int) = performAction(action, *emptyArray())

    @ScriptInterface
    override fun performAction(action: Int, vararg arguments: ActionArgument): Boolean {
        return untilFind().performAction(action, *arguments)
    }

    override fun toString() = selector.toString()

    override fun toStringReadable() = "[${UiSelector::class.java.simpleName}: ${toString()}]"

    internal fun findOf(root: UiObject): UiObjectCollection = findOf(root, Int.MAX_VALUE)

    internal fun findOneOf(root: UiObject): UiObject? = findOf(root, 1).takeIf { it.size() > 0 }?.get(0)

    internal fun findAndReturnList(root: UiObject, max: Int = Int.MAX_VALUE): List<UiObject> = searchAlgorithm.search(root, selector, max)

    protected fun findImpl(max: Int): UiObjectCollection = findImpl(mAccessibilityBridge?.windowRoots() ?: emptyList(), max)

    protected fun findImpl(node: AccessibilityNodeInfo, max: Int): UiObjectCollection = findImpl(listOf(node), max)

    protected fun findImpl(roots: List<AccessibilityNodeInfo?>, max: Int): UiObjectCollection {
        mAccessibilityBridge ?: return of(emptyList())
        fun isInWhitelist(node: AccessibilityNodeInfo) = !mAccessibilityBridge.config.isInBlacklist("${node.packageName}")
        val result = mutableListOf<UiObject?>()
        for (root in roots.filterNotNull()) {
            if (isInWhitelist(root)) {
                result.addAll(findAndReturnList(UiObject.createRoot(root, mAllocator), max - result.size))
                if (result.size >= max) break
            }
        }
        return of(result)
    }

    protected fun find(max: Int): UiObjectCollection {
        mA11yTool.ensureService()
        mAccessibilityBridge ?: return findImpl(max)
        if (isMainThread()) return findImpl(max)
        if (mAccessibilityBridge.flags and AccessibilityBridge.FLAG_FIND_ON_UI_THREAD == 0) return findImpl(max)
        return VolatileBox<UiObjectCollection>().run {
            mAccessibilityBridge.post { unblock(findImpl(max)) }
            blockedGet()
        }
    }

    private fun findOf(root: UiObject, max: Int): UiObjectCollection = of(findAndReturnList(root, max))

    private fun addFilter(filter: Filter) = also { selector.add(filter) }

    fun append(selector: UiSelector?) = also {
        selector?.let {
            this.selector.append(it)
            this.searchAlgorithm = it.searchAlgorithm
        }
    }

    fun plus(selector: UiSelector?) = selector?.let { paramSel ->
        UiSelector().also { newSel ->
            newSel.selector.filters.addAll(this.selector.filters + paramSel.selector.filters)
            newSel.searchAlgorithm = paramSel.searchAlgorithm
        }
    } ?: this

    companion object {

        internal const val ID_IDENTIFIER = ":id/"

        @JvmStatic
        fun pickup(
            scriptRuntime: ScriptRuntime,
            root: UiObject?,
            selector: Any?,
            compass: CharSequence?,
            resultType: Any?,
            callback: BaseFunction? = null,
        ): Any? = Picker.Builder()
            .setRoot(root)
            .setCompass(compass)
            .setResultType(resultType)
            .setSelector(scriptRuntime, selector)
            .setCallback(callback)
            .build()
            .pick(scriptRuntime)

        internal class Picker private constructor(
            val root: UiObject?,
            val selector: UiSelector?,
            val compass: CharSequence?,
            val resultType: Any?,
            val callback: BaseFunction? = null,
        ) {

            internal fun pick(scriptRuntime: ScriptRuntime) = Detector(compass, object : Result(resultType) {

                override fun byOne() = selector?.let { sel ->
                    root?.let { return sel.findOneOf(it) } ?: sel.findOnce()
                }

                override fun byAll() = selector?.let { sel ->
                    root?.let { return sel.findOf(it) } ?: sel.find()
                } ?: EMPTY

            }, callback, selector).detect(scriptRuntime)

            internal class Builder {

                private var mRoot: UiObject? = null
                private var mSelector = UiSelector()
                private var mCompass: CharSequence? = COMPASS_PASS_ON
                private var mResultType: Any = RESULT_TYPE_WIDGET
                private var mCallback: BaseFunction? = null

                private val cString = String::class.java
                private val cBoolean = Boolean::class.java
                private val cInt = Int::class.java
                private val cDouble = Double::class.java
                private val cBooleanSupplier = BooleanFilter.BooleanSupplier::class.java
                private val cNativeRegExp = NativeRegExp::class.java
                private val cApp = App::class.java

                /* cDouble */
                private val doubleTypes = hashMapOf(
                    "bounds" to arrayOf(cDouble, cDouble, cDouble, cDouble),
                    "boundsContains" to arrayOf(cDouble, cDouble, cDouble, cDouble),
                    "boundsInside" to arrayOf(cDouble, cDouble, cDouble, cDouble),
                    "boundsLeft" to arrayOf(arrayOf(cDouble, cDouble), cDouble),
                    "boundsTop" to arrayOf(arrayOf(cDouble, cDouble), cDouble),
                    "boundsRight" to arrayOf(arrayOf(cDouble, cDouble), cDouble),
                    "boundsBottom" to arrayOf(arrayOf(cDouble, cDouble), cDouble),
                    "boundsWidth" to arrayOf(arrayOf(cDouble, cDouble), cDouble),
                    "boundsHeight" to arrayOf(arrayOf(cDouble, cDouble), cDouble),
                    "boundsCenterX" to arrayOf(arrayOf(cDouble, cDouble), cDouble),
                    "boundsCenterY" to arrayOf(arrayOf(cDouble, cDouble), cDouble),
                    "boundsMinLeft" to cDouble,
                    "boundsMinTop" to cDouble,
                    "boundsMinRight" to cDouble,
                    "boundsMinBottom" to cDouble,
                    "boundsMinWidth" to cDouble,
                    "boundsMinHeight" to cDouble,
                    "boundsMinCenterX" to cDouble,
                    "boundsMinCenterY" to cDouble,
                    "boundsMaxLeft" to cDouble,
                    "boundsMaxTop" to cDouble,
                    "boundsMaxRight" to cDouble,
                    "boundsMaxBottom" to cDouble,
                    "boundsMaxWidth" to cDouble,
                    "boundsMaxHeight" to cDouble,
                    "boundsMaxCenterX" to cDouble,
                    "boundsMaxCenterY" to cDouble,
                    "left" to arrayOf(arrayOf(cDouble, cDouble), cDouble),
                    "top" to arrayOf(arrayOf(cDouble, cDouble), cDouble),
                    "right" to arrayOf(arrayOf(cDouble, cDouble), cDouble),
                    "bottom" to arrayOf(arrayOf(cDouble, cDouble), cDouble),
                    "width" to arrayOf(arrayOf(cDouble, cDouble), cDouble),
                    "height" to arrayOf(arrayOf(cDouble, cDouble), cDouble),
                    "centerX" to arrayOf(arrayOf(cDouble, cDouble), cDouble),
                    "centerY" to arrayOf(arrayOf(cDouble, cDouble), cDouble),
                    "minLeft" to cDouble,
                    "minTop" to cDouble,
                    "minRight" to cDouble,
                    "minBottom" to cDouble,
                    "minWidth" to cDouble,
                    "minHeight" to cDouble,
                    "minCenterX" to cDouble,
                    "minCenterY" to cDouble,
                    "maxLeft" to cDouble,
                    "maxTop" to cDouble,
                    "maxRight" to cDouble,
                    "maxBottom" to cDouble,
                    "maxWidth" to cDouble,
                    "maxHeight" to cDouble,
                    "maxCenterX" to cDouble,
                    "maxCenterY" to cDouble,
                    "screenCenterX" to arrayOf(arrayOf(cBoolean, cDouble), cDouble),
                    "screenCenterY" to arrayOf(arrayOf(cBoolean, cDouble), cDouble),
                    "screenCoverage" to cDouble,
                )

                /* cBoolean / null */
                private val nullableBoolTypes = listOf(
                    "accessibilityFocused", "checkable", "checked", "clickable",
                    "contentInvalid", "contextClickable", "dismissable", "editable",
                    "enabled", "focusable", "focused", "longClickable", "multiLine",
                    "password", "scrollable", "selected", "visibleToUser", "hasChildren",
                )

                /* cString */
                private val stringTypes = listOf(
                    "algorithm", "idHex",
                    "id", "idContains", "idEndsWith", "idStartsWith",
                    "desc", "descContains", "descEndsWith", "descStartsWith",
                    "text", "textContains", "textEndsWith", "textStartsWith",
                    "content", "contentContains", "contentEndsWith", "contentStartsWith",
                    "packageNameContains", "packageNameEndsWith", "packageNameStartsWith",
                    "className", "classNameContains", "classNameEndsWith", "classNameStartsWith",
                )

                /* cString / cNativeRegExp */
                private val regexTypes = listOf(
                    "idMatches", "idMatch",
                    "descMatches", "descMatch",
                    "textMatches", "textMatch",
                    "contentMatches", "contentMatch",
                    "packageNameMatches", "packageNameMatch",
                    "classNameMatches", "classNameMatch",
                )

                /* cInt */
                private val intTypes = listOf(
                    "row", "rowCount", "rowSpan",
                    "column", "columnCount", "columnSpan",
                    "depth", "drawingOrder", "indexInParent",
                    "childCount", "minChildCount", "maxChildCount",
                )

                /* cApp / cString */
                private val appTypes = listOf(
                    "currentApp", "packageName",
                )

                fun setRoot(root: UiObject?) = also { mRoot = root }

                fun setSelector(scriptRuntime: ScriptRuntime, selector: Any?) = also {
                    when (selector) {
                        null -> return@also
                        is String -> setSelector(selector)
                        is NativeRegExp -> setSelector(selector)
                        is UiSelector -> setSelector(selector)
                        is Map<*, *> -> setSelector(scriptRuntime, selector)
                        is List<*> -> setSelector(scriptRuntime, selector)
                        is BigInteger -> setSelector(selector.toString())
                        is Double -> setSelector(selector.toInt().toString())
                        is NativeJavaMethod -> throw SelectorMethodWithoutCallingException(selector)
                        else -> throw IllegalArgumentException(str(R.string.error_unknown_picker_selector_type, selector.toString(), selector::class.java))
                    }
                }

                private fun setSelector(selector: UiSelector) = also {
                    mSelector.append(selector)
                }

                private fun setSelector(selector: String) = also {
                    mSelector.append(UiSelector().content(selector))
                }

                private fun setSelector(selector: NativeRegExp) = also {
                    mSelector.append(UiSelector().contentMatch(selector))
                }

                private fun setSelector(scriptRuntime: ScriptRuntime, selector: List<*>): Builder = also {
                    selector.filterNotNull().forEach { setSelector(scriptRuntime, it) }
                }

                private fun setSelector(scriptRuntime: ScriptRuntime, selector: Map<*, *>) = also {
                    mSelector.append(checkSelectorMap(scriptRuntime, selector))
                }

                // FIXME by SuperMonster003 on Nov 23, 2022.
                //  ! A more elegant way is needed.
                //  ! Especially for list<*> typed value handling.
                //  ! zh-CN:
                //  ! 需要一种更优雅的方式.
                //  ! 尤其是对于 list<*> 类型值的处理.
                private fun checkSelectorMap(scriptRuntime: ScriptRuntime, selector: Map<*, *>) = UiSelector().also { tmp ->
                    selector.entries.forEach { (key, value) ->
                        try {
                            when (key) {
                                !is String -> return@forEach
                                "action" -> when (value) {
                                    null -> return@forEach
                                    is NativeArray -> tmp.action(*value.filterNotNull().toTypedArray())
                                    else -> tmp.action(value)
                                }
                                "filter" -> tmp.filter(object : BooleanFilter.BooleanSupplier {
                                    override fun get(node: UiObject): Boolean {
                                        val func = (if (value is NativeArray) value[0] else value) as BaseFunction
                                        return RhinoUtils.callFunction(scriptRuntime, func, arrayOf(node)) as Boolean
                                    }
                                })
                                else -> when (value) {
                                    null -> tmp::class.java
                                        .getMethod(key)
                                        .invoke(tmp)
                                    is String -> tmp::class.java
                                        .getMethod(key, cString)
                                        .invoke(tmp, value.toString())
                                    is NativeRegExp -> tmp::class.java
                                        .getMethod(key, cNativeRegExp)
                                        .invoke(tmp, value)
                                    is Boolean -> tmp::class.java
                                        .getMethod(key, cBoolean)
                                        .invoke(tmp, value)
                                    is Double -> when (doubleTypes.containsKey(key)) {
                                        true -> tmp::class.java
                                            .getMethod(key, cDouble)
                                            .invoke(tmp, value)
                                        else -> tmp::class.java
                                            .getMethod(key, cInt)
                                            .invoke(tmp, value.toInt())
                                    }
                                    is BigInteger -> tmp::class.java
                                        .getMethod(key, cInt)
                                        .invoke(tmp, value.toInt())
                                    is BaseFunction -> tmp::class.java
                                        .getMethod(key, cBooleanSupplier)
                                        .invoke(tmp, object : BooleanFilter.BooleanSupplier {
                                            override fun get(node: UiObject) = RhinoUtils.callFunction(scriptRuntime, value, arrayOf(node)) as Boolean
                                        })
                                    is List<*> -> when {
                                        doubleTypes.containsKey(key) -> when (val types = doubleTypes.getValue(key)) {
                                            cDouble -> tmp::class.java
                                                .getMethod(key, cDouble)
                                                .invoke(tmp, *value.map { it.toString().toDouble() }.toTypedArray())
                                            is Array<*> -> when ( /* isOverloaded */ types.any { it is Array<*> }) {
                                                true -> when (value.size) {
                                                    1 -> types.find { it == cDouble }?.let {
                                                        tmp::class.java
                                                            .getMethod(key, cDouble)
                                                            .invoke(tmp, value[0].toString().toDouble())
                                                    } ?: throw Exception("UiSelector: $key($value)")
                                                    2 -> types.find { it is Array<*> }?.let { type: Any ->
                                                        val t = type as Array<*>
                                                        when {
                                                            t[0] == cDouble && t[1] == cDouble -> tmp::class.java
                                                                .getMethod(key, cDouble, cDouble)
                                                                .invoke(tmp, *value.map { it.toString().toDouble() }.toTypedArray())
                                                            t[0] == cBoolean && t[1] == cDouble -> tmp::class.java
                                                                .getMethod(key, cBoolean, cDouble)
                                                                .invoke(tmp, *value.mapIndexed { index, any ->
                                                                    when (index) {
                                                                        0 -> any.toString().toBoolean()
                                                                        1 -> any.toString().toDouble()
                                                                        else -> throw Exception("UiSelector: $key($value)")
                                                                    }
                                                                }.toTypedArray())
                                                            else -> throw Exception("UiSelector: $key($value)")
                                                        }
                                                    } ?: throw Exception("UiSelector: $key($value)")
                                                    else -> throw Exception("UiSelector: $key($value)")
                                                }
                                                else -> @Suppress("UNCHECKED_CAST") tmp::class.java
                                                    .getMethod(key, *(types as Array<out Class<*>>))
                                                    .invoke(tmp, *value.map { it.toString().toDouble() }.toTypedArray())
                                            }
                                            else -> throw Exception("UiSelector: $key($value)")
                                        }
                                        intTypes.contains(key) -> tmp::class.java
                                            .getMethod(key, cInt)
                                            .invoke(tmp, value[0].toString().toInt())
                                        stringTypes.contains(key) -> tmp::class.java
                                            .getMethod(key, cString)
                                            .invoke(tmp, value[0])
                                        regexTypes.contains(key) -> when (value[0]) {
                                            is NativeRegExp -> tmp::class.java
                                                .getMethod(key, cNativeRegExp)
                                                .invoke(tmp, value[0])
                                            is String -> tmp::class.java
                                                .getMethod(key, cString)
                                                .invoke(tmp, value[0])
                                            else -> throw Exception("UiSelector: $key($value)")
                                        }
                                        appTypes.contains(key) -> when (value[0]) {
                                            is App -> tmp::class.java
                                                .getMethod(key, cApp)
                                                .invoke(tmp, value[0])
                                            is String -> tmp::class.java
                                                .getMethod(key, cString)
                                                .invoke(tmp, value[0])
                                            else -> throw Exception("UiSelector: $key($value)")
                                        }
                                        nullableBoolTypes.contains(key) -> when (value.size) {
                                            0 -> tmp::class.java
                                                .getMethod(key)
                                                .invoke(tmp)
                                            1 -> tmp::class.java
                                                .getMethod(key, cBoolean)
                                                .invoke(tmp, value[0].toString().toBoolean())
                                            else -> throw Exception("UiSelector: $key($value)")
                                        }
                                        else -> throw Exception("UiSelector: $key($value)")
                                    }
                                    else -> throw Exception("UiSelector: $key($value)")
                                }
                            }
                        } catch (e: NoSuchMethodException) {
                            e.printStackTrace()
                            val s = str(R.string.error_no_such_selector_method, key.toString(), value.toString(), value?.let { it::class.java } ?: "null")
                            throw IllegalArgumentException(s)
                        } catch (e: Exception) {
                            e.printStackTrace()
                            throw IllegalMapSelectorException(key.toString(), value ?: "null")
                        }
                    }
                }

                fun setCompass(compass: CharSequence?) = also { compass?.let { mCompass = it } }

                fun setResultType(resultType: Any?) = also { resultType?.let { mResultType = it } }

                fun setCallback(callback: BaseFunction?) = also { callback?.let { mCallback = it } }

                fun build() = Picker(mRoot, mSelector, mCompass, mResultType, mCallback)

            }

        }

        internal class IllegalMapSelectorException(key: String, value: Any) : IllegalArgumentException(
            str(R.string.error_invalid_selector_map_element, key, value.run {
                when (this) {
                    is NativeArray -> "[${joinToString { it.toString() }}]"
                    else -> toString()
                }
            }, value::class.java)
        )

        internal class SelectorMethodWithoutCallingException(selector: NativeJavaMethod) : IllegalArgumentException(
            str(R.string.error_selector_method_without_calling, selector.toString())
        )

        private fun intermission() {
            if (Thread.currentThread().isInterrupted) {
                throw ScriptInterruptedException()
            }
            try {
                Thread.sleep(50)
            } catch (e: InterruptedException) {
                throw ScriptInterruptedException()
            }
        }

    }

}
