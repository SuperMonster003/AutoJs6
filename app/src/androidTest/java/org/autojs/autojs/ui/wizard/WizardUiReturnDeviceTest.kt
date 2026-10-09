package org.autojs.autojs.ui.wizard

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class WizardUiReturnDeviceTest {

    private class Owner(initialState: Lifecycle.State) : LifecycleOwner {
        override val lifecycle = LifecycleRegistry(this).apply { currentState = initialState }
    }

    @Test fun installerLaunchedWhileHostIsAlreadyStoppedStillDetectsReturn() = runBlocking {
        withContext(Dispatchers.Main) {
            for (state in listOf(Lifecycle.State.CREATED, Lifecycle.State.STARTED)) {
                val owner = Owner(state)
                val result = async(start = CoroutineStart.UNDISPATCHED) {
                    WizardUi.launchAndAwaitReturn(owner.lifecycle, 100) { true }
                }
                assertFalse(result.isCompleted)
                owner.lifecycle.currentState = Lifecycle.State.RESUMED
                assertEquals(WizardUi.ReturnResult.RETURNED, withTimeout(1000) { result.await() })
                assertEquals(0, owner.lifecycle.observerCount)
            }
        }
    }

    @Test fun resumedHostWaitsForPauseAndReturnOnEveryInstall() = runBlocking {
        withContext(Dispatchers.Main) {
            val owner = Owner(Lifecycle.State.RESUMED)
            repeat(2) {
                val result = async(start = CoroutineStart.UNDISPATCHED) {
                    WizardUi.launchAndAwaitReturn(owner.lifecycle, 100) {
                        owner.lifecycle.currentState = Lifecycle.State.CREATED
                        true
                    }
                }
                assertFalse(result.isCompleted)
                owner.lifecycle.currentState = Lifecycle.State.RESUMED
                assertEquals(WizardUi.ReturnResult.RETURNED, withTimeout(1000) { result.await() })
                assertEquals(0, owner.lifecycle.observerCount)
            }
        }
    }

    @Test fun launchFailureAndLeaveTimeoutRemoveTheirObservers() = runBlocking {
        withContext(Dispatchers.Main) {
            val owner = Owner(Lifecycle.State.RESUMED)
            assertEquals(WizardUi.ReturnResult.LAUNCH_FAILED, WizardUi.launchAndAwaitReturn(owner.lifecycle, 50) { false })
            assertEquals(0, owner.lifecycle.observerCount)
            assertEquals(WizardUi.ReturnResult.NOT_LEFT, WizardUi.launchAndAwaitReturn(owner.lifecycle, 50) { true })
            assertEquals(0, owner.lifecycle.observerCount)
        }
    }

    @Test fun destroyingOrCancellingAWaitRemovesItsObserver() = runBlocking {
        withContext(Dispatchers.Main) {
            coroutineScope {
                val owner = Owner(Lifecycle.State.CREATED)
                val result = async(start = CoroutineStart.UNDISPATCHED) {
                    WizardUi.launchAndAwaitReturn(owner.lifecycle, 100) { true }
                }
                owner.lifecycle.currentState = Lifecycle.State.DESTROYED
                assertEquals(WizardUi.ReturnResult.DESTROYED, withTimeout(1000) { result.await() })
                assertEquals(0, owner.lifecycle.observerCount)
            }
            coroutineScope {
                val owner = Owner(Lifecycle.State.CREATED)
                val result = async(start = CoroutineStart.UNDISPATCHED) {
                    WizardUi.launchAndAwaitReturn(owner.lifecycle, 100) { true }
                }
                result.cancelAndJoin()
                assertEquals(0, owner.lifecycle.observerCount)
            }
        }
    }
}
