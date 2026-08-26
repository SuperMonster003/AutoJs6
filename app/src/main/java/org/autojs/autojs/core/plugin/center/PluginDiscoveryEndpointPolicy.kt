package org.autojs.autojs.core.plugin.center

/** Selects exactly one metadata endpoint for each installed plugin package. */
internal object PluginDiscoveryEndpointPolicy {

    /**
     * A null action represents the common plugin-info service. Prefer it when declared;
     * otherwise preserve the discovery order of specialized service actions.
     */
    fun selectAction(actions: List<String?>): String? {
        require(actions.isNotEmpty()) { "At least one plugin service action is required" }
        return if (actions.any { it == null }) null else actions.first()
    }
}
