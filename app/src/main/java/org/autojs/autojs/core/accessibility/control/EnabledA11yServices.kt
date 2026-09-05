package org.autojs.autojs.core.accessibility.control

/**
 * Operations on the value of the `enabled_accessibility_services` secure setting: a colon-separated
 * list of `package/class` component names. Blank entries and the literal "null" (what `settings get`
 * prints for an unset value) are ignored, so the helpers accept both the raw setting and shell output.
 *
 * zh-CN: 对安全设置 `enabled_accessibility_services` 值的操作: 以冒号分隔的 `包名/类名` 组件名列表.
 * 空白项与字面量 "null" (`settings get` 对未设置的值的输出) 会被忽略, 因此这些函数同时接受原始设置值与 shell 输出.
 *
 * Created by SuperMonster003 on Sep 5, 2026.
 */
object EnabledA11yServices {

    const val DELIMITER = ":"

    /** The component names listed in [raw], in order, without blanks and "null". zh-CN: [raw] 中的组件名, 保持顺序, 去除空白与 "null". */
    fun parse(raw: String?): List<String> = raw.orEmpty()
        .replace("\n", "")
        .split(DELIMITER)
        .map { it.trim() }
        .filter { it.isNotEmpty() && it != "null" }

    fun contains(raw: String?, serviceName: String): Boolean = parse(raw).any { it == serviceName }

    /**
     * [raw] without every service of [serviceName]'s package (an older service class of the same
     * package is dropped as well).
     * zh-CN: 去除 [serviceName] 所属包名的全部服务后的 [raw] (同包名的旧服务类也会被去除).
     */
    fun detach(raw: String?, serviceName: String): String {
        val packageName = packageOf(serviceName)
        return parse(raw).filterNot { packageOf(it) == packageName }.joinToString(DELIMITER)
    }

    /** [detach] followed by [serviceName] appended at the end. zh-CN: 先 [detach], 再把 [serviceName] 追加到末尾. */
    fun attach(raw: String?, serviceName: String): String {
        return (parse(detach(raw, serviceName)) + serviceName).joinToString(DELIMITER)
    }

    private fun packageOf(componentName: String) = componentName.substringBefore('/')

}
