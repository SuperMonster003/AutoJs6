package org.autojs.autojs.pluginclient

import android.util.Log
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import com.google.gson.JsonElement
import io.reactivex.subjects.BehaviorSubject
import org.autojs.autojs.core.pref.Pref.getBoolean
import org.autojs.autojs.core.pref.Pref.putBoolean
import org.autojs.autojs.util.StringUtils.key
import org.autojs.autojs6.R
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket

class JsonSocketServer(service: DevPluginService?, port: Int) : JsonSocket(service) {

    private var mSocket: Socket? = null

    init {
        try {
            setStateConnecting()
            if (!isServerSocketSetUp) {
                serverSocket = ServerSocket(port)
            }
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    override fun isSocketReady(): Boolean {
        val s = mSocket ?: return false
        return s.isConnected &&
                !s.isClosed &&
                !s.isInputShutdown &&
                !s.isOutputShutdown
    }

    private val isServerSocketSetUp
        get() = serverSocket != null && !serverSocket!!.isClosed

    override fun getSocket() = mSocket

    override fun setSocket(socket: Socket) = also { mSocket = socket }

    @Throws(IOException::class)
    override fun switchOff() {
        if (isServerSocketSetUp) {
            serverSocket?.close()
            serverSocket = null
        }
        close()
        setStateDisconnected()
        isServerSocketNormallyClosed = true
    }

    @Throws(IOException::class)
    override fun close() {
        if (isSocketReady) {
            mSocket?.close()
            mSocket = null
        }
    }

    override fun monitorMessage() = also { super.monitorMessage(mSocket, this) }

    @MainThread
    override fun onSocketData(element: JsonElement) {
        Log.d(TAG, "onSocketData...")
        try {
            if (!element.isJsonObject) {
                onSocketError(Exception("Not a JSON object"))
                return
            }
            val obj = element.asJsonObject
            val typeElement = obj["type"]
            if (typeElement == null || !typeElement.isJsonPrimitive) {
                return
            }
            val type = typeElement.asString
            Log.d(TAG, "json type: $type")
            when (type) {
                TYPE_HELLO -> setStateConnected()
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
    public override fun onSocketError(e: Throwable) {
        Log.w(TAG, "onSocketError")
        e.printStackTrace()

        // Close client socket and keep listening socket alive.
        // zh-CN: 关闭客户端 socket, 保持监听 socket 存活.
        try {
            close()
        } catch (ex: Exception) {
            ex.printStackTrace()
        }

        // Notify service to update connection count.
        // zh-CN: 通知 service 更新连接计数.
        service.onServerClientDisconnected(this)
    }

    override fun setStateConnected() = also { setState(cxnState, DevPluginService.State.CONNECTED) }

    private fun setStateConnecting() = also { setState(cxnState, DevPluginService.State.CONNECTING) }

    private fun setStateDisconnected() = also { setState(cxnState, DevPluginService.State.DISCONNECTED) }

    companion object {

        private val TAG = JsonSocketServer::class.java.simpleName

        // Replay latest state to new subscribers (e.g. after language change / recreation).
        // zh-CN: 向新订阅者回放最新状态 (例如切换语言/重建后).
        val cxnState: BehaviorSubject<DevPluginService.State> =
            BehaviorSubject.createDefault(DevPluginService.State(DevPluginService.State.DISCONNECTED))

        var isServerSocketNormallyClosed
            get() = getBoolean(key(R.string.key_server_socket_normally_closed), true)
            set(state) {
                putBoolean(key(R.string.key_server_socket_normally_closed), state)
            }

        var serverSocket: ServerSocket? = null

    }

}