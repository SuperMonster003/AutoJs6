package org.autojs.autojs.core.plugin.center

import org.junit.Assert.assertEquals
import org.junit.Test

class PluginCenterLegacyRefreshStateTest {

    @Test
    fun nextRefreshStartsFromLastSuccessfulDiscovery() {
        val state = PluginCenterLegacyRefreshState<String>()

        assertEquals(emptyList<String>(), state.beginRefresh())
        state.completeRefresh(listOf("sdk.alpha", "sdk.beta"))

        assertEquals(listOf("sdk.alpha", "sdk.beta"), state.beginRefresh())
    }

    @Test
    fun repeatedRefreshesRetainSnapshotUntilAnotherSuccessArrives() {
        val state = PluginCenterLegacyRefreshState<String>()
        state.completeRefresh(listOf("sdk.alpha", "sdk.beta"))

        assertEquals(listOf("sdk.alpha", "sdk.beta"), state.beginRefresh())
        assertEquals(listOf("sdk.alpha", "sdk.beta"), state.beginRefresh())
    }

    @Test
    fun successfulEmptyDiscoveryClearsPreviousSnapshot() {
        val state = PluginCenterLegacyRefreshState<String>()
        state.completeRefresh(listOf("sdk.alpha", "sdk.beta"))

        state.completeRefresh(emptyList())

        assertEquals(emptyList<String>(), state.beginRefresh())
    }

    @Test
    fun completedSnapshotDoesNotAliasMutableDiscoveryInput() {
        val state = PluginCenterLegacyRefreshState<String>()
        val discovered = mutableListOf("sdk.alpha", "sdk.beta")

        state.completeRefresh(discovered)
        discovered.clear()

        assertEquals(listOf("sdk.alpha", "sdk.beta"), state.beginRefresh())
    }
}
