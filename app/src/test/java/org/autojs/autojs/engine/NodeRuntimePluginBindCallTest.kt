package org.autojs.autojs.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.util.concurrent.atomic.AtomicInteger

class NodeRuntimePluginBindCallTest {

    @Test
    fun refusedBindIsReleasedExactlyOnce() {
        val releaseCount = AtomicInteger()

        val outcome = NodeRuntimePluginBindCall.attempt(bind = { false }, release = { releaseCount.incrementAndGet() })

        assertSame(NodeRuntimePluginBindCall.Outcome.Refused, outcome)
        assertEquals(1, releaseCount.get())
    }

    @Test
    fun deniedBindIsReleasedExactlyOnceAndKeepsItsError() {
        val releaseCount = AtomicInteger()
        val error = SecurityException("expected")

        val outcome = NodeRuntimePluginBindCall.attempt(bind = { throw error }, release = { releaseCount.incrementAndGet() })

        assertTrue(outcome is NodeRuntimePluginBindCall.Outcome.Denied)
        assertSame(error, (outcome as NodeRuntimePluginBindCall.Outcome.Denied).error)
        assertEquals(1, releaseCount.get())
    }

    @Test
    fun acceptedBindStaysRegisteredForItsOwner() {
        val releaseCount = AtomicInteger()

        val outcome = NodeRuntimePluginBindCall.attempt(bind = { true }, release = { releaseCount.incrementAndGet() })

        assertSame(NodeRuntimePluginBindCall.Outcome.Bound, outcome)
        assertEquals(0, releaseCount.get())
    }

    @Test
    fun unexpectedBindFailureIsReleasedAndThrownAgain() {
        val releaseCount = AtomicInteger()
        val error = IllegalStateException("expected")

        try {
            NodeRuntimePluginBindCall.attempt(bind = { throw error }, release = { releaseCount.incrementAndGet() })
            fail("The bind failure was not thrown again")
        } catch (thrown: IllegalStateException) {
            assertSame(error, thrown)
        }
        assertEquals(1, releaseCount.get())
    }

    @Test
    fun repeatedRefusalsLeaveNoRegisteredConnection() {
        // Models LoadedApk.mServices: bindService registers the connection before the system refuses it.
        val registered = LinkedHashSet<Any>()
        var accepted: Any? = null

        repeat(50) { attempt ->
            val connection = Any()
            val refuse = attempt < 49
            NodeRuntimePluginBindCall.attempt(
                bind = {
                    registered += connection
                    if (attempt % 2 == 0 && refuse) throw SecurityException("expected")
                    !refuse
                },
                release = { registered -= connection },
            )
            if (!refuse) accepted = connection
        }

        assertEquals(listOf(accepted), registered.toList())
    }
}
