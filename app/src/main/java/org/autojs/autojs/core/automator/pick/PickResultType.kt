package org.autojs.autojs.core.automator.pick

/**
 * The result type argument of `pickup` / `detect`, classified: the symbolic forms and their
 * aliases (`#` widget, `{}` collection, `[]` widgets, `$` content, `.` point, `@` selector, `?`
 * existence, each with its plural spellings), a method of the node by name (`"text"`,
 * `["child", 0]`), or nothing. This is the single alias table; the dispatch lives in [Detector].
 * Free of Android and Rhino dependencies.
 * zh-CN: `pickup` / `detect` 的结果类型参数的分类: 符号形式及其别名 (`#` 控件, `{}` 集合, `[]` 控件数组, `$` 内容,
 * `.` 坐标点, `@` 选择器, `?` 存在性, 各有复数拼写), 按名称调用节点的方法 (`"text"`, `["child", 0]`), 或无结果.
 * 这是唯一的别名表; 分派在 [Detector] 中. 不依赖 Android 与 Rhino.
 *
 * Created by SuperMonster003 on Sep 6, 2026.
 */
sealed class PickResultType {

    /** The empty string: nothing is produced. zh-CN: 空串: 不产生结果. */
    object Nothing : PickResultType()

    /** `#` / `w` / `widget`: the node. zh-CN: `#` / `w` / `widget`: 节点本身. */
    object Widget : PickResultType()

    /** `{}` / `wc` / `collection` / `list` and `#{}` forms: a `UiObjectCollection`. zh-CN: `{}` / `wc` / `collection` / `list` 及 `#{}` 形式: `UiObjectCollection`. */
    object WidgetCollection : PickResultType()

    /** `[]` / `ws` / `widgets` and `##` / `#[]` forms: a JS array of nodes. zh-CN: `[]` / `ws` / `widgets` 及 `##` / `#[]` 形式: 节点的 JS 数组. */
    object Widgets : PickResultType()

    /** `$` / `txt` / `content`: the content string. zh-CN: `$` / `txt` / `content`: 内容字符串. */
    object Content : PickResultType()

    /** `contents` and `$$` / `$[]` forms: a JS array of content strings. zh-CN: `contents` 及 `$$` / `$[]` 形式: 内容字符串的 JS 数组. */
    object Contents : PickResultType()

    /** `.` / `pt` / `point`: the centre point. zh-CN: `.` / `pt` / `point`: 中心点. */
    object Point : PickResultType()

    /** `points` / `pts` and `..` / `.[]` forms: a JS array of centre points. zh-CN: `points` / `pts` 及 `..` / `.[]` 形式: 中心点的 JS 数组. */
    object Points : PickResultType()

    /** `@` / `selector` / `sel`: the selector itself. zh-CN: `@` / `selector` / `sel`: 选择器本身. */
    object Selector : PickResultType()

    /** `?` / `exists`: whether anything was found. zh-CN: `?` / `exists`: 是否找到. */
    object Existence : PickResultType()

    /**
     * A method of the node called by name with [arguments]: `"text"`, `["child", 0]`. A plain
     * string that is no symbol is a call without arguments.
     * zh-CN: 以 [arguments] 按名称调用节点的方法: `"text"`, `["child", 0]`. 不是符号的普通字符串即无参调用.
     */
    data class Invocation(val name: String, val arguments: List<Any?>) : PickResultType()

    /** Anything else (a number, a list whose head is not a string): yields `null`. zh-CN: 其余值 (数字, 首项不是字符串的列表): 结果为 `null`. */
    data class Unsupported(val value: Any?) : PickResultType()

    companion object {

        /** The default result type of `pickup` / `detect`. zh-CN: `pickup` / `detect` 的默认结果类型. */
        const val WIDGET = "widget"

        // Built on the first lookup, not by the class initializer: when the first class of the hierarchy a process
        // touches is one of the objects (SelectorForms of the new layer reads WidgetCollection), the JVM initializes
        // this class while that object is still being initialized, so the object would be null here and its
        // spellings would resolve to method names for the life of the process (docs/selector/ROADMAP.md appendix C Q9).
        // zh-CN: 在首次查表时建立, 而不是在类初始化时: 进程首先触达的若是层级中的某个对象 (新层 SelectorForms 读取 WidgetCollection),
        // JVM 会在该对象初始化途中初始化本类, 此处该对象尚为 null, 其拼写在整个进程内都会解析为方法名 (docs/selector/ROADMAP.md 附录 C Q9).
        private val groups: Map<PickResultType, List<String>> by lazy {
            linkedMapOf(
                Widget to listOf("#", "w", WIDGET),
                WidgetCollection to listOf("{}", "wc", "collection", "list") + listAliases("#", "w"),
                Widgets to listOf("[]", "ws", "widgets") + arrayAliases("#", "w"),
                Content to listOf("$", "txt", "content"),
                Contents to listOf("contents") + arrayAliases("$", "txt", "content"),
                Point to listOf(".", "pt", "point"),
                Points to listOf("points", "pts") + arrayAliases(".", "point", "pt"),
                Selector to listOf("@", "selector", "sel"),
                Existence to listOf("?", "exists"),
            )
        }

        private val symbols: Map<String, PickResultType> by lazy {
            groups.entries
                .flatMap { (type, names) -> names.map { it to type } }
                .toMap()
        }

        /** The spellings of a symbolic type, the canonical one first. zh-CN: 符号类型的各种拼写, 规范拼写在前. */
        fun spellings(type: PickResultType): List<String> = groups[type].orEmpty()

        fun of(resultType: Any?): PickResultType = when (resultType) {
            "" -> Nothing
            is String -> symbols[resultType] ?: Invocation(resultType, emptyList())
            is List<*> -> when {
                resultType.isEmpty() -> Widgets
                else -> when (val head = resultType[0]) {
                    is String -> Invocation(head, resultType.drop(1))
                    else -> Unsupported(resultType)
                }
            }
            else -> Unsupported(resultType)
        }

        /**
         * The array form a one-element list falls back to when its head turns out not to be a
         * method: `["#"]` are the widgets, `["$"]` the contents, `["."]` the points.
         * zh-CN: 单元素列表的首项不是方法时回退到的数组形式: `["#"]` 为控件数组, `["$"]` 为内容数组, `["."]` 为坐标点数组.
         */
        fun arrayFormOf(head: Any?): PickResultType? = when (of(head)) {
            Widget -> Widgets
            Content -> Contents
            Point -> Points
            else -> null
        }

        private fun arrayAliases(symbol: String, vararg others: String) = listOf(
            "$symbol$symbol", "$symbol[]", "[$symbol]",
        ) + others.flatMap { listOf("$it[]", "[$it]") }

        private fun listAliases(symbol: String, vararg others: String) = listOf(
            "$symbol{}", "{$symbol}",
        ) + others.flatMap { listOf("$it{}", "{$it}") }

    }

}
