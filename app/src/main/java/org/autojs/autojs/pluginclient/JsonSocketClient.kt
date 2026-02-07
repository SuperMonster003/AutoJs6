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
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.core.pref.Pref.getBoolean
import org.autojs.autojs.core.pref.Pref.putBoolean
import org.autojs.autojs.util.StringUtils.key
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.BuildConfig
import org.autojs.autojs6.R
import java.io.IOException
import java.net.InetSocketAddress
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.Executors

class JsonSocketClient(service: DevPluginService?, private val ctx: Context, private val host: String?, port: Int) : JsonSocket(service) {

    private val jsonSocketExecutor = Executors.newSingleThreadExecutor()

    private var mSocket: Socket? = null

    var isExtensionVersionCheckFailed = false
    var hasErrorMessageOnHello = false

    // Unique id for guarding against stale state emission from old instances.
    // zh-CN: 用于防止旧实例发出的状态污染 UI 的唯一 id.
    internal val instanceId: Long = System.nanoTime()

    private val handshakeTimeoutRunnable = Runnable {
        if (isExtensionVersionCheckFailed || hasErrorMessageOnHello) {
            return@Runnable
        }
        if (!isSocketReady && !isClientSocketNormallyClosed) {
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
                        connect(InetSocketAddress(host, port), CONNECT_TIMEOUT)
                    }
                }
            } catch (e: IOException) {
                e.printStackTrace()
                runCatching { onSocketError(e) }
            }
        }
    }

    override fun isSocketReady(): Boolean {
        val s = mSocket ?: return false
        return s.isConnected &&
                !s.isClosed &&
                !s.isInputShutdown &&
                !s.isOutputShutdown
    }

    override fun getSocket() = mSocket

    override fun setSocket(socket: Socket) = also { mSocket = socket }

    @Throws(IOException::class)
    override fun switchOff() {
        close()
        isClientSocketNormallyClosed = true
    }

    @Throws(IOException::class)
    override fun close() {
        Log.w(TAG, "closing socket...")
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

    private fun onHello(message: JsonObject) {
        var currentVersion: String? = null
        val requiredVersion = BuildConfig.VSCODE_EXT_REQUIRED_VERSION
        Log.i(TAG, "onHello: $message")
        val data = message["data"]
        if (data != null && data.isJsonObject) {
            val errorMessage = data.asJsonObject["errorMessage"]
            if (errorMessage != null && errorMessage.isJsonPrimitive) {
                val msg = errorMessage.asString

                if (ctx is Activity) {
                    ctx.runOnUiThread {
                        MaterialDialog.Builder(ctx)
                            .title(ctx.getString(R.string.text_connection_cannot_be_established))
                            .content(msg)
                            .positiveText(R.string.dialog_button_dismiss)
                            .positiveColorRes(R.color.dialog_button_default)
                            .build()
                            .also {
                                mHandler.post { it.show() }
                            }
                    }
                } else {
                    ViewUtils.showToast(ctx, msg, true)
                }

                hasErrorMessageOnHello = true

                // Mark as disconnected when server rejects handshake (e.g. version mismatch).
                // zh-CN: 当服务端拒绝握手 (例如版本不匹配) 时, 标记为已断开.
                service.clientConnectionIpAddress.onNext("Disconnected")
                setStateDisconnected(IllegalStateException(errorMessage.asString))

                try {
                    switchOff()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
                return
            }

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
        setStateDisconnected(IllegalStateException("Handshake rejected"))

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

        if (ctx is Activity) {
            ctx.runOnUiThread {
                MaterialDialog.Builder(ctx)
                    .title(ctx.getString(R.string.text_connection_cannot_be_established))
                    .content(msg)
                    .positiveText(R.string.dialog_button_dismiss)
                    .positiveColorRes(R.color.dialog_button_default)
                    .build()
                    .also {
                        it.contentView?.apply {
                            autoLinkMask = Linkify.WEB_URLS
                            text = text
                        }
                        mHandler.post { it.show() }
                    }
            }
        } else {
            val toastMsg = """
                ${ctx.getString(R.string.text_min_version_of_vscode_vsc_ext)}:
                $requiredVersion
            """.trimIndent()
            ViewUtils.showToast(ctx, toastMsg, true)
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
                TYPE_HELLO -> onHello(obj)
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

    @MainThread
    @Throws(IOException::class)
    public override fun onSocketError(e: Throwable) {
        Log.w(TAG, "onSocketError")
        e.printStackTrace()
        setStateDisconnected(e)
        switchOff()
    }

    @MainThread
    @Throws(IOException::class)
    fun onHandshakeTimeout() {
        Log.i(TAG, "onHandshakeTimeout")
        setStateDisconnected(SocketTimeoutException(ctx.getString(R.string.error_handshake_timed_out, HANDSHAKE_TIMEOUT)))
        switchOff()
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
        emitStateIfActive(DevPluginService.State.DISCONNECTED)
    }

    private fun setStateDisconnected(e: Throwable?) = also {
        cancelHandshakeTimeout()
        emitStateIfActive(DevPluginService.State.DISCONNECTED, e)
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
            get() = Pref.getLinkedHashSet(R.string.key_pc_server_address_history)
            private set(value) = Pref.putLinkedHashSet(R.string.key_pc_server_address_history, value)

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

        var isClientSocketNormallyClosed
            get() = getBoolean(key(R.string.key_client_socket_normally_closed), true)
            set(state) = putBoolean(key(R.string.key_client_socket_normally_closed), state)

    }

}