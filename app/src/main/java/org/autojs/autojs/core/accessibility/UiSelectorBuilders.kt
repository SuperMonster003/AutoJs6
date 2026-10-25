@file:Suppress("unused", "DEPRECATION")

package org.autojs.autojs.core.accessibility

import androidx.annotation.FloatRange
import org.autojs.autojs.annotation.ScriptInterface
import org.autojs.autojs.core.automator.filter.BooleanFilter
import org.autojs.autojs.util.App
import org.mozilla.javascript.regexp.NativeRegExp

/*
 * GENERATED from SelectorRegistry by SelectorBuilderGenerator (app/src/test), do not edit by hand:
 * change the registry, run UiSelectorBuildersTest and copy the rendering it writes to
 * app/build/generated-selector-builders/ over this file.
 *
 * The builder methods of UiSelector, one per registry overload, each routing to the registry
 * through applyEntry. They exist as real methods so that scripts (Rhino), the d.ts generator and
 * Kotlin callers see the same typed surface as before; the behaviour lives in the registry.
 *
 * zh-CN: 由 SelectorRegistry 经 SelectorBuilderGenerator (app/src/test) 生成, 请勿手工编辑: 修改注册表后运行
 * UiSelectorBuildersTest, 并把它写到 app/build/generated-selector-builders/ 的渲染复制到本文件.
 *
 * UiSelector 的 builder 方法, 注册表的每个重载对应一个, 全部经 applyEntry 转到注册表. 它们以真实方法存在, 使脚本
 * (Rhino), d.ts 生成器与 Kotlin 调用方看到与从前相同的类型化表面; 行为在注册表中.
 */
abstract class UiSelectorBuilders {

    /** Applies registry property [name] with [args] to this selector and returns it. zh-CN: 把注册表属性 [name] 以 [args] 施加到本选择器并返回它. */
    protected abstract fun applyEntry(name: String, vararg args: Any?): UiSelector

    @ScriptInterface
    fun id(s: String): UiSelector = applyEntry("id", s)

    @ScriptInterface
    fun id(regex: NativeRegExp): UiSelector = applyEntry("id", regex)

    @ScriptInterface
    fun idStartsWith(prefix: String): UiSelector = applyEntry("idStartsWith", prefix)

    @ScriptInterface
    fun idEndsWith(suffix: String): UiSelector = applyEntry("idEndsWith", suffix)

    @ScriptInterface
    fun idContains(s: String): UiSelector = applyEntry("idContains", s)

    @ScriptInterface
    fun idContains(regex: NativeRegExp): UiSelector = applyEntry("idContains", regex)

    @ScriptInterface
    @Deprecated("Deprecated in Java", ReplaceWith("idMatch(s)"))
    fun idMatches(s: String): UiSelector = applyEntry("idMatches", s)

    @ScriptInterface
    @Deprecated("Deprecated in Java", ReplaceWith("idMatch(regex)"))
    fun idMatches(regex: NativeRegExp): UiSelector = applyEntry("idMatches", regex)

    @ScriptInterface
    fun idMatch(s: String): UiSelector = applyEntry("idMatch", s)

    @ScriptInterface
    fun idMatch(regex: NativeRegExp): UiSelector = applyEntry("idMatch", regex)

    @ScriptInterface
    fun idHex(str: String): UiSelector = applyEntry("idHex", str)

    @ScriptInterface
    fun text(s: String): UiSelector = applyEntry("text", s)

    @ScriptInterface
    fun text(regex: NativeRegExp): UiSelector = applyEntry("text", regex)

    @ScriptInterface
    fun textStartsWith(prefix: String): UiSelector = applyEntry("textStartsWith", prefix)

    @ScriptInterface
    fun textEndsWith(suffix: String): UiSelector = applyEntry("textEndsWith", suffix)

    @ScriptInterface
    fun textContains(s: String): UiSelector = applyEntry("textContains", s)

    @ScriptInterface
    fun textContains(regex: NativeRegExp): UiSelector = applyEntry("textContains", regex)

    @ScriptInterface
    @Deprecated("Deprecated in Java", ReplaceWith("textMatch(s)"))
    fun textMatches(s: String): UiSelector = applyEntry("textMatches", s)

    @ScriptInterface
    @Deprecated("Deprecated in Java", ReplaceWith("textMatch(regex)"))
    fun textMatches(regex: NativeRegExp): UiSelector = applyEntry("textMatches", regex)

