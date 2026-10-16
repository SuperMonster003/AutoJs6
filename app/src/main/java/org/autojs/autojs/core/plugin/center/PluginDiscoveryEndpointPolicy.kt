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

    /**
     * The plugin tool provider services that list a package (MCP roadmap D60): only those of packages without any other
     * declared plugin service, so that third parties need no INFO service while plugins declaring INFO or a family
     * service keep their endpoint.
     */
    fun <T> toolProviderOnly(declared: List<T>, toolProviders: List<T>, packageOf: (T) -> String): List<T> {
        val covered = declared.mapTo(HashSet(), packageOf)
        return toolProviders.filter { packageOf(it) !in covered }
    }
}
