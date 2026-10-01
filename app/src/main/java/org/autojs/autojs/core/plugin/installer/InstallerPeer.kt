package org.autojs.autojs.core.plugin.installer

import org.autojs.plugin.installer.api.InstallerCapabilityKeys
import org.autojs.plugin.installer.api.InstallerContract
import org.autojs.plugin.installer.api.InstallerErrorCodes

/** Capability decisions are made for the same Binder that receives the operation, before sending it. */
internal data class InstallerPeer(
    val contractVersion: Int,
    val features: Set<String>,
    val authorizers: Set<String>,
) {
    fun requireAuthorizer(authorizer: String) {
        require(InstallerContract.isAuthorizer(authorizer)) { "unknown authorizer: $authorizer" }
        if (authorizer == InstallerContract.AUTHORIZER_AUTO) return
        if (authorizer !in authorizers ||
            authorizer == InstallerContract.AUTHORIZER_DHIZUKU && contractVersion < 2) {
            throw InstallerPluginException(InstallerErrorMapper.error(InstallerErrorCodes.AUTHORIZER_UNAVAILABLE,
                "The selected installer plugin does not implement $authorizer", retryable = false,))
        }
    }

    fun requireInstall(authorizer: String, interaction: String) {
        requireAuthorizer(authorizer)
        require(InstallerContract.isInteraction(interaction)) { "unknown interaction: $interaction" }
        if (interaction == InstallerContract.INTERACTION_NOTIFICATION) {
            requireExtension(InstallerCapabilityKeys.FEATURE_NOTIFICATION_INSTALL, "notification installation")
        }
    }

    fun requireDefault(authorizer: String, mode: String) {
        requireAuthorizer(authorizer)
        require(InstallerContract.isDefaultMode(mode)) { "unknown default-installer mode: $mode" }
        if (mode == InstallerContract.DEFAULT_MODE_PERSISTENT) {
            requireExtension(InstallerCapabilityKeys.FEATURE_PERSISTENT_DEFAULT_INSTALLER, "persistent default installation")
        }
    }

    private fun requireExtension(feature: String, label: String) {
        if (contractVersion < 2 || feature !in features) throw InstallerPluginException(
            InstallerErrorMapper.error(InstallerErrorCodes.INVALID_ARGUMENT,
                "The selected installer plugin does not support $label; contract V2 and $feature are required", retryable = false))
    }

    companion object {
        /** An absent maximum describes a single-version legacy peer; malformed ranges fail closed. */
        fun negotiate(base: Long?, maximum: Long?, features: Set<String>, authorizers: Set<String>): InstallerPeer {
            require(base != null && base in 1L..InstallerContract.MAX_CONTRACT_VERSION.toLong()) {
                "Unsupported minimum installer contract version: $base"
            }
            val highest = maximum ?: base
            require(highest >= base && highest <= Int.MAX_VALUE.toLong()) { "Invalid installer contract version range: $base..$highest" }
            val negotiated = minOf(highest, InstallerContract.MAX_CONTRACT_VERSION.toLong()).toInt()
            return InstallerPeer(negotiated, features.intersect(InstallerCapabilityKeys.FEATURES.toSet()),
                authorizers.filterTo(linkedSetOf()) { it in InstallerContract.AUTHORIZERS &&
                    (it != InstallerContract.AUTHORIZER_DHIZUKU || negotiated >= 2) })
        }
    }
}
