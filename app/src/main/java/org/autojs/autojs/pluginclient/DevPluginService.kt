package org.autojs.autojs.pluginclient

import android.content.Context
import androidx.annotation.AnyThread
import io.reactivex.Observable
import io.reactivex.schedulers.Schedulers
import io.reactivex.subjects.PublishSubject
import org.autojs.autojs.annotation.ScriptInterface
import org.autojs.autojs.pluginclient.JsonSocket.HANDSHAKE_TIMEOUT
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.ui.main.drawer.DrawerMenuDisposableItem
import org.autojs.autojs.util.ThreadUtils
import java.io.File
import java.io.IOException
import java.net.ServerSocket

/**
 * Created by Stardust on 2017/5/11.
 * Modified by SuperMonster003 as of Jan 1, 2022.
 * Transformed by SuperMonster003 on Jul 1, 2023.
 */
class DevPluginService(val context: Context) {

    class State @JvmOverloads constructor(val state: Int, val exception: Throwable? = null) {

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

    @get:ScriptInterface
    @Volatile
    var jsonSocketClient: JsonSocketClient? = null
        private set

    @get:ScriptInterface
    @Volatile
    var jsonSocketServer: JsonSocketServer? = null
        private set

    @Volatile
    private var mServerSocket: ServerSocket? = null

    val isJsonSocketClientConnected
        get() = jsonSocketClient != null && jsonSocketClient!!.isSocketReady

    val isServerSocketConnected
        get() = mServerSocket != null && !mServerSocket!!.isClosed

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

    fun disconnectJsonSocketClient() {
        try {
            jsonSocketClient?.switchOff()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    fun disconnectJsonSocketServer() {
        try {
            jsonSocketServer?.switchOff()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    @AnyThread
    fun connectToRemoteServer(context: Context, host: String, clientModeItem: DrawerMenuDisposableItem?) = connectToRemoteServer(context, host, clientModeItem, false)

    @AnyThread
    fun connectToRemoteServer(context: Context, host: String, clientModeItem: DrawerMenuDisposableItem?, ignoreExceptions: Boolean): Observable<JsonSocketClient> {
        try {
            var port = Port.PC_SERVER
            var ip = host
            val i = host.lastIndexOf(':')
            if (i > 0 && i < host.length - 1) {
                port = host.substring(i + 1).toInt()
                ip = host.substring(0, i)
            }
            return Observable
                .just(JsonSocketClient(this, context, ip, port))
                .observeOn(Schedulers.newThread())
                .doOnNext { jsonSocketClient ->
                    try {
                        clientModeItem?.subtitle = "$ip ..."
                        this.jsonSocketClient = jsonSocketClient
                        if (ThreadUtils.wait({ jsonSocketClient.isSocketReady }, HANDSHAKE_TIMEOUT)) {
                            clientModeItem?.subtitle = ip
                            this.isClientSocketNormallyClosed = false
                            JsonSocketClient.addIntoHistories(ip)
                            jsonSocketClient
                                .subscribeMessage()
                                .monitorMessage()
                                .sayHello()
                        } else {
                            if (jsonSocketClient.isExtensionVersionCheckFailed || jsonSocketClient.hasErrorMessageOnHello) {
                                JsonSocketClient.addIntoHistories(ip)
                                jsonSocketClient.switchOff()
                                return@doOnNext
                            }
                            if (!ignoreExceptions) {
                                jsonSocketClient.onHandshakeTimeout()
                            } else {
                                jsonSocketClient.switchOff()
                            }
                        }
                    } catch (e: IOException) {
                        if (!ignoreExceptions) {
                            jsonSocketClient.onSocketError(e)
                        } else {
                            jsonSocketClient.switchOff()
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

    @AnyThread
    fun enableLocalServer(): Observable<JsonSocketServer> {
        return Observable
            .just(JsonSocketServer(this, Port.AJ_SERVER))
            .observeOn(Schedulers.newThread())
            .doOnNext { jsonSocketServer: JsonSocketServer ->
                do {
                    try {
                        this.jsonSocketServer = jsonSocketServer.apply {
                            mServerSocket = JsonSocketServer.serverSocket
                        }
                        if (mServerSocket != null) {
                            jsonSocketServer
                                .setStateConnected()
                                .setSocket(mServerSocket!!.accept())
                                .subscribeMessage()
                                .monitorMessage()
                                .sayHello()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        val msg = e.message ?: ""
                        if (!msg.contains(Regex("Socket closed"))) {
                            jsonSocketServer.onSocketError(e)
                        }
                        try {
                            jsonSocketServer.socket?.close()
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }
                } while (mServerSocket != null)
            }
    }

    // FIXME by SuperMonster003 as of Dec 29, 2021.
    //  ! Would print double (may be even more times) the amount of
    //  ! messages on VSCode when multi connection were established.
    @AnyThread
    fun print(log: String?) {
        jsonSocketClient?.writeLog(log)
        jsonSocketServer?.writeLog(log)
    }

    companion object {
        const val TYPE_HELLO = "hello"
        const val TYPE_COMMAND = "command"
        const val TYPE_BYTES_COMMAND = "bytes_command"
        fun setState(cxn: PublishSubject<State?>, state: Int) {
            cxn.onNext(State(state))
        }

        fun setState(cxn: PublishSubject<State?>, state: Int, e: Throwable?) {
            cxn.onNext(State(state, e))
        }
    }

}