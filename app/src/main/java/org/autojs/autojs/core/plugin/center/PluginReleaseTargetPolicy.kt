package org.autojs.autojs.core.plugin.center

/** Selects the action target shown for an installed plugin. */
object PluginReleaseTargetPolicy {

    data class Target(
        val release: PluginIndexRelease,
        val requiresDowngradeReinstall: Boolean,
    )

    fun resolve(
        releases: List<PluginIndexRelease>,
        installedVersionCode: Long?,
        allowUpdateCheck: Boolean,
        isIgnored: (Long) -> Boolean,
    ): Target? {
        if (!allowUpdateCheck || installedVersionCode == null) return null

        releases
            .asSequence()
            .filter { it.versionCode > installedVersionCode }
            .sortedByDescending { it.versionCode }
            .firstOrNull { !isIgnored(it.versionCode) }
            ?.let { release ->
                return Target(
                    release = release,
                    requiresDowngradeReinstall = false,
                )
            }

        val matrixTarget = releases
            .asSequence()
            .filter { it.compatibilityMatrixMatched }
            .maxByOrNull { it.versionCode }
            ?: return null
        if (matrixTarget.versionCode >= installedVersionCode) return null

        return Target(
            release = matrixTarget,
            requiresDowngradeReinstall = true,
        )
    }
}
