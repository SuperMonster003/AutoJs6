package org.autojs.autojs.pluginclient

import android.util.Log
import androidx.annotation.MainThread
import androidx.annotation.WorkerThread
import com.google.gson.JsonElement
import io.reactivex.subjects.PublishSubject
import org.autojs.autojs.core.pref.Pref.getBoolean
import org.autojs.autojs.core.pref.Pref.putBoolean
import org.autojs.autojs.util.StringUtils.key
import org.autojs.autojs.util.ViewUtils
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

    override fun isSocketReady() = mSocket != null && !mSocket!!.isClosed

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
        e.printStackTrace()
        ViewUtils.showToast(context, e.message)
    }

    override fun setStateConnected() = also { setState(cxnState, DevPluginService.State.CONNECTED) }

    private fun setStateConnecting() = also { setState(cxnState, DevPluginService.State.CONNECTING) }

    private fun setStateDisconnected() = also { setState(cxnState, DevPluginService.State.DISCONNECTED) }

    companion object {

        private val TAG = JsonSocketServer::class.java.simpleName

        val cxnState = PublishSubject.create<DevPluginService.State>()

        var isServerSocketNormallyClosed
            get() = getBoolean(key(R.string.key_server_socket_normally_closed), true)
            set(state) {
                putBoolean(key(R.string.key_server_socket_normally_closed), state)
            }

        var serverSocket: ServerSocket? = null

    }

}