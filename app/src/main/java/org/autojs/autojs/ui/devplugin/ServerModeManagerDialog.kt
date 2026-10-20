package org.autojs.autojs.ui.devplugin

import android.content.Context
import android.text.format.DateFormat
import com.afollestad.materialdialogs.MaterialDialog
import io.reactivex.android.schedulers.AndroidSchedulers
import org.autojs.autojs.AutoJs
import org.autojs.autojs.app.tool.JsonSocketServerTool
import org.autojs.autojs.core.pref.keys.ConnectionKeys
import org.autojs.autojs.pluginclient.DevPluginService
import org.autojs.autojs.pluginclient.JsonSocket
import org.autojs.autojs.pluginclient.JsonSocketServer
import org.autojs.autojs.util.NetworkUtils
import org.autojs.autojs6.R
import java.util.Date

/**
 * Manager of the server mode (the VSCode extension connects to AutoJs6, e.g. over ADB or LAN). Status:
 * listening state and address, uptime, client count. Clients: one row per connected VSCode with host,
 * extension / protocol version, address, duration and last activity; tapping a row opens its details with
 * "reconnect" (`bye(restart)`, VSCode re-establishes the connection) and "disconnect" (`bye(user)`).
 * Controls: start, stop, restart (also as header buttons when the rows are collapsed), disconnect all
 * clients, copy address. Settings: start on launch, notify on client changes, LAN discovery.
 * zh-CN: 服务端模式 (VSCode 插件连接到 AutoJs6, 如 ADB 或局域网) 管理器. 状态: 监听状态与地址, 运行时长, 客户端数量.
 * 客户端: 每个已连接的 VSCode 一行, 含主机, 插件 / 协议版本, 地址, 时长与最近活动; 点击行打开详情, 提供 "重新连接"
 * (`bye(restart)`, 由 VSCode 重新建立连接) 与 "断开连接" (`bye(user)`). 控制: 启动, 停止, 重启 (控制行收起时亦作为标题按钮),
 * 断开所有客户端, 复制地址. 设置: 启动时开启服务端, 客户端连接变化提示, 局域网发现.
 *
 * Created by SuperMonster003 on Sep 16, 2026.
 */
