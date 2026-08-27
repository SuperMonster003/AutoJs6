package org.autojs.autojs.ui.explorer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExplorerViewStateLifecyclePolicyTest {

    @Test
    fun freshActivityCreationClearsStaleExplorerState() {
        assertTrue(
            ExplorerViewStateLifecyclePolicy.shouldClearOnCreate(
                hasSavedInstanceState = false,
            ),
        )
    }

    @Test
    fun recreatedActivityRetainsExplorerState() {
        assertFalse(
            ExplorerViewStateLifecyclePolicy.shouldClearOnCreate(
                hasSavedInstanceState = true,
            ),
        )
    }

    @Test
    fun finalActivityFinishClearsTransientExplorerState() {
        assertTrue(
            ExplorerViewStateLifecyclePolicy.shouldClearOnDestroy(
                isActivityFinishing = true,
                isChangingConfigurations = false,
            ),
        )
    }

    @Test
    fun backgroundActivityDestructionRetainsExplorerState() {
        assertFalse(
            ExplorerViewStateLifecyclePolicy.shouldClearOnDestroy(
                isActivityFinishing = false,
                isChangingConfigurations = false,
            ),
        )
    }

    @Test
    fun configurationChangeRetainsExplorerState() {
        assertFalse(
            ExplorerViewStateLifecyclePolicy.shouldClearOnDestroy(
                isActivityFinishing = false,
                isChangingConfigurations = true,
            ),
        )
    }

    @Test
    fun configurationChangeWinsOverDefensiveFinishingSignal() {
        assertFalse(
            ExplorerViewStateLifecyclePolicy.shouldClearOnDestroy(
                isActivityFinishing = true,
                isChangingConfigurations = true,
            ),
        )
    }

}
