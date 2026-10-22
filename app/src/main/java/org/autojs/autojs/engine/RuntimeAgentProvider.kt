package org.autojs.autojs.engine

import android.content.ContentProvider
import android.content.ContentValues
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Binder
import android.os.Bundle
import android.os.IBinder
import java.util.concurrent.ConcurrentHashMap

/** Only the main AutoJs6 application exposes this consent-gated Agent endpoint. */
class RuntimeAgentProvider : ContentProvider() {
    private data class Session(val identity: RuntimeAgentIdentity, val bridge: RuntimeAgentBridge, val death: IBinder.DeathRecipient)
    override fun onCreate(): Boolean {
        val app = requireNotNull(context).applicationContext
        if (observing.compareAndSet(false, true)) {
            val filter = android.content.IntentFilter().apply {
                addAction(android.content.Intent.ACTION_PACKAGE_REMOVED)
                addAction(android.content.Intent.ACTION_PACKAGE_REPLACED)
                addAction(android.content.Intent.ACTION_PACKAGE_CHANGED)
                addDataScheme("package")
            }
            androidx.core.content.ContextCompat.registerReceiver(app, object : android.content.BroadcastReceiver() {
                override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
                    if (intent?.action == android.content.Intent.ACTION_PACKAGE_REMOVED && intent.getBooleanExtra(android.content.Intent.EXTRA_REPLACING, false)) return
                    intent?.data?.schemeSpecificPart?.let { RuntimeAgentAccessStore(app).reconcile(it) }
                }
            }, filter, androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED)
        }
        return true
    }
    override fun call(method: String, arg: String?, extras: Bundle?): Bundle {
        val context = requireNotNull(context)
        check(context.packageName == RuntimeAgentBridge.HOST)
        require(!org.autojs.autojs.core.plugin.hostbroker.HostCapabilityBrokerCore.closeUnexpectedPayload(extras))
        require(extras == null || extras.keySet().all { it in setOf("lifetime", "request") })
        val caller = requireNotNull(callingPackage)
        val uid = Binder.getCallingUid()
        val store = RuntimeAgentAccessStore(context)
        val identity = requireNotNull(store.identity(caller))
        if (identity.uid != uid || context.packageManager.checkPermission(RuntimeAgentRemote.PERMISSION, caller) != PackageManager.PERMISSION_GRANTED)
            throw SecurityException("Agent caller identity or entry permission is invalid")
        require(arg == null)
        if (method == "hasAccess") return Bundle().apply { putBoolean("authorized", store.granted(identity)) }
        val token = requireNotNull(extras?.getBinder("lifetime"))
        if (method == "dispose") {
            sessions[token]?.takeIf { it.identity == identity }?.let { remove(token, it) }
            return Bundle()
        }
        require(method == "dispatch")
        val json = requireNotNull(extras.getString("request"))
        require(json.toByteArray(Charsets.UTF_8).size <= 128 * 1024)
        val request = NodeBridgeRequest.fromJson(json)
        require(request.module == "aiAgent" && request.method in setOf("open", "start", "poll", "ack", "cancel", "release", "close"))
        if (!store.granted(identity)) return reply(NodeBridgeResponse.failure(request.id, NodeBridgeError(
            code = "ERR_AUTOJS6_AGENT_PERMISSION_DENIED", message = "Call ai.agent.requestAccess() to authorize this application in AutoJs6", module = "aiAgent", method = request.method)))
        var session = sessions[token]
        if (session == null) synchronized(sessions) {
            session = sessions[token]
            if (session == null) {
                if (request.method != "open") return reply(NodeBridgeResponse.failure(request.id, NodeBridgeError(
                    code = "ERR_AUTOJS6_AGENT_SESSION_CLOSED", message = "Agent client is closed", module = "aiAgent", method = request.method)))
                val bridge = RuntimeAgentBridge(context, org.autojs.autojs.util.WorkingDirectoryUtils.path, access = store.scope(identity))
                val death = IBinder.DeathRecipient { sessions.remove(token)?.bridge?.close() }
                token.linkToDeath(death, 0)
                session = Session(identity, bridge, death).also { sessions[token] = it }
                if (!token.isBinderAlive) { remove(token, requireNotNull(session)); throw IllegalStateException("Agent client process exited") }
            }
        }
        val current = requireNotNull(session)
        if (current.identity != identity) throw SecurityException("Agent client belongs to another application")
        val previous = Binder.clearCallingIdentity()
        try {
            var result: NodeBridgeResponse? = null
            current.bridge.dispatch(request) { result = it }
            return reply(requireNotNull(result))
        } finally { Binder.restoreCallingIdentity(previous) }
    }
    private fun reply(response: NodeBridgeResponse) = Bundle().apply { putString("response", response.toJson()) }
    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, args: Array<out String>?, sort: String?): Cursor? = null
    override fun getType(uri: Uri): String? = null
    override fun insert(uri: Uri, values: ContentValues?): Uri? = throw UnsupportedOperationException()
    override fun delete(uri: Uri, selection: String?, args: Array<out String>?): Int = throw UnsupportedOperationException()
    override fun update(uri: Uri, values: ContentValues?, selection: String?, args: Array<out String>?): Int = throw UnsupportedOperationException()
    companion object {
        private val sessions = ConcurrentHashMap<IBinder, Session>()
        private val observing = java.util.concurrent.atomic.AtomicBoolean()
        private fun remove(token: IBinder, session: Session) {
            if (sessions.remove(token, session)) { runCatching { token.unlinkToDeath(session.death, 0) }; session.bridge.close() }
        }
        internal fun revoke(identity: RuntimeAgentIdentity) {
            sessions.forEach { (token, session) -> if (session.identity == identity) remove(token, session) }
        }
    }
}
