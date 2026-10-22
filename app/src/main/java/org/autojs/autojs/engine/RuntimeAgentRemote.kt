package org.autojs.autojs.engine

import android.content.Context
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import java.io.Closeable

/** Generated applications use the installed AutoJs6 host, which owns the Agent's device brokers. */
internal class RuntimeAgentRemote(private val context: Context) : Closeable {
    private val lifetime = Binder()
    private var closed = false
    private val uri = Uri.parse("content://$AUTHORITY")

    fun hasAccess(): Boolean = runCatching {
        trustedHost() && call("hasAccess", null)?.getBoolean("authorized") == true
    }.getOrDefault(false)

    fun dispatch(request: NodeBridgeRequest, reply: (NodeBridgeResponse) -> Unit) {
        check(!closed)
        require(trustedHost()) { "Install the official AutoJs6 host to use ai.agent" }
        val result = requireNotNull(call("dispatch", request.toJson())) { "AutoJs6 Agent service is unavailable" }
        reply(NodeBridgeResponse.fromJson(requireNotNull(result.getString("response"))))
    }

    fun closeClient(id: String): Boolean = runCatching {
        if (closed) return false
        var result = false
        dispatch(NodeBridgeRequest("close-agent", "aiAgent", "close", org.json.JSONArray().put(id).toString(), 10000)) {
            result = it.ok && it.resultJson == "true"
        }
        result
    }.getOrDefault(false)

    override fun close() {
        if (closed) return
        closed = true
        runCatching { call("dispose", null) }
    }

    private fun call(method: String, json: String?): Bundle? {
        val identity = Binder.clearCallingIdentity()
        return try { context.contentResolver.call(uri, method, null, Bundle().apply {
            putBinder("lifetime", lifetime); json?.let { putString("request", it) }
        }) } finally { Binder.restoreCallingIdentity(identity) }
    }

    private fun trustedHost(): Boolean = org.autojs.autojs.core.plugin.center.PluginTrustManager
        .resolveTrustInfo(context, RuntimeAgentBridge.HOST).isOfficial

    companion object {
        const val AUTHORITY = "org.autojs.autojs6.agent.runtime"
        const val PERMISSION = "org.autojs.permission.AGENT_RUNTIME"
        const val ACCESS_ACTIVITY = "org.autojs.autojs.engine.RuntimeAgentAccessActivity"
    }
}
