package org.autojs.plugin.tool.api

/**
 * The relative paths an IPluginToolOutputTree accepts (MCP roadmap D53): segments separated by '/', none empty, `.` or
 * `..`, no leading '/', no backslash and no control character, at most PluginToolLimits.MAX_OUTPUT_TREE_PATH_BYTES
 * UTF-8 bytes and MAX_OUTPUT_TREE_SEGMENT_BYTES per segment. The host applies it; plugins may check before they ask.
 *
 * zh-CN: 目录输出接受的相对路径: 以 '/' 分隔, 段不能为空, `.` 或 `..`, 不能以 '/' 开头, 不含反斜杠与控制字符, 并受长度限制.
 */
object PluginToolOutputTreePaths {

    /** Null when [relativePath] is acceptable, otherwise why not. */
    fun problemOf(relativePath: String): String? {
        if (relativePath.isEmpty()) return "the path is empty"
        if (relativePath.startsWith("/")) return "the path is absolute"
        if (relativePath.toByteArray(Charsets.UTF_8).size > PluginToolLimits.MAX_OUTPUT_TREE_PATH_BYTES) {
            return "the path has more than ${PluginToolLimits.MAX_OUTPUT_TREE_PATH_BYTES} bytes"
        }
        if (relativePath.any { it == '\\' || it.code < 0x20 || it.code == 0x7f }) return "the path has a backslash or a control character"
        for (segment in relativePath.removeSuffix("/").split('/')) {
            if (segment.isEmpty() || segment == "." || segment == "..") return "the path has an empty, \".\" or \"..\" segment"
            if (segment.toByteArray(Charsets.UTF_8).size > PluginToolLimits.MAX_OUTPUT_TREE_SEGMENT_BYTES) {
                return "a segment has more than ${PluginToolLimits.MAX_OUTPUT_TREE_SEGMENT_BYTES} bytes"
            }
        }
        return null
    }
}
