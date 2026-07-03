package org.autojs.autojs.core.plugin.center

import android.os.Bundle
import org.autojs.plugin.common.api.PluginCapabilityKeys
import org.json.JSONObject

/**
 * Created by JetBrains AI Assistant (GPT-5.3-Codex (xhigh)) on Mar 25, 2026.
 */
object PluginCapabilityResolver {

    fun resolveRequiresHostVersion(capabilities: Bundle?): Long? {
        if (capabilities == null) return null
        @Suppress("DEPRECATION")
        val value = capabilities.get(PluginCapabilityKeys.REQUIRES_HOST_VERSION) ?: return null
        return when (value) {
            is Long -> value
            is Int -> value.toLong()
            is Short -> value.toLong()
            is Byte -> value.toLong()
            is Double -> value.toLong()
            is Float -> value.toLong()
            is String -> value.toLongOrNull()
            else -> null
        }?.takeIf { it > 0 }
    }

    fun resolveRequiresHostVersion(capabilities: JSONObject?): Long? {
        if (capabilities == null || !capabilities.has(PluginCapabilityKeys.REQUIRES_HOST_VERSION)) return null
        val value = capabilities.opt(PluginCapabilityKeys.REQUIRES_HOST_VERSION) ?: return null
        return when (value) {
            is Long -> value
            is Int -> value.toLong()
            is Short -> value.toLong()
            is Byte -> value.toLong()
            is Double -> value.toLong()
            is Float -> value.toLong()
            is String -> value.toLongOrNull()
            else -> null
        }?.takeIf { it > 0 }
    }

    fun isHostVersionCompatible(hostVersionCode: Long, requiresHostVersion: Long?): Boolean {
        if (requiresHostVersion == null || requiresHostVersion <= 0L) return true
        return hostVersionCode >= requiresHostVersion
    }
}
