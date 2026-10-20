package org.autojs.autojs.pluginclient

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.annotation.AnyThread
import io.reactivex.Observable
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.Subject
import org.autojs.autojs.annotation.ScriptInterface
import org.autojs.autojs.core.http.LocalNetworkDiagnostics
import org.autojs.autojs.core.pref.keys.ConnectionKeys
import org.autojs.autojs.permission.LocalNetworkPermission
import org.autojs.autojs6.R
import org.autojs.autojs.pluginclient.JsonSocket.HANDSHAKE_TIMEOUT
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.util.ThreadUtils
import org.autojs.autojs.util.ViewUtils
import java.io.File
import java.io.IOException
import java.net.ServerSocket
import java.net.InetAddress
import java.net.SocketException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Created by Stardust on May 11, 2017.
 * Modified by SuperMonster003 as of Jan 9, 2026.
 * Transformed by SuperMonster003 on Jan 15, 2026.
 */
class DevPluginService(val context: Context) {

    class State @JvmOverloads constructor(private val state: Int, val exception: Throwable? = null) {

        fun isDisconnected() = state == DISCONNECTED

        fun isConnecting() = state == CONNECTING

        fun isConnected() = state == CONNECTED

        companion object {
            const val DISCONNECTED = 0
            const val CONNECTING = 1
            const val CONNECTED = 2
        }

    }

    object Port {
        const val PC_SERVER = 6347
        const val AJ_SERVER = 7347
    }

    val responseHandler = DevPluginResponseHandler(context, File(context.cacheDir, "remote_project"))

    // Handles protocol v2 requests (remote file management etc.).
    // zh-CN: 处理协议 v2 请求 (远程文件管理等).
    val requestHandler = DevPluginRequestHandler(context)

    // Restores the client mode connection after unexpected disconnects (roadmap 3.9).
    // zh-CN: 意外断开后恢复客户端模式连接 (roadmap 3.9).
    val clientReconnector = DevPluginClientReconnector(this)

    /** mDNS advertisement of the local server (roadmap 3.10). zh-CN: 本地服务端的 mDNS 广播 (roadmap 3.10). */
    val discovery = DevPluginDiscovery(context)

    // Publish current server-side connection count.
    // zh-CN: 发布当前服务端连接数量.
    val serverConnectionCount = BehaviorSubject.createDefault(0)

    val clientConnectionIpAddress = BehaviorSubject.createDefault(ConnectionKeys.SERVER_ADDRESS.get())

    @get:ScriptInterface
    @Volatile
    var jsonSocketClient: JsonSocketClient? = null
        private set

    @get:ScriptInterface
    @Volatile
    var jsonSocketServer: JsonSocketServer? = null
        private set

    val isJsonSocketClientConnected
        get() = jsonSocketClient?.isSocketReady == true

    val isServerSocketConnected
        get() = mServerSocket?.isClosed == false

    var isClientSocketNormallyClosed
        get() = JsonSocketClient.isClientSocketNormallyClosed
        set(state) {
            JsonSocketClient.isClientSocketNormallyClosed = state
        }

    var isServerSocketNormallyClosed
        get() = JsonSocketServer.isServerSocketNormallyClosed
        set(state) {
            JsonSocketServer.isServerSocketNormallyClosed = state
        }

    @Volatile
    private var mServerSocket: ServerSocket? = null

    // Track all active server-side client connections.
    // zh-CN: 跟踪所有服务端已建立的客户端连接.
    private val mServerConnections = ConcurrentHashMap.newKeySet<JsonSocketServer>()

    /** Snapshot of the server-side client connections. zh-CN: 服务端客户端连接快照. */
    val serverConnections: List<JsonSocketServer>
        get() = mServerConnections.toList()

    /** Epoch millis when the listening socket was opened, 0 while stopped. zh-CN: 监听 socket 打开的时间戳, 停止时为 0. */
    @Volatile
    var serverStartedAt = 0L
        private set

    // Prevent duplicate server startup.
    // zh-CN: 防止服务端重复启动.
    private val mEnableLocalServerStarted = AtomicBoolean(false)

