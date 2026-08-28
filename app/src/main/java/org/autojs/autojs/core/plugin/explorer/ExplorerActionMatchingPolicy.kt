package org.autojs.autojs.core.plugin.explorer

import org.autojs.plugin.explorer.api.ExplorerActionValues

internal object ExplorerActionMatchingPolicy {

    fun matchingActionIds(
        descriptors: List<ExplorerActionDescriptor>,
        targets: List<ExplorerActionTarget>,
    ): Set<String> {
        val descriptorsById = descriptors.associateBy(ExplorerActionDescriptor::id)
        return descriptors.mapNotNullTo(linkedSetOf()) { descriptor ->
            val probeTargetId = descriptor.probeForActionId
            when {
                probeTargetId == null && ExplorerActionCatalogPolicy.matches(descriptor, targets) ->
                    descriptor.id
                probeTargetId == null -> null
                targets.size != 1 || targets.single().kind != ExplorerActionValues.TARGET_FILE -> null
                else -> descriptorsById[probeTargetId]
                    ?.takeUnless { target -> ExplorerActionCatalogPolicy.matches(target, targets) }
                    ?.let { descriptor.id }
            }
        }
    }
}
