package org.autojs.plugin.tool.api

/**
 * The plugin tool contract (docs/dev/plugin-tool-protocol-v1.md, MCP Server roadmap P12 D29 / D30): a plugin exports
 * one service for [SERVICE_ACTION] whose binder is an `IPluginToolProvider`, and the host lists its tools and forwards
 * calls from MCP, Agent and scripts. The host writes its contract version into every request; a plugin answers the
 * versions it supports and reports [PluginToolErrorCodes.UNSUPPORTED_CONTRACT_VERSION] otherwise. Later versions may
 * only append keys, values and AIDL transactions.
 *
 * zh-CN: 插件工具契约: 插件导出一个响应 [SERVICE_ACTION] 的服务, 其 Binder 为 `IPluginToolProvider`, 宿主列出其工具并转发 MCP, Agent
 * 与脚本的调用. 宿主在每个请求中写入自身契约版本; 插件应答其支持的版本, 否则返回 [PluginToolErrorCodes.UNSUPPORTED_CONTRACT_VERSION].
 * 后续版本只能追加键, 取值与 AIDL 事务.
 */
object PluginToolContract {

    /** Intent action of the provider service; the service is protected by `org.autojs.permission.PLUGIN` like every plugin entry. */
    const val SERVICE_ACTION = "org.autojs.plugin.TOOL_PROVIDER"

    const val CONTRACT_VERSION = 1
    const val MIN_CONTRACT_VERSION = 1
    const val MAX_CONTRACT_VERSION = 1

    @JvmStatic
    fun supportsContractVersion(version: Int): Boolean = version in MIN_CONTRACT_VERSION..MAX_CONTRACT_VERSION
}

/** Keys of `PluginInfo.capabilities` a provider declares. */
object PluginToolCapabilityKeys {

    /** Int: the highest contract version the provider speaks. */
    const val CONTRACT_VERSION = "pluginToolContractVersion"
}
