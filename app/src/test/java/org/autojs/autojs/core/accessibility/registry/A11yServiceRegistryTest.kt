package org.autojs.autojs.core.accessibility.registry

import org.autojs.autojs.core.accessibility.AccessibilityServiceCallback
import org.autojs.autojs.core.accessibility.registry.A11yServiceRegistry.Snapshot
import org.autojs.autojs.core.accessibility.registry.A11yServiceRegistry.State
import org.autojs.autojs.core.accessibility.registry.A11yServiceRegistry.StateListener
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class A11yServiceRegistryTest {

    private class FakeService(private val name: String) {
        override fun toString() = name
    }

    private class Recorder<S : Any> : StateListener<S> {
        val transitions = mutableListOf<Pair<Snapshot<S>, Snapshot<S>>>()
        override fun onStateChanged(previous: Snapshot<S>, current: Snapshot<S>) {
            transitions += previous to current
        }
    }

    private val errors = mutableListOf<Throwable>()
    private val registry = A11yServiceRegistry<FakeService> { _, error -> errors += error }
    private val recorder = Recorder<FakeService>().also { registry.addListener("recorder", it) }
    private val service = FakeService("service")

    @Test
    fun startsUnbound() {
        assertEquals(State.UNBOUND, registry.state)
        assertNull(registry.instance)
        assertFalse(registry.isBound)
        assertFalse(registry.isOperational)
    }

    @Test
    fun connectThenEventReachesOperational() {
        registry.onConnected(service)
        assertEquals(State.CONNECTED, registry.state)
        assertSame(service, registry.instance)
        assertTrue(registry.isBound)
        assertFalse(registry.isOperational)

        registry.onEvent(service)
        assertEquals(State.OPERATIONAL, registry.state)
        assertTrue(registry.isOperational)

        registry.onEvent(service)
        assertEquals("repeated events do not produce transitions", 2, recorder.transitions.size)
        assertEquals(State.UNBOUND to State.CONNECTED, recorder.transitions[0].states())
        assertEquals(State.CONNECTED to State.OPERATIONAL, recorder.transitions[1].states())
    }

    @Test
    fun eventWithoutConnectAdoptsTheInstance() {
        registry.onEvent(service)

        assertEquals(State.OPERATIONAL, registry.state)
        assertSame(service, registry.instance)
        assertEquals(listOf(State.UNBOUND to State.OPERATIONAL), recorder.transitions.map { it.states() })
    }

    @Test
    fun reconnectResetsOperationalFlag() {
        registry.onEvent(service)
        registry.onConnected(service)

        assertEquals(State.CONNECTED, registry.state)
        assertSame(service, registry.instance)
    }

    @Test
    fun anotherInstanceReplacesTheBoundOne() {
        val replacement = FakeService("replacement")
        registry.onEvent(service)
        registry.onConnected(replacement)

        assertSame(replacement, registry.instance)
        assertEquals(State.CONNECTED, registry.state)
        assertFalse("disposing the stale instance changes nothing", registry.onDisposed(service))
        assertSame(replacement, registry.instance)
    }

    @Test
    fun disposingTheBoundInstanceUnbinds() {
        registry.onEvent(service)

        assertTrue(registry.onDisposed(service))
        assertEquals(State.UNBOUND, registry.state)
        assertNull(registry.instance)
        assertFalse("already unbound", registry.onDisposed(service))
        assertEquals(2, recorder.transitions.size)
    }

    @Test
    fun operationalFlagCanBeToggledOnlyWhileBound() {
        registry.markOperational()
        assertEquals(State.UNBOUND, registry.state)

        registry.onConnected(service)
        registry.markOperational()
        assertEquals(State.OPERATIONAL, registry.state)

        registry.resetOperational()
        assertEquals(State.CONNECTED, registry.state)
        assertSame(service, registry.instance)
    }

    @Test
    fun awaitReturnsImmediatelyWhenSatisfiedAndTimesOutOtherwise() {
        assertFalse(registry.await(State.CONNECTED, 20))

        registry.onConnected(service)
        assertTrue(registry.await(State.CONNECTED, 0))
        assertFalse(registry.await(State.OPERATIONAL, 20))

        registry.onEvent(service)
        assertTrue(registry.await(State.OPERATIONAL, 0))
        assertTrue(registry.await(State.CONNECTED, 0))
    }

    @Test
    fun awaitWakesUpOnTransitionFromAnotherThread() {
        val started = CountDownLatch(1)
        var result = false
        val waiter = thread {
            started.countDown()
            result = registry.await(State.OPERATIONAL, 5000)
        }
        started.await(1, TimeUnit.SECONDS)
        Thread.sleep(50)
        registry.onEvent(service)
        waiter.join(5000)

        assertFalse(waiter.isAlive)
        assertTrue(result)
    }

    @Test
    fun negativeTimeoutWaitsUntilSignalled() {
        var result = false
        val waiter = thread { result = registry.await(State.CONNECTED, -1) }
        Thread.sleep(50)
        assertTrue(waiter.isAlive)
        registry.onConnected(service)
        waiter.join(5000)

        assertFalse(waiter.isAlive)
        assertTrue(result)
    }

    @Test
    fun awaitUnboundReturnsOnceTheInstanceIsGone() {
        assertTrue("nothing bound yet", registry.awaitUnbound(0))

        registry.onEvent(service)
        assertFalse(registry.awaitUnbound(20))

        var result = false
        val waiter = thread { result = registry.awaitUnbound(5000) }
        Thread.sleep(50)
        registry.onDisposed(FakeService("stale"))
        assertTrue("disposing a stale instance does not wake the waiter", waiter.isAlive)
        registry.onDisposed(service)
        waiter.join(5000)

        assertFalse(waiter.isAlive)
        assertTrue(result)
    }

    @Test
    fun interruptedAwaitReturnsFalseAndKeepsTheInterruptFlag() {
        var result = true
        var interrupted = false
        val waiter = thread {
            result = registry.await(State.CONNECTED, 5000)
            interrupted = Thread.currentThread().isInterrupted
        }
        Thread.sleep(50)
        waiter.interrupt()
        waiter.join(5000)

        assertFalse(waiter.isAlive)
        assertFalse(result)
        assertTrue(interrupted)
    }

    @Test
    fun listenersAreKeyedByOwnerAndIsolatedFromEachOther() {
        val first = Recorder<FakeService>()
        val second = Recorder<FakeService>()
        registry.addListener("owner", first)
        registry.addListener("owner", second)
        registry.addListener("thrower", StateListener { _, _ -> error("boom") })
        val tail = Recorder<FakeService>().also { registry.addListener("tail", it) }

        registry.onConnected(service)

        assertTrue("replaced listener is not invoked", first.transitions.isEmpty())
        assertEquals(1, second.transitions.size)
        assertEquals("listener after the throwing one still runs", 1, tail.transitions.size)
        assertEquals(1, errors.size)

        assertTrue(registry.removeListener("owner"))
        assertFalse(registry.removeListener("owner"))
        assertFalse(registry.hasListener("owner"))
        registry.onDisposed(service)
        assertEquals(1, second.transitions.size)
    }

    @Test
    fun connectionCallbackAdapterMapsBindingChanges() {
        val log = mutableListOf<String>()
        val callback = object : AccessibilityServiceCallback {
            override fun onConnected() { log += "connected" }
            override fun onDisconnected() { log += "disconnected" }
        }
        registry.addListener(callback, ConnectionListeners.connectionCallback(callback))

        registry.onEvent(service)
        registry.onConnected(service)
        registry.onEvent(service)
        assertEquals("adopting from an event counts as connecting once", listOf("connected"), log)

        registry.onConnected(FakeService("replacement"))
        assertEquals(listOf("connected", "connected"), log)

        registry.onDisposed(service)
        assertEquals("disposing the stale instance is silent", listOf("connected", "connected"), log)

        registry.onDisposed(registry.instance!!)
        assertEquals(listOf("connected", "connected", "disconnected"), log)
    }

    @Test
    fun disconnectCallbackAdapterOnlyForwardsUnbinding() {
        val log = mutableListOf<String>()
        val callback = object : AccessibilityServiceCallback {
            override fun onConnected() { log += "connected" }
            override fun onDisconnected() { log += "disconnected" }
        }
        registry.addListener(callback, ConnectionListeners.disconnectCallback(callback))

        registry.onConnected(service)
        registry.onEvent(service)
        registry.onDisposed(service)

        assertEquals(listOf("disconnected"), log)
        assertTrue(ConnectionListeners.isBindingChanged(Snapshot(State.UNBOUND, null), Snapshot(State.CONNECTED, service)))
        assertFalse(ConnectionListeners.isBindingChanged(Snapshot(State.CONNECTED, service), Snapshot(State.OPERATIONAL, service)))
    }

    @Test
    fun adoptionFromAnEventIsFlaggedUntilTheCallbackArrives() {
        assertFalse(registry.adoptedByEvent)
        registry.onEvent(service)
        assertTrue("adopted without onServiceConnected", registry.adoptedByEvent)
        registry.onEvent(service)
        assertTrue("further events keep the flag", registry.adoptedByEvent)
        registry.resetOperational()
        registry.markOperational()
        assertTrue("the operational toggles keep the flag", registry.adoptedByEvent)

        registry.onConnected(service)
        assertFalse("the late callback clears the flag", registry.adoptedByEvent)
        registry.onEvent(service)
        assertFalse("events after the callback do not set it", registry.adoptedByEvent)
    }

    @Test
    fun theAdoptionFlagFollowsTheInstance() {
        registry.onConnected(service)
        registry.onEvent(service)
        assertFalse(registry.adoptedByEvent)

        val replacement = FakeService("replacement")
        registry.onEvent(replacement)
        assertSame(replacement, registry.instance)
        assertTrue("a replacement adopted from an event is flagged", registry.adoptedByEvent)

        assertTrue(registry.onDisposed(replacement))
        assertFalse("unbinding clears the flag", registry.adoptedByEvent)
    }

    @Test
    fun listenersAreCountedAndRemovedByOwner() {
        val before = registry.listenerCount
        registry.addListener("runtime@1", Recorder())
        registry.addListener("bridge@1", Recorder())
        registry.addListener("runtime@2", Recorder())
        val owned: (Any) -> Boolean = { it == "runtime@1" || it == "bridge@1" }

        assertEquals(2, registry.countListeners(owned))
        assertEquals(2, registry.removeListenersIf(owned))
        assertEquals(0, registry.countListeners(owned))
        assertEquals(before + 1, registry.listenerCount)
        assertTrue(registry.hasListener("runtime@2"))
    }

    private fun Pair<Snapshot<FakeService>, Snapshot<FakeService>>.states() = first.state to second.state

}
