package org.autojs.autojs.core.web

import okhttp3.OkHttpClient
import okhttp3.Request.Builder
import okhttp3.Response
import okhttp3.WebSocketListener
import okio.ByteString
import org.autojs.autojs.AutoJs
import org.autojs.autojs.core.eventloop.EventEmitter

val runtime = AutoJs.instance.runtime

/**
 * Created by SuperMonster003 on Apr 30, 2023.
 */
// @Reference to kkevsekk1/AutoX on Apr 30, 2023.
class WebSocket @JvmOverloads constructor(val client: OkHttpClient, val url: String, isInCurrentThread: Boolean = true) : EventEmitter(
    runtime.bridges, runtime.timers.timerForCurrentThread.takeIf { isInCurrentThread }
), okhttp3.WebSocket {

    private var listener: WebSocketMessageListener
    private var webSocket: okhttp3.WebSocket

    init {
        WebSocketMessageListener(this).let {
            listener = it
            webSocket = client.newWebSocket(Builder().url(url).build(), it)
        }
    }

    override fun cancel() = webSocket.cancel()

    override fun close(code: Int, reason: String?) = webSocket.close(code, reason)

    fun close(code: Int) = webSocket.close(code, null)

    override fun queueSize() = webSocket.queueSize()

    override fun request() = webSocket.request()

    override fun send(text: String) = webSocket.send(text)

    override fun send(bytes: ByteString) = webSocket.send(bytes)

    inner class WebSocketMessageListener(private val ws: WebSocket) : WebSocketListener() {

        override fun onClosed(webSocket: okhttp3.WebSocket, code: Int, reason: String) {
            ws.emit("closed", code, reason, ws)
        }

        override fun onClosing(webSocket: okhttp3.WebSocket, code: Int, reason: String) {
            ws.emit("closing", code, reason, ws)
        }

        override fun onFailure(webSocket: okhttp3.WebSocket, t: Throwable, response: Response?) {
            ws.emit("failure", t, response, ws)
        }

        override fun onMessage(webSocket: okhttp3.WebSocket, text: String) {
            ws.emit("message", text, ws)
            ws.emit("text", text, ws)
        }

        override fun onMessage(webSocket: okhttp3.WebSocket, bytes: ByteString) {
            ws.emit("message", bytes, ws)
            ws.emit("bytes", bytes, ws)
        }

        override fun onOpen(webSocket: okhttp3.WebSocket, response: Response) {
            ws.emit("open", response, ws)
        }

    }

}