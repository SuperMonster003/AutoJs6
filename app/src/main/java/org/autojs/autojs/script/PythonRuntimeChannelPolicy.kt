package org.autojs.autojs.script

import org.autojs.autojs6.BuildConfig

/** Central default-off admission policy for the Python Runtime host channel. */
internal object PythonRuntimeChannelPolicy {

    /**
     * Debug builds admit only the developer-preview flag; non-debug builds admit only the
     * independently supplied release-candidate flag. The inactive channel's flag is ignored.
     */
    fun isEnabled(
        debuggable: Boolean,
        previewEnabled: Boolean,
        releaseCandidateEnabled: Boolean,
    ): Boolean = if (debuggable) previewEnabled else releaseCandidateEnabled

    val isEnabledForCurrentBuild: Boolean
        get() = isEnabled(
            debuggable = BuildConfig.DEBUG,
            previewEnabled = BuildConfig.PYTHON_RUNTIME_PREVIEW_ENABLED,
            releaseCandidateEnabled = BuildConfig.PYTHON_RUNTIME_RELEASE_CANDIDATE_ENABLED,
        )
}
