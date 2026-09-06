package org.autojs.autojs.core.automator.toolkit

import org.autojs.autojs.core.automator.FakeUiNode
import org.autojs.autojs.core.automator.flow.FlowError
import org.autojs.autojs.core.automator.toolkit.AppLauncher.BackOptions
import org.autojs.autojs.core.automator.toolkit.AppLauncher.LaunchOptions
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * App launching and backing out (roadmap P4.1): package resolution, the wait for the package,
 * the already-in-front cases, and the back loops.
 * zh-CN: 应用启动与返回 (Roadmap P4.1): 包名解析, 等待包名切换, 已在前台的情况, 以及返回循环.
 */
class AppLauncherTest {

    private val rig = ToolkitRig()
    private val kit = rig.kit
    private val actions = rig.actions
    private val b = rig.bindings

    private val settings = "com.android.settings"

    init {
        b.packages["Settings"] = settings
        b.currentPackage = "com.example.launcher"
    }

    private fun error(block: () -> Unit): FlowError = assertThrows(FlowError::class.java) { block() }

    @Test
    fun launchAndWaitLaunchesAndWaitsForThePackage() {
        var polls = 0
        b.packageSupplier = { if (++polls >= 3) settings else "com.example.launcher" }
        val launched = kit.apps.launchAndWait("Settings")
        assertEquals(settings, launched.packageName)
        assertTrue(launched.launched)
        assertEquals(listOf("launch($settings)"), actions.calls)
        assertEquals(listOf(200L, 200L), rig.sleeps)
        assertEquals(400, launched.elapsed)
        assertEquals(3, launched.attempts)
    }

    @Test
    fun alreadyInFrontWithoutBringToFrontDoesNothing() {
        b.currentPackage = settings
        val launched = kit.apps.launchAndWait("Settings", LaunchOptions(bringToFront = false))
        assertFalse(launched.launched)
        assertEquals(settings, launched.packageName)
        assertTrue(actions.calls.isEmpty())
    }

    @Test
    fun alreadyInFrontWithBringToFrontLaunchesAgain() {
        b.currentPackage = settings
        val launched = kit.apps.launchAndWait("Settings")
        assertTrue(launched.launched)
        assertEquals(1, launched.attempts)
        assertEquals(listOf("launch($settings)"), actions.calls)
    }

    @Test
    fun unknownAppIsInvalidTarget() {
        val e = error { kit.apps.launchAndWait("Nowhere") }
        assertEquals(FlowError.Code.INVALID_TARGET, e.code)
        assertEquals(AppLauncher.REASON_UNKNOWN_APP, e.reason)
        assertEquals(AppLauncher.STEP_LAUNCH, e.step)
        assertEquals("text(\"Nowhere\")", e.selector)
    }

    @Test
    fun refusedLaunchIsActionFailed() {
        actions.launchResult = { false }
        val e = error { kit.apps.launchAndWait("Settings") }
        assertEquals(FlowError.Code.ACTION_FAILED, e.code)
        assertEquals(AppLauncher.REASON_LAUNCH, e.reason)
        assertEquals(settings, e.selector)
    }

    @Test
    fun packageNeverInFrontTimesOut() {
        val e = error { kit.apps.launchAndWait("Settings", LaunchOptions(timeout = 1000, interval = 250)) }
        assertEquals(FlowError.Code.TIMEOUT, e.code)
        assertEquals(AppLauncher.REASON_PACKAGE, e.reason)
        assertEquals(settings, e.selector)
        assertEquals(1250, e.elapsed)
        assertEquals(6, e.attempts)
    }

    @Test
    fun backUntilPressesBackUntilTheConditionHolds() {
        val home = FakeUiNode.root("home")
        var looks = 0
        b.show("home") { if (++looks >= 3) home else null }
        val outcome = kit.apps.backUntil("home")
        assertSame(home, outcome.value)
        assertEquals(2, outcome.presses)
        assertEquals(listOf("back", "back"), actions.calls)
        assertEquals(listOf(500L, 500L), rig.sleeps)
    }

    @Test
    fun backUntilChecksBeforeTheFirstPress() {
        b.show("home", FakeUiNode.root("home"))
        assertEquals(0, kit.apps.backUntil("home").presses)
        assertTrue(actions.calls.isEmpty())
    }

    @Test
    fun backUntilAcceptsAPredicate() {
        var presses = 0
        actions.backResult = {
            presses += 1
            true
        }
        val outcome = kit.apps.backUntil({ if (presses >= 2) "arrived" else null }, BackOptions(delay = 0))
        assertEquals("arrived", outcome.value)
        assertEquals(2, outcome.presses)
    }

    @Test
    fun backUntilGivesUpAfterMaxTimes() {
        val e = error { kit.apps.backUntil("home", BackOptions(maxTimes = 3, delay = 100)) }
        assertEquals(FlowError.Code.TIMEOUT, e.code)
        assertEquals(AppLauncher.REASON_MAX_TIMES, e.reason)
        assertEquals(AppLauncher.STEP_BACK, e.step)
        assertEquals("text(\"home\")", e.selector)
        assertEquals(3, actions.calls("back"))
        assertEquals(4, e.attempts)
        assertEquals(300, e.elapsed)
    }

    @Test
    fun refusedBackIsActionFailed() {
        actions.backResult = { false }
        val e = error { kit.apps.backUntil("home") }
        assertEquals(FlowError.Code.ACTION_FAILED, e.code)
        assertEquals(AppLauncher.REASON_BACK, e.reason)
    }

    @Test
    fun backToAppWatchesTheCurrentPackage() {
        var presses = 0
        actions.backResult = {
            presses += 1
            true
        }
        b.packageSupplier = { if (presses >= 1) settings else "com.example.other" }
        val outcome = kit.apps.backToApp("Settings", BackOptions(delay = 0, humanize = Humanize(minDelay = 10, maxDelay = 10)))
        assertEquals(settings, outcome.value)
        assertEquals(1, outcome.presses)
        assertEquals(listOf(10L), rig.sleeps)
    }

    @Test
    fun backToAppNeedsAKnownApp() {
        val e = error { kit.apps.backToApp("Nowhere") }
        assertEquals(AppLauncher.REASON_UNKNOWN_APP, e.reason)
        assertEquals(AppLauncher.STEP_BACK_TO_APP, e.step)
    }

    @Test
    fun optionsAreValidated() {
        assertThrows(IllegalArgumentException::class.java) { LaunchOptions(timeout = -1) }
        assertThrows(IllegalArgumentException::class.java) { BackOptions(maxTimes = -1) }
        assertThrows(IllegalArgumentException::class.java) { BackOptions(delay = -1) }
    }

}
