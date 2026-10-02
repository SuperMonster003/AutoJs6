package org.autojs.autojs.core.accessibility.inspector

import org.autojs.autojs.core.automator.UiNode
import org.autojs.autojs.core.automator.filter.Filter

/**
 * The class-name presets of the filter profile (roadmap D18): each preset is a set of suffix /
 * substring rules on the simplified class name (package stripped) plus explicit exclusions, so that
 * third-party subclasses (`MaterialButton`, `AppCompatImageView`) are covered. [regex] is the
 * equivalent whole-match pattern used by [ClassPresetFilter.toString] for the selector export.
 *
 * zh-CN: 筛选配置的类名预设 (Roadmap D18): 每个预设是一组作用于简化类名 (去掉包名) 的后缀 / 子串规则加显式排除, 因此能覆盖第三方
 * 子类 (`MaterialButton`, `AppCompatImageView`). [regex] 是等价的整体匹配模式, 供 [ClassPresetFilter.toString] 导出选择器.
 *
 * Created by SuperMonster003 on Oct 2, 2026.
 */
enum class ClassPreset(
    private val includes: List<Rule>,
    private val excludes: List<Rule> = emptyList(),
) {

    /** `*Button`, except the compound buttons that belong to [TOGGLE]. zh-CN: `*Button`, 但属于 [TOGGLE] 的复合按钮除外. */
    BUTTON(listOf(Rule.suffix("Button")), listOf(Rule.suffix("RadioButton"), Rule.suffix("ToggleButton"), Rule.suffix("CompoundButton"))),

    /** `*TextView`, except the auto-complete inputs that belong to [INPUT]. zh-CN: `*TextView`, 但属于 [INPUT] 的自动补全输入框除外. */
    TEXT(listOf(Rule.suffix("TextView")), listOf(Rule.suffix("AutoCompleteTextView"))),

    INPUT(listOf(Rule.suffix("EditText"), Rule.suffix("AutoCompleteTextView"))),

    IMAGE(listOf(Rule.suffix("ImageView"))),

    TOGGLE(listOf(Rule.suffix("CheckBox"), Rule.contains("Switch"), Rule.suffix("RadioButton"), Rule.suffix("ToggleButton"), Rule.suffix("CompoundButton"))),

    SCROLL(listOf(Rule.suffix("RecyclerView"), Rule.suffix("ListView"), Rule.suffix("ScrollView"), Rule.contains("ViewPager"), Rule.suffix("GridView"))),

    CONTAINER(listOf(Rule.suffix("Layout"), Rule.suffix("ViewGroup"), Rule.contains("Container")));

    /** A suffix or substring test on the simplified class name. zh-CN: 对简化类名的后缀或子串测试. */
    class Rule private constructor(private val part: String, private val anchored: Boolean) {

        fun matches(simpleName: String): Boolean = if (anchored) simpleName.endsWith(part) else simpleName.contains(part)

        /** The regex fragment matching whole class names, simple or qualified. zh-CN: 匹配完整类名 (简化或带包名) 的正则片段. */
        fun regex(): String = if (anchored) ".*$part" else ".*$part.*"

        companion object {
            fun suffix(part: String) = Rule(part, anchored = true)
            fun contains(part: String) = Rule(part, anchored = false)
        }
    }

    /** Whether [className] (qualified or simple, null = no) belongs to this preset. zh-CN: [className] (带包名或简化, null 为否) 是否属于本预设. */
    fun matches(className: String?): Boolean {
        val simple = simplify(className ?: return false)
        return includes.any { it.matches(simple) } && excludes.none { it.matches(simple) }
    }

    /** Whole-match regex over the class name, equivalent to [matches]. zh-CN: 作用于类名的整体匹配正则, 与 [matches] 等价. */
    val regex: String
        get() {
            val include = includes.joinToString("|") { it.regex() }
            if (excludes.isEmpty()) return "(?:$include)"
            val exclude = excludes.joinToString("|") { it.regex() }
            return "(?!(?:$exclude)$)(?:$include)"
        }

    companion object {

        /** The part after the last dot. zh-CN: 最后一个点之后的部分. */
        fun simplify(className: String): String = className.substringAfterLast('.')

        /** Presets matching [className], for the "class names in this tree" listing. zh-CN: 匹配 [className] 的预设, 供 "当前树中的类名" 列表使用. */
        fun of(className: String?): Set<ClassPreset> = entries.filterTo(LinkedHashSet()) { it.matches(className) }

    }

}

/**
 * Nodes whose class name belongs to any of [presets]; renders as `classNameMatches("...")` with the
 * combined [ClassPreset.regex] so the export stays a runnable selector.
 * zh-CN: 类名属于 [presets] 之一的节点; 以合并后的 [ClassPreset.regex] 输出为 `classNameMatches("...")`, 使导出仍是可运行的选择器.
 */
class ClassPresetFilter(val presets: Set<ClassPreset>) : Filter {

    override fun filter(node: UiNode<*>): Boolean {
        val className = node.className()
        return presets.any { it.matches(className) }
    }

    /** The regex every preset is OR-ed into. zh-CN: 所有预设 OR 在一起的正则. */
    val regex: String get() = presets.sortedBy { it.ordinal }.joinToString("|") { it.regex }

    override fun toString() = "classNameMatches(\"$regex\")"

}