class ServerModeManagerDialog private constructor(
    context: Context,
    private val tool: JsonSocketServerTool,
) : ConnectionManagerDialog(
    context,
    R.string.text_server_mode_manager,
    R.string.key_server_mode_manager_status_level,
    R.string.key_server_mode_manager_control_level,
    R.string.key_server_mode_manager_settings_collapsed,
    R.string.key_server_mode_manager_clients_level,
) {

    private val service = AutoJs.instance.devPluginService

    private val start = addAction(Action(R.string.text_start, R.drawable.ic_play_arrow_white_48dp, header = true) {
        tool.connect()
        refresh()
    })

    private val stop = addAction(Action(R.string.text_stop, R.drawable.ic_stop_button_fill, header = true) {
        tool.disconnect()
        refresh()
    })

    private val restart = addAction(Action(R.string.text_restart, R.drawable.ic_refresh_white_24dp, header = true) {
        tool.disconnect()
        // The listening socket closes synchronously; give the accept loop a moment to exit before rebinding.
        // zh-CN: 监听 socket 同步关闭; 给 accept 循环一点时间退出后再重新绑定.
        handler.postDelayed({ if (dialog.isShowing) tool.connect() }, RESTART_DELAY_MILLIS)
        refresh()
    })

    private val disconnectAllClients = addAction(Action(R.string.text_disconnect_all_clients, R.drawable.ic_phonelink_off_black_48dp, header = true) {
        service.disconnectServerClients()
        refresh()
    })

    init {
        setClientsSectionVisible(true)

        addAction(Action(R.string.text_copy_address, R.drawable.ic_content_copy_black_24dp, level = Level.ALL) {
            copyText(listeningAddress())
        })

        addSwitch(
            R.string.text_restore_server_on_launch,
            R.string.summary_restore_server_on_launch,
            ConnectionKeys.SERVER_MODE_RESTORE_ON_LAUNCH,
        )
        addSwitch(
            R.string.text_notify_client_connection_changes,
            R.string.summary_notify_client_connection_changes,
            ConnectionKeys.SERVER_MODE_NOTIFY_CONNECTION_CHANGES,
        )
        addSwitch(
            R.string.text_lan_discovery,
            R.string.summary_lan_discovery,
            ConnectionKeys.SERVER_MODE_LAN_DISCOVERY,
        ) { enabled ->
            // Take effect at once while listening. zh-CN: 监听期间立即生效.
            if (service.isServerSocketConnected) {
                if (enabled) service.discovery.register(DevPluginService.Port.AJ_SERVER) else service.discovery.unregister()
            }
        }
    }

    override fun onShown() {
        super.onShown()
        disposables.add(JsonSocketServer.cxnState.observeOn(AndroidSchedulers.mainThread()).subscribe({ refresh() }, {}))
        disposables.add(service.serverConnectionCount.observeOn(AndroidSchedulers.mainThread()).subscribe({ refresh() }, {}))
    }

    private fun listeningAddress(): String = "${NetworkUtils.getIpAddress()}:${DevPluginService.Port.AJ_SERVER}"

    override fun refresh() {
        val isListening = service.isServerSocketConnected
        val connections = service.serverConnections.filter { it.peerHelloAt > 0L }.sortedBy { it.peerHelloAt }
        val now = System.currentTimeMillis()

        val status = getString(if (isListening) R.string.text_listening else R.string.text_stopped)
        val entries = ArrayList<StatusRow>()
        entries += StatusRow(getString(R.string.text_connection_status), status)
        entries += StatusRow(getString(R.string.text_listening_address), listeningAddress())
        if (isListening && service.serverStartedAt > 0L) {
            entries += StatusRow(getString(R.string.text_uptime), formatElapsed(now - service.serverStartedAt), basic = false)
        }
        entries += StatusRow(getString(R.string.text_connected_clients), "${connections.size}")
        setStatus(entries, status)

        setClients(connections.map { conn ->
            ClientRow(
                iconRes = platformIcon(conn.peerPlatform),
                title = clientTitle(conn),
                subtitle = listOf(
                    "v${conn.peerExtensionVersion ?: getString(R.string.text_unknown)} (p${conn.peerProtocolVersion})",
                    conn.remoteAddressText ?: getString(R.string.text_unknown),
                    formatElapsed(now - conn.peerHelloAt),
                    getString(R.string.text_elapsed_ago, formatElapsed(now - conn.lastInboundAt)),
                ).joinToString(" | "),
            ) { showClientDetails(conn) }
        })

        setActionVisible(start, !isListening)
        setActionVisible(stop, isListening)
        setActionVisible(restart, isListening)
        setActionVisible(disconnectAllClients, connections.isNotEmpty())
    }

    private fun clientTitle(conn: JsonSocketServer): String {
        return listOfNotNull(conn.peerName, conn.peerPlatform?.let { "[$it]" })
            .joinToString(" ")
            .ifEmpty { conn.remoteAddressText ?: getString(R.string.text_unknown) }
    }

    private fun platformIcon(platform: String?) = when (platform?.lowercase()) {
        "win32" -> R.drawable.ic_laptop_windows_black_48dp
        "darwin" -> R.drawable.ic_laptop_mac_black_48dp
        else -> R.drawable.ic_laptop_black_48dp
    }

    /**
     * Details of one client with per-client actions. The text is a snapshot; the row in the manager keeps
     * refreshing behind it.
     * zh-CN: 单个客户端的详情与操作. 文本为快照; 管理器中的行在其后继续刷新.
     */
    private fun showClientDetails(conn: JsonSocketServer) {
        val now = System.currentTimeMillis()
        val unknown = getString(R.string.text_unknown)
        val entries = listOf(
            getString(R.string.text_vscode_host) to (conn.peerName ?: unknown),
            getString(R.string.text_platform) to (conn.peerPlatform ?: unknown),
            getString(R.string.text_extension_version) to (conn.peerExtensionVersion ?: unknown),
            getString(R.string.text_protocol_version) to "${conn.peerProtocolVersion}",
            getString(R.string.text_remote_address) to (conn.remoteAddressText ?: unknown),
            getString(R.string.text_connected_at) to DateFormat.getTimeFormat(context).format(Date(conn.peerHelloAt)),
            getString(R.string.text_connection_duration) to formatElapsed(now - conn.peerHelloAt),
            getString(R.string.text_last_activity) to getString(R.string.text_elapsed_ago, formatElapsed(now - conn.lastInboundAt)),
        )
        val info = entries.joinToString("\n") { "${it.first}: ${it.second}" }
        val content = "$info\n\n${getString(R.string.summary_reconnect_client)}"

        MaterialDialog.Builder(context)
            .title(clientTitle(conn))
            .content(content)
            .positiveText(R.string.text_reconnect)
            .positiveColorRes(R.color.dialog_button_attraction)
            .onPositive { _, _ ->
                service.disconnectServerClient(conn, JsonSocket.BYE_REASON_RESTART)
                refresh()
            }
            .negativeText(R.string.text_disconnect)
            .negativeColorRes(R.color.dialog_button_warn)
            .onNegative { _, _ ->
                service.disconnectServerClient(conn, JsonSocket.BYE_REASON_USER)
                refresh()
            }
            .neutralText(R.string.dialog_button_copy)
            .neutralColorRes(R.color.dialog_button_hint)
            .onNeutral { _, _ -> copyText(info) }
            .show()
    }

    companion object {

        private const val RESTART_DELAY_MILLIS = 400L

        /**
         * Shows the manager. [context] must be an Activity context; the caller keeps the returned dialog
         * (weakly) and dismisses it when that Activity goes away.
         * zh-CN: 显示管理器. [context] 须为 Activity 上下文; 调用方 (弱) 持有返回的对话框, 并在 Activity 销毁时关闭它.
         */
        fun show(context: Context, tool: JsonSocketServerTool): MaterialDialog {
            return ServerModeManagerDialog(context, tool).show()
        }

    }

}
