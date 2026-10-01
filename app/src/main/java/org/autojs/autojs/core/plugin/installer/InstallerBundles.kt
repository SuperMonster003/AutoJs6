package org.autojs.autojs.core.plugin.installer

import android.os.Bundle
import org.autojs.autojs.core.plugin.center.PluginCapabilityResolver
import org.autojs.plugin.installer.api.InstallerCapabilityKeys
import org.autojs.plugin.installer.api.InstallerContract
import java.nio.charset.StandardCharsets

/** Bundle envelopes of the installer contract: every host bundle carries the contract and host versions. */
internal object InstallerBundles {

    fun request(requestJson: String, hostVersionCode: Long, hostSessionId: String? = null,
        contractVersion: Int = InstallerContract.MIN_CONTRACT_VERSION): Bundle = Bundle().apply {
        require(InstallerContract.supportsContractVersion(contractVersion))
        putInt(InstallerContract.KEY_CONTRACT_VERSION, contractVersion)
        putLong(InstallerContract.KEY_HOST_VERSION_CODE, hostVersionCode)
        putString(InstallerContract.KEY_REQUEST_JSON, requestJson)
        hostSessionId?.let { putString(InstallerContract.KEY_HOST_SESSION_ID, it) }
    }

    @Suppress("DEPRECATION")
    fun peer(capabilities: Bundle): InstallerPeer {
        val base = PluginCapabilityResolver.coerceLong(capabilities.get(InstallerCapabilityKeys.CONTRACT_VERSION))
        val maximum = if (capabilities.containsKey(InstallerCapabilityKeys.MAX_CONTRACT_VERSION)) {
            requireNotNull(PluginCapabilityResolver.coerceLong(capabilities.get(InstallerCapabilityKeys.MAX_CONTRACT_VERSION))) {
                "Invalid maximum installer contract version"
            }
        } else null
        return InstallerPeer.negotiate(base, maximum,
            capabilities.getStringArray(InstallerCapabilityKeys.FEATURES_KEY).orEmpty().filterNotNull().toSet(),
            capabilities.getStringArray(InstallerCapabilityKeys.AUTHORIZERS).orEmpty().filterNotNull().toSet())
    }

    /** The string under [key], or null when absent, blank or beyond the JSON ceiling. */
    fun string(bundle: Bundle?, key: String): String? {
        val value = bundle?.getString(key)?.takeIf { it.isNotBlank() } ?: return null
        return value.takeIf { it.toByteArray(StandardCharsets.UTF_8).size <= InstallerContract.MAX_JSON_BYTES }
    }
}
