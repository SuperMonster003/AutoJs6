package org.autojs.autojs.core.plugin.python

/** Canonical request/PFD order: source, optional workspace, optional stdin, optional host snapshot. */
internal data class PythonRuntimeDescriptorLayout(
    val sourceIndex: Int,
    val workspaceIndex: Int?,
    val stdinIndex: Int?,
    val hostCapabilityIndex: Int?,
    val descriptorCount: Int,
) {
    companion object {
        fun create(
            hasWorkspace: Boolean,
            hasStdin: Boolean,
            hasHostCapabilities: Boolean,
        ): PythonRuntimeDescriptorLayout {
            var nextIndex = 0
            val sourceIndex = nextIndex++
            val workspaceIndex = if (hasWorkspace) nextIndex++ else null
            val stdinIndex = if (hasStdin) nextIndex++ else null
            val hostCapabilityIndex = if (hasHostCapabilities) nextIndex++ else null
            return PythonRuntimeDescriptorLayout(
                sourceIndex = sourceIndex,
                workspaceIndex = workspaceIndex,
                stdinIndex = stdinIndex,
                hostCapabilityIndex = hostCapabilityIndex,
                descriptorCount = nextIndex,
            )
        }
    }
}
