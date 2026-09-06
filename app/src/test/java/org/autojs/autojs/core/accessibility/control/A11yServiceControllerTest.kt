package org.autojs.autojs.core.accessibility.control

import org.autojs.autojs.core.accessibility.control.A11yServiceController.EnsureResult
import org.autojs.autojs.core.accessibility.control.A11yServiceController.FailureReason
import org.autojs.autojs.core.accessibility.registry.A11yServiceRegistry
import org.autojs.autojs.core.accessibility.registry.A11yServiceRegistry.State
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

class A11yServiceControllerTest {

    private class FakeService(private val name: String) {
        override fun toString() = name
    }

    /**
     * A settings "system": the enabled-services value plus the registry, so that a strategy can
     * simulate the system binding / unbinding the service after a write.
     */
    private inner class System {
        @Volatile
        var enabled: String? = null
        val registry = A11yServiceRegistry<FakeService>()

        @Volatile
        var nextInstance = FakeService("instance-1")
        private var instances = 1

        fun bind() {
            registry.onConnected(nextInstance)
        }

        fun deliverEvent() {
            registry.onEvent(registry.instance ?: nextInstance)
        }

        fun unbind() {
            // Prepare the next instance before the registry reports the unbind: a controller waiting
            // for the unbind on another thread binds again as soon as the registry is unbound.
            nextInstance = FakeService("instance-${++instances}")
            registry.instance?.let { registry.onDisposed(it) }
        }
    }

    private inner class FakeStrategy(
        override val name: String,
        private val available: Boolean = true,
        private val enableResult: Boolean = true,
        private val disableResult: Boolean = true,
        private val bindsOnEnable: Boolean = true,
        private val unbindsOnDisable: Boolean = true,
        private val bindDelayMillis: Long = 0L,
    ) : ServiceStartStrategy {
        val calls = mutableListOf<String>()

        override fun isAvailable() = available

        override fun enable(): Boolean {
            calls += "enable"
            if (!enableResult) return false
            system.enabled = EnabledA11yServices.attach(system.enabled, self)
            if (bindsOnEnable) {
                if (bindDelayMillis > 0L) thread { Thread.sleep(bindDelayMillis); system.bind() } else system.bind()
            }
            return true
        }

        override fun disable(): Boolean {
            calls += "disable"
            if (!disableResult) return false
            system.enabled = EnabledA11yServices.detach(system.enabled, self)
            if (unbindsOnDisable) system.unbind()
            return true
        }
    }

    private val self = "org.autojs.autojs6/.Usher"
    private val system = System()
    private val logs = mutableListOf<String>()
    private var settingsUiLaunches = 0
    private var onSettingsUi: () -> Unit = {}
    private var disableSelfResult = true

    private fun controller(vararg strategies: ServiceStartStrategy, startTimeout: Long = 200L, stopTimeout: Long = 200L) =
        A11yServiceController(
            serviceName = self,
            registry = system.registry,
            enabledServicesReader = { system.enabled },
            strategies = strategies.toList(),
            disableSelf = {
                if (disableSelfResult) system.unbind()
                disableSelfResult
            },
            settingsUi = {
                settingsUiLaunches++
                onSettingsUi()
            },
            startTimeoutMillis = startTimeout,
            stopTimeoutMillis = stopTimeout,
            log = { logs += it },
        )

    private fun enabledAndBound() {
        system.enabled = self
        system.bind()
    }

    /* Quiet recovery (roadmap P5.5). */

    @Test
    fun awaitAvailableIsImmediateWhileBound() {
        enabledAndBound()
        val strategy = FakeStrategy("root")
        assertTrue(controller(strategy).awaitAvailable(0))
        assertTrue(strategy.calls.isEmpty())
        assertEquals(0, settingsUiLaunches)
    }

    @Test
    fun awaitAvailableReEnablesAMalfunctioningServiceThroughTheStrategies() {
        system.enabled = self
        val strategy = FakeStrategy("root")
        val controller = controller(strategy)
        assertTrue(controller.awaitAvailable(200))
        assertTrue(controller.isRunning())
        assertEquals(listOf("enable"), strategy.calls)
        assertEquals(0, settingsUiLaunches)
    }

