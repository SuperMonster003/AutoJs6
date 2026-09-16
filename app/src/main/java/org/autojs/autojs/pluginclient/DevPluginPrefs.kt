package org.autojs.autojs.pluginclient

import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs6.R

/**
 * Preferences of the VSCode extension connection, edited from the client / server mode managers.
 * zh-CN: VSCode 插件连接相关偏好, 由客户端 / 服务端模式管理器编辑.
 */
object DevPluginPrefs {

    /** Client mode: reconnect after an unexpected disconnect. zh-CN: 客户端模式: 意外断开后自动重连. */
    val isClientAutoReconnectEnabled: Boolean
        get() = Pref.getBoolean(R.string.key_client_mode_auto_reconnect, R.bool.pref_client_mode_auto_reconnect)

    /** Client mode: retry at once when a network becomes available while reconnecting. zh-CN: 客户端模式: 重连期间网络可用时立即重试. */
    val isClientReconnectOnNetworkChangeEnabled: Boolean
        get() = Pref.getBoolean(R.string.key_client_mode_reconnect_on_network_change, R.bool.pref_client_mode_reconnect_on_network_change)

    /** Client mode: reconnect to the last server when the app starts. zh-CN: 客户端模式: 应用启动时恢复上次连接. */
    val isClientRestoreOnLaunchEnabled: Boolean
        get() = Pref.getBoolean(R.string.key_client_mode_restore_on_launch, R.bool.pref_client_mode_restore_on_launch)

    /** Server mode: listen again when the app starts. zh-CN: 服务端模式: 应用启动时重新监听. */
    val isServerRestoreOnLaunchEnabled: Boolean
        get() = Pref.getBoolean(R.string.key_server_mode_restore_on_launch, R.bool.pref_server_mode_restore_on_launch)

    /** Server mode: toast when a VSCode client connects or disconnects. zh-CN: 服务端模式: VSCode 客户端连接或断开时提示. */
    val isServerNotifyConnectionChangesEnabled: Boolean
        get() = Pref.getBoolean(R.string.key_server_mode_notify_connection_changes, R.bool.pref_server_mode_notify_connection_changes)

}