    @ScriptInterface
    fun textMatch(s: String): UiSelector = applyEntry("textMatch", s)

    @ScriptInterface
    fun textMatch(regex: NativeRegExp): UiSelector = applyEntry("textMatch", regex)

    @ScriptInterface
    fun desc(s: String): UiSelector = applyEntry("desc", s)

    @ScriptInterface
    fun desc(regex: NativeRegExp): UiSelector = applyEntry("desc", regex)

    @ScriptInterface
    fun descStartsWith(prefix: String): UiSelector = applyEntry("descStartsWith", prefix)

    @ScriptInterface
    fun descEndsWith(suffix: String): UiSelector = applyEntry("descEndsWith", suffix)

    @ScriptInterface
    fun descContains(s: String): UiSelector = applyEntry("descContains", s)

    @ScriptInterface
    fun descContains(regex: NativeRegExp): UiSelector = applyEntry("descContains", regex)

    @ScriptInterface
    @Deprecated("Deprecated in Java", ReplaceWith("descMatch(s)"))
    fun descMatches(s: String): UiSelector = applyEntry("descMatches", s)

    @ScriptInterface
    @Deprecated("Deprecated in Java", ReplaceWith("descMatch(regex)"))
    fun descMatches(regex: NativeRegExp): UiSelector = applyEntry("descMatches", regex)

    @ScriptInterface
    fun descMatch(s: String): UiSelector = applyEntry("descMatch", s)

    @ScriptInterface
    fun descMatch(regex: NativeRegExp): UiSelector = applyEntry("descMatch", regex)

    @ScriptInterface
    fun content(s: String): UiSelector = applyEntry("content", s)

    @ScriptInterface
    fun content(regex: NativeRegExp): UiSelector = applyEntry("content", regex)

    @ScriptInterface
    fun contentStartsWith(prefix: String): UiSelector = applyEntry("contentStartsWith", prefix)

    @ScriptInterface
    fun contentEndsWith(suffix: String): UiSelector = applyEntry("contentEndsWith", suffix)

    @ScriptInterface
    fun contentContains(s: String): UiSelector = applyEntry("contentContains", s)

    @ScriptInterface
    fun contentContains(regex: NativeRegExp): UiSelector = applyEntry("contentContains", regex)

    @ScriptInterface
    @Deprecated("Deprecated in Java", ReplaceWith("contentMatch(s)"))
    fun contentMatches(s: String): UiSelector = applyEntry("contentMatches", s)

    @ScriptInterface
    @Deprecated("Deprecated in Java", ReplaceWith("contentMatch(regex)"))
    fun contentMatches(regex: NativeRegExp): UiSelector = applyEntry("contentMatches", regex)

    @ScriptInterface
    fun contentMatch(s: String): UiSelector = applyEntry("contentMatch", s)

    @ScriptInterface
    fun contentMatch(regex: NativeRegExp): UiSelector = applyEntry("contentMatch", regex)

    @ScriptInterface
    fun className(s: String): UiSelector = applyEntry("className", s)

    @ScriptInterface
    fun className(regex: NativeRegExp): UiSelector = applyEntry("className", regex)

    @ScriptInterface
    fun classNameStartsWith(prefix: String): UiSelector = applyEntry("classNameStartsWith", prefix)

    @ScriptInterface
    fun classNameEndsWith(suffix: String): UiSelector = applyEntry("classNameEndsWith", suffix)

    @ScriptInterface
    fun classNameContains(s: String): UiSelector = applyEntry("classNameContains", s)

    @ScriptInterface
    fun classNameContains(regex: NativeRegExp): UiSelector = applyEntry("classNameContains", regex)

    @ScriptInterface
    @Deprecated("Deprecated in Java", ReplaceWith("classNameMatch(s)"))
    fun classNameMatches(s: String): UiSelector = applyEntry("classNameMatches", s)

    @ScriptInterface
    @Deprecated("Deprecated in Java", ReplaceWith("classNameMatch(regex)"))
    fun classNameMatches(regex: NativeRegExp): UiSelector = applyEntry("classNameMatches", regex)

    @ScriptInterface
    fun classNameMatch(s: String): UiSelector = applyEntry("classNameMatch", s)

    @ScriptInterface
    fun classNameMatch(regex: NativeRegExp): UiSelector = applyEntry("classNameMatch", regex)