    @Test
    fun awaitAvailableOnlyWaitsForAServiceThatIsNotEnabled() {
        val strategy = FakeStrategy("root")
        val controller = controller(strategy)
        thread {
            Thread.sleep(50)
            enabledAndBound()
        }
        assertTrue(controller.awaitAvailable(2000))
        assertTrue("a service that is not enabled is not re-enabled behind the user's back (D16)", strategy.calls.isEmpty())
        assertEquals(0, settingsUiLaunches)
    }

    @Test
    fun awaitAvailableGivesUpQuietlyAfterTheTimeout() {
        assertFalse(controller().awaitAvailable(50))
        system.enabled = self
        assertFalse("no strategy can re-enable it", controller().awaitAvailable(50))
        assertEquals(0, settingsUiLaunches)
    }

    /* Predicates. */

    @Test
    fun predicatesCombineSettingsAndRegistry() {
        val controller = controller()
        assertFalse(controller.hasService())
        assertFalse(controller.isRunning())
        assertFalse(controller.isMalfunctioning())
        assertEquals(FailureReason.NOT_ENABLED, controller.failureReason())

        system.enabled = "com.other/.Service:$self"
        assertTrue(controller.hasService())
        assertTrue(controller.isMalfunctioning())
        assertFalse(controller.isRunning())
        assertEquals(listOf("com.other/.Service", self), controller.enabledServices())
        assertEquals(FailureReason.ENABLED_BUT_UNBOUND, controller.failureReason())

        system.bind()
        assertTrue(controller.isRunning())
        assertFalse(controller.isOperational())
        assertFalse(controller.isMalfunctioning())
        assertTrue(controller.isSatisfied(State.CONNECTED))
        assertFalse(controller.isSatisfied(State.OPERATIONAL))
        assertEquals(FailureReason.BOUND_BUT_NOT_OPERATIONAL, controller.failureReason())

        system.deliverEvent()
        assertTrue(controller.isOperational())
        assertTrue(controller.isSatisfied(State.OPERATIONAL))

        system.enabled = null
        assertFalse("an instance without the setting is not running", controller.isRunning())
        assertFalse(controller.isOperational())
    }

    /* start. */

    @Test
    fun startLeavesARunningServiceAlone() {
        enabledAndBound()
        val strategy = FakeStrategy("root")
        assertTrue(controller(strategy).start(launchSettingsIfNeeded = true))
        assertEquals("no strategy touched, no settings page", emptyList<String>(), strategy.calls)
        assertEquals(0, settingsUiLaunches)
    }

    @Test
    fun startUsesTheFirstAvailableStrategyThatSucceeds() {
        val unavailable = FakeStrategy("root", available = false)
        val failing = FakeStrategy("secure settings", enableResult = false)
        val working = FakeStrategy("shizuku")
        assertTrue(controller(unavailable, failing, working).start(launchSettingsIfNeeded = true))
        assertEquals(emptyList<String>(), unavailable.calls)
        assertEquals(listOf("enable"), failing.calls)
        assertEquals(listOf("enable"), working.calls)
        assertTrue(system.enabled!!.contains(self))
        assertEquals(0, settingsUiLaunches)
    }

    @Test
    fun startFallsBackToTheSettingsPageOnlyWhenAsked() {
        val failing = FakeStrategy("root", enableResult = false)
        assertFalse(controller(failing).start(launchSettingsIfNeeded = false))
        assertEquals(0, settingsUiLaunches)
        assertFalse(controller(failing).start(launchSettingsIfNeeded = true))
        assertEquals(1, settingsUiLaunches)
    }

    /* stop. */

