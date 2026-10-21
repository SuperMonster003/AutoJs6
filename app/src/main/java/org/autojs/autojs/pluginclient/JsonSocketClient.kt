package org.autojs.autojs.pluginclient

import android.app.Activity
import android.content.Context
import android.text.util.Linkify
import android.util.Log
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import com.afollestad.materialdialogs.MaterialDialog
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import io.github.g00fy2.versioncompare.Version
import io.reactivex.subjects.BehaviorSubject
import org.autojs.autojs.core.http.LocalNetworkDiagnostics
import org.autojs.autojs.core.pref.keys.MiscStateKeys
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.BuildConfig
import org.autojs.autojs6.R
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.Executors

class JsonSocketClient(service: DevPluginService?, private val ctx: Context, val host: String?, val serverPort: Int) : JsonSocket(service) {

    private val jsonSocketExecutor = Executors.newSingleThreadExecutor()

    private var mSocket: Socket? = null

    var isExtensionVersionCheckFailed = false
    var hasErrorMessageOnHello = false

    /** One-time token from the connection QR code, sent in our hello (roadmap 4.4). zh-CN: 连接二维码中的一次性令牌, 随 hello 发送 (roadmap 4.4). */
    @Volatile
    var helloQrToken: String? = null

    /** True after the extension answered with a `pending` hello (it is asking the person). zh-CN: 插件回复 pending hello (正在询问用户) 后为 true. */
    @Volatile
    var isWaitingForApproval = false
        private set

    // Unique id for guarding against stale state emission from old instances.
    // zh-CN: 用于防止旧实例发出的状态污染 UI 的唯一 id.
    internal val instanceId: Long = System.nanoTime()

    /**
     * True once the person (or an equivalent explicit action) switched this connection off. The read loop then
     * treats end-of-stream as a normal close. Unlike [isClientSocketNormallyClosed] this is per instance.
     * zh-CN: 用户 (或等效的显式操作) 关闭本连接后为 true, 读循环随后将流结束视为正常关闭. 与 [isClientSocketNormallyClosed] 不同, 此标记仅针对本实例.
     */
    @Volatile
    var isSwitchedOff = false
        private set

    // Each instance publishes DISCONNECTED at most once, so a `close()` following an error does not overwrite
    // the error with a "clean" disconnect (the reconnector relies on the exception to decide whether to retry).
    // zh-CN: 每个实例最多发布一次 DISCONNECTED, 避免错误之后的 `close()` 用 "正常断开" 覆盖错误 (重连器依赖异常判断是否重试).
    @Volatile
    private var isDisconnectPublished = false

    /** Last error published by this instance (for the manager dialog). zh-CN: 本实例最近发布的错误 (供管理器展示). */
    @Volatile
    var lastError: Throwable? = null
        private set

