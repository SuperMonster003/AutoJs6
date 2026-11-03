package org.autojs.autojs.engine

import android.annotation.SuppressLint
import android.content.Context
import org.autojs.autojs.AutoJs
import org.autojs.autojs.permission.LocalNetworkPermission
import org.autojs.autojs.pluginclient.DevPluginService
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException

/** The model-facing entry to the same server mode used by the drawer and VS Code extension. */
internal object NodeBridgeRemoteServer {
    @SuppressLint("CheckResult")
    fun start(context: Context, request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) {
        if (runCatching { JSONArray(request.argsJson).length() }.getOrNull() != 0) {
            callback(NodeBridgeResponse.failure(request.id,
                NodeBridgeError.invalidRequest("app.startRemoteServer takes no arguments.", request.module, request.method)))
            return
        }
        if (!LocalNetworkPermission.isGranted(context)) {
            callback(NodeBridgeResponse.failure(request.id, NodeBridgeError.permissionDenied(
                "Allow AutoJs6 to access nearby devices / the local network on the phone, then retry. The MCP Server plugin's permission is separate.",
                request.module, request.method)))
            return
        }
        val service = AutoJs.instance.devPluginService
        service.startLocalServer()
            .timeout(request.timeoutMs.coerceIn(1L, 10_000L), TimeUnit.MILLISECONDS)
            .subscribe({ alreadyRunning ->
                callback(NodeBridgeResponse.success(request.id, JSONObject()
                    .put("schema", "autojs6-bridge-remote-server-v1")
                    .put("enabled", true)
                    .put("alreadyRunning", alreadyRunning)
                    .put("port", DevPluginService.Port.AJ_SERVER)
                    .put("connectedClients", service.serverConnectionCount.value ?: 0)
                    .toString()))
            }, { error ->
                callback(NodeBridgeResponse.failure(request.id, when (error) {
                    is TimeoutException -> NodeBridgeError.timeout(request.module, request.method)
                    is SecurityException -> NodeBridgeError.permissionDenied(error.message ?: "Local network access denied.", request.module, request.method)
                    else -> NodeBridgeError.providerFailed("Could not start AutoJs6 remote server on port ${DevPluginService.Port.AJ_SERVER}: ${error.message ?: error.javaClass.simpleName}", request.module, request.method)
                }))
            })
    }
}
