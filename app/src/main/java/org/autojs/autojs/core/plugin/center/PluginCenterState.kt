package org.autojs.autojs.core.plugin.center

import org.autojs.autojs.util.StringUtils.str
import org.autojs.autojs6.R

enum class PluginMechanism(private val displayNameRes: Int) {
    AIDL(R.string.text_plugin_mechanism_aidl),
    SDK(R.string.text_plugin_mechanism_sdk),
    ;

    val displayName: String
        get() = str(displayNameRes)
}

enum class PluginAuthorizedState {
    OFFICIAL,
    TRUSTED,
    USER_GRANTED,
    REQUIRED,
}

enum class PluginActivatedState {
    NOT_SUPPORTED,
    UNKNOWN,
    RECOMMENDED,
    DONE,
}

sealed class PluginEnabledState {
    data object READY : PluginEnabledState()
    data object DISABLED : PluginEnabledState()
    data class ERROR(val error: PluginError) : PluginEnabledState()
}

enum class PluginErrorCode {
    NOT_AUTHORIZED,
    BIND_FAILED,
    BIND_SECURITY_EXCEPTION,
    SERVICE_NOT_FOUND,
    HANDSHAKE_TIMEOUT,
    DEAD_OBJECT,
    PLUGIN_PROCESS_CRASHED,
    PROTOCOL_MISMATCH,
    HOST_VERSION_TOO_LOW,
    ROM_FIRST_RUN_RESTRICTED_SUSPECTED,
    INTERNAL_ERROR,
}

data class PluginError(
    val code: PluginErrorCode,
    val message: String? = null,
    val recoverHint: String? = null,
    val causeClass: String? = null,
    // The system refused the binding (PluginBindRefusedException), as ColorOS does for a plugin that is not running.
    // zh-CN: 系统拒绝了绑定 (PluginBindRefusedException), 如 ColorOS 对未运行的插件.
    val bindRefused: Boolean = false,
)
