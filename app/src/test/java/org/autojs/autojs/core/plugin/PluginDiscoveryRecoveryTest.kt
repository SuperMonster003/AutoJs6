package org.autojs.autojs.core.plugin

import org.junit.Assert.assertEquals
import org.junit.Test

class PluginDiscoveryRecoveryTest {

    private data class Item(val packageName: String, val service: String, val failed: Boolean)

    private var now = 10_000L
    private val recovery = PluginDiscoveryRecovery(throttleMillis = 60_000L, clock = { now })

    private fun wake(items: List<Item>, shouldWake: (String) -> Boolean = { true }) =
        recovery.packagesToWake(items, { it.packageName }, { it.failed }, shouldWake)

    @Test
    fun wakesEachPackageWithAFailedServiceOnce() {
        val items = listOf(
            Item("plugin.a", ".First", failed = true),
            Item("plugin.a", ".Second", failed = true),
            Item("plugin.b", ".Service", failed = false),
            Item("plugin.c", ".Service", failed = true),
        )

        assertEquals(listOf("plugin.a", "plugin.c"), wake(items))
    }

    @Test
    fun skipsPackagesTheCallerDoesNotAccept() {
        val items = listOf(Item("plugin.disabled", ".Service", true), Item("plugin.enabled", ".Service", true))

        assertEquals(listOf("plugin.enabled"), wake(items) { it == "plugin.enabled" })
        // A rejected package is not recorded, so it is woken as soon as the caller accepts it.
        assertEquals(listOf("plugin.disabled"), wake(items) { it == "plugin.disabled" })
    }

    @Test
    fun wakesAPackageAgainOnlyAfterTheThrottleWindow() {
        val items = listOf(Item("plugin.a", ".Service", true))

        assertEquals(listOf("plugin.a"), wake(items))
        now += 59_999L
        assertEquals(emptyList<String>(), wake(items))
        now += 1L
        assertEquals(listOf("plugin.a"), wake(items))
    }

    @Test
    fun mergeReplacesOnlyFailedItemsAndKeepsTheFirstOrder() {
        val first = listOf(
            Item("plugin.a", ".Service", failed = true),
            Item("plugin.b", ".Service", failed = false),
            Item("plugin.c", ".Service", failed = true),
        )
        val second = listOf(
            Item("plugin.c", ".Service", failed = false),
            Item("plugin.a", ".Service", failed = true),
            Item("plugin.b", ".Service", failed = true),
        )

        val merged = PluginDiscoveryRecovery.merge(first, second, { it.packageName to it.service }, { it.failed })

        assertEquals(
            listOf(
                Item("plugin.a", ".Service", failed = true),
                Item("plugin.b", ".Service", failed = false),
                Item("plugin.c", ".Service", failed = false),
            ),
            merged,
        )
    }

    @Test
    fun mergeKeepsAFailedItemThatTheSecondDiscoveryDidNotReturn() {
        val first = listOf(Item("plugin.a", ".Service", failed = true))

        assertEquals(first, PluginDiscoveryRecovery.merge(first, emptyList(), { it.packageName to it.service }, { it.failed }))
    }
}