    private val handshakeTimeoutRunnable = Runnable {
        if (isExtensionVersionCheckFailed || hasErrorMessageOnHello) {
            return@Runnable
        }
        if (!isSocketReady && !isSwitchedOff) {
            try {
                onHandshakeTimeout()
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    init {
        // Mark this instance as active as early as possible so CONNECTING state is visible.
        // zh-CN: 尽可能早地将此实例标记为 active, 以便 CONNECTING 状态可见.
        activeInstanceId = instanceId

        jsonSocketExecutor.submit {
            try {
                setStateConnecting()
                if (mSocket?.isConnected != true) {
                    // Use connect timeout to avoid long blocking (e.g. DNS/connect stall).
                    // zh-CN: 使用 connect 超时避免长时间阻塞 (例如 DNS/连接卡住).
                    mSocket = Socket().apply {
                        connect(InetSocketAddress(host, serverPort), CONNECT_TIMEOUT)
                    }
                    // The attempt may have been abandoned (timeout / user abort) while connect() was blocking.
                    // zh-CN: connect() 阻塞期间该尝试可能已被放弃 (超时 / 用户中止).
                    if (isDisconnectPublished || isSwitchedOff) {
                        runCatching { mSocket?.close() }
                        mSocket = null
                    }
                }
            } catch (e: Exception) {
                // IOException as well as IllegalArgumentException for an unusable host/port.
                // zh-CN: 包括 IOException 及 host/port 不可用时的 IllegalArgumentException.
                e.printStackTrace()
                mHandler.post { runCatching { onSocketError(e) } }
            }
        }
    }

    override fun isSocketReady(): Boolean {
        if (isSwitchedOff) return false
        val s = mSocket ?: return false
        return s.isConnected &&
                !s.isClosed &&
                !s.isInputShutdown &&
                !s.isOutputShutdown
    }

    override fun getSocket() = mSocket

    override fun setSocket(socket: Socket) = also { mSocket = socket }

    /**
     * Explicit close by the person: remembered across launches (no restore, no auto reconnect) and announced to a
     * v2 peer with `bye(user)` so VSCode does not reconnect either. The socket closes once the frame is written.
     * zh-CN: 用户显式关闭: 跨启动记忆 (不恢复, 不自动重连), 并向 v2 对端发送 `bye(user)` 使 VSCode 也不重连. 帧写出后关闭 socket.
     */
    @Throws(IOException::class)
    override fun switchOff() {
        isSwitchedOff = true
        isClientSocketNormallyClosed = true
        if (isSocketReadyIgnoringSwitch() && isPeerProtocolV2) {
            sayBye(BYE_REASON_USER) { runCatching { close() }.onFailure { it.printStackTrace() } }
        } else {
            close()
        }
    }

    private fun isSocketReadyIgnoringSwitch(): Boolean {
        val s = mSocket ?: return false
        return s.isConnected && !s.isClosed && !s.isInputShutdown && !s.isOutputShutdown
    }

    /**
     * Release the socket without touching the "normally closed" preference; used for every non-user close
     * (errors, timeouts, peer shutdown) so auto reconnect stays allowed.
     * zh-CN: 释放 socket 但不改动 "正常关闭" 偏好; 用于所有非用户关闭 (错误, 超时, 对端退出), 以保留自动重连资格.
     */
    @Throws(IOException::class)
    override fun close() {
        Log.w(TAG, "closing socket...")
        releaseResources()
        cancelHandshakeTimeout()
        setStateDisconnected()
        mSocket?.apply { close() }
        mSocket = null
        jsonSocketExecutor.shutdown()

        // Clear subtitle on close.
        // zh-CN: 关闭连接时清空 subtitle.
        service.clientConnectionIpAddress.onNext("Socket closed")
    }

    override fun sayHello() {
        super.sayHello()
        cancelHandshakeTimeout()
        mHandler.postDelayed(handshakeTimeoutRunnable, HANDSHAKE_TIMEOUT.toLong())
    }

    private fun cancelHandshakeTimeout() {
        // Cancel pending handshake timeout to avoid stale callbacks.
        // zh-CN: 取消挂起的握手超时回调, 避免旧回调误触发.
        mHandler.removeCallbacks(handshakeTimeoutRunnable)
    }

    override fun getContext() = ctx

    override fun getQrToken(): String? = helloQrToken

    private fun onHello(message: JsonObject) {
        var currentVersion: String? = null
        val requiredVersion = BuildConfig.VSCODE_EXT_REQUIRED_VERSION
        Log.i(TAG, "onHello: $message")
        val data = message["data"]
        if (data != null && data.isJsonObject) {
            val errorMessage = data.asJsonObject["errorMessage"]
            if (errorMessage != null && errorMessage.isJsonPrimitive) {
                val msg = errorMessage.asString

                showHelloProblem(msg, linkify = false)

                hasErrorMessageOnHello = true

                // Mark as disconnected when server rejects handshake (e.g. version mismatch).
                // zh-CN: 当服务端拒绝握手 (例如版本不匹配) 时, 标记为已断开.
                service.clientConnectionIpAddress.onNext("Disconnected")
                setStateDisconnected(HandshakeRejectedException(msg))

                try {
                    switchOff()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
                return
            }

            val pending = data.asJsonObject["pending"]
            if (pending != null && pending.isJsonPrimitive && runCatching { pending.asBoolean }.getOrDefault(false)) {
                // The extension is asking the person whether this device may connect (roadmap 4.4); the final hello
                // (or an errorMessage) follows once they answer. Keep CONNECTING and tell the person where to look.
                // zh-CN: 插件正在询问用户是否允许本设备连接 (roadmap 4.4); 用户回答后会收到最终 hello (或 errorMessage).
                // 保持 CONNECTING 状态并提示用户前往 VSCode 确认.
                if (!isWaitingForApproval) {
                    isWaitingForApproval = true
                    cancelHandshakeTimeout()
                    val hint = ctx.getString(R.string.text_waiting_for_vscode_approval)
                    service.clientConnectionIpAddress.onNext(hint)
                    ViewUtils.showToast(ctx, hint, true)
                }
                return
            }
            isWaitingForApproval = false

            val extensionVersion = data.asJsonObject["extensionVersion"]
            if (extensionVersion != null && extensionVersion.isJsonPrimitive) {
                currentVersion = extensionVersion.asString
                if (Version(currentVersion).isAtLeast(requiredVersion)) {
                    // Handshake accepted.
                    // zh-CN: 握手通过.
                    host?.let { service.clientConnectionIpAddress.onNext(it) }
                    service.isClientSocketNormallyClosed = false
                    setStateConnected()
                    return
                }
                isExtensionVersionCheckFailed = true
            }
        }

        // Fallback: version check failed or invalid hello.
        // zh-CN: 兜底: 版本校验失败或 hello 异常.
        service.clientConnectionIpAddress.onNext("Handshake rejected")
        setStateDisconnected(HandshakeRejectedException("Handshake rejected"))

        try {
            switchOff()
        } catch (e: IOException) {
            e.printStackTrace()
        }

        val msg = """
            ${ctx.getString(R.string.text_vsc_ext_version_not_meet_requirement)}.

            ${ctx.getString(R.string.text_min_version)}: $requiredVersion
            ${ctx.getString(R.string.text_current_version)}: ${currentVersion ?: "${ctx.getString(R.string.text_lower_than)} $requiredVersion"}

            ${ctx.getString(R.string.text_repo_url_of_vscode_vsc_ext)}:
            ${ctx.getString(R.string.url_github_autojs6_vscode_extension_repo)}
            """.trimIndent()

        val toastMsg = """
            ${ctx.getString(R.string.text_min_version_of_vscode_vsc_ext)}:
            $requiredVersion
        """.trimIndent()

        showHelloProblem(msg, linkify = true, toastFallback = toastMsg)
    }

    /**
     * Show a handshake problem as a dialog when the context is a living Activity, otherwise as a toast. The
     * Activity may have been recreated (language / dark mode change) since the connection was started.
     * zh-CN: 上下文为存活的 Activity 时以对话框展示握手问题, 否则以 toast 展示. 连接发起后 Activity 可能已重建 (切换语言/暗色模式).
     */
    private fun showHelloProblem(msg: String, linkify: Boolean, toastFallback: String = msg) {
        val activity = ctx as? Activity
        if (activity == null || activity.isFinishing || activity.isDestroyed) {
            ViewUtils.showToast(ctx, toastFallback, true)
            return
        }
        activity.runOnUiThread {
            if (activity.isFinishing || activity.isDestroyed) {
                ViewUtils.showToast(ctx, toastFallback, true)
                return@runOnUiThread
            }
            runCatching {
                MaterialDialog.Builder(activity)
                    .title(activity.getString(R.string.text_connection_cannot_be_established))
                    .content(msg)
                    .positiveText(R.string.dialog_button_dismiss)
                    .positiveColorRes(R.color.dialog_button_default)
                    .build()
                    .also {
                        if (linkify) {
                            it.contentView?.apply {
                                autoLinkMask = Linkify.WEB_URLS
                                text = text
                            }
                        }
                        it.show()
                    }
            }.onFailure {
                it.printStackTrace()
                ViewUtils.showToast(ctx, toastFallback, true)
            }
        }
    }

    @MainThread
    override fun onSocketData(element: JsonElement) {
        Log.d(TAG, "onSocketData...")
        try {
            if (!element.isJsonObject) {
                onSocketError(Exception("Not a JSON object"))
                return
            }
            val obj = element.asJsonObject
            val typeElement = obj["type"] ?: return
            if (!typeElement.isJsonPrimitive) {
                return
            }
            val type = typeElement.asString
            Log.d(TAG, "json type: $type")
            when (type) {
                TYPE_HELLO -> {
                    onPeerHello(obj)
                    onHello(obj)
                }
                TYPE_PING, TYPE_PONG, TYPE_REQUEST, TYPE_RESPONSE, TYPE_BYE -> handleProtocolMessage(type, obj)
                TYPE_BYTES_COMMAND -> {
                    val md5 = obj["md5"].asString
                    val bytes = sBytes.remove(md5)
                    if (bytes != null) {
                        handleBytes(obj, bytes)
                    } else {
                        sRequiredBytesCommands[md5] = obj
                    }
                }

                else -> service.responseHandler.handle(obj)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @WorkerThread
    override fun onSocketData(bytes: Bytes) {
        Log.d(TAG, "onSocketData bytes")
        val command = sRequiredBytesCommands.remove(bytes.md5)
        if (command != null) {
            handleBytes(command, bytes)
        } else {
            sBytes[bytes.md5] = bytes
        }
    }

    /**
     * The extension announced its close. `user` means the person disconnected in VSCode: respected like a local
     * disconnect (no auto reconnect, no restore on launch). Any other reason (shutdown, restart, replaced) leaves
     * the reconnector free to retry.
     * zh-CN: 插件宣告关闭. `user` 表示用户在 VSCode 中断开: 与本地断开同样对待 (不自动重连, 启动时不恢复). 其他原因 (退出, 重启, 被替换) 允许重连器重试.
     */
    @MainThread
    override fun onPeerBye(reason: String) {
        super.onPeerBye(reason)
        if (reason == BYE_REASON_USER) {
            isSwitchedOff = true
            isClientSocketNormallyClosed = true
            service.clientConnectionIpAddress.onNext("Disconnected by VSCode")
            setStateDisconnected()
        } else {
            setStateDisconnected(PeerClosedException(reason))
        }
        try {
            close()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    @MainThread
    @Throws(IOException::class)
    public override fun onSocketError(e: Throwable) {
        Log.w(TAG, "onSocketError")
        e.printStackTrace()
        setStateDisconnected(if (e is IOException) LocalNetworkDiagnostics.explain(ctx, host, e) else e)
        close()
    }

    @MainThread
    @Throws(IOException::class)
    fun onHandshakeTimeout() {
        Log.i(TAG, "onHandshakeTimeout")
        setStateDisconnected(SocketTimeoutException(ctx.getString(R.string.error_handshake_timed_out, HANDSHAKE_TIMEOUT)))
        close()
    }

    override fun monitorMessage() = also { super.monitorMessage(mSocket, this) }

    override fun setStateConnected() = also {
        cancelHandshakeTimeout()
        emitStateIfActive(DevPluginService.State.CONNECTED)
    }

    private fun setStateConnecting() = also {
        emitStateIfActive(DevPluginService.State.CONNECTING)
    }

    private fun setStateDisconnected() = also {
        cancelHandshakeTimeout()
        if (!isDisconnectPublished) {
            isDisconnectPublished = true
            emitStateIfActive(DevPluginService.State.DISCONNECTED)
        }
    }

    private fun setStateDisconnected(e: Throwable?) = also {
        cancelHandshakeTimeout()
        if (!isDisconnectPublished) {
            isDisconnectPublished = true
            lastError = e
            emitStateIfActive(DevPluginService.State.DISCONNECTED, e)
        }
    }

    private fun emitStateIfActive(state: Int, e: Throwable? = null) {
        // Only the active client instance is allowed to publish state to the global subject.
        // zh-CN: 仅允许当前活跃的客户端实例向全局 subject 发布状态.
        if (activeInstanceId != instanceId) return
        when (e) {
            null -> setState(cxnState, state)
            else -> setState(cxnState, state, e)
        }
    }

    companion object {

        private val TAG = JsonSocketClient::class.java.simpleName

        // Connect timeout = handshake timeout + 5 seconds (default).
        // zh-CN: 连接超时 = 握手超时 + 5 秒 (默认).
        private const val CONNECT_TIMEOUT = HANDSHAKE_TIMEOUT + 5_000

        var serverAddressHistory: LinkedHashSet<String>
            get() = MiscStateKeys.linkedSet(MiscStateKeys.PC_SERVER_ADDRESS_HISTORY)
            private set(value) = MiscStateKeys.setLinkedSet(MiscStateKeys.PC_SERVER_ADDRESS_HISTORY, value)

        fun addIntoHistory(ip: String) {
            serverAddressHistory = linkedSetOf(ip).apply { addAll(serverAddressHistory) }
        }

        fun removeFromHistory(ip: String) {
            serverAddressHistory = serverAddressHistory.apply { remove(ip) }
        }

        fun clearHistory() {
            serverAddressHistory = linkedSetOf()
        }

        // Track current active instance for guarding state emission.
        // zh-CN: 记录当前活跃实例, 用于防止旧实例状态污染.
        @Volatile
        internal var activeInstanceId: Long = 0L

        val cxnState: BehaviorSubject<DevPluginService.State> =
            BehaviorSubject.createDefault(DevPluginService.State(DevPluginService.State.DISCONNECTED))

        /**
         * Whether the last client connection ended by an explicit user action (here or in VSCode). True by
         * default so a fresh install never connects on its own; set to false once a handshake succeeds.
         * zh-CN: 上次客户端连接是否由用户显式结束 (本地或 VSCode 中). 默认为 true, 使全新安装不会自行连接; 握手成功后置为 false.
         */
        var isClientSocketNormallyClosed
            get() = MiscStateKeys.CLIENT_SOCKET_NORMALLY_CLOSED.get()
            set(state) = MiscStateKeys.CLIENT_SOCKET_NORMALLY_CLOSED.set(state)

    }

}
