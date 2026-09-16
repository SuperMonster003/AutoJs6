package org.autojs.autojs.ui.devplugin

import android.content.Context
import com.afollestad.materialdialogs.MaterialDialog
import io.reactivex.android.schedulers.AndroidSchedulers
import org.autojs.autojs.AutoJs
import org.autojs.autojs.app.tool.JsonSocketServerTool
import org.autojs.autojs.pluginclient.DevPluginService
import org.autojs.autojs.pluginclient.JsonSocketServer
import org.autojs.autojs.util.NetworkUtils
import org.autojs.autojs6.R

/**
 * Manager of the server mode (the VSCode extension connects to AutoJs6, e.g. over ADB or LAN). Status:
 * listening state and address, uptime, connected clients with host / extension version / address /
 * duration / last activity. Controls: start, stop, restart, disconnect all clients, copy address.
 * Settings: start on launch, notify on client changes.
 * zh-CN: 服务端模式 (VSCode 插件连接到 AutoJs6, 如 ADB 或局域网) 管理器. 状态: 监听状态与地址, 运行时长,
 * 已连接客户端及其主机 / 插件版本 / 地址 / 时长 / 最近活动. 控制: 启动, 停止, 重启, 断开所有客户端, 复制地址.
 * 设置: 启动时开启服务端, 客户端连接变化提示.
 *
 * Created by SuperMonster003 on Sep 16, 2026.
 */
class ServerModeManagerDialog private constructor(
    context: Context,
    private val tool: JsonSocketServerTool,
) : ConnectionManagerDialog(context, R.string.text_server_mode_manager) {

    private val service = AutoJs.instance.devPluginService

    private val start = addAction(Action(R.string.text_start, R.drawable.ic_play_arrow_white_48dp) {
        tool.connect()
        refresh()
    })

    private val stop = addAction(Action(R.string.text_stop, R.drawable.ic_stop_button_fill) {
        tool.disconnect()
        refresh()
    })

    private val restart = addAction(Action(R.string.text_restart, R.drawable.ic_refresh_white_24dp) {
        tool.disconnect()
        // The listening socket closes synchronously; give the accept loop a moment to exit before rebinding.
        // zh-CN: 监听 socket 同步关闭; 给 accept 循环一点时间退出后再重新绑定.
        handler.postDelayed({ if (dialog.isShowing) tool.connect() }, RESTART_DELAY_MILLIS)
        refresh()
    })

    private val disconnectAllClients = addAction(Action(R.string.text_disconnect_all_clients, R.drawable.ic_phonelink_off_black_48dp) {
        service.disconnectServerClients()
        refresh()
    })

    init {
        addAction(Action(R.string.text_copy_address, R.drawable.ic_content_copy_black_24dp) {
            copyText(listeningAddress())
        })

        addSwitch(
            R.string.text_restore_server_on_launch,
            R.string.summary_restore_server_on_launch,
            R.string.key_server_mode_restore_on_launch,
            R.bool.pref_server_mode_restore_on_launch,
        )
        addSwitch(
            R.string.text_notify_client_connection_changes,
            R.string.summary_notify_client_connection_changes,
            R.string.key_server_mode_notify_connection_changes,
            R.bool.pref_server_mode_notify_connection_changes,
        )
    }

    override fun onShown() {
        super.onShown()
        disposables.add(JsonSocketServer.cxnState.observeOn(AndroidSchedulers.mainThread()).subscribe({ refresh() }, {}))
        disposables.add(service.serverConnectionCount.observeOn(AndroidSchedulers.mainThread()).subscribe({ refresh() }, {}))
    }

    private fun listeningAddress(): String = "${NetworkUtils.getIpAddress()}:${DevPluginService.Port.AJ_SERVER}"

    override fun refresh() {
        val isListening = service.isServerSocketConnected
        val connections = service.serverConnections.filter { it.peerHelloAt > 0L }
        val now = System.currentTimeMillis()
        val unknown = getString(R.string.text_unknown)

        val entries = ArrayList<Pair<String, String>>()
        entries += getString(R.string.text_connection_status) to getString(if (isListening) R.string.text_listening else R.string.text_stopped)
        entries += getString(R.string.text_listening_address) to listeningAddress()
        if (isListening && service.serverStartedAt > 0L) {
            entries += getString(R.string.text_uptime) to formatElapsed(now - service.serverStartedAt)
        }
        entries += getString(R.string.text_connected_clients) to "${connections.size}"
        connections.forEachIndexed { index, conn ->
            val host = listOfNotNull(conn.peerName, conn.peerPlatform?.let { "[$it]" }).joinToString(" ").ifEmpty { unknown }
            val parts = listOf(
                host,
                "v${conn.peerExtensionVersion ?: unknown} (p${conn.peerProtocolVersion})",
                conn.remoteAddressText ?: unknown,
                formatElapsed(now - conn.peerHelloAt),
                getString(R.string.text_elapsed_ago, formatElapsed(now - conn.lastInboundAt)),
            )
            entries += getString(R.string.text_client_n, index + 1) to parts.joinToString(" | ")
        }
        setStatus(entries)

        setActionVisible(start, !isListening)
        setActionVisible(stop, isListening)
        setActionVisible(restart, isListening)
        setActionVisible(disconnectAllClients, connections.isNotEmpty())
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