    @ScriptInterface
    fun packageName(s: String): UiSelector = applyEntry("packageName", s)

    @ScriptInterface
    fun packageName(regex: NativeRegExp): UiSelector = applyEntry("packageName", regex)

    @ScriptInterface
    fun packageName(app: App): UiSelector = applyEntry("packageName", app)

    @ScriptInterface
    fun packageNameStartsWith(prefix: String): UiSelector = applyEntry("packageNameStartsWith", prefix)

    @ScriptInterface
    fun packageNameEndsWith(suffix: String): UiSelector = applyEntry("packageNameEndsWith", suffix)

    @ScriptInterface
    fun packageNameContains(s: String): UiSelector = applyEntry("packageNameContains", s)

    @ScriptInterface
    fun packageNameContains(regex: NativeRegExp): UiSelector = applyEntry("packageNameContains", regex)

    @ScriptInterface
    @Deprecated("Deprecated in Java", ReplaceWith("packageNameMatch(s)"))
    fun packageNameMatches(s: String): UiSelector = applyEntry("packageNameMatches", s)

    @ScriptInterface
    @Deprecated("Deprecated in Java", ReplaceWith("packageNameMatch(regex)"))
    fun packageNameMatches(regex: NativeRegExp): UiSelector = applyEntry("packageNameMatches", regex)

    @ScriptInterface
    fun packageNameMatch(s: String): UiSelector = applyEntry("packageNameMatch", s)

    @ScriptInterface
    fun packageNameMatch(regex: NativeRegExp): UiSelector = applyEntry("packageNameMatch", regex)

    @ScriptInterface
    fun currentApp(app: App): UiSelector = applyEntry("currentApp", app)

    @ScriptInterface
    fun currentApp(name: String): UiSelector = applyEntry("currentApp", name)

    @ScriptInterface
    fun bounds(l: Double, t: Double, r: Double, b: Double): UiSelector = applyEntry("bounds", l, t, r, b)

    @ScriptInterface
    fun boundsInside(l: Double, t: Double, r: Double, b: Double): UiSelector = applyEntry("boundsInside", l, t, r, b)

    @ScriptInterface
    fun boundsContains(l: Double, t: Double, r: Double, b: Double): UiSelector = applyEntry("boundsContains", l, t, r, b)

    @ScriptInterface
    fun boundsLeft(value: Double): UiSelector = applyEntry("boundsLeft", value)

    @ScriptInterface
    fun boundsLeft(min: Double, max: Double): UiSelector = applyEntry("boundsLeft", min, max)

    @ScriptInterface
    fun boundsTop(value: Double): UiSelector = applyEntry("boundsTop", value)

    @ScriptInterface
    fun boundsTop(min: Double, max: Double): UiSelector = applyEntry("boundsTop", min, max)

    @ScriptInterface
    fun boundsRight(value: Double): UiSelector = applyEntry("boundsRight", value)

    @ScriptInterface
    fun boundsRight(min: Double, max: Double): UiSelector = applyEntry("boundsRight", min, max)

    @ScriptInterface
    fun boundsBottom(value: Double): UiSelector = applyEntry("boundsBottom", value)

    @ScriptInterface
    fun boundsBottom(min: Double, max: Double): UiSelector = applyEntry("boundsBottom", min, max)

    @ScriptInterface
    fun boundsWidth(value: Double): UiSelector = applyEntry("boundsWidth", value)

    @ScriptInterface
    fun boundsWidth(min: Double, max: Double): UiSelector = applyEntry("boundsWidth", min, max)

    @ScriptInterface
    fun boundsHeight(value: Double): UiSelector = applyEntry("boundsHeight", value)

    @ScriptInterface
    fun boundsHeight(min: Double, max: Double): UiSelector = applyEntry("boundsHeight", min, max)

    @ScriptInterface
    fun boundsCenterX(value: Double): UiSelector = applyEntry("boundsCenterX", value)

    @ScriptInterface
    fun boundsCenterX(min: Double, max: Double): UiSelector = applyEntry("boundsCenterX", min, max)

    @ScriptInterface
    fun boundsCenterY(value: Double): UiSelector = applyEntry("boundsCenterY", value)

    @ScriptInterface
    fun boundsCenterY(min: Double, max: Double): UiSelector = applyEntry("boundsCenterY", min, max)

    @ScriptInterface
    fun boundsMinLeft(min: Double): UiSelector = applyEntry("boundsMinLeft", min)

