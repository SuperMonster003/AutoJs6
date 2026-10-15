package org.autojs.autojs.core.plugin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginWakePolicyTest {

    private var now = 10_000L
    private val policy = PluginWakePolicy(backoffMillis = 60_000L, clock = { now })

    @Test
    fun wakesThatHelpedNeverHoldBackTheNextWake() {
        assertTrue(policy.mayWake("plugin.a"))
        policy.onWakeHelped("plugin.a")
        assertTrue(policy.mayWake("plugin.a"))
    }

    @Test
    fun aWakeThatDidNotHelpBacksOffThatPackageOnly() {
        policy.onWakeFailed("plugin.a")
        assertFalse(policy.mayWake("plugin.a"))
        assertTrue(policy.mayWake("plugin.b"))
        now += 59_999L
        assertFalse(policy.mayWake("plugin.a"))
        now += 1L
        assertTrue(policy.mayWake("plugin.a"))
    }

    @Test
    fun aWakeThatHelpedClearsAnEarlierBackoff() {
        policy.onWakeFailed("plugin.a")
        policy.onWakeHelped("plugin.a")
        assertTrue(policy.mayWake("plugin.a"))
    }

    @Test
    fun theRetrySchedulePausesAtMostThreeSeconds() {
        assertEquals(300L, PluginWakePolicy.RETRY_DELAYS_MS.first())
        assertTrue(PluginWakePolicy.RETRY_DELAYS_MS.sum() <= 3_000L)
    }

    @Test
    fun refusedBindingsAreRecognizedThroughTheirCauses() {
        val refused = PluginBindRefusedException("plugin.a", "bindService failed: plugin.a/.Service")
        assertTrue(refused.isPluginBindRefused())
        assertTrue(IllegalStateException("Node.js runtime unavailable", refused).isPluginBindRefused())
        assertFalse(IllegalStateException("bindService timeout: plugin.a/.Service").isPluginBindRefused())
        // The wording stays what the Plugin Center maps to BIND_FAILED. zh-CN: 措辞保持插件中心映射为 BIND_FAILED 的形式.
        assertTrue(refused.message!!.startsWith("bindService failed"))
    }
}
