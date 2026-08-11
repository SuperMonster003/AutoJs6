package org.autojs.autojs.core.plugin.lua

/** Host-supported capability allowlist used during provider selection and execution pinning. */
internal object LuaRuntimeHostCapabilities {
    const val DEVICE_INFO = "device.info"

    val ENABLED: List<String> = listOf(DEVICE_INFO)
}
