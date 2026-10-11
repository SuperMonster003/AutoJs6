package org.autojs.autojs.core.plugin.center

import org.autojs.autojs.core.plugin.center.PluginEnableSuspension.State
import org.junit.Assert.assertEquals
import org.junit.Test

class PluginEnableSuspensionTest {

    private val enabled = State(enabled = true, suspended = false)
    private val disabledByUser = State(enabled = false, suspended = false)
    private val suspended = State(enabled = false, suspended = true)

    @Test
    fun aTransientFailureSuspendsAnEnabledPluginUntilItIsReachableAgain() {
        val afterFailure = PluginEnableSuspension.onUnreachable(enabled)
        assertEquals(suspended, afterFailure)
        // Repeated failures keep it suspended; a refusal by policy keeps it off for now.
        assertEquals(suspended, PluginEnableSuspension.onUnreachable(afterFailure))
        assertEquals(suspended, PluginEnableSuspension.onReachable(afterFailure, usable = false))
        assertEquals(enabled, PluginEnableSuspension.onReachable(afterFailure, usable = true))
    }

    @Test
    fun theUsersOwnChoiceIsNeverRestoredOrSuspended() {
        assertEquals(disabledByUser, PluginEnableSuspension.onUnreachable(disabledByUser))
        assertEquals(disabledByUser, PluginEnableSuspension.onReachable(disabledByUser, usable = true))
        assertEquals(enabled, PluginEnableSuspension.onReachable(enabled, usable = true))
    }

    @Test
    fun aDecisionReplacesTheStateAndClearsTheSuspension() {
        // Switching a suspended plugin off by hand must stick, even when it becomes reachable again.
        val switchedOff = PluginEnableSuspension.onDecision(false)
        assertEquals(disabledByUser, switchedOff)
        assertEquals(disabledByUser, PluginEnableSuspension.onReachable(switchedOff, usable = true))
        assertEquals(enabled, PluginEnableSuspension.onDecision(true))
    }
}
