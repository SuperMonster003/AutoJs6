package org.autojs.autojs.core.plugin.center

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginReleaseTargetPolicyTest {

    @Test
    fun selectsHighestNonIgnoredUpgrade() {
        val target = PluginReleaseTargetPolicy.resolve(
            releases = listOf(release(11), release(13), release(12)),
            installedVersionCode = 10,
            allowUpdateCheck = true,
            isIgnored = { it == 13L },
        )

        requireNotNull(target)
        assertEquals(12L, target.release.versionCode)
        assertFalse(target.requiresDowngradeReinstall)
    }

    @Test
    fun exposesLowerMatrixMatchAsDowngradeReinstall() {
        val target = PluginReleaseTargetPolicy.resolve(
            releases = listOf(release(680101, matrixMatched = true)),
            installedVersionCode = 681101,
            allowUpdateCheck = true,
            isIgnored = { false },
        )

        requireNotNull(target)
        assertEquals(680101L, target.release.versionCode)
        assertTrue(target.requiresDowngradeReinstall)
    }

    @Test
    fun neverTreatsOrdinaryLowerReleaseAsActionTarget() {
        assertNull(
            PluginReleaseTargetPolicy.resolve(
                releases = listOf(release(9)),
                installedVersionCode = 10,
                allowUpdateCheck = true,
                isIgnored = { false },
            ),
        )
    }

    @Test
    fun equalMatrixMatchRequiresNoAction() {
        assertNull(
            PluginReleaseTargetPolicy.resolve(
                releases = listOf(release(10, matrixMatched = true)),
                installedVersionCode = 10,
                allowUpdateCheck = true,
                isIgnored = { false },
            ),
        )
    }

    @Test
    fun disabledUpdateChecksSuppressMatrixDowngradeAction() {
        assertNull(
            PluginReleaseTargetPolicy.resolve(
                releases = listOf(release(9, matrixMatched = true)),
                installedVersionCode = 10,
                allowUpdateCheck = false,
                isIgnored = { false },
            ),
        )
    }

    private fun release(code: Long, matrixMatched: Boolean = false) = PluginIndexRelease(
        versionName = "1.0.0",
        versionCode = code,
        compatibilityMatrixMatched = matrixMatched,
    )
}
