package org.autojs.autojs.core.plugin.explorer

import org.autojs.autojs.core.plugin.center.PluginCapabilityResolver
import org.autojs.plugin.explorer.api.ExplorerActionProtocol
import java.util.Locale

internal object ExplorerActionAvailabilityPolicy {

    fun isProtocolSupported(protocolVersion: Long?): Boolean {
        return protocolVersion != null &&
                protocolVersion in ExplorerActionProtocol.MIN_SUPPORTED_VERSION.toLong()..
                ExplorerActionProtocol.MAX_SUPPORTED_VERSION.toLong()
    }

    fun isCatalogProtocolCompatible(advertisedProtocolVersion: Long?, catalogProtocolVersion: Int): Boolean {
        return isProtocolSupported(advertisedProtocolVersion) &&
                advertisedProtocolVersion == catalogProtocolVersion.toLong()
    }

    fun isHostVersionCompatible(hostVersionCode: Long, requiresHostVersion: Long?): Boolean {
        return PluginCapabilityResolver.isHostVersionCompatible(hostVersionCode, requiresHostVersion)
    }

    fun isAbiCompatible(pluginAbis: Collection<String>?, hostAbis: Collection<String>): Boolean {
        if (pluginAbis.isNullOrEmpty()) return true
        val normalizedPluginAbis = pluginAbis.map { normalizeAbi(it) ?: return false }
        val normalizedHostAbis = hostAbis.mapNotNull(::normalizeAbi).toSet()
        return normalizedPluginAbis.any { it in normalizedHostAbis }
    }

    fun isActivityDeclarationValid(
        expectedPackageName: String,
        actualPackageName: String,
        exported: Boolean,
        activityEnabled: Boolean,
        applicationEnabled: Boolean,
        requiredPermission: String,
        actualPermission: String?,
    ): Boolean {
        return actualPackageName == expectedPackageName &&
                exported &&
                activityEnabled &&
                applicationEnabled &&
                actualPermission == requiredPermission
    }

    private fun normalizeAbi(abi: String): String? {
        return abi.trim()
            .lowercase(Locale.ROOT)
            .takeIf(String::isNotEmpty)
    }
}