    @Test
    fun stopPrefersStrategiesThenDisableSelfThenSettingsPage() {
        enabledAndBound()
        val strategy = FakeStrategy("root")
        assertTrue(controller(strategy).stop(launchSettingsIfNeeded = true))
        assertEquals(listOf("disable"), strategy.calls)
        assertFalse(system.registry.isBound)
        assertEquals(0, settingsUiLaunches)

        enabledAndBound()
        val failing = FakeStrategy("root", disableResult = false)
        // disableSelf unbinds but cannot change the setting; a service still listed is not stopped.
        assertFalse(controller(failing).stop(launchSettingsIfNeeded = true))
        assertEquals(1, settingsUiLaunches)

        system.enabled = null
        system.bind()
        assertTrue("disableSelf counts once the setting no longer lists the service", controller(failing).stop(launchSettingsIfNeeded = true))
        assertEquals(1, settingsUiLaunches)
    }

    /* restart. */

    @Test
    fun restartWaitsForTheUnbindBeforeEnablingAgain() {
        enabledAndBound()
        val before = system.registry.instance
        val events = mutableListOf<String>()
        val strategy = object : ServiceStartStrategy {
            override val name = "root"
            override fun isAvailable() = true
            override fun enable(): Boolean {
                events += "enable(bound=${system.registry.isBound})"
                system.enabled = EnabledA11yServices.attach(system.enabled, self)
                system.bind()
                return true
            }

            override fun disable(): Boolean {
                events += "disable"
                system.enabled = EnabledA11yServices.detach(system.enabled, self)
                // The system unbinds asynchronously, a little later.
                thread { Thread.sleep(80); system.unbind() }
                return true
            }
        }
        assertTrue(controller(strategy, stopTimeout = 1000L).restart(launchSettingsIfNeeded = false))
        assertEquals(listOf("disable", "enable(bound=false)"), events)
        assertTrue(system.registry.isBound)
        assertTrue(system.registry.instance !== before)
    }

    @Test
    fun restartReportsFailureWhenTheStopFailed() {
        enabledAndBound()
        val cannotDisable = FakeStrategy("root", disableResult = false)
        disableSelfResult = false
        assertFalse(controller(cannotDisable).restart(launchSettingsIfNeeded = false))
        assertEquals("the start is still attempted", listOf("disable"), cannotDisable.calls)
    }

    /* ensure. */

    @Test
    fun ensureReturnsImmediatelyWhenSatisfied() {
        enabledAndBound()
        val strategy = FakeStrategy("root")
        assertEquals(EnsureResult.Satisfied(null), controller(strategy).ensure(State.CONNECTED, 10L))
        assertEquals(emptyList<String>(), strategy.calls)

        system.deliverEvent()
        assertEquals(EnsureResult.Satisfied(null), controller(strategy).ensure(State.OPERATIONAL, 10L))
    }

    @Test
    fun ensureBindsThroughAStrategyAndWaitsForTheLevel() {
        val strategy = FakeStrategy("root", bindDelayMillis = 50L)
        val controller = controller(strategy, startTimeout = 1000L)
        assertEquals(EnsureResult.Satisfied("root"), controller.ensure(State.CONNECTED, 1000L))
        assertTrue(controller.isRunning())
        assertEquals(0, settingsUiLaunches)

        // A bound service is not re-enabled; OPERATIONAL only waits for the first event within the caller's timeout.
        thread { Thread.sleep(50); system.deliverEvent() }
        assertEquals(EnsureResult.Satisfied(null), controller.ensure(State.OPERATIONAL, 1000L))
        assertEquals(listOf("enable"), strategy.calls)
    }

    @Test
    fun ensureFailsWithBoundButNotOperationalWhenNoEventArrives() {
        val strategy = FakeStrategy("root")
        val result = controller(strategy, startTimeout = 100L).ensure(State.OPERATIONAL, 100L)
        assertEquals(EnsureResult.Failed(FailureReason.BOUND_BUT_NOT_OPERATIONAL), result)
        assertEquals("no settings page once a strategy bound the service", 0, settingsUiLaunches)
    }

    @Test
    fun ensureMovesOnWhenAStrategyEnablesButTheSystemNeverBinds() {
        val stuck = FakeStrategy("root", bindsOnEnable = false)
        val working = FakeStrategy("secure settings")
        val result = controller(stuck, working, startTimeout = 50L).ensure(State.CONNECTED, 50L)
        assertEquals(EnsureResult.Satisfied("secure settings"), result)
        assertEquals(listOf("enable"), stuck.calls)
        assertEquals(listOf("enable"), working.calls)
    }

