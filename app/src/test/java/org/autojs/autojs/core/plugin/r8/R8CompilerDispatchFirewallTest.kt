package org.autojs.autojs.core.plugin.r8

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.Executors

class R8CompilerDispatchFirewallTest {
    @Test
    fun everyPreAndPostDispatchFailurePropagatesWithoutSemanticFallback() {
        R8CompilerAttemptPhase.values().filter { it != R8CompilerAttemptPhase.COMPLETED }.forEach { target ->
            val firewall = firewallAt(target)
            val directive = firewall.failed()
            assertTrue(directive.propagateAsR8Failure)
            assertFalse(directive.semanticFallbackAllowed)
            assertEquals(target, directive.phase)
            assertEquals(R8CompilerAttemptPhase.FAILED, firewall.currentPhase)
        }
    }

    @Test
    fun successfulAttemptRequiresTheExactMonotonicChain() {
        val firewall = R8CompilerDispatchFirewall()
        firewall.negotiated()
        firewall.dispatched()
        firewall.completed()
        assertEquals(R8CompilerAttemptPhase.COMPLETED, firewall.currentPhase)
    }

    @Test
    fun concurrentTerminalFailuresStillConvergeOnOneFailClosedState() {
        val firewall = R8CompilerDispatchFirewall().apply {
            negotiated()
            dispatched()
        }
        val pool = Executors.newFixedThreadPool(8)
        try {
            val decisions = (0 until 32).map { pool.submit<R8CompilerFailureDirective> { firewall.failed() } }
                .map { it.get() }
            assertTrue(decisions.all { it.propagateAsR8Failure && !it.semanticFallbackAllowed })
            assertEquals(R8CompilerAttemptPhase.FAILED, firewall.currentPhase)
        } finally {
            pool.shutdownNow()
        }
    }

    private fun firewallAt(phase: R8CompilerAttemptPhase): R8CompilerDispatchFirewall =
        R8CompilerDispatchFirewall().apply {
            if (phase >= R8CompilerAttemptPhase.NEGOTIATED) negotiated()
            if (phase >= R8CompilerAttemptPhase.DISPATCHED) dispatched()
            if (phase == R8CompilerAttemptPhase.FAILED) failed()
        }
}
