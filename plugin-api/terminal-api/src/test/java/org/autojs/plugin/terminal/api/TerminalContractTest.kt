package org.autojs.plugin.terminal.api

import org.autojs.plugin.common.api.PluginActions
import org.autojs.plugin.common.api.PluginCapabilityKeys
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Modifier

class TerminalContractTest {

    @Test
    fun identityAndDiscoveryLiteralsAreStable() {
        assertEquals(PluginActions.INFO, TerminalActions.INFO)
        assertEquals("org.autojs.plugin.TERMINAL", TerminalActions.SERVICE_ACTION)
        assertEquals("terminal", TerminalActions.SERVICE_CATEGORY)
        assertEquals("org.autojs.permission.PLUGIN", TerminalActions.PLUGIN_PERMISSION)
        assertEquals("org.autojs.plugin.TERMINAL_OPEN", TerminalActions.OPEN_TERMINAL)
        assertEquals("org.autojs.plugin.TERMINAL_SETTINGS", TerminalActions.OPEN_SETTINGS)

        assertEquals("three-shell-terminal", TerminalIds.PLUGIN_ID)
        assertEquals("terminal", TerminalIds.ENGINE)
        assertEquals("default", TerminalIds.VARIANT_DEFAULT)
        assertEquals("io.github.supermonster003.autojs6.plugin.three.shell.terminal", TerminalIds.DEFAULT_PACKAGE_NAME)
        assertTrue(TerminalIds.REQUIRED_HOST_VERSION_CODE >= 5304L)

        assertEquals(PluginCapabilityKeys.REQUIRES_HOST_VERSION, TerminalCapabilityKeys.REQUIRES_HOST_VERSION)
        assertEquals("terminalContractVersion", TerminalCapabilityKeys.CONTRACT_VERSION)
        assertEquals("terminalFeatures", TerminalCapabilityKeys.FEATURES_KEY)
        assertEquals("terminalMaxSessions", TerminalCapabilityKeys.MAX_SESSIONS)
        assertEquals("terminalMaxSubscriptions", TerminalCapabilityKeys.MAX_SUBSCRIPTIONS)
        assertEquals("terminalNodeCli", TerminalCapabilityKeys.NODE_CLI)
        assertEquals(TerminalCapabilityKeys.FEATURES.size, TerminalCapabilityKeys.FEATURES.toSet().size)
        assertEquals(listOf("node-cli", "output-subscription", "transcript", "settings"), TerminalCapabilityKeys.FEATURES)
    }

    @Test
    fun capabilityKeysDoNotCollideWithTheCommonPluginKeys() {
        val common = constantsOf(PluginCapabilityKeys::class.java).values.toSet()
        val own = constantsOf(TerminalCapabilityKeys::class.java)
            .filterKeys { it != "REQUIRES_HOST_VERSION" && !it.startsWith("FEATURE_") }
            .values
        assertTrue(own.isNotEmpty())
        own.forEach { key -> assertFalse("terminal key $key collides with PluginCapabilityKeys", key in common) }
        assertTrue(own.all { it.startsWith("terminal") })
        assertEquals(own.size, own.toSet().size)
    }

    @Test
    fun contractVersionRangeIncludesItsBoundsAndRejectsNeighbours() {
        assertEquals(1, TerminalContract.CONTRACT_VERSION)
        assertEquals(1, TerminalContract.MIN_CONTRACT_VERSION)
        assertTrue(TerminalContract.MIN_CONTRACT_VERSION <= TerminalContract.MAX_CONTRACT_VERSION)
        assertTrue(TerminalContract.supportsContractVersion(TerminalContract.MIN_CONTRACT_VERSION))
        assertTrue(TerminalContract.supportsContractVersion(TerminalContract.CONTRACT_VERSION))
        assertTrue(TerminalContract.supportsContractVersion(TerminalContract.MAX_CONTRACT_VERSION))
        assertFalse(TerminalContract.supportsContractVersion(TerminalContract.MIN_CONTRACT_VERSION - 1))
        assertFalse(TerminalContract.supportsContractVersion(TerminalContract.MAX_CONTRACT_VERSION + 1))
    }

    @Test
    fun bundleKeysAndIntentExtrasAreUniqueAndFrozen() {
        val keys = constants("KEY_")
        assertEquals(keys.size, keys.values.toSet().size)
        assertEquals("subscriptionId", TerminalContract.KEY_SUBSCRIPTION_ID)
        assertEquals("fd", TerminalContract.KEY_FD)
        assertEquals("text", TerminalContract.KEY_TEXT)
        assertEquals("truncated", TerminalContract.KEY_TRUNCATED)
        assertEquals("errorJson", TerminalContract.KEY_ERROR_JSON)

        val extras = constants("EXTRA_")
        assertEquals(extras.size, extras.values.toSet().size)
        assertEquals(
            listOf("directory", "sessionId", "newSession", "command", "manager"),
            listOf(
                TerminalContract.EXTRA_DIRECTORY,
                TerminalContract.EXTRA_SESSION_ID,
                TerminalContract.EXTRA_NEW_SESSION,
                TerminalContract.EXTRA_COMMAND,
                TerminalContract.EXTRA_MANAGER,
            ),
        )
        assertEquals(5, extras.size)
    }

