package org.autojs.autojs.core.plugin.center

import org.autojs.autojs.util.StringUtils.str
import org.autojs.autojs6.R

enum class PluginMechanism(val displayName: String) {
    AIDL(str(R.string.text_plugin_mechanism_aidl)),
    SDK(str(R.string.text_plugin_mechanism_sdk)),
}

enum class PluginAuthorizedState {
    OFFICIAL,
    TRUSTED,
    USER_GRANTED,
    REQUIRED,
    DENIED,
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
    PROTOCOL_MISMATCH,
    ROM_FIRST_RUN_RESTRICTED_SUSPECTED,
    INTERNAL_ERROR,
}

data class PluginError(
    val code: PluginErrorCode,
    val message: String? = null,
    val recoverHint: String? = null,
    val causeClass: String? = null,
)
