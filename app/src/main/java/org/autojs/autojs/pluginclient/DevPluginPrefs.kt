package org.autojs.autojs.pluginclient

import org.autojs.autojs.core.pref.keys.ConnectionKeys

/**
 * Preferences of the VSCode extension connection, edited from the client / server mode managers.
 * zh-CN: VSCode 插件连接相关偏好, 由客户端 / 服务端模式管理器编辑.
 *
 * The keys moved to [ConnectionKeys] (docs/settings/ROADMAP.md P2.5, T17); this object forwards to them until the old
 * entries are removed (P5.2 / P14.1).
 * zh-CN: 这些键已并入 [ConnectionKeys] (ROADMAP P2.5, T17); 本对象在旧入口删除 (P5.2 / P14.1) 前转发到它们.
 */
@Deprecated("Use ConnectionKeys (docs/settings/ROADMAP.md P2.5)", ReplaceWith("ConnectionKeys", "org.autojs.autojs.core.pref.keys.ConnectionKeys"))
object DevPluginPrefs {

    /** Client mode: reconnect after an unexpected disconnect. zh-CN: 客户端模式: 意外断开后自动重连. */
    val isClientAutoReconnectEnabled: Boolean
        get() = ConnectionKeys.CLIENT_MODE_AUTO_RECONNECT.get()

    /** Client mode: retry at once when a network becomes available while reconnecting. zh-CN: 客户端模式: 重连期间网络可用时立即重试. */
    val isClientReconnectOnNetworkChangeEnabled: Boolean
        get() = ConnectionKeys.CLIENT_MODE_RECONNECT_ON_NETWORK_CHANGE.get()

    /** Client mode: reconnect to the last server when the app starts. zh-CN: 客户端模式: 应用启动时恢复上次连接. */
    val isClientRestoreOnLaunchEnabled: Boolean
        get() = ConnectionKeys.CLIENT_MODE_RESTORE_ON_LAUNCH.get()

    /** Server mode: listen again when the app starts. zh-CN: 服务端模式: 应用启动时重新监听. */
    val isServerRestoreOnLaunchEnabled: Boolean
        get() = ConnectionKeys.SERVER_MODE_RESTORE_ON_LAUNCH.get()

    /** Server mode: toast when a VSCode client connects or disconnects. zh-CN: 服务端模式: VSCode 客户端连接或断开时提示. */
    val isServerNotifyConnectionChangesEnabled: Boolean
        get() = ConnectionKeys.SERVER_MODE_NOTIFY_CONNECTION_CHANGES.get()

    /** Server mode: advertise the server via mDNS as `_autojs6._tcp` (roadmap 3.10). zh-CN: 服务端模式: 通过 mDNS 以 `_autojs6._tcp` 广播服务端 (roadmap 3.10). */
    val isServerLanDiscoveryEnabled: Boolean
        get() = ConnectionKeys.SERVER_MODE_LAN_DISCOVERY.get()

}
