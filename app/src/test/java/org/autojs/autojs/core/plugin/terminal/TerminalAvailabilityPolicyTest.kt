package org.autojs.autojs.core.plugin.terminal

import org.autojs.plugin.terminal.api.TerminalIds
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TerminalAvailabilityPolicyTest {

    private val ready = TerminalPluginFacts(installed = true, hostVersion = 6000L, requiredHostVersion = 5304L)

    @Test
    fun `decision table maps every input to the state and route of D25`() {
        val rows = listOf(
            Triple(ready, TerminalPluginState.AVAILABLE, TerminalRoute.OPEN),
            Triple(ready.copy(installed = false), TerminalPluginState.NOT_INSTALLED, TerminalRoute.PLUGIN_CENTER),
            Triple(ready.copy(applicationEnabled = false), TerminalPluginState.DISABLED, TerminalRoute.APPLICATION_SETTINGS),
            Triple(ready.copy(serviceDeclared = false), TerminalPluginState.INCOMPATIBLE, TerminalRoute.INCOMPATIBLE_DIALOG),
            Triple(ready.copy(entryDeclared = false), TerminalPluginState.INCOMPATIBLE, TerminalRoute.INCOMPATIBLE_DIALOG),
            Triple(ready.copy(requiredHostVersion = 6001L), TerminalPluginState.INCOMPATIBLE, TerminalRoute.INCOMPATIBLE_DIALOG),
            Triple(ready.copy(authorized = false), TerminalPluginState.DISABLED, TerminalRoute.PLUGIN_SETTINGS),
            Triple(ready.copy(pluginEnabled = false), TerminalPluginState.DISABLED, TerminalRoute.PLUGIN_SETTINGS),
            Triple(ready.copy(bindable = false), TerminalPluginState.UNAVAILABLE, TerminalRoute.FAILURE_TOAST),
        )
        rows.forEach { (facts, state, route) ->
            assertEquals(facts.toString(), state, TerminalAvailabilityPolicy.state(facts))
            assertEquals(facts.toString(), route, TerminalAvailabilityPolicy.route(facts))
            assertEquals(state, TerminalInspection(facts).state)
            assertEquals(route, TerminalInspection(facts).route)
        }
        assertEquals(5, TerminalPluginState.entries.size)
        assertEquals(TerminalPluginState.entries.toSet(), rows.map { it.second }.toSet())
        assertEquals(TerminalRoute.entries.toSet(), rows.map { it.third }.toSet())
    }

    @Test
    fun `installation and android enablement are decided before compatibility and authorization`() {
        val everythingWrong = ready.copy(installed = false, applicationEnabled = false, serviceDeclared = false, authorized = false, pluginEnabled = false, bindable = false)
        assertEquals(TerminalPluginState.NOT_INSTALLED, TerminalAvailabilityPolicy.state(everythingWrong))
        assertEquals(TerminalPluginState.DISABLED, TerminalAvailabilityPolicy.state(everythingWrong.copy(installed = true)))
        assertEquals(TerminalRoute.APPLICATION_SETTINGS, TerminalAvailabilityPolicy.route(everythingWrong.copy(installed = true)))
        assertEquals(TerminalPluginState.INCOMPATIBLE, TerminalAvailabilityPolicy.state(everythingWrong.copy(installed = true, applicationEnabled = true)))
        assertEquals(TerminalPluginState.DISABLED, TerminalAvailabilityPolicy.state(everythingWrong.copy(installed = true, applicationEnabled = true, serviceDeclared = true)))
        assertEquals(TerminalRoute.PLUGIN_SETTINGS, TerminalAvailabilityPolicy.route(everythingWrong.copy(installed = true, applicationEnabled = true, serviceDeclared = true)))
        assertEquals(TerminalPluginState.UNAVAILABLE, TerminalAvailabilityPolicy.state(ready.copy(bindable = false, pluginEnabled = true)))
    }

    @Test
    fun `a plugin built for a newer host is incompatible while an equal or older requirement is fine`() {
        assertTrue(ready.copy(requiredHostVersion = 6001L).requiresNewerHost)
        assertFalse(ready.copy(requiredHostVersion = 6000L).requiresNewerHost)
        assertFalse(ready.copy(requiredHostVersion = TerminalIds.REQUIRED_HOST_VERSION_CODE).requiresNewerHost)
        assertEquals(TerminalPluginState.AVAILABLE, TerminalAvailabilityPolicy.state(ready.copy(requiredHostVersion = 6000L)))
        val defaults = TerminalPluginFacts(installed = true)
        assertEquals(TerminalIds.REQUIRED_HOST_VERSION_CODE, defaults.requiredHostVersion)
        assertEquals(TerminalPluginState.AVAILABLE, TerminalAvailabilityPolicy.state(defaults))
    }

    @Test
    fun `inspection exposes availability only for the available state`() {
        assertTrue(TerminalInspection(ready).available)
        TerminalPluginState.entries.filter { it != TerminalPluginState.AVAILABLE }.forEach { state ->
            val facts = when (state) {
                TerminalPluginState.NOT_INSTALLED -> ready.copy(installed = false)
                TerminalPluginState.DISABLED -> ready.copy(pluginEnabled = false)
                TerminalPluginState.INCOMPATIBLE -> ready.copy(entryDeclared = false)
                TerminalPluginState.UNAVAILABLE -> ready.copy(bindable = false)
                TerminalPluginState.AVAILABLE -> ready
            }
            assertFalse(state.name, TerminalInspection(facts).available)
        }
    }
}
