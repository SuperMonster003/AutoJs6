package org.autojs.plugin.tool.api

/** Enumerated values of the contract (docs/dev/plugin-tool-protocol-v1.md). */
object PluginToolValues {

    /** Reads only; enabled with the plugin's group (MCP roadmap D34). */
    const val RISK_READONLY = "readonly"

    /** Writes data, such as files in the working directory. */
    const val RISK_WRITE = "write"

    /** May delete or overwrite data the tool did not create. */
    const val RISK_DESTRUCTIVE = "destructive"

    /** Talks to the network. */
    const val RISK_NETWORK = "network"

    val RISKS: Set<String> = setOf(RISK_READONLY, RISK_WRITE, RISK_DESTRUCTIVE, RISK_NETWORK)

    /** Value of PluginToolCatalogJson.SPECIAL_TYPE: an image, given as [IMAGE_SCREEN] or a path. */
    const val SPECIAL_TYPE_IMAGE = "image"

    /** Value of PluginToolCatalogJson.SPECIAL_TYPE: a file, given as a path. */
    const val SPECIAL_TYPE_FILE = "file"

    val SPECIAL_TYPES: Set<String> = setOf(SPECIAL_TYPE_IMAGE, SPECIAL_TYPE_FILE)

    /** The argument value of an image parameter that asks the host for the current screen. */
    const val IMAGE_SCREEN = "screen"

    /** PluginToolInputJson.SOURCE: the host captured the screen. */
    const val INPUT_SOURCE_SCREEN = "screen"

    /** PluginToolInputJson.SOURCE: the host opened a file. */
    const val INPUT_SOURCE_FILE = "file"

    const val CONSUMER_MCP = "mcp"
    const val CONSUMER_AGENT = "agent"
    const val CONSUMER_SCRIPT = "script"

    const val CANCEL_REASON_CALLER = "caller"
    const val CANCEL_REASON_TIMEOUT = "timeout"
    const val CANCEL_REASON_HOST = "host"
}

/** Error codes a provider reports in PluginToolErrorKeys.CODE, PluginToolAcceptKeys.ERROR_CODE and PluginToolCatalogKeys.ERROR_CODE. */
object PluginToolErrorCodes {

    const val UNSUPPORTED_CONTRACT_VERSION = "UNSUPPORTED_CONTRACT_VERSION"

    /** The tool is not in the catalog (anymore). */
    const val UNKNOWN_TOOL = "UNKNOWN_TOOL"

    /** The arguments are valid for the schema but not for the tool, such as an unknown configuration name. */
    const val INVALID_ARGUMENTS = "INVALID_ARGUMENTS"

    /** The tool cannot run yet, such as while a model is missing or loading; the message says what is needed. */
    const val NOT_READY = "NOT_READY"

    /** The tool's concurrency limit is reached; the caller may retry. */
    const val BUSY = "BUSY"

    /** An input or the result exceeds a limit of PluginToolLimits or of the plugin. */
    const val LIMIT_EXCEEDED = "LIMIT_EXCEEDED"

    /** An input descriptor could not be read or decoded. */
    const val INPUT_UNREADABLE = "INPUT_UNREADABLE"

    /** The call stopped after `cancel`. */
    const val CANCELLED = "CANCELLED"

    /** The plugin gave up after its own time limit. */
    const val TIMEOUT = "TIMEOUT"

    /** Any other failure; the message says what happened. */
    const val FAILED = "FAILED"
}
