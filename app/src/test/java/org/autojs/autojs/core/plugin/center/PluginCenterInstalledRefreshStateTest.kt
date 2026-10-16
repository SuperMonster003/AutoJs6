package org.autojs.autojs.core.plugin.center

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class PluginCenterInstalledRefreshStateTest {

    private data class Plugin(val packageName: String, val versionCode: Long, val updated: Long, val complete: Boolean)

    private val state = PluginCenterInstalledRefreshState<Plugin>(
        packageOf = { it.packageName },
        buildOf = { it.versionCode to it.updated },
    )

    @Test
    fun theFirstRefreshRendersTheSnapshot() {
        val snapshot = listOf(Plugin("a", 1, 10, complete = false))
        assertSame(snapshot, state.baseline(snapshot))
    }

    @Test
    fun anUnchangedBuildKeepsTheCompleteEntryOfThePreviousDiscovery() {
        val complete = Plugin("fake", 1, 10, complete = true)
        state.complete(listOf(complete, Plugin("b", 2, 20, complete = true)))

        val baseline = state.baseline(listOf(Plugin("fake", 1, 10, complete = false), Plugin("b", 2, 20, complete = false)))

        assertSame(complete, baseline.first())
        assertEquals(listOf(true, true), baseline.map { it.complete })
    }

    @Test
    fun updatedReinstalledAndAddedPackagesUseTheSnapshot() {
        state.complete(listOf(Plugin("updated", 1, 10, complete = true), Plugin("reinstalled", 1, 10, complete = true)))

        val baseline = state.baseline(
            listOf(
                Plugin("updated", 2, 30, complete = false),
                Plugin("reinstalled", 1, 40, complete = false),
                Plugin("added", 1, 50, complete = false),
            ),
        )

        assertEquals(listOf(false, false, false), baseline.map { it.complete })
    }

    @Test
    fun packagesMissingFromTheSnapshotAreGone() {
        state.complete(listOf(Plugin("kept", 1, 10, complete = true), Plugin("removed", 1, 10, complete = true)))

        assertEquals(listOf("kept"), state.baseline(listOf(Plugin("kept", 1, 10, complete = false))).map { it.packageName })
    }

    @Test
    fun anEmptyCompleteDiscoveryReplacesThePreviousOne() {
        state.complete(listOf(Plugin("a", 1, 10, complete = true)))
        state.complete(emptyList())

        assertEquals(listOf(false), state.baseline(listOf(Plugin("a", 1, 10, complete = false))).map { it.complete })
    }
}
