package org.autojs.autojs.apkbuilder.template

import org.autojs.autojs.project.BuildInfo
import org.autojs.autojs.project.ProjectConfig
import java.io.IOException

object RemoteProjectConfigUpdater {

    /**
     * Merges only build metadata returned by the official builder. The original ProjectConfig
     * object retains its fuzzy-schema key map, so AutoJs4/AutoX aliases are preserved on write.
     */
    @JvmStatic
    @Throws(IOException::class)
    fun mergeBuildInfoJson(
        originalProjectConfig: ProjectConfig,
        updatedProjectConfigJson: String,
    ): String {
        val updatedConfig = ProjectConfig.fromJson(updatedProjectConfigJson)
            ?: throw IOException("APK Builder plugin returned an invalid project config.")
        return mergeBuildInfo(originalProjectConfig, updatedConfig.buildInfo)
    }

    internal fun mergeBuildInfo(
        originalProjectConfig: ProjectConfig,
        updatedBuildInfo: BuildInfo,
    ): String {
        originalProjectConfig.buildInfo.apply {
            buildId = updatedBuildInfo.buildId
            buildNumber = updatedBuildInfo.buildNumber
            buildTime = updatedBuildInfo.buildTime
        }
        return originalProjectConfig.toJson(false)
    }
}
