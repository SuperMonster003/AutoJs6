package org.autojs.autojs.core.accessibility.window

/**
 * A window filter described by properties (roadmap P5.3): the object form of
 * `auto.setWindowFilter(...)` and the argument of `auto.findWindows(...)` / `auto.findWindowRoots(...)`.
 * Every given rule must hold. Android-free: the window is opaque and its facts come from a
 * [Facts] accessor (`A11yWindowFacts` in production), so the rules are unit tested on the JVM.
 * zh-CN: 以属性描述的窗口过滤器 (Roadmap P5.3): `auto.setWindowFilter(...)` 的对象形式, 以及 `auto.findWindows(...)` /
 * `auto.findWindowRoots(...)` 的参数. 给定的规则须全部成立. 不依赖 Android: 窗口为不透明类型, 其事实来自 [Facts] 访问器
 * (生产环境为 `A11yWindowFacts`), 规则因此在 JVM 上单元测试.
 *
 * Created by SuperMonster003 on Sep 6, 2026.
 */
class WindowMatch(
    /** Accepted window types (`AccessibilityWindowInfo.TYPE_*`), any when `null`. zh-CN: 接受的窗口类型 (`AccessibilityWindowInfo.TYPE_*`), `null` 为任意. */
    val types: Set<Int>? = null,
    val packageName: TextRule? = null,
    val title: TextRule? = null,
    val displayId: Int? = null,
    val active: Boolean? = null,
    val focused: Boolean? = null,
    val id: Int? = null,
    val layer: Int? = null,
) {

    /** The facts of a window the rules are checked against. zh-CN: 规则据以检查的窗口事实. */
    interface Facts<W> {
        fun type(window: W): Int
        fun packageName(window: W): String?
        fun title(window: W): String?
        fun displayId(window: W): Int
        fun isActive(window: W): Boolean
        fun isFocused(window: W): Boolean
        fun id(window: W): Int
        fun layer(window: W): Int
    }

    /** A rule on a text: the whole string, or a regular expression search. zh-CN: 对文本的规则: 整个字符串, 或正则搜索. */
    class TextRule(val detail: String, val test: (String?) -> Boolean) {

        companion object {

            fun whole(expected: String) = TextRule("\"$expected\"") { it == expected }

            fun regex(regex: Regex, literal: String) = TextRule(literal) { it != null && regex.containsMatchIn(it) }

        }

    }

    /** Whether no rule is given. zh-CN: 是否没有任何规则. */
    val isEmpty: Boolean
        get() = types == null && packageName == null && title == null && displayId == null && active == null && focused == null && id == null && layer == null

    /** Whether [window] passes every rule; the costlier facts (package name, title) are read only when a rule needs them. zh-CN: [window] 是否通过全部规则; 较贵的事实 (包名, 标题) 只在有规则需要时读取. */
    fun <W> matches(window: W, facts: Facts<W>): Boolean {
        if (types != null && facts.type(window) !in types) return false
        if (displayId != null && facts.displayId(window) != displayId) return false
        if (active != null && facts.isActive(window) != active) return false
        if (focused != null && facts.isFocused(window) != focused) return false
        if (id != null && facts.id(window) != id) return false
        if (layer != null && facts.layer(window) != layer) return false
        if (packageName != null && !packageName.test(facts.packageName(window))) return false
        if (title != null && !title.test(facts.title(window))) return false
        return true
    }

    /** `{ type: application, packageName: "x" }`. zh-CN: `{ type: application, packageName: "x" }`. */
    override fun toString(): String {
        val parts = ArrayList<String>()
        types?.let { set -> parts += "type: ${set.joinToString("|") { typeName(it) }}" }
        packageName?.let { parts += "packageName: ${it.detail}" }
        title?.let { parts += "title: ${it.detail}" }
        displayId?.let { parts += "displayId: $it" }
        active?.let { parts += "active: $it" }
        focused?.let { parts += "focused: $it" }
        id?.let { parts += "id: $it" }
        layer?.let { parts += "layer: $it" }
        return parts.joinToString(", ", "{ ", " }")
    }

    companion object {

        /** `AccessibilityWindowInfo.TYPE_*` by name (values fixed by the framework). zh-CN: 按名称的 `AccessibilityWindowInfo.TYPE_*` (值由框架固定). */
        val TYPES: Map<String, Int> = linkedMapOf(
            "application" to 1,
            "inputMethod" to 2,
            "system" to 3,
            "accessibilityOverlay" to 4,
            "splitScreenDivider" to 5,
            "magnificationOverlay" to 6,
        )

        private val CAMEL_BOUNDARY = Regex("([a-z0-9])([A-Z])")

        /** The type of [name] in any spelling (`inputMethod`, `input_method`, `TYPE_INPUT_METHOD`), or `null`. zh-CN: 任一写法 (`inputMethod`, `input_method`, `TYPE_INPUT_METHOD`) 的 [name] 对应的类型, 或 `null`. */
        fun typeOf(name: String): Int? {
            val normalized = name.trim().replace(CAMEL_BOUNDARY, "$1_$2").uppercase().removePrefix("TYPE_").replace("_", "")
            return TYPES.entries.firstOrNull { it.key.uppercase() == normalized }?.value
        }

        fun typeName(type: Int): String = TYPES.entries.firstOrNull { it.value == type }?.key ?: "type$type"

    }

}
