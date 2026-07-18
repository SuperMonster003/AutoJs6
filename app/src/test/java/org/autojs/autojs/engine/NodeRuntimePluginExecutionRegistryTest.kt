package org.autojs.autojs.engine

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NodeRuntimePluginExecutionRegistryTest {

    @Test
    fun cancellationBeforeDispatchConsumesOnlyKnownReservation() {
        val registry = NodeRuntimePluginExecutionRegistry<Any>()
        assertSame(
            NodeRuntimePluginExecutionRegistry.Cancellation.Unknown,
            registry.cancel("unknown"),
        )
        assertTrue(registry.reserve("unknown") != null)

        val reservation = registry.reserve("known")!!
        assertSame(
            NodeRuntimePluginExecutionRegistry.Cancellation.BeforeDispatch,
            registry.cancel("known"),
        )
        assertFalse(registry.markDispatched(reservation, Any()))
    }

    @Test
    fun cancellationAfterDispatchCarriesExactSession() {
        val registry = NodeRuntimePluginExecutionRegistry<Any>()
        val reservation = registry.reserve("dispatched")!!
        val session = Any()

        assertTrue(registry.markDispatched(reservation, session))
        val cancellation = registry.cancel("dispatched")

        assertTrue(cancellation is NodeRuntimePluginExecutionRegistry.Cancellation.AfterDispatch<*>)
        assertSame(
            session,
            (cancellation as NodeRuntimePluginExecutionRegistry.Cancellation.AfterDispatch<*>).value,
        )
    }

    @Test
    fun staleReleaseCannotRemoveReplacementReservation() {
        val registry = NodeRuntimePluginExecutionRegistry<Any>()
        val stale = registry.reserve("reused")!!
        registry.release(stale)
        val replacement = registry.reserve("reused")!!

        registry.release(stale)

        assertTrue(registry.isActive(replacement))
        assertNull(registry.reserve("reused"))
    }
}
