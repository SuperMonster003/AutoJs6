package org.autojs.autojs.theme

import org.autojs.autojs.theme.ThemeColorApplyPolicy.Action
import org.autojs.autojs.theme.ThemeColorSource.ACTIVATION
import org.autojs.autojs.theme.ThemeColorSource.INTELLIGENT
import org.autojs.autojs.theme.ThemeColorSource.RESTORE
import org.autojs.autojs.theme.ThemeColorSource.SCRIPT
import org.autojs.autojs.theme.ThemeColorSource.USER
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeColorApplyPolicyTest {

    private val policy = ThemeColorApplyPolicy<Int>(scriptWindowMillis = 500)

    @Test
    fun userAndRestoreApplyImmediatelyEvenWhilePaused() {
        policy.pause("page")
        assertEquals(Action.Apply(1, USER), policy.request(1, USER, 0))
        assertEquals(Action.Apply(2, RESTORE), policy.request(2, RESTORE, 0))
    }

    @Test
    fun activationAppliesWhilePausedAndKeepsTheLibraryActive() {
        policy.request(1, SCRIPT, 0)
        policy.pause("page")
        assertEquals(Action.Apply(2, ACTIVATION), policy.request(2, ACTIVATION, 100))
        assertNull(policy.pendingScript)
        assertFalse(ThemeColorApplyPolicy.deactivatesIntelligent(ACTIVATION))
    }

    @Test
    fun intelligentChangesAreDroppedWhilePaused() {
        assertEquals(Action.Apply(1, INTELLIGENT), policy.request(1, INTELLIGENT, 0))
        policy.pause("page")
        assertEquals(Action.None, policy.request(2, INTELLIGENT, 0))
        assertTrue(policy.resume("page"))
        assertEquals(Action.Apply(3, INTELLIGENT), policy.request(3, INTELLIGENT, 0))
    }

    @Test
    fun onlyTheLastReleasedTokenResumes() {
        val first = Any()
        val second = Any()
        policy.pause(first)
        policy.pause(second)
        policy.pause(second)
        assertFalse(policy.resume(first))
        assertTrue(policy.isPaused)
        assertTrue(policy.resume(second))
        assertFalse(policy.isPaused)
        assertFalse(policy.resume(second))
        assertFalse(policy.resume(Any()))
    }

    @Test
    fun scriptRequestsInsideAWindowCollapseIntoTheLast() {
        assertEquals(Action.ScheduleFlush(500), policy.request(1, SCRIPT, 0))
        for (value in 2..100) {
            assertEquals(Action.None, policy.request(value, SCRIPT, value.toLong()))
        }
        assertEquals(100, policy.pendingScript)
        assertEquals(Action.Apply(100, SCRIPT), policy.flush(500))
        assertNull(policy.pendingScript)
        assertEquals(Action.None, policy.flush(600))
    }

    @Test
    fun slowScriptLoopsGetOneChangePerWindow() {
        assertEquals(Action.ScheduleFlush(500), policy.request(1, SCRIPT, 0))
        assertEquals(Action.None, policy.request(2, SCRIPT, 400))
        assertEquals(Action.Apply(2, SCRIPT), policy.flush(500))
        assertEquals(Action.ScheduleFlush(1300), policy.request(3, SCRIPT, 800))
        assertEquals(Action.Apply(3, SCRIPT), policy.flush(1300))
    }

    @Test
    fun scriptChangesAreNotHeldByPauseTokens() {
        policy.pause("page")
        assertEquals(Action.ScheduleFlush(500), policy.request(1, SCRIPT, 0))
        assertEquals(Action.Apply(1, SCRIPT), policy.flush(500))
    }

    @Test
    fun aUserChoiceDiscardsThePendingScriptChange() {
        policy.request(1, SCRIPT, 0)
        assertEquals(Action.Apply(9, USER), policy.request(9, USER, 100))
        assertNull(policy.pendingScript)
        assertEquals(Action.None, policy.flush(500))
    }

    @Test
    fun staleOrEarlyFlushesAreIgnored() {
        policy.request(1, SCRIPT, 0)
        policy.request(9, USER, 100)
        // A new window opens after the user change; the old schedule at 500 must not close it early.
        // zh-CN: 用户修改后开启了新窗口; 原先在 500 的调度不得提前关闭它.
        assertEquals(Action.ScheduleFlush(1100), policy.request(2, SCRIPT, 600))
        assertEquals(Action.None, policy.flush(500))
        assertEquals(2, policy.pendingScript)
        assertEquals(Action.Apply(2, SCRIPT), policy.flush(1100))
    }

    @Test
    fun onlyExplicitChoicesDeactivateTheIntelligentLibrary() {
        assertTrue(ThemeColorApplyPolicy.deactivatesIntelligent(USER))
        assertTrue(ThemeColorApplyPolicy.deactivatesIntelligent(SCRIPT))
        assertFalse(ThemeColorApplyPolicy.deactivatesIntelligent(INTELLIGENT))
        assertFalse(ThemeColorApplyPolicy.deactivatesIntelligent(RESTORE))
    }

}