    fun disconnectJsonSocketClient() {
        // Explicit user action: silence the reconnector before the state change reaches it.
        // zh-CN: 用户显式操作: 在状态变化到达重连器之前先使其静默.
        clientReconnector.onUserDisconnect()
        try {
            jsonSocketClient?.switchOff()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun disconnectJsonSocketServer() {
        try {
            // Tell every client that the person stopped the server (so VSCode does not reconnect), then close.
            // zh-CN: 告知所有客户端用户已停止服务端 (VSCode 因此不会重连), 然后关闭.
            disconnectServerClients()

            // Then close listening socket.
            // zh-CN: 然后关闭监听 socket.
            jsonSocketServer?.switchOff()
            serverStartedAt = 0L

            // Publish disconnected state for UI.
            // zh-CN: 发布断开状态用于 UI 刷新.
            JsonSocketServer.cxnState.onNext(State(State.DISCONNECTED))
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    /**
     * Close every server-side client connection while the listening socket stays up. A v2 peer receives
     * `bye(reason)` first; the default `user` reason tells VSCode not to reconnect automatically.
     * zh-CN: 关闭所有服务端客户端连接, 监听 socket 保持. v2 对端先收到 `bye(reason)`; 默认的 `user` 表示 VSCode 不应自动重连.
     */
    fun disconnectServerClients(reason: String = JsonSocket.BYE_REASON_USER) {
        mServerConnections.toList().forEach { disconnectServerClient(it, reason) }
    }

    /**
     * Close one server-side client connection. `bye(user)` tells VSCode not to reconnect, `bye(restart)` asks
     * it to re-establish the connection right away (server manager "reconnect").
     * zh-CN: 关闭一个服务端客户端连接. `bye(user)` 告知 VSCode 不要重连, `bye(restart)` 要求其立即重新建立连接 (服务端管理器 "重新连接").
     */
    fun disconnectServerClient(conn: JsonSocketServer, reason: String = JsonSocket.BYE_REASON_USER) {
        if (!mServerConnections.remove(conn)) return
        serverConnectionCount.onNext(mServerConnections.size)
        conn.sayBye(reason) { runCatching { conn.close() }.onFailure { it.printStackTrace() } }
    }

    @AnyThread
    fun connectToRemoteServer(
        context: Context,
        host: String,
        ignoreExceptions: Boolean = false,
    ): Observable<JsonSocketClient> {
        // Reuse existing connection to avoid reconnect on Activity recreation.
        // zh-CN: 复用现有连接, 避免 Activity 重建时重复连接.
        if (isJsonSocketClientConnected) {
            return Observable.just(jsonSocketClient)
        }

        try {
            val endpoint = parseRemoteEndpoint(host, Port.PC_SERVER)
            val ip = endpoint.host
            val port = endpoint.port

            // Show connecting subtitle immediately.
            // zh-CN: 立即显示正在连接的 subtitle.
            clientConnectionIpAddress.onNext("$ip ...")

            val client = JsonSocketClient(this, context, ip, port)
            pendingQrToken?.let { (tokenHost, token) ->
                // Attach the token only to the host it was scanned for, then forget it (one-time).
                // zh-CN: 仅将令牌附加到扫码得到的主机, 随后丢弃 (一次性).
                if (tokenHost.equals(ip, ignoreCase = true)) client.helloQrToken = token
                pendingQrToken = null
            }
            return Observable
                .just(client)
                .observeOn(Schedulers.newThread())
                .doOnNext { client ->
                    try {
                        this.jsonSocketClient = client

                        // Mark this instance as active before it can emit state.
                        // zh-CN: 在该实例可能发出状态前, 将其标记为活跃实例.
                        JsonSocketClient.activeInstanceId = client.instanceId

                        if (ThreadUtils.wait({ client.isSocketReady }, HANDSHAKE_TIMEOUT)) {
                            // Do NOT mark as connected here. Wait for hello validation in JsonSocketClient.
                            // zh-CN: 不要在此处标记为已连接, 等待 JsonSocketClient 的 hello 校验通过.
                            client
                                .subscribeMessage()
                                .monitorMessage()
                                .sayHello()
                        } else {
                            if (client.isExtensionVersionCheckFailed || client.hasErrorMessageOnHello) {
                                JsonSocketClient.addIntoHistory(ip)
                                runCatching { client.close() }
                                return@doOnNext
                            }
                            // Publish the timeout as an error in both cases (the reconnector relies on it);
                            // `ignoreExceptions` only silences the script-facing exception popup.
                            // zh-CN: 两种情况都将超时作为错误发布 (重连器依赖它); `ignoreExceptions` 仅抑制面向脚本的异常弹窗.
                            ThreadUtils.runOnMain { runCatching { client.onHandshakeTimeout() } }
                        }
                    } catch (e: IOException) {
                        ThreadUtils.runOnMain { runCatching { client.onSocketError(e) } }
                    }
                }
        } catch (e: Exception) {
            e.printStackTrace()
            if (!ignoreExceptions) {
                ScriptRuntime.popException(e.message)
            }
        }
        return Observable.empty()
    }

    // Parse host[:port] supporting domain/IPv4/IPv6.
    // zh-CN: 解析 host[:port], 支持 域名/IPv4/IPv6.
    private fun parseRemoteEndpoint(input: String, defaultPort: Int): RemoteEndpoint {
        val s = input.trim()

        // Bracketed IPv6: [addr]:port or [addr]
        // zh-CN: 方括号 IPv6: [addr]:port 或 [addr].
        if (s.startsWith("[") && s.contains("]")) {
            val end = s.indexOf(']')
            val hostPart = s.substring(1, end)
            val rest = s.substring(end + 1)
            if (rest.startsWith(":") && rest.length > 1) {
                val port = rest.substring(1).toInt()
                return RemoteEndpoint(hostPart, port)
            }
            return RemoteEndpoint(hostPart, defaultPort)
        }

        // For non-bracketed input:
        // - If there is exactly one ':' -> treat as host:port (domain or IPv4)
        // - If there are multiple ':' -> treat as raw IPv6 with no port
        // zh-CN:
        // - 若仅 1 个 ':' -> 视为 host:port (域名 或 IPv4)
        // - 若多个 ':' -> 视为不带端口的 IPv6.
        val colonCount = s.count { it == ':' }
        if (colonCount == 1) {
            val i = s.lastIndexOf(':')
            val hostPart = s.substring(0, i)
            val portPart = s.substring(i + 1)
            val port = portPart.toInt()
            return RemoteEndpoint(hostPart, port)
        }

        return RemoteEndpoint(s, defaultPort)
    }

    private data class RemoteEndpoint(val host: String, val port: Int)

    /** `RouteInfo.hasGateway()` is API 29; older systems fall back to the gateway address itself. */
    private fun hasGateway(route: android.net.RouteInfo): Boolean =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) route.hasGateway()
        else route.gateway?.isAnyLocalAddress == false

    /** A UI request heuristic, evaluated off the main thread; the OS still authorizes sockets. */
    fun needsLocalNetworkPermission(host: String): Boolean {
        if (LocalNetworkPermission.isGranted(context)) return false
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val network = manager.activeNetwork
        val capabilities = manager.getNetworkCapabilities(network)
        if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true ||
            capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true
        ) return false
        val name = runCatching { parseRemoteEndpoint(host, Port.PC_SERVER).host }.getOrNull() ?: return false
        val addresses = runCatching { InetAddress.getAllByName(name) }.getOrNull()
            ?: return name.trimEnd('.').endsWith(".local", ignoreCase = true)
        val routes = manager.getLinkProperties(network)?.routes.orEmpty()
        return addresses.any { address ->
            when {
                address.isLoopbackAddress || address.isAnyLocalAddress -> false
                address.address.size == 4 -> LocalNetworkDiagnostics.couldBeLocalHost(address.hostAddress)
                address.isLinkLocalAddress || address.isSiteLocalAddress || address.isMulticastAddress -> true
                (address.address[0].toInt() and 0xfe) == 0xfc -> true
                else -> routes.any { !hasGateway(it) && it.destination.prefixLength > 0 && it.matches(address) }
            }
        }
    }

    @AnyThread
    fun enableLocalServer(): Observable<JsonSocketServer> {
        return Observable
            .create<JsonSocketServer> { emitter ->

                if (!LocalNetworkPermission.isGranted(context)) {
                    emitter.onError(IOException(context.getString(R.string.error_local_network_permission_required)))
                    return@create
                }

                // Ensure only one accept loop runs at the same time.
                // zh-CN: 确保同一时间只有一个 accept 循环在运行.
                if (!mEnableLocalServerStarted.compareAndSet(false, true)) {
                    jsonSocketServer?.let { emitter.onNext(it) }
                    emitter.onComplete()
                    return@create
                }

                JsonSocketServer(this, Port.AJ_SERVER).also {
                    // Listening started.
                    // zh-CN: 监听已启动.
                    this.jsonSocketServer = it
                    it.setStateConnected()
                    serverStartedAt = System.currentTimeMillis()
                    if (ConnectionKeys.SERVER_MODE_LAN_DISCOVERY.get()) discovery.register(Port.AJ_SERVER)
                    emitter.onNext(it)
                }

                try {
                    while (true) {
                        val ss = JsonSocketServer.serverSocket
                        mServerSocket = ss
                        if (ss == null || ss.isClosed) break

                        try {
                            val acceptedSocket = ss.accept()
                            // Create a dedicated connection instance per client.
                            // zh-CN: 为每个客户端创建独立连接实例.
                            val conn = JsonSocketServer(this, Port.AJ_SERVER).apply {
                                setSocket(acceptedSocket)
                                subscribeMessage()
                                monitorMessage()
                                sayHello()
                            }

                            mServerConnections.add(conn)
                            serverConnectionCount.onNext(mServerConnections.size)

                            emitter.onNext(conn)
                        } catch (e: SocketException) {
                            // Treat "Socket closed" as a normal shutdown path.
                            // zh-CN: 将 "Socket closed" 视为正常关闭流程.
                            if ((e.message ?: "").contains("Socket closed", true)) {
                                break
                            }
                            emitter.onError(e)
                            return@create
                        }
                    }

                    emitter.onComplete()
                } finally {
                    // Allow restarting after loop exits.
                    // zh-CN: 循环退出后允许再次启动.
                    mEnableLocalServerStarted.set(false)
                    serverStartedAt = 0L
                    discovery.unregister()
                }
            }
            // Run blocking accept loop on IO scheduler to avoid ANR.
            // zh-CN: 在 IO 调度器运行阻塞 accept 循环, 避免 ANR.
            .subscribeOn(Schedulers.io())
    }

    internal fun onServerClientDisconnected(conn: JsonSocketServer) {
        // Remove disconnected connection and update count.
        // zh-CN: 移除已断开连接并更新计数.
        val removed = mServerConnections.remove(conn)
        serverConnectionCount.onNext(mServerConnections.size)
        if (removed && conn.peerHelloAt > 0L && ConnectionKeys.SERVER_MODE_NOTIFY_CONNECTION_CHANGES.get()) {
            ViewUtils.showToast(context, context.getString(R.string.text_vscode_disconnected_format, describeClient(conn)))
        }
    }

    /** A VSCode client completed the handshake on the local server. zh-CN: 一个 VSCode 客户端已在本地服务端完成握手. */
    internal fun onServerClientHello(conn: JsonSocketServer) {
        if (ConnectionKeys.SERVER_MODE_NOTIFY_CONNECTION_CHANGES.get()) {
            ViewUtils.showToast(context, context.getString(R.string.text_vscode_connected_format, describeClient(conn)))
        }
    }

    private fun describeClient(conn: JsonSocketServer): String {
        return conn.peerName ?: conn.remoteAddressText ?: context.getString(R.string.text_unknown)
    }

    @AnyThread
    fun print(log: String?) {
        jsonSocketClient?.writeLog(log)

        // Broadcast logs to all active server-side connections.
        // zh-CN: 向所有服务端已连接客户端广播日志.
        mServerConnections.forEach { it.writeLog(log) }
    }

    companion object {

        const val TYPE_HELLO = "hello"
        const val TYPE_COMMAND = "command"
        const val TYPE_BYTES_COMMAND = "bytes_command"
        const val TYPE_PING = "ping"
        const val TYPE_PONG = "pong"
        const val TYPE_REQUEST = "request"
        const val TYPE_RESPONSE = "response"
        const val TYPE_BYE = "bye"

        /**
         * `host to token` captured from the last scanned QR code; consumed by the next connection to that host so
         * its hello carries the one-time token (roadmap 4.4).
         * zh-CN: 最近一次扫码得到的 `host to token`; 由下一次到该主机的连接消费, 使其 hello 携带一次性令牌 (roadmap 4.4).
         */
        @Volatile
        var pendingQrToken: Pair<String, String>? = null

        /** Extract the pairing token of a connection QR code, if any. zh-CN: 提取连接二维码中的配对令牌 (如有). */
        fun parseQrToken(text: String?): String? {
            val s = text?.trim().orEmpty()
            if (!s.startsWith("autojs6://", ignoreCase = true)) return null
            val uri = runCatching { android.net.Uri.parse(s) }.getOrNull() ?: return null
            return uri.getQueryParameter("token")?.trim()?.takeIf { it.isNotEmpty() }
        }

        /**
         * Convert the text of a connection QR code into a `host[:port]` string accepted by the address dialog.
         * Supports `autojs6://connect?host=<ip>&port=<port>` and plain `host[:port]`. Returns null when invalid.
         * zh-CN: 将连接二维码文本转换为地址对话框接受的 `host[:port]` 形式.
         * 支持 `autojs6://connect?host=..&port=..` 与纯 `host[:port]`. 无效时返回 null.
         */
        fun parseQrConnectPayload(text: String?): String? {
            val s = text?.trim().orEmpty()
            if (s.isEmpty()) return null
            if (s.startsWith("autojs6://", ignoreCase = true)) {
                val uri = runCatching { android.net.Uri.parse(s) }.getOrNull() ?: return null
                val host = uri.getQueryParameter("host")?.trim().orEmpty()
                if (host.isEmpty()) return null
                val port = uri.getQueryParameter("port")?.toIntOrNull()?.takeIf { it in 1..65535 } ?: Port.PC_SERVER
                val hostPart = if (host.contains(':') && !host.startsWith("[")) "[$host]" else host
                return "$hostPart:$port"
            }
            return if (Regex("""[0-9a-zA-Z.\-_:\[\]%]+""").matches(s)) s else null
        }

        fun setState(cxn: Subject<State?>, state: Int) {
            cxn.onNext(State(state))
        }

        fun setState(cxn: Subject<State?>, state: Int, e: Throwable?) {
            cxn.onNext(State(state, e))
        }

    }

}
