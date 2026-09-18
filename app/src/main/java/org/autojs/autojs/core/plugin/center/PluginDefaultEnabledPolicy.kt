package org.autojs.autojs.core.plugin.center

import org.autojs.plugin.mcp.server.api.McpServerIds
import org.autojs.plugin.yolo.api.YoloContract

object PluginDefaultEnabledPolicy {

    fun isEnabledByDefault(engine: String?): Boolean {
        val normalizedEngine = engine?.trim()
        return !normalizedEngine.equals(YoloContract.ENGINE_ID, ignoreCase = true) &&
                !normalizedEngine.equals(McpServerIds.ENGINE, ignoreCase = true)
    }
}
