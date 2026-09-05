package org.autojs.autojs.core.automator.filter

import org.autojs.autojs.core.accessibility.UiSelector.Companion.ID_IDENTIFIER
import org.autojs.autojs.core.automator.UiNode

/**
 * The string properties a selector can compare, each knowing which value(s) of a [UiNode] to feed a
 * [StringMatcher]: `content` accepts a match on either the description or the text, `ID_ENTRY` is the
 * entry name of a resource id (`login` of `com.example:id/login`), the rest are single values. A
 * `null` value (no description, no id, ...) never matches.
 * zh-CN: 选择器可比较的字符串属性, 各自决定把 [UiNode] 的哪些值交给 [StringMatcher]: `content` 接受描述或文本任一匹配,
 * `ID_ENTRY` 为资源 id 的条目名 (`com.example:id/login` 中的 `login`), 其余为单个值. `null` 值 (无描述, 无 id 等) 一律不匹配.
 *
 * Created by SuperMonster003 on Sep 6, 2026.
 */
enum class StringProperty(private val label: String) {

    TEXT("text") {
        override fun test(node: UiNode<*>, matcher: StringMatcher) = matcher.matches(node.text())
    },
    DESC("desc") {
        override fun test(node: UiNode<*>, matcher: StringMatcher) = matcher.matchesOrNull(node.desc())
    },
    CONTENT("content") {
        override fun test(node: UiNode<*>, matcher: StringMatcher) = matcher.matchesOrNull(node.desc()) || matcher.matches(node.text())
    },
    ID("id") {
        override fun test(node: UiNode<*>, matcher: StringMatcher) = matcher.matchesOrNull(node.id())
    },
    ID_ENTRY("id") {
        override fun test(node: UiNode<*>, matcher: StringMatcher) = matcher.matchesOrNull(node.id()?.substringAfterLast(ID_IDENTIFIER))
    },
    ID_HEX("idHex") {
        override fun test(node: UiNode<*>, matcher: StringMatcher) = matcher.matchesOrNull(node.idHex())
    },
    CLASS_NAME("className") {
        override fun test(node: UiNode<*>, matcher: StringMatcher) = matcher.matchesOrNull(node.className())
    },
    PACKAGE_NAME("packageName") {
        override fun test(node: UiNode<*>, matcher: StringMatcher) = matcher.matchesOrNull(node.packageName())
    };

    abstract fun test(node: UiNode<*>, matcher: StringMatcher): Boolean

    override fun toString() = label

    protected fun StringMatcher.matchesOrNull(value: CharSequence?): Boolean = value != null && matches(value)

}
