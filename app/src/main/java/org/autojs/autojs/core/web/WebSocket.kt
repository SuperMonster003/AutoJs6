package org.autojs.autojs.core.web

import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request.Builder
import okhttp3.Response
import okhttp3.WebSocketListener
import okio.ByteString
import org.autojs.autojs.AutoJs
import org.autojs.autojs.core.eventloop.EventEmitter
import java.lang.ref.WeakReference

/**
 * Created by SuperMonster003 on Apr 30, 2023.
 */
// @Reference to kkevsekk1/AutoX (https://github.com/kkevsekk1/AutoX) on Apr 30, 2023.
class WebSocket @JvmOverloads constructor(val client: OkHttpClient, val url: String, isInCurrentThread: Boolean = true) : EventEmitter(
    AutoJs.instance.runtime.bridges, AutoJs.instance.runtime.timers.timerForCurrentThread.takeIf { isInCurrentThread }
), okhttp3.WebSocket {

    private var maxRebuildTimes = Int.MAX_VALUE
    private var currentRebuildTimes = 0
    private var isExitOnClose = false
    private var exitOnCloseTimeout = DEFAULT_EXIT_ON_CLOSE_TIMEOUT

    private var listener: WebSocketMessageListener
    private lateinit var webSocket: okhttp3.WebSocket

    init {
        WebSocketMessageListener(this).also { listener = it }
        build()
    }

    private fun build() {
        webSocket = client.newWebSocket(Builder().url(url).build(), listener)
        instances.add(WeakReference(this))
    }

    fun rebuild() {
        cancel()
        if (currentRebuildTimes < maxRebuildTimes) {
            build()
            currentRebuildTimes += 1
        } else {
            emit(EVENT_MAX_REBUILDS, maxRebuildTimes, this)
        }
    }

    fun rebuild(maxRebuildTimes: Int) {
        this.maxRebuildTimes = maxRebuildTimes
        rebuild()
    }

    override fun cancel() = webSocket.cancel()

    override fun close(code: Int, reason: String?) = webSocket.close(code, reason)

    @JvmOverloads
    fun close(code: Int = CODE_CLOSE_NORMAL) = webSocket.close(code, null)

    fun close(reason: String?) = webSocket.close(CODE_CLOSE_NORMAL, reason)

    override fun queueSize() = webSocket.queueSize()

    override fun request() = webSocket.request()

    override fun send(text: String) = webSocket.send(text)

    override fun send(bytes: ByteString) = webSocket.send(bytes)

    override fun on(eventName: String, listener: Any) = this.also { super.on(eventName, listener) }
    override fun once(eventName: String, listener: Any) = this.also { super.once(eventName, listener) }

    @JvmOverloads
    fun exitOnClose(isExitOnClose: Boolean = true) {
        this.isExitOnClose = isExitOnClose
    }

    fun exitOnClose(timeout: Long) {
        exitOnClose(true)
        exitOnCloseTimeout = maxOf(0, timeout)
    }

    inner class WebSocketMessageListener(private val ws: WebSocket) : WebSocketListener() {

        override fun onOpen(webSocket: okhttp3.WebSocket, response: Response) {
            ws.emit(EVENT_OPEN, response, ws)
        }

        override fun onMessage(webSocket: okhttp3.WebSocket, text: String) {
            ws.emit(EVENT_MESSAGE, text, ws)
            ws.emit(EVENT_TEXT, text, ws)
        }

        override fun onMessage(webSocket: okhttp3.WebSocket, bytes: ByteString) {
            ws.emit(EVENT_MESSAGE, bytes, ws)
            ws.emit(EVENT_BYTES, bytes, ws)
        }

        override fun onClosing(webSocket: okhttp3.WebSocket, code: Int, reason: String) {
            ws.emit(EVENT_CLOSING, code, reason, ws)
        }

        override fun onClosed(webSocket: okhttp3.WebSocket, code: Int, reason: String) {
            ws.emit(EVENT_CLOSED, code, reason, ws)
        }

        override fun onFailure(webSocket: okhttp3.WebSocket, t: Throwable, response: Response?) {
            Log.w(TAG, "onFailure")
            t.printStackTrace()
            ws.emit(EVENT_FAILURE, t, response, ws)
        }

    }

    companion object {

        private val TAG = WebSocket::class.java.simpleName

        private val instances = ArrayList<WeakReference<WebSocket>>()

        /**
         * Successful operation / regular socket shutdown.
         *
         * zh-CN: 成功操作或常规的 Socket 关闭.
         */
        const val CODE_CLOSE_NORMAL = 1000

        /**
         * Client is leaving (browser tab closing).
         *
         * zh-CN: 终端正在处于移除状态, 服务端或客户端即将不可用.
         */
        const val CODE_CLOSE_GOING_AWAY = 1001

        /**
         * Endpoint received a malformed frame.
         *
         * zh-CN: 终端因协议错误或无效帧而即将终止连接.
         */
        const val CODE_CLOSE_PROTOCOL_ERROR = 1002

        /**
         * Endpoint received an unsupported frame (e.g. binary-only endpoint received text frame).
         *
         * zh-CN: 终端因帧数据类型不支持而即将终止连接.
         */
        const val CODE_CLOSE_UNSUPPORTED = 1003

        /**
         * Expected close status, received none.
         *
         * zh-CN: 不包含错误原因, 仅代表已经关闭的状态.
         */
        const val CODE_CLOSED_NO_STATUS = 1005

        /**
         * No close code frame has been receieved.
         *
         * zh-CN: 异常关闭 (如浏览器关闭).
         */
        const val CODE_CLOSE_ABNORMAL = 1006

        /**
         * Endpoint received inconsistent message (e.g. malformed UTF-8).
         *
         * zh-CN: 终端接收到不一致的报文 (如异常格式的 UTF-8).
         */
        const val CODE_UNSUPPORTED_PAYLOAD = 1007

        /**
         * Generic code used for situations other than 1003 and 1009.
         *
         * zh-CN: 终端因收到了违反其策略的报文而即将终止连接.
         */
        const val CODE_POLICY_VIOLATION = 1008

        /**
         * Endpoint won't process large frame.
         *
         * zh-CN: 终端因无法处理长度过大的报文而即将终止连接.
         */
        const val CODE_CLOSE_TOO_LARGE = 1009

        /**
         * Client wanted an extension which server did not negotiate.
         *
         * zh-CN: 终端因期望与服务端进行扩展协商而即将终止连接.
         */
        const val CODE_MANDATORY_EXTENSION = 1010

        /**
         * Internal server error while operating.
         *
         * zh-CN: 服务端因发生内部错误而即将终止连接.
         */
        const val CODE_SERVER_ERROR = 1011

        /**
         * Server/service is restarting.
         *
         * zh-CN: 服务端正在重启过程中.
         */
        const val CODE_SERVICE_RESTART = 1012

        /**
         * Temporary server condition forced blocking client's request.
         *
         * zh-CN: 服务端临时拒绝了终端请求.
         */
        const val CODE_TRY_AGAIN_LATER = 1013

        /**
         * Server acting as gateway received an invalid response.
         *
         * zh-CN: 网关服务器接收到无效的请求.
         */
        const val CODE_BAD_GATEWAY = 1014

        /**
         * Transport Layer Security handshake failure.
         *
         * zh-CN: TLS 握手失败 (如服务端证书未通过验证等).
         */
        const val CODE_TLS_HANDSHAKE_FAIL = 1015

        const val EVENT_CLOSED = "closed"
        const val EVENT_CLOSING = "closing"
        const val EVENT_FAILURE = "failure"
        const val EVENT_TEXT = "text"
        const val EVENT_MESSAGE = "message"
        const val EVENT_BYTES = "bytes"
        const val EVENT_OPEN = "open"
        const val EVENT_MAX_REBUILDS = "max_rebuilds"

        private const val DEFAULT_EXIT_ON_CLOSE_TIMEOUT = 0L

        @JvmStatic
        fun onExit(reason: String?) {
            val wsList = instances.mapNotNull { ref -> ref.get() }
            if (wsList.isNotEmpty()) {
                Log.d(TAG, "onExit ready")
                wsList.forEach {
                    val r = {
                        Log.d(TAG, "onExit triggered after delayed")
                        if (it.isExitOnClose) it.close(CODE_CLOSE_NORMAL, reason)
                    }
                    AutoJs.instance.runtime.uiHandler.postDelayed(r, it.exitOnCloseTimeout)
                }
            }
        }

    }

}