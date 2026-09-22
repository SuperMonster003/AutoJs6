package org.autojs.autojs.core.plugin.mcp

import android.content.Context
import android.os.Bundle
import org.autojs.autojs.core.plugin.hostbroker.HostCapabilityBrokerCore
import org.autojs.autojs.core.plugin.hostbroker.HostCapabilityBrokerStub
import org.autojs.plugin.mcp.server.api.IMcpHostCapabilityBroker
import org.autojs.plugin.mcp.server.api.IMcpHostCapabilityCallback
import org.autojs.plugin.mcp.server.api.McpServerContract

/** MCP v1 Binder adapter. Both MCP versions share the same grant, resources and lifetime. */
class McpHostCapabilityBroker(
    context: Context,
    val id: String,
    val grant: McpCapabilityGrant,
    sourceName: String = DEFAULT_SOURCE_NAME,
) : IMcpHostCapabilityBroker.Stub() {

    internal val core = HostCapabilityBrokerCore(
        context, id, grant, sourceName, engineName = ENGINE_NAME, label = "MCP", grantSource = "mcp-grant",
    )

    internal val sharedBroker by lazy { HostCapabilityBrokerStub(core) }

    override fun getBrokerInfo(): Bundle = core.getBrokerInfo().apply {
        // A v1 plugin must continue seeing the v1 envelope, even after the host supports v2.
        putInt(McpServerContract.KEY_CONTRACT_VERSION, McpServerContract.MIN_SUPPORTED_CONTRACT_VERSION)
    }

    override fun dispatch(request: Bundle?, callback: IMcpHostCapabilityCallback?) {
        core.dispatch(request, callback?.let { target -> { response -> target.onResponse(response) } })
    }

    override fun destroy(reason: Bundle?) = core.destroy(reason)

    val isDestroyed: Boolean get() = core.isDestroyed

    companion object {
        const val ENGINE_NAME = "mcp"
        const val DEFAULT_SOURCE_NAME = "mcp-server"
    }
}