    @ScriptInterface
    fun boundsMinTop(min: Double): UiSelector = applyEntry("boundsMinTop", min)

    @ScriptInterface
    fun boundsMinRight(min: Double): UiSelector = applyEntry("boundsMinRight", min)

    @ScriptInterface
    fun boundsMinBottom(min: Double): UiSelector = applyEntry("boundsMinBottom", min)

    @ScriptInterface
    fun boundsMinWidth(min: Double): UiSelector = applyEntry("boundsMinWidth", min)

    @ScriptInterface
    fun boundsMinHeight(min: Double): UiSelector = applyEntry("boundsMinHeight", min)

    @ScriptInterface
    fun boundsMinCenterX(min: Double): UiSelector = applyEntry("boundsMinCenterX", min)

    @ScriptInterface
    fun boundsMinCenterY(min: Double): UiSelector = applyEntry("boundsMinCenterY", min)

    @ScriptInterface
    fun boundsMaxLeft(max: Double): UiSelector = applyEntry("boundsMaxLeft", max)

    @ScriptInterface
    fun boundsMaxTop(max: Double): UiSelector = applyEntry("boundsMaxTop", max)

    @ScriptInterface
    fun boundsMaxRight(max: Double): UiSelector = applyEntry("boundsMaxRight", max)

    @ScriptInterface
    fun boundsMaxBottom(max: Double): UiSelector = applyEntry("boundsMaxBottom", max)

    @ScriptInterface
    fun boundsMaxWidth(max: Double): UiSelector = applyEntry("boundsMaxWidth", max)

    @ScriptInterface
    fun boundsMaxHeight(max: Double): UiSelector = applyEntry("boundsMaxHeight", max)

    @ScriptInterface
    fun boundsMaxCenterX(max: Double): UiSelector = applyEntry("boundsMaxCenterX", max)

    @ScriptInterface
    fun boundsMaxCenterY(max: Double): UiSelector = applyEntry("boundsMaxCenterY", max)

    @ScriptInterface
    fun left(value: Double): UiSelector = applyEntry("left", value)

    @ScriptInterface
    fun left(min: Double, max: Double): UiSelector = applyEntry("left", min, max)

    @ScriptInterface
    fun top(value: Double): UiSelector = applyEntry("top", value)

    @ScriptInterface
    fun top(min: Double, max: Double): UiSelector = applyEntry("top", min, max)

    @ScriptInterface
    fun right(value: Double): UiSelector = applyEntry("right", value)

    @ScriptInterface
    fun right(min: Double, max: Double): UiSelector = applyEntry("right", min, max)

    @ScriptInterface
    fun bottom(value: Double): UiSelector = applyEntry("bottom", value)

    @ScriptInterface
    fun bottom(min: Double, max: Double): UiSelector = applyEntry("bottom", min, max)

    @ScriptInterface
    fun width(value: Double): UiSelector = applyEntry("width", value)

    @ScriptInterface
    fun width(min: Double, max: Double): UiSelector = applyEntry("width", min, max)

    @ScriptInterface
    fun height(value: Double): UiSelector = applyEntry("height", value)

    @ScriptInterface
    fun height(min: Double, max: Double): UiSelector = applyEntry("height", min, max)

    @ScriptInterface
    fun centerX(value: Double): UiSelector = applyEntry("centerX", value)

    @ScriptInterface
    fun centerX(min: Double, max: Double): UiSelector = applyEntry("centerX", min, max)

    @ScriptInterface
    fun centerY(value: Double): UiSelector = applyEntry("centerY", value)

    @ScriptInterface
    fun centerY(min: Double, max: Double): UiSelector = applyEntry("centerY", min, max)

    @ScriptInterface
    fun minLeft(value: Double): UiSelector = applyEntry("minLeft", value)

    @ScriptInterface
    fun minTop(value: Double): UiSelector = applyEntry("minTop", value)

    @ScriptInterface
    fun minRight(value: Double): UiSelector = applyEntry("minRight", value)

    @ScriptInterface
    fun minBottom(value: Double): UiSelector = applyEntry("minBottom", value)

    @ScriptInterface
    fun minWidth(value: Double): UiSelector = applyEntry("minWidth", value)

    @ScriptInterface
    fun minHeight(value: Double): UiSelector = applyEntry("minHeight", value)

