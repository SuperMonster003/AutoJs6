package org.autojs.plugin.tool.api

/**
 * Hard ceilings of contract version 1 (docs/dev/plugin-tool-protocol-v1.md). The host refuses a catalog or a call beyond
 * them instead of truncating it. The catalog budget keeps one `getCatalog` answer far below the Binder transaction
 * buffer (about 1 MiB per process); binary data never travels inline but through descriptors.
 *
 * zh-CN: 契约版本 1 的硬上限. 宿主拒绝超限的目录或调用, 不做截断. 目录预算使单次 `getCatalog` 应答远低于 Binder 事务缓冲区
 * (每进程约 1 MiB); 二进制数据只经描述符传递.
 */
object PluginToolLimits {

    /** UTF-8 bytes of one catalog document (MCP roadmap P12, second feasibility review). */
    const val MAX_CATALOG_BYTES = 256 * 1024

    /** Tools of one catalog. */
    const val MAX_TOOLS_PER_PLUGIN = 64

    /** Characters of a short name (PluginToolNames.SHORT_NAME_PATTERN). */
    const val MIN_SHORT_NAME_CHARS = 2
    const val MAX_SHORT_NAME_CHARS = 16

    /** The prefix MCP clients put before a server's tool names, for the server registered as `autojs6`. */
    const val MCP_TOOL_NAME_PREFIX = "mcp__autojs6__"

    /** Characters of a tool name as MCP clients see it, prefix included. */
    const val MAX_MCP_TOOL_NAME_CHARS = 64

    /** Characters of `<shortName>_<name>`, so that the MCP name fits (MCP roadmap D33). */
    const val MAX_FULL_NAME_CHARS = MAX_MCP_TOOL_NAME_CHARS - 14

    const val MAX_TITLE_CHARS = 128
    const val MAX_DESCRIPTION_CHARS = 4096

    /** Nesting levels of objects and arrays in one schema. */
    const val MAX_SCHEMA_DEPTH = 8

    /** Properties of one schema object. */
    const val MAX_SCHEMA_PROPERTIES = 64

    /** UTF-8 bytes of PluginToolInvokeKeys.ARGUMENTS_JSON. */
    const val MAX_ARGUMENTS_BYTES = 64 * 1024

    /** UTF-8 bytes of PluginToolResultKeys.RESULT_JSON and TEXT together. */
    const val MAX_RESULT_BYTES = 512 * 1024

    /**
     * UTF-16 units (`String.length`) of PluginToolResultKeys.RESULT_JSON and TEXT together, besides [MAX_RESULT_BYTES]. The
     * result reaches the host in the oneway IPluginToolCallback.onResult, whose Parcel holds strings as UTF-16: two bytes a
     * unit, about 384 KiB at the ceiling. A oneway transaction gets at most half of the receiver's Binder buffer (about
     * 508 KiB), shared with the other oneway calls into the host (MCP roadmap D65).
     */
    const val MAX_RESULT_CHARS = 192 * 1024

    /** Bytes behind one image input or one output descriptor (the host's descriptor ceiling). */
    const val MAX_DESCRIPTOR_BYTES = 8 * 1024 * 1024

    /**
     * Bytes of one file the host opens read-only for a file or directory parameter. The provider reads the file through
     * the descriptor without a copy, so the ceiling is higher than for images and outputs (MCP roadmap D49).
     */
    const val MAX_FILE_INPUT_BYTES = 512L * 1024 * 1024

    /** Image, file or directory parameters of one tool, and binary outputs of one tool. */
    const val MAX_INPUTS = 8
    const val MAX_OUTPUTS = 8

    /** Files the host opens for one directory parameter. */
    const val MAX_DIRECTORY_FILES = 16

    /** Input descriptors of one call, directory files included. */
    const val MAX_INPUT_DESCRIPTORS = 32

    /** Files one directory output may hold (MCP roadmap D53). */
    const val MAX_OUTPUT_TREE_FILES = 100_000

    /** UTF-8 bytes of one relative path of a directory output, and of one of its segments. */
    const val MAX_OUTPUT_TREE_PATH_BYTES = 4096
    const val MAX_OUTPUT_TREE_SEGMENT_BYTES = 255

    const val DEFAULT_TIMEOUT_MS = 30_000L
    const val MAX_TIMEOUT_MS = 300_000L

    const val DEFAULT_MAX_CONCURRENCY = 1
    const val MAX_CONCURRENCY = 4

    /** Characters of an error message the host keeps. */
    const val MAX_ERROR_MESSAGE_CHARS = 4096
}
