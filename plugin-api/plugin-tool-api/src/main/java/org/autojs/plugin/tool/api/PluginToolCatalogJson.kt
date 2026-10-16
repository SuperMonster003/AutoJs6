package org.autojs.plugin.tool.api

/**
 * Field names of the catalog document a provider returns in PluginToolCatalogKeys.CATALOG_JSON
 * (docs/dev/plugin-tool-protocol-v1.md):
 *
 * ```json
 * {
 *   "shortName": "opencc",
 *   "tools": [{
 *     "name": "convert",
 *     "title": "Convert Chinese text",
 *     "description": "Converts Chinese text between scripts and regional variants ...",
 *     "inputSchema": {"type": "object", "properties": {"text": {"type": "string"}}, "required": ["text"], "additionalProperties": false},
 *     "risk": "readonly",
 *     "timeoutMs": 30000
 *   }]
 * }
 * ```
 *
 * A consumer sees each tool as `<shortName>_<name>` (MCP roadmap D33). Descriptions are English: they go into model
 * context as written. The host validates the whole document when it loads the catalog (MCP roadmap D31): a document
 * that breaks the limits or the naming rules is refused as a whole, a tool whose schema breaks the rules is dropped
 * alone, and the reasons are reported as diagnostics.
 *
 * zh-CN: 提供者在 PluginToolCatalogKeys.CATALOG_JSON 中返回的目录文档字段名. 消费方看到的工具名为 `<shortName>_<name>`. 描述用英文,
 * 原样进入模型上下文. 宿主载入目录时校验整个文档: 违反上限或命名规则的文档整体拒绝, schema 不合规的工具单独剔除, 原因作为诊断报告.
 */
object PluginToolCatalogJson {

    /** String: the plugin's short name (PluginToolNames.SHORT_NAME_PATTERN), unique among providers and not reserved. */
    const val SHORT_NAME = "shortName"

    /**
     * String, optional, reserved by version 1 (MCP roadmap D33): a family that several OFFICIAL plugins may share so that
     * tool names stay stable when the provider changes, such as `ocr`. Version 1 hosts only validate it.
     */
    const val SHARED_FAMILY = "sharedFamily"

    /** Array of tool objects, 1 to PluginToolLimits.MAX_TOOLS_PER_PLUGIN. */
    const val TOOLS = "tools"

    /** String: the tool name (PluginToolNames.TOOL_NAME_PATTERN), unique within the catalog. */
    const val NAME = "name"

    /** String: short human-readable name, at most PluginToolLimits.MAX_TITLE_CHARS. */
    const val TITLE = "title"

    /** String: what the tool does, in English, at most PluginToolLimits.MAX_DESCRIPTION_CHARS. */
    const val DESCRIPTION = "description"

    /**
     * Object: a closed JSON Schema object for the arguments, within the subset of the protocol document (MCP roadmap
     * D31). A top-level property may carry [SPECIAL_TYPE] to ask the host for an image or a file.
     */
    const val INPUT_SCHEMA = "inputSchema"

    /** Object, optional: JSON Schema of the result object. */
    const val OUTPUT_SCHEMA = "outputSchema"

    /** String: PluginToolValues.RISK_*; decides switches and consumer annotations (MCP roadmap D34). */
    const val RISK = "risk"

    /** Number, optional: milliseconds a call may take, default PluginToolLimits.DEFAULT_TIMEOUT_MS, at most PluginToolLimits.MAX_TIMEOUT_MS. */
    const val TIMEOUT_MS = "timeoutMs"

    /** Number, optional: calls of this tool the plugin runs at once, default 1, at most PluginToolLimits.MAX_CONCURRENCY. */
    const val MAX_CONCURRENCY = "maxConcurrency"

    /**
     * Array, optional: outputs the tool can produce, `{name, kind?, mimeType, description?}`, at most
     * PluginToolLimits.MAX_OUTPUTS. A file output (the default kind) needs its mimeType; a directory output does not.
     */
    const val OUTPUTS = "outputs"

    /** Field of an output entry: name, unique within the tool. */
    const val OUTPUT_NAME = "name"

    /** Field of an output entry, optional: PluginToolValues.OUTPUT_KIND_FILE (default) or OUTPUT_KIND_DIRECTORY. */
    const val OUTPUT_KIND = "kind"

    /** Field of an output entry: the MIME type of the bytes, such as `image/png`; ignored for a directory output. */
    const val OUTPUT_MIME_TYPE = "mimeType"

    /** Field of an output entry, optional. */
    const val OUTPUT_DESCRIPTION = "description"

    /**
     * Schema keyword of a top-level string property: PluginToolValues.SPECIAL_TYPE_IMAGE or SPECIAL_TYPE_FILE. The
     * consumer passes `"screen"` or a path (image) or a path (file); the host captures the screen or checks the path
     * scope, opens the file and hands the plugin a descriptor instead of the string.
     */
    const val SPECIAL_TYPE = "x-autojs6-type"

    /**
     * Schema keyword beside SPECIAL_TYPE `directory`, optional: the plain file names (1 to
     * PluginToolLimits.MAX_DIRECTORY_FILES) the tool needs from the directory. The host refuses a directory that lacks
     * one of them and opens only these; without the keyword it opens every regular file directly inside.
     */
    const val DIRECTORY_FILES = "x-autojs6-files"
}

/** Field names of one entry of PluginToolInvokeKeys.INPUTS_JSON, describing the descriptor at the same position. */
object PluginToolInputJson {

    /** String: the argument the descriptor stands for. */
    const val NAME = "name"

    /** String: PluginToolValues.INPUT_SOURCE_*. */
    const val SOURCE = "source"

    const val MIME_TYPE = "mimeType"

    /** String, optional: the file name without its directories. */
    const val DISPLAY_NAME = "displayName"

    /** Number, optional: bytes behind the descriptor. */
    const val BYTE_COUNT = "byteCount"

    /** Numbers, optional: pixel size of an image. */
    const val WIDTH = "width"
    const val HEIGHT = "height"
}

/** Field names of one entry of PluginToolResultKeys.OUTPUTS_JSON, describing the descriptor at the same position. */
object PluginToolOutputJson {

    /** String: a declared output name. */
    const val NAME = "name"

    const val MIME_TYPE = "mimeType"
}