    @ScriptInterface
    fun minCenterX(value: Double): UiSelector = applyEntry("minCenterX", value)

    @ScriptInterface
    fun minCenterY(value: Double): UiSelector = applyEntry("minCenterY", value)

    @ScriptInterface
    fun maxLeft(value: Double): UiSelector = applyEntry("maxLeft", value)

    @ScriptInterface
    fun maxTop(value: Double): UiSelector = applyEntry("maxTop", value)

    @ScriptInterface
    fun maxRight(value: Double): UiSelector = applyEntry("maxRight", value)

    @ScriptInterface
    fun maxBottom(value: Double): UiSelector = applyEntry("maxBottom", value)

    @ScriptInterface
    fun maxWidth(value: Double): UiSelector = applyEntry("maxWidth", value)

    @ScriptInterface
    fun maxHeight(value: Double): UiSelector = applyEntry("maxHeight", value)

    @ScriptInterface
    fun maxCenterX(value: Double): UiSelector = applyEntry("maxCenterX", value)

    @ScriptInterface
    fun maxCenterY(value: Double): UiSelector = applyEntry("maxCenterY", value)

    @ScriptInterface
    fun screenCenterX(b: Boolean, @FloatRange(0.0, 0.5) tolerance: Double): UiSelector = applyEntry("screenCenterX", b, tolerance)

    @ScriptInterface
    fun screenCenterX(b: Boolean): UiSelector = applyEntry("screenCenterX", b)

    @ScriptInterface
    fun screenCenterX(@FloatRange(0.0, 0.5) tolerance: Double): UiSelector = applyEntry("screenCenterX", tolerance)

    @ScriptInterface
    fun screenCenterX(): UiSelector = applyEntry("screenCenterX")

    @ScriptInterface
    fun screenCenterY(b: Boolean, @FloatRange(0.0, 0.5) tolerance: Double): UiSelector = applyEntry("screenCenterY", b, tolerance)

    @ScriptInterface
    fun screenCenterY(b: Boolean): UiSelector = applyEntry("screenCenterY", b)

    @ScriptInterface
    fun screenCenterY(@FloatRange(0.0, 0.5) tolerance: Double): UiSelector = applyEntry("screenCenterY", tolerance)

    @ScriptInterface
    fun screenCenterY(): UiSelector = applyEntry("screenCenterY")

    @ScriptInterface
    fun screenCoverage(@FloatRange(0.0, 1.0) min: Double): UiSelector = applyEntry("screenCoverage", min)

    @ScriptInterface
    fun screenCoverage(): UiSelector = applyEntry("screenCoverage")

    @ScriptInterface
    fun algorithm(str: String): UiSelector = applyEntry("algorithm", str)

    @ScriptInterface
    fun normalize(): UiSelector = applyEntry("normalize")

    @ScriptInterface
    fun normalize(b: Boolean): UiSelector = applyEntry("normalize", b)

    @ScriptInterface
    fun normalize(options: Map<*, *>): UiSelector = applyEntry("normalize", options)

    @ScriptInterface
    fun prefer(vararg keys: String): UiSelector = applyEntry("prefer", *keys)

    @ScriptInterface
    fun action(vararg actions: Any): UiSelector = applyEntry("action", *actions)

    @ScriptInterface
    fun filter(filter: BooleanFilter.BooleanSupplier): UiSelector = applyEntry("filter", filter)

    @ScriptInterface
    fun hasChildren(): UiSelector = applyEntry("hasChildren")

    @ScriptInterface
    fun hasChildren(b: Boolean): UiSelector = applyEntry("hasChildren", b)

    @ScriptInterface
    fun checkable(b: Boolean): UiSelector = applyEntry("checkable", b)

    @ScriptInterface
    fun checkable(): UiSelector = applyEntry("checkable")

    @ScriptInterface
    fun checked(b: Boolean): UiSelector = applyEntry("checked", b)

    @ScriptInterface
    fun checked(): UiSelector = applyEntry("checked")

    @ScriptInterface
    fun focusable(b: Boolean): UiSelector = applyEntry("focusable", b)

    @ScriptInterface
    fun focusable(): UiSelector = applyEntry("focusable")

    @ScriptInterface
    fun focused(b: Boolean): UiSelector = applyEntry("focused", b)

    @ScriptInterface
    fun focused(): UiSelector = applyEntry("focused")

    @ScriptInterface
    fun visibleToUser(b: Boolean): UiSelector = applyEntry("visibleToUser", b)

