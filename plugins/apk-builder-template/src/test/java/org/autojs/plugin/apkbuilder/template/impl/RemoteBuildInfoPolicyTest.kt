package org.autojs.plugin.apkbuilder.template.impl

import org.autojs.plugin.apkbuilder.template.ApkBuildRequestExtraKeys
import org.junit.Assert.assertEquals
import org.junit.Test

class RemoteBuildInfoPolicyTest {

    @Test
    fun `single file build number starts at version code`() {
        assertEquals(
            17L,
            RemoteBuildInfoPolicy.nextBuildNumber(
                sourceKind = ApkBuildRequestExtraKeys.SOURCE_KIND_FILE,
                currentBuildNumber = 0L,
                versionCode = 17,
            )
        )
    }

    @Test
    fun `project directory increments persisted build number`() {
        assertEquals(
            43L,
            RemoteBuildInfoPolicy.nextBuildNumber(
                sourceKind = ApkBuildRequestExtraKeys.SOURCE_KIND_DIRECTORY,
                currentBuildNumber = 42L,
                versionCode = 17,
            )
        )
    }

    @Test
    fun `first project directory build starts at one independent of version code`() {
        assertEquals(
            1L,
            RemoteBuildInfoPolicy.nextBuildNumber(
                sourceKind = ApkBuildRequestExtraKeys.SOURCE_KIND_DIRECTORY,
                currentBuildNumber = 0L,
                versionCode = 17,
            )
        )
    }
}
