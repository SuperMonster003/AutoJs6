package org.autojs.autojs.core.plugin.center

import org.autojs.plugin.mcp.server.api.McpServerIds
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginDefaultEnabledPolicyTest {

    private val officialSignerEngines = listOf("three-stove-agent", " THREE-STOVE-AGENT ", McpServerIds.ENGINE, " MCP-SERVER ", "yolo", " YOLO ")

    @Test
    fun theAgentMcpServerAndYoloAreEnabledByDefaultOnlyWithTheOfficialSigner() {
        for (engine in officialSignerEngines) {
            for (state in PluginAuthorizedState.entries) {
                assertTrue("$engine $state", PluginDefaultEnabledPolicy.isEnabledByDefault(engine, state) == (state == PluginAuthorizedState.OFFICIAL))
            }
        }
    }

    @Test
    fun withoutAKnownSignerTheyStayDisabled() {
        for (engine in officialSignerEngines) {
            assertFalse(engine, PluginDefaultEnabledPolicy.isEnabledByDefault(engine))
        }
    }

    @Test
    fun existingAndUnknownEnginesKeepLegacyDefault() {
        assertTrue(PluginDefaultEnabledPolicy.isEnabledByDefault("paddle-ocr"))
        assertTrue(PluginDefaultEnabledPolicy.isEnabledByDefault(null))
        assertTrue(PluginDefaultEnabledPolicy.isEnabledByDefault("paddle-ocr", PluginAuthorizedState.REQUIRED))
    }

    @Test
    fun officialSignerAutoEnablesEveryEngineAfterInstall() {
        for (engine in officialSignerEngines + listOf("paddle-ocr", null)) {
            assertTrue("$engine", PluginDefaultEnabledPolicy.shouldAutoEnableAfterInstall(engine, PluginAuthorizedState.OFFICIAL))
        }
    }

    @Test
    fun otherSignersKeepTheOfficialSignerEnginesDisabledAfterInstall() {
        val nonOfficial = listOf(PluginAuthorizedState.TRUSTED, PluginAuthorizedState.USER_GRANTED, PluginAuthorizedState.REQUIRED)
        for (state in nonOfficial) {
            for (engine in officialSignerEngines) {
                assertFalse("$engine $state", PluginDefaultEnabledPolicy.shouldAutoEnableAfterInstall(engine, state))
            }
        }
    }

    @Test
    fun legacyEnginesAutoEnableForEveryAuthorizedSigner() {
        val authorized = listOf(PluginAuthorizedState.OFFICIAL, PluginAuthorizedState.TRUSTED, PluginAuthorizedState.USER_GRANTED)
        for (state in authorized) {
            assertTrue(state.name, PluginDefaultEnabledPolicy.shouldAutoEnableAfterInstall("paddle-ocr", state))
            assertTrue(state.name, PluginDefaultEnabledPolicy.shouldAutoEnableAfterInstall(null, state))
        }
        assertFalse(PluginDefaultEnabledPolicy.shouldAutoEnableAfterInstall("paddle-ocr", PluginAuthorizedState.REQUIRED))
        assertFalse(PluginDefaultEnabledPolicy.shouldAutoEnableAfterInstall(null, PluginAuthorizedState.REQUIRED))
    }
}
