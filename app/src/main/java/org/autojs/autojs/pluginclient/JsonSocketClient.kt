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
import io.reactivex.subjects.PublishSubject
import org.autojs.autojs.AutoJs
import org.autojs.autojs.pref.Pref
import org.autojs.autojs.pref.Pref.getBoolean
import org.autojs.autojs.pref.Pref.putBoolean
import org.autojs.autojs.util.StringUtils.key
import org.autojs.autojs.util.ViewUtils
import org.autojs.autojs6.BuildConfig
import org.autojs.autojs6.R
import java.io.IOException
import java.net.Socket
import java.net.SocketTimeoutException
import java.util.concurrent.Executors


class JsonSocketClient(service: DevPluginService?, private val ctx: Context, host: String?, port: Int) : JsonSocket(service) {

    private val jsonSocketExecutor = Executors.newSingleThreadExecutor()

    private var mSocket: Socket? = null

    var isExtensionVersionCheckFailed = false
    var hasErrorMessageOnHello = false

    init {
        jsonSocketExecutor.submit {
            try {
                setStateConnecting()
                if (mSocket?.isConnected != true) {
                    mSocket = Socket(host, port)
                }
            } catch (e: IOException) {
                e.printStackTrace()
            }
        }
    }

    override fun isSocketReady() = mSocket?.isConnected == true

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
        setStateDisconnected()
        mSocket?.apply { close() }
        mSocket = null
        jsonSocketExecutor.shutdown()
    }

    override fun sayHello() {
        super.sayHello()
        mHandler.postDelayed({
            if (isExtensionVersionCheckFailed || hasErrorMessageOnHello) {
                return@postDelayed
            }
            if (!isSocketReady && !isClientSocketNormallyClosed) {
                try {
                    onHandshakeTimeout()
                } catch (e: IOException) {
                    e.printStackTrace()
                }
            }
        }, HANDSHAKE_TIMEOUT.toLong())
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
                ViewUtils.showToast(context, errorMessage.asString, true)
                hasErrorMessageOnHello = true
            }
            val extensionVersion = data.asJsonObject["extensionVersion"]
            if (extensionVersion != null && extensionVersion.isJsonPrimitive) {
                currentVersion = extensionVersion.asString
                if (Version(currentVersion).isAtLeast(requiredVersion)) {
                    setStateConnected()
                    return
                }
                isExtensionVersionCheckFailed = true
            }
        }
        try {
            switchOff()
        } catch (e: IOException) {
            e.printStackTrace()
        }

        val activity = AutoJs.instance.appUtils.currentActivity ?: context

        val msg = """
            ${activity.getString(R.string.text_vsc_ext_version_not_meet_requirement)}.
            
            ${activity.getString(R.string.text_min_version)}: $requiredVersion
            ${activity.getString(R.string.text_current_version)}: ${currentVersion ?: "${activity.getString(R.string.text_lower_than)} $requiredVersion"}
            
            ${activity.getString(R.string.text_repo_url_of_vscode_vsc_ext)}:
            ${activity.getString(R.string.url_github_autojs6_vscode_extension_repo)}
            """.trimIndent()

        if (activity is Activity) {
            activity.runOnUiThread {
                MaterialDialog.Builder(activity)
                    .title(activity.getString(R.string.text_connection_cannot_be_established))
                    .content(msg)
                    .positiveText(R.string.dialog_button_dismiss)
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
                ${activity.getString(R.string.text_min_version_of_vscode_vsc_ext)}:
                $requiredVersion
            """.trimIndent()
            ViewUtils.showToast(activity, toastMsg, true)
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
        setStateDisconnected(SocketTimeoutException(context.getString(R.string.error_handshake_timed_out, HANDSHAKE_TIMEOUT)))
        switchOff()
    }

    override fun monitorMessage() = also { super.monitorMessage(mSocket, this) }

    override fun setStateConnected() = also { setState(cxnState, DevPluginService.State.CONNECTED) }

    private fun setStateConnecting() = also { setState(cxnState, DevPluginService.State.CONNECTING) }

    private fun setStateDisconnected() = also { setState(cxnState, DevPluginService.State.DISCONNECTED) }

    private fun setStateDisconnected(e: Throwable?) = also { setState(cxnState, DevPluginService.State.DISCONNECTED, e) }

    companion object {

        private val TAG = JsonSocketClient::class.java.simpleName

        var serverAddressHistories: LinkedHashSet<String>
            get() = Pref.getLinkedHashSet(R.string.key_pc_server_address_histories)
            private set(value) = Pref.putLinkedHashSet(R.string.key_pc_server_address_histories, value)

        fun addIntoHistories(ip: String) {
            serverAddressHistories = linkedSetOf(ip).apply { addAll(serverAddressHistories) }
        }

        fun removeFromHistories(ip: String) {
            serverAddressHistories = serverAddressHistories.apply { remove(ip) }
        }

        val cxnState = PublishSubject.create<DevPluginService.State>()

        var isClientSocketNormallyClosed
            get() = getBoolean(key(R.string.key_client_socket_normally_closed), true)
            set(state) = putBoolean(key(R.string.key_client_socket_normally_closed), state)

    }

}