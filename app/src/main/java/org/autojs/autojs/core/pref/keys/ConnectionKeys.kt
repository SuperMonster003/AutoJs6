package org.autojs.autojs.core.pref.keys

import org.autojs.autojs.AbstractAutoJs.Companion.isInrt
import org.autojs.autojs.core.pref.PrefKey
import org.autojs.autojs.core.pref.SettingCategory.Companion.CONNECTION
import org.autojs.autojs.core.pref.SettingDefault
import org.autojs.autojs.core.pref.SettingKey
import org.autojs.autojs.core.pref.SettingsDomain
import org.autojs.autojs.util.NetworkUtils
import org.autojs.autojs6.R

/**
 * Client and server mode (docs/settings/ROADMAP.md P2.5, appendix A.1 `connection`): the server address of the client
 * mode and the switches of the client and server mode managers (the readers of the former `DevPluginPrefs`, T17).
 * The address history and the expanded states of the managers are states (P4.2).
 *
 * zh-CN: 客户端与服务端模式 (ROADMAP P2.5, 附录 A.1 `connection`): 客户端模式的服务端地址, 以及客户端 / 服务端模式管理器中的开关
 * (原 `DevPluginPrefs` 的读取函数, T17). 地址历史与管理器的展开状态为状态 (P4.2).
 */
object ConnectionKeys : SettingsDomain {

    /** The address the client mode connects to; the gateway address when not set (inrt: `NetworkUtils.DEFAULT_IP_ADDRESS`). */
    @JvmField
    val SERVER_ADDRESS = SettingKey.Str(
        R.string.key_server_address, CONNECTION,
        SettingDefault.computed("if (!isInrt) NetworkUtils.getGatewayAddress() else NetworkUtils.DEFAULT_IP_ADDRESS") {
            if (!isInrt) NetworkUtils.getGatewayAddress() else NetworkUtils.DEFAULT_IP_ADDRESS
        },
    )

    /** Client mode: reconnect after an unexpected disconnect. */
    @JvmField
    val CLIENT_MODE_AUTO_RECONNECT = SettingKey.Bool(
        R.string.key_client_mode_auto_reconnect, CONNECTION,
        boolResource(R.bool.pref_client_mode_auto_reconnect, "pref_client_mode_auto_reconnect"),
    )

    /** Client mode: retry at once when a network becomes available while reconnecting. */
    @JvmField
    val CLIENT_MODE_RECONNECT_ON_NETWORK_CHANGE = SettingKey.Bool(
        R.string.key_client_mode_reconnect_on_network_change, CONNECTION,
        boolResource(R.bool.pref_client_mode_reconnect_on_network_change, "pref_client_mode_reconnect_on_network_change"),
    )

    /** Client mode: reconnect to the last server when the app starts. */
    @JvmField
    val CLIENT_MODE_RESTORE_ON_LAUNCH = SettingKey.Bool(
        R.string.key_client_mode_restore_on_launch, CONNECTION,
        boolResource(R.bool.pref_client_mode_restore_on_launch, "pref_client_mode_restore_on_launch"),
    )

    /** Server mode: listen again when the app starts. */
    @JvmField
    val SERVER_MODE_RESTORE_ON_LAUNCH = SettingKey.Bool(
        R.string.key_server_mode_restore_on_launch, CONNECTION,
        boolResource(R.bool.pref_server_mode_restore_on_launch, "pref_server_mode_restore_on_launch"),
    )

    /** Server mode: toast when a VSCode client connects or disconnects. */
    @JvmField
    val SERVER_MODE_NOTIFY_CONNECTION_CHANGES = SettingKey.Bool(
        R.string.key_server_mode_notify_connection_changes, CONNECTION,
        boolResource(R.bool.pref_server_mode_notify_connection_changes, "pref_server_mode_notify_connection_changes"),
    )

    /** Server mode: advertise the server via mDNS as `_autojs6._tcp`. */
    @JvmField
    val SERVER_MODE_LAN_DISCOVERY = SettingKey.Bool(
        R.string.key_server_mode_lan_discovery, CONNECTION,
        boolResource(R.bool.pref_server_mode_lan_discovery, "pref_server_mode_lan_discovery"),
    )

    override val keys: List<PrefKey<*>> = listOf(
        SERVER_ADDRESS, CLIENT_MODE_AUTO_RECONNECT, CLIENT_MODE_RECONNECT_ON_NETWORK_CHANGE, CLIENT_MODE_RESTORE_ON_LAUNCH,
        SERVER_MODE_RESTORE_ON_LAUNCH, SERVER_MODE_NOTIFY_CONNECTION_CHANGES, SERVER_MODE_LAN_DISCOVERY,
    )
}
