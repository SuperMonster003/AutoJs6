package org.autojs.autojs.ui.devplugin

import android.content.Context
import com.afollestad.materialdialogs.MaterialDialog
import io.reactivex.android.schedulers.AndroidSchedulers
import org.autojs.autojs.AutoJs
import org.autojs.autojs.app.tool.JsonSocketClientTool
import org.autojs.autojs.core.pref.keys.ConnectionKeys
import org.autojs.autojs.core.pref.keys.MiscStateKeys
import org.autojs.autojs.pluginclient.DevPluginClientReconnector
import org.autojs.autojs.pluginclient.JsonSocketClient
import org.autojs.autojs6.R

/**
 * Manager of the client mode (AutoJs6 connects to the VSCode extension). Status: connection state, server
 * address, VSCode host, extension / protocol versions, connection duration, last activity, reconnect
 * session and last error. Controls: connect, disconnect, reconnect now, stop reconnecting (also as header
 * buttons when the rows are collapsed), server address history, copy address. Settings: auto reconnect,
 * retry on network change, restore on launch.
 * zh-CN: 客户端模式 (AutoJs6 连接到 VSCode 插件) 管理器. 状态: 连接状态, 服务端地址, VSCode 主机, 插件 / 协议版本,
 * 连接时长, 最近活动, 重连会话与最近错误. 控制: 连接, 断开, 立即重连, 停止重连 (控制行收起时亦作为标题按钮),
 * 服务端地址历史记录, 复制地址. 设置: 自动重连, 网络变化时重试, 启动时恢复连接.
 *
 * Created by SuperMonster003 on Sep 16, 2026.
 */
class ClientModeManagerDialog private constructor(
    context: Context,
    private val tool: JsonSocketClientTool,
) : ConnectionManagerDialog(
    context,
    R.string.text_client_mode_manager,
    MiscStateKeys.CLIENT_MODE_MANAGER_STATUS_LEVEL,
    MiscStateKeys.CLIENT_MODE_MANAGER_CONTROL_LEVEL,
    MiscStateKeys.CLIENT_MODE_MANAGER_SETTINGS_COLLAPSED,
    MiscStateKeys.CLIENT_MODE_MANAGER_CLIENTS_LEVEL,
) {

    private val service = AutoJs.instance.devPluginService
    private val reconnector = service.clientReconnector

    private val connect = addAction(Action(R.string.text_connect, R.drawable.ic_play_arrow_white_48dp, header = true) {
        tool.connect()
    })

    private val disconnect = addAction(Action(R.string.text_disconnect, R.drawable.ic_stop_button_fill, header = true) {
        tool.disconnect()
        refresh()
    })

    private val reconnectNow = addAction(Action(R.string.text_reconnect_now, R.drawable.ic_refresh_white_24dp, header = true) {
        if (!reconnector.reconnectNow()) snack(R.string.error_no_saved_server_address)
        refresh()
    })

    private val stopReconnecting = addAction(Action(R.string.text_stop_reconnecting, R.drawable.ic_close_white_24dp, header = true) {
        reconnector.cancel()
        refresh()
    })

    init {
        addAction(Action(R.string.text_history, R.drawable.ic_history_black_48dp, R.string.summary_server_address_history, header = true) {
            tool.showServerAddressHistory { address -> tool.connectTo(address) }
        })
        addAction(Action(R.string.text_copy_address, R.drawable.ic_content_copy_black_24dp, level = Level.ALL) {
            copyText(currentAddress())
        })

        addSwitch(
            R.string.text_auto_reconnect,
            R.string.summary_auto_reconnect,
            ConnectionKeys.CLIENT_MODE_AUTO_RECONNECT,
        ) { enabled -> if (!enabled) reconnector.cancel() }
        addSwitch(
            R.string.text_reconnect_on_network_change,
            R.string.summary_reconnect_on_network_change,
            ConnectionKeys.CLIENT_MODE_RECONNECT_ON_NETWORK_CHANGE,
        )
        addSwitch(
            R.string.text_restore_connection_on_launch,
            R.string.summary_restore_connection_on_launch,
            ConnectionKeys.CLIENT_MODE_RESTORE_ON_LAUNCH,
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
            else -> ConnectionKeys.SERVER_ADDRESS.get().trim()
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

        val status = when {
            isConnected -> getString(R.string.text_connected)
            isConnecting -> getString(R.string.text_connecting)
            reconnect.isActive -> getString(R.string.text_reconnecting_with_attempts, reconnect.attempts)
            else -> getString(R.string.text_disconnected)
        }
        val entries = ArrayList<StatusRow>()
        entries += StatusRow(getString(R.string.text_connection_status), status)
        entries += StatusRow(getString(R.string.text_server_address), currentAddress().ifEmpty { none })
        val connectedClient = client?.takeIf { isConnected }
        if (connectedClient != null) {
            val host = listOfNotNull(connectedClient.peerName, connectedClient.peerPlatform?.let { "[$it]" }).joinToString(" ")
            entries += StatusRow(getString(R.string.text_vscode_host), host.ifEmpty { unknown }, basic = false)
            entries += StatusRow(getString(R.string.text_extension_version), connectedClient.peerExtensionVersion ?: unknown, basic = false)
            entries += StatusRow(getString(R.string.text_protocol_version), "${connectedClient.peerProtocolVersion}", basic = false)
            if (connectedClient.peerHelloAt > 0L) {
                entries += StatusRow(getString(R.string.text_connection_duration), formatElapsed(now - connectedClient.peerHelloAt), basic = false)
            }
            entries += StatusRow(getString(R.string.text_last_activity), getString(R.string.text_elapsed_ago, formatElapsed(now - connectedClient.lastInboundAt)), basic = false)
        }
        if (reconnect.isActive) {
            entries += StatusRow(getString(R.string.text_reconnect_attempts), "${reconnect.attempts}", basic = false)
            entries += StatusRow(getString(R.string.text_next_retry), when {
                reconnect.nextAttemptAt > now -> formatElapsed(reconnect.nextAttemptAt - now)
                else -> getString(R.string.text_in_progress)
            })
        }
        if (!isConnected) {
            val lastError = reconnect.lastError ?: state?.exception?.message ?: client?.lastError?.message
            if (!lastError.isNullOrBlank()) entries += StatusRow(getString(R.string.text_last_error), lastError)
        }
        setStatus(entries, status)

        setActionVisible(connect, !isConnected && !isConnecting)
        setActionVisible(disconnect, isConnected || isConnecting)
        setActionVisible(reconnectNow, !isConnected && !isConnecting && !reconnect.isActive && ConnectionKeys.SERVER_ADDRESS.get().isNotBlank())
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
