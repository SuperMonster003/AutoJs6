package org.autojs.autojs.core.plugin.center

import org.autojs.plugin.mcp.server.api.McpServerIds
import org.autojs.plugin.ai.agent.api.AiAgentIds
import org.autojs.plugin.yolo.api.YoloContract

object PluginDefaultEnabledPolicy {

    /**
     * Whether a plugin without a saved enable preference counts as enabled. Exact-component
     * plugins (YOLO, MCP Server, AI Agent) stay disabled until an explicit record exists, in Plugin Center
     * and in every runtime consumer alike.
     * zh-CN: 没有保存启用记录时插件是否视为已启用. 精确组件插件 (YOLO, MCP Server, AI Agent) 在插件中心与
     * 所有运行时消费者中均保持关闭, 直到存在显式记录.
     */
    fun isEnabledByDefault(engine: String?): Boolean {
        val normalizedEngine = engine?.trim()
        return !normalizedEngine.equals(YoloContract.ENGINE_ID, ignoreCase = true) &&
                !normalizedEngine.equals(McpServerIds.ENGINE, ignoreCase = true) &&
                !normalizedEngine.equals(AiAgentIds.ENGINE, ignoreCase = true)
    }

    /**
     * Whether Plugin Center may write an explicit enable record right after it observes a fresh
     * (non-replacing) installation. Legacy engines keep their historical behavior: any authorized
     * signer qualifies. Default-off engines qualify only when the package is signed with the host's
     * own official certificate; trusted third-party or user-granted signers still require an explicit
     * choice, and runtime consumers keep reading the saved record only.
     * zh-CN: 插件中心观察到全新 (非替换) 安装后是否可直接写入显式启用记录. 旧引擎沿用历史行为:
     * 任一已授权签名即可. 默认关闭的引擎仅在包由宿主官方证书签名时自动启用; 第三方信任或用户
     * 授权签名仍需显式选择, 运行时消费者仍只读取已保存的记录.
     */
    fun shouldAutoEnableAfterInstall(engine: String?, authorizedState: PluginAuthorizedState): Boolean = when (authorizedState) {
        PluginAuthorizedState.OFFICIAL -> true
        PluginAuthorizedState.TRUSTED,
        PluginAuthorizedState.USER_GRANTED,
            -> isEnabledByDefault(engine)
        PluginAuthorizedState.REQUIRED -> false
    }
}
