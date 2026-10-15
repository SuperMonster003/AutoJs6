package org.autojs.plugin.tool.api

/**
 * Naming rules of contract version 1 (MCP roadmap D33): a consumer sees `<shortName>_<name>`. A short name has no
 * underscore, so the first underscore of a full name always separates the two parts; both use lower-case ASCII so the
 * names survive every MCP client.
 *
 * zh-CN: 契约版本 1 的命名规则: 消费方看到 `<shortName>_<name>`. 短名不含下划线, 因此全名的第一个下划线总是分隔两部分; 二者都用小写
 * ASCII, 以兼容所有 MCP 客户端.
 */
object PluginToolNames {

    /** Lower-case letter first, then letters and digits; 2 to 16 characters. */
    val SHORT_NAME_PATTERN = Regex("^[a-z][a-z0-9]{1,15}$")

    /** Lower-case letter first, then letters, digits and single underscores between them. */
    val TOOL_NAME_PATTERN = Regex("^[a-z][a-z0-9]*(_[a-z0-9]+)*$")

    /**
     * Short names a provider may not take: the MCP Server tool groups and their prefixes, the plugin families of MCP
     * roadmap P11, and names that would read as the host itself. The host refuses such a catalog as a whole.
     */
    val RESERVED_SHORT_NAMES: Set<String> = setOf(
        "script", "ui", "screen", "files", "device", "shell", "console", "editor", "app", "clipboard", "toast",
        "ocr", "barcode", "opencv", "pinyin", "pinyin4j", "mediainfo", "docs",
        "agent", "autojs", "autojs6", "host", "plugin", "plugins", "tool", "tools", "mcp",
    )

    fun isValidShortName(shortName: String): Boolean = SHORT_NAME_PATTERN.matches(shortName)

    fun isReservedShortName(shortName: String): Boolean = shortName in RESERVED_SHORT_NAMES

    fun isValidToolName(name: String): Boolean = TOOL_NAME_PATTERN.matches(name)

    /** The name consumers see. */
    fun fullName(shortName: String, name: String): String = "${shortName}_$name"

    /** True when [fullName] still fits after MCP clients put [PluginToolLimits.MCP_TOOL_NAME_PREFIX] before it. */
    fun fitsMcpName(fullName: String): Boolean = fullName.length <= PluginToolLimits.MAX_FULL_NAME_CHARS

    /** The short name and the tool name of [fullName], or null when it is not `<shortName>_<name>` with valid parts. */
    fun split(fullName: String): Pair<String, String>? {
        val separator = fullName.indexOf('_')
        if (separator <= 0) return null
        val shortName = fullName.substring(0, separator)
        val name = fullName.substring(separator + 1)
        return if (isValidShortName(shortName) && isValidToolName(name)) shortName to name else null
    }
}
