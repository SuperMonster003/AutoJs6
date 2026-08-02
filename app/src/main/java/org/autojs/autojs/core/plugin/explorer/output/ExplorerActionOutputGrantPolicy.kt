package org.autojs.autojs.core.plugin.explorer.output

internal enum class ExplorerActionOutputUriRole {
    SOURCE,
    OUTPUT,
}

internal data class ExplorerActionOutputUriGrant(
    val role: ExplorerActionOutputUriRole,
    val read: Boolean,
    val write: Boolean,
    val prefix: Boolean,
)

internal object ExplorerActionOutputGrantPolicy {

    fun exactGrantPlan(): List<ExplorerActionOutputUriGrant> = listOf(
        ExplorerActionOutputUriGrant(
            role = ExplorerActionOutputUriRole.SOURCE,
            read = true,
            write = false,
            prefix = false,
        ),
        ExplorerActionOutputUriGrant(
            role = ExplorerActionOutputUriRole.OUTPUT,
            read = false,
            write = true,
            prefix = false,
        ),
    )

    fun isSafe(plan: Collection<ExplorerActionOutputUriGrant>): Boolean {
        if (plan.size != ExplorerActionOutputUriRole.entries.size) return false
        val byRole = plan.groupBy(ExplorerActionOutputUriGrant::role)
        if (byRole.values.any { it.size != 1 }) return false
        val source = byRole[ExplorerActionOutputUriRole.SOURCE]?.singleOrNull() ?: return false
        val output = byRole[ExplorerActionOutputUriRole.OUTPUT]?.singleOrNull() ?: return false
        return source.read && !source.write && !source.prefix &&
            !output.read && output.write && !output.prefix
    }
}
