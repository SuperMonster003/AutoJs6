package org.autojs.autojs.pluginclient

import android.content.Context
import androidx.annotation.AnyThread
import io.reactivex.Observable
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.BehaviorSubject
import io.reactivex.subjects.Subject
import org.autojs.autojs.annotation.ScriptInterface
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.pluginclient.JsonSocket.HANDSHAKE_TIMEOUT
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.util.ThreadUtils
import java.io.File
import java.io.IOException
import java.net.ServerSocket
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

    // Publish current server-side connection count.
    // zh-CN: 发布当前服务端连接数量.
    val serverConnectionCount = BehaviorSubject.createDefault(0)

    val clientConnectionIpAddress = BehaviorSubject.createDefault(Pref.getServerAddress())

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

    // Prevent duplicate server startup.
    // zh-CN: 防止服务端重复启动.
    private val mEnableLocalServerStarted = AtomicBoolean(false)

    fun disconnectJsonSocketClient() {
        try {
            jsonSocketClient?.switchOff()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun disconnectJsonSocketServer() {
        try {
            // Close all active client connections first.
            // zh-CN: 先关闭所有已连接客户端.
            mServerConnections.toList().forEach { runCatching { it.close() } }
            mServerConnections.clear()
            serverConnectionCount.onNext(0)

            // Then close listening socket.
            // zh-CN: 然后关闭监听 socket.
            jsonSocketServer?.switchOff()

            // Publish disconnected state for UI.
            // zh-CN: 发布断开状态用于 UI 刷新.
            JsonSocketServer.cxnState.onNext(State(State.DISCONNECTED))
        } catch (e: IOException) {
            e.printStackTrace()
        }
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

            return Observable
                .just(JsonSocketClient(this, context, ip, port))
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
                                client.switchOff()
                                return@doOnNext
                            }
                            if (!ignoreExceptions) {
                                client.onHandshakeTimeout()
                            } else {
                                client.switchOff()
                            }
                        }
                    } catch (e: IOException) {
                        if (!ignoreExceptions) {
                            client.onSocketError(e)
                        } else {
                            client.switchOff()
                        }
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

    @AnyThread
    fun enableLocalServer(): Observable<JsonSocketServer> {
        return Observable
            .create<JsonSocketServer> { emitter ->

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
                }
            }
            // Run blocking accept loop on IO scheduler to avoid ANR.
            // zh-CN: 在 IO 调度器运行阻塞 accept 循环, 避免 ANR.
            .subscribeOn(Schedulers.io())
    }

    internal fun onServerClientDisconnected(conn: JsonSocketServer) {
        // Remove disconnected connection and update count.
        // zh-CN: 移除已断开连接并更新计数.
        mServerConnections.remove(conn)
        serverConnectionCount.onNext(mServerConnections.size)
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

        fun setState(cxn: Subject<State?>, state: Int) {
            cxn.onNext(State(state))
        }

        fun setState(cxn: Subject<State?>, state: Int, e: Throwable?) {
            cxn.onNext(State(state, e))
        }

    }

}