package org.autojs.autojs.core.plugin.center

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginCenterResumeRefreshPolicyTest {

    @Test
    fun initialAndPagerResumesDoNotRequestRedundantDiscovery() {
        val policy = PluginCenterResumeRefreshPolicy()

        assertFalse(policy.consumeRefreshOnResume(nightModeChanged = false))
        assertFalse(policy.consumeRefreshOnResume(nightModeChanged = false))
    }

    @Test
    fun firstResumeAfterStopRequestsExactlyOneCatchUpDiscovery() {
        val policy = PluginCenterResumeRefreshPolicy()

        policy.onStopped()

        assertTrue(policy.consumeRefreshOnResume(nightModeChanged = false))
        assertFalse(policy.consumeRefreshOnResume(nightModeChanged = false))
    }

    @Test
    fun nightModeChangeRequestsDiscoveryWithoutStop() {
        val policy = PluginCenterResumeRefreshPolicy()

        assertTrue(policy.consumeRefreshOnResume(nightModeChanged = true))
        assertFalse(policy.consumeRefreshOnResume(nightModeChanged = false))
    }

    @Test
    fun returningFromPluginSettingsRequestsOneRefreshEvenWithoutStop() {
        val policy = PluginCenterResumeRefreshPolicy()

        policy.onPluginSettingsOpened()

        assertTrue(policy.consumeRefreshOnResume(nightModeChanged = false))
        assertFalse(policy.consumeRefreshOnResume(nightModeChanged = false))
    }

    @Test
    fun settingsAndLifecycleRefreshRequestsAreConsumedTogether() {
        val policy = PluginCenterResumeRefreshPolicy()

        policy.onPluginSettingsOpened()
        policy.onStopped()

        assertTrue(policy.consumeRefreshOnResume(nightModeChanged = true))
        assertFalse(policy.consumeRefreshOnResume(nightModeChanged = false))
    }
}