    @Test
    fun documentFieldsAreUniqueWithinTheirDocuments() {
        val fields = constants("FIELD_")
        // The nested error and node objects repeat top-level names (`sessionId`, `version`, `pluginVersion`).
        val nestedPrefixes = listOf("FIELD_ERROR_", "FIELD_NODE_")
        val topLevel = fields.filterKeys { key -> nestedPrefixes.none(key::startsWith) }
        assertEquals(topLevel.size, topLevel.values.toSet().size)
        nestedPrefixes.forEach { prefix ->
            val nested = fields.filterKeys { it.startsWith(prefix) }
            assertEquals(nested.size, nested.values.toSet().size)
        }
        // Session documents and the script-side TerminalSession share these names (roadmap appendix A.3 / B.2).
        assertEquals("id", TerminalContract.FIELD_ID)
        assertEquals("cwd", TerminalContract.FIELD_CWD)
        assertEquals("title", TerminalContract.FIELD_TITLE)
        assertEquals("createdAt", TerminalContract.FIELD_CREATED_AT)
        assertEquals("alive", TerminalContract.FIELD_ALIVE)
        assertEquals("exitCode", TerminalContract.FIELD_EXIT_CODE)
        assertEquals("pid", TerminalContract.FIELD_PID)
        assertEquals("fromStart", TerminalContract.FIELD_FROM_START)
    }

    @Test
    fun closedValueSetsAreConsistent() {
        assertEquals(listOf("pending", "running", "exited"), TerminalContract.STATES)
        assertTrue(TerminalContract.isState("running"))
        assertFalse(TerminalContract.isState("closed"))
        assertTrue(TerminalContract.isTerminalState("exited"))
        assertFalse(TerminalContract.isTerminalState("pending"))

        assertEquals(listOf("granted", "denied", "not_applicable"), TerminalContract.STORAGE_ACCESS_STATES)
        assertTrue(TerminalContract.isStorageAccessState("denied"))
        assertFalse(TerminalContract.isStorageAccessState(null))

        assertEquals(8, TerminalContract.NODE_CLI_STATES.size)
        assertEquals(TerminalContract.NODE_CLI_STATES.size, TerminalContract.NODE_CLI_STATES.toSet().size)
        assertEquals("available", TerminalContract.NODE_CLI_STATES.first())
        TerminalContract.NODE_CLI_STATES.forEach { state ->
            assertTrue(state, Regex("[a-z]+(-[a-z]+)*").matches(state))
            assertTrue(TerminalContract.isNodeCliState(state))
        }
        assertFalse(TerminalContract.isNodeCliState("ready"))
    }

    @Test
    fun ceilingsMatchTheRoadmap() {
        assertEquals(16, TerminalContract.MAX_SESSIONS)
        assertEquals(65_536, TerminalContract.MAX_COMMAND_BYTES)
        assertEquals(65_536, TerminalContract.MAX_INPUT_BYTES)
        assertEquals(64, TerminalContract.MAX_ENV_ENTRIES)
        assertEquals(4_096, TerminalContract.MAX_ENV_ENTRY_BYTES)
        assertEquals(4_096, TerminalContract.MAX_PATH_BYTES)
        assertEquals(65_536, TerminalContract.MAX_JSON_BYTES)
        assertEquals(8, TerminalContract.MAX_CALLBACKS)
        assertEquals(4, TerminalContract.MAX_SUBSCRIPTIONS_PER_SESSION)
        assertEquals(1_048_576, TerminalContract.MAX_TRANSCRIPT_BYTES)
        assertEquals(1_048_576, TerminalContract.OUTPUT_BUFFER_BYTES)
        assertTrue(TerminalContract.MAX_COMMAND_BYTES <= TerminalContract.MAX_JSON_BYTES)
        assertTrue(TerminalContract.MAX_ENV_ENTRIES * TerminalContract.MAX_ENV_ENTRY_BYTES > TerminalContract.MAX_JSON_BYTES)
    }

    @Test
    fun errorCodesAreClosedUniqueAndSelfDescribing() {
        assertEquals(14, TerminalErrorCodes.ALL.size)
        assertEquals(TerminalErrorCodes.ALL.size, TerminalErrorCodes.ALL.toSet().size)
        TerminalErrorCodes.ALL.forEach { code ->
            assertTrue(code, Regex("[A-Z][A-Z_]+").matches(code))
            assertTrue(TerminalErrorCodes.isKnown(code))
        }
        assertFalse(TerminalErrorCodes.isKnown("NOPE"))
        assertFalse(TerminalErrorCodes.isKnown(null))
        assertTrue(TerminalErrorCodes.HOST_ONLY.all { it in TerminalErrorCodes.ALL })
        assertTrue(TerminalErrorCodes.isHostOnly(TerminalErrorCodes.PLUGIN_UNAVAILABLE))
        assertFalse(TerminalErrorCodes.isHostOnly(TerminalErrorCodes.PTY_FAILED))
        // Every public String constant of the object is listed exactly once in ALL.
        val declared = constantsOf(TerminalErrorCodes::class.java)
        assertEquals(declared.values.sorted(), TerminalErrorCodes.ALL.sorted())
    }

    private fun constants(prefix: String): Map<String, String> =
        constantsOf(TerminalContract::class.java).filterKeys { it.startsWith(prefix) }

    private fun constantsOf(type: Class<*>): Map<String, String> = type.declaredFields
        .filter { Modifier.isStatic(it.modifiers) && it.type == String::class.java }
        .associate { field -> field.isAccessible = true; field.name to field.get(null) as String }
}
