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

}
