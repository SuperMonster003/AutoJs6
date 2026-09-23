package org.autojs.autojs.engine

import org.junit.Assert.*
import org.junit.Test
import java.io.Closeable
import java.util.UUID

class AgentInvocationRegistryTest {
    @Test fun stopBeforeDispatchPreventsClaimAndClosesLateHandle() {
        val registry = AgentInvocationRegistry { 0 }; val id = UUID.randomUUID().toString()
        registry.stop(id)
        assertFalse(registry.claim(id))
        var stopped = 0
        registry.attach(id, Closeable { stopped++ })
        assertEquals(1, stopped)
    }
    @Test fun stopAndDisconnectOnlyCloseTheirOwnedActiveExecutions() {
        val first = AgentInvocationRegistry { 0 }; val second = AgentInvocationRegistry { 0 }
        val id = UUID.randomUUID().toString(); var a = 0; var b = 0
        assertTrue(first.claim(id)); assertTrue(second.claim(id))
        first.attach(id, Closeable { a++ }); second.attach(id, Closeable { b++ })
        first.stop(id); first.stop(id); first.close()
        assertEquals(1, a); assertEquals(0, b)
        second.close(); second.close(); assertEquals(1, b)
    }
    @Test fun completedAndReplayedIdsCannotBeLaunchedTwice() {
        val registry = AgentInvocationRegistry { 0 }; val id = UUID.randomUUID().toString()
        assertTrue(registry.claim(id)); assertFalse(registry.claim(id))
        var stopped = 0; registry.attach(id, Closeable { stopped++ }); registry.finish(id)
        assertFalse(registry.claim(id)); registry.stop(id); registry.close()
        assertEquals(0, stopped)
    }
    @Test fun disconnectBeforeAttachmentClosesTheHandle() {
        val registry = AgentInvocationRegistry { 0 }; val id = UUID.randomUUID().toString()
        assertTrue(registry.claim(id)); registry.close()
        var stopped = 0; registry.attach(id, Closeable { stopped++ }); assertEquals(1, stopped)
    }
    @Test fun cancelledIdsRemainForTheEntireMaximumExecutionDeadline() {
        var now = 0L; val registry = AgentInvocationRegistry { now }; val id = UUID.randomUUID().toString()
        registry.stop(id); now = 300_000; assertFalse(registry.claim(id))
        now = 310_001; assertTrue(registry.claim(id))
    }
    @Test fun invalidIdentifiersAndUnboundedPendingRequestsAreRejected() {
        val registry = AgentInvocationRegistry { 0 }
        assertThrows(IllegalArgumentException::class.java) { registry.stop("42") }
        repeat(1024) { assertTrue(registry.claim(UUID.randomUUID().toString())) }
        assertThrows(IllegalArgumentException::class.java) { registry.claim(UUID.randomUUID().toString()) }
        registry.close()
    }
}
