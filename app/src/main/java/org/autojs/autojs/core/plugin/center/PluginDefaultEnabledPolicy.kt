package org.autojs.autojs.core.plugin.center

import android.content.Context
import org.autojs.plugin.mcp.server.api.McpServerIds
import org.autojs.plugin.three.stove.agent.api.ThreeStoveAgentIds
import org.autojs.plugin.yolo.api.YoloContract

object PluginDefaultEnabledPolicy {

    // Engines that count as enabled without a saved record only when signed with the host's official certificate.
    // zh-CN: 仅在使用宿主官方证书签名时, 无启用记录也视为启用的引擎.
    private val OFFICIAL_SIGNER_DEFAULT_ENGINES = listOf(ThreeStoveAgentIds.ENGINE, McpServerIds.ENGINE, YoloContract.ENGINE_ID)

    /**
     * Whether a plugin without a saved enable preference counts as enabled. The Agent, MCP Server and YOLO count as
     * enabled only with the host's official signer (MCP Server and YOLO since the maintainer decision of 2026-10-16,
     * before that they stayed disabled until an explicit record existed): a trusted third-party or user-granted signer,
     * or an unknown one, still requires an explicit choice. Other engines are enabled by default. A saved user choice
     * always takes precedence over this default.
     * zh-CN: 没有已保存启用偏好的插件是否视为启用. Agent, MCP Server 与 YOLO 仅在使用宿主官方签名时视为启用 (MCP Server 与 YOLO
     * 自维护者 2026-10-16 的决定起, 此前在有显式记录前一直保持关闭): 第三方信任签名, 用户授权签名或未知签名仍需显式选择.
     * 其他引擎默认启用. 用户保存的选择始终优先于该默认值.
     */
    fun isEnabledByDefault(engine: String?, authorizedState: PluginAuthorizedState? = null): Boolean {
        val normalizedEngine = engine?.trim()
        if (OFFICIAL_SIGNER_DEFAULT_ENGINES.any { normalizedEngine.equals(it, ignoreCase = true) }) {
            return authorizedState == PluginAuthorizedState.OFFICIAL
        }
        return true
    }

    /**
     * [isEnabledByDefault] for an installed [packageName], resolving its signer. zh-CN: 对已安装的包解析签名后判断 [isEnabledByDefault].
     */
    fun isEnabledByDefault(context: Context, engine: String?, packageName: String): Boolean {
        val authorizedState = runCatching { PluginTrustManager.resolveTrustInfo(context, packageName).authorizedState }.getOrNull()
        return isEnabledByDefault(engine, authorizedState)
    }

    /**
     * Whether Plugin Center may write an explicit enable record right after it observes a fresh
     * (non-replacing) installation. Legacy engines keep their historical behavior: any authorized
     * signer qualifies. The official-signer engines above qualify only when the package is signed with
     * the host's own official certificate; trusted third-party or user-granted signers still require an
     * explicit choice.
     * zh-CN: 插件中心观察到全新 (非替换) 安装后是否可直接写入显式启用记录. 旧引擎沿用历史行为: 任一已授权签名即可.
     * 上述仅官方签名默认启用的引擎仅在包由宿主官方证书签名时自动启用; 第三方信任或用户授权签名仍需显式选择.
     */
    fun shouldAutoEnableAfterInstall(engine: String?, authorizedState: PluginAuthorizedState): Boolean = when (authorizedState) {
        PluginAuthorizedState.OFFICIAL -> true
        PluginAuthorizedState.TRUSTED,
        PluginAuthorizedState.USER_GRANTED,
            -> isEnabledByDefault(engine)
        PluginAuthorizedState.REQUIRED -> false
    }
}
