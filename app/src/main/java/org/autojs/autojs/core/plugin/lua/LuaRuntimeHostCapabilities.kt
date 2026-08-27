package org.autojs.autojs.core.plugin.lua

/** Host-supported capability allowlist used during provider selection and execution pinning. */
internal object LuaRuntimeHostCapabilities {
    const val DEVICE_INFO = "device.info"
    const val MODULE_SNAPSHOT = "module.snapshot.v1"
    const val STORAGE_KV = "storage.kv.v1"
    const val UI_TOAST = "ui.toast.v1"

    val ENABLED: List<String> = listOf(DEVICE_INFO, MODULE_SNAPSHOT, STORAGE_KV, UI_TOAST)
}
