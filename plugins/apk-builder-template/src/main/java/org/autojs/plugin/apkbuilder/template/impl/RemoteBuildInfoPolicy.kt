package org.autojs.plugin.apkbuilder.template.impl

import org.autojs.plugin.apkbuilder.template.ApkBuildRequestExtraKeys

internal object RemoteBuildInfoPolicy {

    /**
     * Mirrors the former in-host ApkBuilder semantics:
     * - project directory: increment the persisted build number;
     * - single file: seed the packaged config from versionCode without incrementing it.
     */
    fun nextBuildNumber(
        sourceKind: String,
        currentBuildNumber: Long,
        versionCode: Int,
    ): Long {
        return when (sourceKind) {
            ApkBuildRequestExtraKeys.SOURCE_KIND_DIRECTORY -> currentBuildNumber + 1L
            ApkBuildRequestExtraKeys.SOURCE_KIND_FILE -> versionCode.toLong()
            else -> throw IllegalArgumentException("Unsupported project source kind: $sourceKind")
        }
    }
}
