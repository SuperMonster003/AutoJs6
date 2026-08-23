package org.autojs.autojs.core.plugin.typescript

import org.autojs.plugin.typescript.api.TypeScriptCompilerContract

/**
 * Host-side source admission for compiler profiles newer than the frozen API-v1 constants.
 *
 * The plugin capability bundle remains the runtime authority. This policy only lets the host
 * prepare inputs which a newer plugin may advertise without mutating the frozen public contract.
 */
internal object TypeScriptSourceExtensions {

    fun forProfile(targetProfile: String): List<String> =
        if (targetProfile == TypeScriptCompilerContract.PROFILE_RHINO) {
            RHINO_EXTENSIONS
        } else {
            TypeScriptCompilerContract.supportedExtensions(targetProfile).asList()
        }

    private val RHINO_EXTENSIONS = listOf(".ts", ".tsx")
}
