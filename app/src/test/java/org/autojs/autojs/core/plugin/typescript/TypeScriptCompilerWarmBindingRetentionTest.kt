package org.autojs.autojs.core.plugin.typescript

import java.io.Closeable
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TypeScriptCompilerWarmBindingRetentionTest {

    @Test
    fun reusesOneLiveBindingAndClosesItOnRelease() = runBlocking {
        val retention = retention()
        val binding = FakeBinding()

        val first = retention.retain { binding }
        val second = retention.retain { error("A live binding must be reused") }

        assertTrue(first.acquired)
        assertSame(binding, first.value)
        assertFalse(second.acquired)
        assertSame(binding, second.value)
        retention.release()
        assertTrue(binding.closed)
    }

    @Test
    fun replacesAndClosesADeadBinding() = runBlocking {
        val retention = retention()
        val first = FakeBinding()
        val replacement = FakeBinding()

        retention.retain { first }
        first.alive = false
        val result = retention.retain { replacement }

        assertTrue(first.closed)
        assertTrue(result.acquired)
        assertSame(replacement, result.value)
        retention.release()
        assertTrue(replacement.closed)
    }

    @Test
    fun releaseFencesAndClosesAnInFlightAcquisition() = runBlocking {
        val retention = retention()
        val acquisitionStarted = CompletableDeferred<Unit>()
        val continueAcquisition = CompletableDeferred<Unit>()
        val candidate = FakeBinding()
        val result = async(Dispatchers.Default) {
            retention.retain {
                acquisitionStarted.complete(Unit)
                continueAcquisition.await()
                candidate
            }
        }

        acquisitionStarted.await()
        retention.release()
        continueAcquisition.complete(Unit)

        assertNull(result.await().value)
        assertTrue(candidate.closed)
    }

    @Test
    fun releaseAlsoFencesARequestBeforeItsAcquisitionStarts() = runBlocking {
        val retention = retention()
        val requestGeneration = retention.generationToken()
        var acquisitionInvoked = false

        retention.release()
        val result = retention.retain(requestGeneration) {
            acquisitionInvoked = true
            FakeBinding()
        }

        assertFalse(acquisitionInvoked)
        assertNull(result.value)
    }

    @Test
    fun offeredBindingWinsAgainstAnInFlightAcquisition() = runBlocking {
        val retention = retention()
        val acquisitionStarted = CompletableDeferred<Unit>()
        val continueAcquisition = CompletableDeferred<Unit>()
        val acquiredCandidate = FakeBinding()
        val offered = FakeBinding()
        val result = async(Dispatchers.Default) {
            retention.retain {
                acquisitionStarted.complete(Unit)
                continueAcquisition.await()
                acquiredCandidate
            }
        }

        acquisitionStarted.await()
        assertTrue(retention.offer(offered))
        continueAcquisition.complete(Unit)

        val completed = result.await()
        assertFalse(completed.acquired)
        assertSame(offered, completed.value)
        assertTrue(acquiredCandidate.closed)
        assertFalse(offered.closed)
        retention.release()
        assertTrue(offered.closed)
    }

    @Test
    fun releaseFencesACompilationBindingOfferedWithAnOlderGeneration() {
        val retention = retention()
        val compilationGeneration = retention.generationToken()
        val candidate = FakeBinding()

        retention.release()

        assertFalse(retention.offer(candidate, compilationGeneration))
        assertTrue(candidate.closed)
    }

    private fun retention() = TypeScriptCompilerWarmBindingRetention<FakeBinding> { it.alive }

    private class FakeBinding : Closeable {
        var alive = true
        var closed = false

        override fun close() {
            closed = true
            alive = false
        }
    }
}
