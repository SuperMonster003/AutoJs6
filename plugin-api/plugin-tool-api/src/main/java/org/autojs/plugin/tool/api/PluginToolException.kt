package org.autojs.plugin.tool.api

/**
 * A failure with one of PluginToolErrorCodes, for PluginToolProviderService (MCP roadmap D59): thrown from `onCall` or
 * `createCatalogJson`, it is reported with [code] and [message] (English, for people and models).
 *
 * zh-CN: 带 PluginToolErrorCodes 错误码的失败; 从 `onCall` 或 `createCatalogJson` 抛出时以 [code] 与 [message] 报告.
 */
class PluginToolException @JvmOverloads constructor(
    val code: String,
    override val message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
