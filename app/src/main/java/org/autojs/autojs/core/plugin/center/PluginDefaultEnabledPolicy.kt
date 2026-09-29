package org.autojs.autojs.core.plugin.center

import org.autojs.plugin.mcp.server.api.McpServerIds
import org.autojs.plugin.three.stove.agent.api.ThreeStoveAgentIds
import org.autojs.plugin.yolo.api.YoloContract

object PluginDefaultEnabledPolicy {

    /**
     * Whether a plugin without a saved enable preference counts as enabled. Exact-component
     * plugins stay disabled until an explicit record exists, except the officially signed Agent.
     * Its installation opts into on-demand use even when Plugin Center missed the install broadcast.
     * A saved user choice always takes precedence over this default.
     */
    fun isEnabledByDefault(engine: String?, authorizedState: PluginAuthorizedState? = null): Boolean {
        val normalizedEngine = engine?.trim()
        if (normalizedEngine.equals(ThreeStoveAgentIds.ENGINE, ignoreCase = true)) {
            return authorizedState == PluginAuthorizedState.OFFICIAL
        }
        return !normalizedEngine.equals(YoloContract.ENGINE_ID, ignoreCase = true) &&
                !normalizedEngine.equals(McpServerIds.ENGINE, ignoreCase = true)
    }

    /**
     * Whether Plugin Center may write an explicit enable record right after it observes a fresh
     * (non-replacing) installation. Legacy engines keep their historical behavior: any authorized
     * signer qualifies. Default-off engines qualify only when the package is signed with the host's
     * own official certificate; trusted third-party or user-granted signers still require an explicit
     * choice. The official Agent already uses the shared default above and does not need this write.
     * zh-CN: 插件中心观察到全新 (非替换) 安装后是否可直接写入显式启用记录. 旧引擎沿用历史行为:
     * 任一已授权签名即可. 默认关闭的引擎仅在包由宿主官方证书签名时自动启用; 第三方信任或用户
     * 授权签名仍需显式选择. 官方 Agent 已使用上述共享默认值, 无需再次写入启用记录.
     */
    fun shouldAutoEnableAfterInstall(engine: String?, authorizedState: PluginAuthorizedState): Boolean = when (authorizedState) {
        PluginAuthorizedState.OFFICIAL -> true
        PluginAuthorizedState.TRUSTED,
        PluginAuthorizedState.USER_GRANTED,
            -> isEnabledByDefault(engine)
        PluginAuthorizedState.REQUIRED -> false
    }
}
