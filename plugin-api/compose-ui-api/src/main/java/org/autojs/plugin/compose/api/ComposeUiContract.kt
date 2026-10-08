package org.autojs.plugin.compose.api

import org.autojs.plugin.common.api.PluginActions
import org.autojs.plugin.common.api.PluginCapabilityKeys

object ComposeUiIds {
    const val PLUGIN_PACKAGE_NAME = "io.github.supermonster003.autojs6.plugin.compose.ui"
    const val HOST_PACKAGE_NAME = "org.autojs.autojs6"
    const val PLUGIN_ID = "compose-ui"
    const val ENGINE = "compose"
    const val VARIANT = "default"
    /** First host build containing the V1 loader, session core and Plugin Center registration. */
    const val REQUIRED_HOST_VERSION_CODE = 5316L
}

object ComposeUiContract {
    const val CONTRACT_VERSION = 2
    const val MIN_SUPPORTED = 1
    const val META_RENDERER_FACTORY = "org.autojs.plugin.compose.RENDERER_FACTORY"
    const val META_REQUIRES_HOST_VERSION = PluginCapabilityKeys.REQUIRES_HOST_VERSION
    const val INFO_ACTION = PluginActions.INFO
    const val INFO_CATEGORY = ComposeUiIds.PLUGIN_ID
    const val PLUGIN_PERMISSION = "org.autojs.permission.PLUGIN"
    const val NO_NODE_ID = -1
    const val SYSTEM_CALLBACK_ID = 0
    fun supports(version: Int) = version in MIN_SUPPORTED..CONTRACT_VERSION
}

/** Bundle value types: Int, ArrayList<String>, ArrayList<String>, String, String respectively. */
object ComposeUiCapabilityKeys {
    const val CONTRACT_VERSION = "contractVersion"
    const val COMPONENTS = "components"
    const val FEATURES = "features"
    const val COMPOSE_VERSION = "composeVersion"
    const val SHARED_DEPS_FINGERPRINT = "sharedDepsFingerprint"
}

object ComposeUiErrorCodes {
    const val PLUGIN_UNAVAILABLE = "PLUGIN_UNAVAILABLE"
    const val PLUGIN_DISABLED = "PLUGIN_DISABLED"
    const val PLUGIN_UNAUTHORIZED = "PLUGIN_UNAUTHORIZED"
    const val PLUGIN_INCOMPATIBLE = "PLUGIN_INCOMPATIBLE"
    const val UI_MODE_REQUIRED = "UI_MODE_REQUIRED"
    const val PERMISSION_REQUIRED = "PERMISSION_REQUIRED"
    const val INVALID_ARGUMENT = "INVALID_ARGUMENT"
    const val UNKNOWN_COMPONENT = "UNKNOWN_COMPONENT"
    const val UNKNOWN_PROP = "UNKNOWN_PROP"
    const val INVALID_MODIFIER = "INVALID_MODIFIER"
    const val SCOPE_MISMATCH = "SCOPE_MISMATCH"
    const val DUPLICATE_KEY = "DUPLICATE_KEY"
    const val LIMIT_EXCEEDED = "LIMIT_EXCEEDED"
    const val RENDER_FAILED = "RENDER_FAILED"
    const val SESSION_CLOSED = "SESSION_CLOSED"
    const val NODE_DETACHED = "NODE_DETACHED"
    const val RENDERER_FAILED = "RENDERER_FAILED"
    const val INTERNAL = "INTERNAL"
}

object ComposeUiLimits {
    const val MAX_NODES_PER_SESSION = 5000
    const val MAX_DEPTH = 64
    const val MAX_PATCHES_PER_BATCH = 2000
    const val MAX_STRING_CHARS = 65536
    const val MAX_CHILDREN_PER_NODE = 2000
    const val MAX_LAZY_CHILDREN_PER_NODE = 10000
    const val MAX_MODIFIER_OPS = 64
    const val MAX_CALLBACKS_PER_SESSION = 4096
    const val MAX_SESSIONS_PER_ENGINE = 8
    const val MAX_BITMAP_PIXELS = 32_000_000L
    const val EVENT_QUEUE_CAPACITY = 1024
    // Bounds for nested value/metadata containers, in addition to session/tree bounds.
    const val MAX_VALUE_DEPTH = 64
    const val MAX_VALUE_ITEMS = 2000
    const val MAX_PROPERTIES_PER_NODE = 128
    const val MAX_SLOTS_PER_NODE = 32
    const val MAX_EVENT_FIELDS = 32
    const val MAX_THEME_COLORS = 64
    /** Parcel transport bound only; ordinary in-process calls do not serialize models. */
    const val MAX_PARCEL_BYTES = 8 * 1024 * 1024
}

class ComposeUiContractException(
    val code: String,
    message: String = code,
    val nodeId: Int? = null,
    val prop: String? = null,
    cause: Throwable? = null,
) : IllegalArgumentException(message, cause) {
    val retryable: Boolean get() = code == ComposeUiErrorCodes.PLUGIN_UNAVAILABLE ||
        code == ComposeUiErrorCodes.PLUGIN_DISABLED || code == ComposeUiErrorCodes.PLUGIN_UNAUTHORIZED ||
        code == ComposeUiErrorCodes.PLUGIN_INCOMPATIBLE
}
