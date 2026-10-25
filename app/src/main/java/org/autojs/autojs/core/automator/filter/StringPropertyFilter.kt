package org.autojs.autojs.core.automator.filter

import org.autojs.autojs.core.automator.UiNode

/**
 * A [StringMatcher] applied to a [StringProperty]: the one filter behind `text` / `desc` / `content` /
 * `id` / `idHex` / `className` / `packageName` and their `StartsWith` / `EndsWith` / `Contains` /
 * `Matches` / `Match` variants. [IdFilter] and [ClassNameFilter] add the argument rules of those two
 * properties on top.
 * zh-CN: 作用于 [StringProperty] 的 [StringMatcher]: `text` / `desc` / `content` / `id` / `idHex` / `className` / `packageName`
 * 及其 `StartsWith` / `EndsWith` / `Contains` / `Matches` / `Match` 变体背后唯一的过滤器. [IdFilter] 与 [ClassNameFilter]
 * 在其上补充这两个属性的参数规则.
 *
 * Created by SuperMonster003 on Sep 6, 2026.
 */
class StringPropertyFilter(val property: StringProperty, val matcher: StringMatcher) : Filter {

    override fun filter(node: UiNode<*>) = property.test(node, matcher)

    override fun toString() = matcher.describe(property)

    /**
     * This filter comparing values normalized by [options] as they apply to [property] (P3.3, [NormalizeOptions.of]); its
     * string form stays the same. zh-CN: 以作用于 [property] 的 [options] 规范化后再比较的本过滤器 (P3.3); 字符串形式不变.
     */
    fun normalized(options: NormalizeOptions): StringPropertyFilter = options.of(property).let { if (it.isOff) this else StringPropertyFilter(property, matcher.normalized(it)) }

    // P2.2: the class name compared exactly is a field read; idHex looks the id up in the resources of the package.
    // zh-CN: P2.2: 精确比较的类名只是读字段; idHex 要在包的资源中查找 id.
    override val cost: Int
        get() = when {
            property == StringProperty.ID_HEX -> Filter.COST_RESOURCE
            matcher.isPattern -> Filter.COST_REGEX
            property == StringProperty.CLASS_NAME && matcher.exact != null -> Filter.COST_PROPERTY
            else -> Filter.COST_STRING
        }

}