    @Test
    fun ensureFallsBackToTheSettingsPageAndWaitsForTheUser() {
        val stuck = FakeStrategy("root", bindsOnEnable = false)
        onSettingsUi = {
            thread {
                Thread.sleep(50)
                system.enabled = self
                system.bind()
            }
        }
        val result = controller(stuck, startTimeout = 30L).ensure(State.CONNECTED, 1000L)
        assertEquals(EnsureResult.Satisfied(A11yServiceController.SETTINGS_UI), result)
        assertEquals(1, settingsUiLaunches)
    }

    @Test
    fun ensureReportsTheReasonWithoutTheSettingsPageWhenNotAllowed() {
        val failing = FakeStrategy("root", enableResult = false)
        assertEquals(EnsureResult.Failed(FailureReason.NOT_ENABLED), controller(failing).ensure(State.CONNECTED, 10L, launchSettingsIfNeeded = false))
        assertEquals(0, settingsUiLaunches)

        system.enabled = self
        assertEquals(EnsureResult.Failed(FailureReason.ENABLED_BUT_UNBOUND), controller(failing).ensure(State.CONNECTED, 10L, launchSettingsIfNeeded = false))
    }

    @Test
    fun ensureHonoursANegativeTimeoutAsWaitForever() {
        val latch = CountDownLatch(1)
        onSettingsUi = { latch.countDown() }
        val controller = controller(startTimeout = 30L)
        val waiter = thread {
            controller.ensure(State.CONNECTED, -1L)
        }
        assertTrue(latch.await(1, TimeUnit.SECONDS))
        Thread.sleep(100)
        assertTrue("still waiting for the user", waiter.isAlive)
        system.enabled = self
        system.bind()
        waiter.join(1000)
        assertFalse(waiter.isAlive)
    }

    @Test
    fun ensureWithForceRestartStopsFirstAndWaitsForTheUnbind() {
        enabledAndBound()
        system.deliverEvent()
        val before = system.registry.instance
        val strategy = FakeStrategy("root")
        val result = controller(strategy).ensure(State.CONNECTED, 200L, forceRestart = true)
        assertEquals(EnsureResult.Satisfied("root"), result)
        assertEquals(listOf("disable", "enable"), strategy.calls)
        assertTrue(system.registry.instance !== before)
        assertFalse("the new instance has not delivered an event yet", system.registry.isOperational)
    }

    /* restoreIfMalfunctioning. */

    @Test
    fun restoreOnlyTouchesAMalfunctioningService() {
        val strategy = FakeStrategy("root")
        val controller = controller(strategy, startTimeout = 30L)

        assertFalse("not enabled: left alone", controller.restoreIfMalfunctioning())
        enabledAndBound()
        assertFalse("running: nothing to do", controller.restoreIfMalfunctioning())
        assertEquals(emptyList<String>(), strategy.calls)

        system.unbind()
        assertTrue(controller.isMalfunctioning())
        assertTrue(controller.restoreIfMalfunctioning())
        assertEquals(listOf("enable"), strategy.calls)
        assertTrue(controller.isRunning())
    }

    @Test
    fun restoreGivesTheSystemAMomentToBindByItself() {
        system.enabled = self
        val strategy = FakeStrategy("root")
        thread { Thread.sleep(50); system.bind() }
        assertFalse(controller(strategy, startTimeout = 1000L).restoreIfMalfunctioning())
        assertEquals("the system bound the service, no re-enable", emptyList<String>(), strategy.calls)
    }

    @Test
    fun waitHelpersDelegateToTheRegistry() {
        val controller = controller()
        assertFalse(controller.waitForStarted(10L))
        thread { Thread.sleep(30); system.bind() }
        assertTrue(controller.waitForStarted(1000L))
        assertFalse(controller.waitForOperational(10L))
        thread { Thread.sleep(30); system.unbind() }
        assertTrue(controller.waitForStopped(1000L))
        assertNull(system.registry.instance)
    }

}
