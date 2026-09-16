package org.autojs.autojs.ui.devplugin

import android.content.Context
import com.afollestad.materialdialogs.MaterialDialog
import io.reactivex.android.schedulers.AndroidSchedulers
import org.autojs.autojs.AutoJs
import org.autojs.autojs.app.tool.JsonSocketClientTool
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.pluginclient.DevPluginClientReconnector
import org.autojs.autojs.pluginclient.JsonSocketClient
import org.autojs.autojs6.R

/**
 * Manager of the client mode (AutoJs6 connects to the VSCode extension). Status: connection state, server
 * address, VSCode host, extension / protocol versions, connection duration, last activity, reconnect
 * session and last error. Controls: connect, disconnect, reconnect now, stop reconnecting, copy address.
 * Settings: auto reconnect, retry on network change, restore on launch.
 * zh-CN: 客户端模式 (AutoJs6 连接到 VSCode 插件) 管理器. 状态: 连接状态, 服务端地址, VSCode 主机, 插件 / 协议版本,
 * 连接时长, 最近活动, 重连会话与最近错误. 控制: 连接, 断开, 立即重连, 停止重连, 复制地址.
 * 设置: 自动重连, 网络变化时重试, 启动时恢复连接.
 *
 * Created by SuperMonster003 on Sep 16, 2026.
 */
class ClientModeManagerDialog private constructor(
    context: Context,
    private val tool: JsonSocketClientTool,
) : ConnectionManagerDialog(context, R.string.text_client_mode_manager) {

    private val service = AutoJs.instance.devPluginService
    private val reconnector = service.clientReconnector

    private val connect = addAction(Action(R.string.text_connect, R.drawable.ic_play_arrow_white_48dp) {
        tool.connect()
    })

    private val disconnect = addAction(Action(R.string.text_disconnect, R.drawable.ic_stop_button_fill) {
        tool.disconnect()
        refresh()
    })

    private val reconnectNow = addAction(Action(R.string.text_reconnect_now, R.drawable.ic_refresh_white_24dp) {
        if (!reconnector.reconnectNow()) snack(R.string.error_no_saved_server_address)
        refresh()
    })

    private val stopReconnecting = addAction(Action(R.string.text_stop_reconnecting, R.drawable.ic_close_white_24dp) {
        reconnector.cancel()
        refresh()
    })

    init {
        addAction(Action(R.string.text_copy_address, R.drawable.ic_content_copy_black_24dp) {
            copyText(currentAddress())
        })

        addSwitch(
            R.string.text_auto_reconnect,
            R.string.summary_auto_reconnect,
            R.string.key_client_mode_auto_reconnect,
            R.bool.pref_client_mode_auto_reconnect,
        ) { enabled -> if (!enabled) reconnector.cancel() }
        addSwitch(
            R.string.text_reconnect_on_network_change,
            R.string.summary_reconnect_on_network_change,
            R.string.key_client_mode_reconnect_on_network_change,
            R.bool.pref_client_mode_reconnect_on_network_change,
        )
        addSwitch(
            R.string.text_restore_connection_on_launch,
            R.string.summary_restore_connection_on_launch,
            R.string.key_client_mode_restore_on_launch,
            R.bool.pref_client_mode_restore_on_launch,
        )
    }

    override fun onShown() {
        super.onShown()
        disposables.add(JsonSocketClient.cxnState.observeOn(AndroidSchedulers.mainThread()).subscribe({ refresh() }, {}))
        disposables.add(reconnector.status.observeOn(AndroidSchedulers.mainThread()).subscribe({ refresh() }, {}))
    }

    private fun currentAddress(): String {
        val client = service.jsonSocketClient
        return when {
            client?.isSocketReady == true && !client.host.isNullOrEmpty() -> "${client.host}:${client.serverPort}"
            else -> Pref.getServerAddress().trim()
        }
    }

    override fun refresh() {
        val state = JsonSocketClient.cxnState.value
        val client = service.jsonSocketClient
        val reconnect = reconnector.status.value ?: DevPluginClientReconnector.Status()
        val now = System.currentTimeMillis()
        val isConnected = state?.isConnected() == true && client?.isSocketReady == true
        val isConnecting = state?.isConnecting() == true
        val none = getString(R.string.text_none)
        val unknown = getString(R.string.text_unknown)

        val entries = ArrayList<Pair<String, String>>()
        entries += getString(R.string.text_connection_status) to when {
            isConnected -> getString(R.string.text_connected)
            isConnecting -> getString(R.string.text_connecting)
            reconnect.isActive -> getString(R.string.text_reconnecting_with_attempts, reconnect.attempts)
            else -> getString(R.string.text_disconnected)
        }
        entries += getString(R.string.text_server_address) to currentAddress().ifEmpty { none }
        val connectedClient = client?.takeIf { isConnected }
        if (connectedClient != null) {
            val host = listOfNotNull(connectedClient.peerName, connectedClient.peerPlatform?.let { "[$it]" }).joinToString(" ")
            entries += getString(R.string.text_vscode_host) to host.ifEmpty { unknown }
            entries += getString(R.string.text_extension_version) to (connectedClient.peerExtensionVersion ?: unknown)
            entries += getString(R.string.text_protocol_version) to "${connectedClient.peerProtocolVersion}"
            if (connectedClient.peerHelloAt > 0L) {
                entries += getString(R.string.text_connection_duration) to formatElapsed(now - connectedClient.peerHelloAt)
            }
            entries += getString(R.string.text_last_activity) to getString(R.string.text_elapsed_ago, formatElapsed(now - connectedClient.lastInboundAt))
        }
        if (reconnect.isActive) {
            entries += getString(R.string.text_reconnect_attempts) to "${reconnect.attempts}"
            entries += getString(R.string.text_next_retry) to when {
                reconnect.nextAttemptAt > now -> formatElapsed(reconnect.nextAttemptAt - now)
                else -> getString(R.string.text_in_progress)
            }
        }
        if (!isConnected) {
            val lastError = reconnect.lastError ?: state?.exception?.message ?: client?.lastError?.message
            if (!lastError.isNullOrBlank()) entries += getString(R.string.text_last_error) to lastError
        }
        setStatus(entries)

        setActionVisible(connect, !isConnected && !isConnecting)
        setActionVisible(disconnect, isConnected || isConnecting)
        setActionVisible(reconnectNow, !isConnected && !isConnecting && !reconnect.isActive && Pref.getServerAddress().isNotBlank())
        setActionVisible(stopReconnecting, reconnect.isActive)
    }

    companion object {

        /**
         * Shows the manager. [context] must be an Activity context; the caller keeps the returned dialog
         * (weakly) and dismisses it when that Activity goes away.
         * zh-CN: 显示管理器. [context] 须为 Activity 上下文; 调用方 (弱) 持有返回的对话框, 并在 Activity 销毁时关闭它.
         */
        fun show(context: Context, tool: JsonSocketClientTool): MaterialDialog {
            return ClientModeManagerDialog(context, tool).show()
        }

    }

}