    @ScriptInterface
    fun visibleToUser(): UiSelector = applyEntry("visibleToUser")

    @ScriptInterface
    fun accessibilityFocused(b: Boolean): UiSelector = applyEntry("accessibilityFocused", b)

    @ScriptInterface
    fun accessibilityFocused(): UiSelector = applyEntry("accessibilityFocused")

    @ScriptInterface
    fun selected(b: Boolean): UiSelector = applyEntry("selected", b)

    @ScriptInterface
    fun selected(): UiSelector = applyEntry("selected")

    @ScriptInterface
    fun clickable(b: Boolean): UiSelector = applyEntry("clickable", b)

    @ScriptInterface
    fun clickable(): UiSelector = applyEntry("clickable")

    @ScriptInterface
    fun longClickable(b: Boolean): UiSelector = applyEntry("longClickable", b)

    @ScriptInterface
    fun longClickable(): UiSelector = applyEntry("longClickable")

    @ScriptInterface
    fun enabled(b: Boolean): UiSelector = applyEntry("enabled", b)

    @ScriptInterface
    fun enabled(): UiSelector = applyEntry("enabled")

    @ScriptInterface
    fun password(b: Boolean): UiSelector = applyEntry("password", b)

    @ScriptInterface
    fun password(): UiSelector = applyEntry("password")

    @ScriptInterface
    fun scrollable(b: Boolean): UiSelector = applyEntry("scrollable", b)

    @ScriptInterface
    fun scrollable(): UiSelector = applyEntry("scrollable")

    @ScriptInterface
    fun editable(b: Boolean): UiSelector = applyEntry("editable", b)

    @ScriptInterface
    fun editable(): UiSelector = applyEntry("editable")

    @ScriptInterface
    fun contentInvalid(b: Boolean): UiSelector = applyEntry("contentInvalid", b)

    @ScriptInterface
    fun contentInvalid(): UiSelector = applyEntry("contentInvalid")

    @ScriptInterface
    fun contextClickable(b: Boolean): UiSelector = applyEntry("contextClickable", b)

    @ScriptInterface
    fun contextClickable(): UiSelector = applyEntry("contextClickable")

    @ScriptInterface
    fun multiLine(b: Boolean): UiSelector = applyEntry("multiLine", b)

    @ScriptInterface
    fun multiLine(): UiSelector = applyEntry("multiLine")

    @ScriptInterface
    fun dismissable(b: Boolean): UiSelector = applyEntry("dismissable", b)

    @ScriptInterface
    fun dismissable(): UiSelector = applyEntry("dismissable")

    @ScriptInterface
    fun depth(d: Int): UiSelector = applyEntry("depth", d)

    @ScriptInterface
    fun row(d: Int): UiSelector = applyEntry("row", d)

    @ScriptInterface
    fun rowCount(d: Int): UiSelector = applyEntry("rowCount", d)

    @ScriptInterface
    fun rowSpan(d: Int): UiSelector = applyEntry("rowSpan", d)

    @ScriptInterface
    fun column(d: Int): UiSelector = applyEntry("column", d)

    @ScriptInterface
    fun columnCount(d: Int): UiSelector = applyEntry("columnCount", d)

    @ScriptInterface
    fun columnSpan(d: Int): UiSelector = applyEntry("columnSpan", d)

    @ScriptInterface
    fun drawingOrder(order: Int): UiSelector = applyEntry("drawingOrder", order)

    @ScriptInterface
    fun indexInParent(index: Int): UiSelector = applyEntry("indexInParent", index)

    @ScriptInterface
    fun childCount(count: Int): UiSelector = applyEntry("childCount", count)

    @ScriptInterface
    fun minChildCount(min: Int): UiSelector = applyEntry("minChildCount", min)

    @ScriptInterface
    fun maxChildCount(max: Int): UiSelector = applyEntry("maxChildCount", max)

    @ScriptInterface
    fun minDepth(min: Int): UiSelector = applyEntry("minDepth", min)

    @ScriptInterface
    fun maxDepth(max: Int): UiSelector = applyEntry("maxDepth", max)

    @ScriptInterface
    fun minIndexInParent(min: Int): UiSelector = applyEntry("minIndexInParent", min)

    @ScriptInterface
    fun maxIndexInParent(max: Int): UiSelector = applyEntry("maxIndexInParent", max)

}
