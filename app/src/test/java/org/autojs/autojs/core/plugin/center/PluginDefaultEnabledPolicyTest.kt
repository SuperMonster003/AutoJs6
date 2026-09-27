package org.autojs.autojs.core.plugin.center

import org.autojs.plugin.mcp.server.api.McpServerIds
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginDefaultEnabledPolicyTest {

    @Test
    fun threeStoveAgentUsesTheSameExplicitEnablePolicyAsMcp() {
        assertFalse(PluginDefaultEnabledPolicy.isEnabledByDefault("three-stove-agent"))
        assertFalse(PluginDefaultEnabledPolicy.isEnabledByDefault(" THREE-STOVE-AGENT "))
        for (state in PluginAuthorizedState.entries) {
            assertTrue(PluginDefaultEnabledPolicy.shouldAutoEnableAfterInstall("three-stove-agent", state) ==
                PluginDefaultEnabledPolicy.shouldAutoEnableAfterInstall("mcp-server", state))
        }
    }

    @Test
    fun mcpServerRequiresExplicitEnablement() {
        assertFalse(PluginDefaultEnabledPolicy.isEnabledByDefault(McpServerIds.ENGINE))
        assertFalse(PluginDefaultEnabledPolicy.isEnabledByDefault(" MCP-SERVER "))
    }

    @Test
    fun yoloIsDisabledByDefault() {
        assertFalse(PluginDefaultEnabledPolicy.isEnabledByDefault("yolo"))
        assertFalse(PluginDefaultEnabledPolicy.isEnabledByDefault(" YOLO "))
    }

    @Test
    fun existingAndUnknownEnginesKeepLegacyDefault() {
        assertTrue(PluginDefaultEnabledPolicy.isEnabledByDefault("paddle-ocr"))
        assertTrue(PluginDefaultEnabledPolicy.isEnabledByDefault(null))
    }

    @Test
    fun officialSignerAutoEnablesDefaultOffEnginesAfterInstall() {
        assertTrue(PluginDefaultEnabledPolicy.shouldAutoEnableAfterInstall("yolo", PluginAuthorizedState.OFFICIAL))
        assertTrue(PluginDefaultEnabledPolicy.shouldAutoEnableAfterInstall(" YOLO ", PluginAuthorizedState.OFFICIAL))
        assertTrue(PluginDefaultEnabledPolicy.shouldAutoEnableAfterInstall(McpServerIds.ENGINE, PluginAuthorizedState.OFFICIAL))
    }

    @Test
    fun otherSignersKeepDefaultOffEnginesDisabledAfterInstall() {
        val nonOfficial = listOf(PluginAuthorizedState.TRUSTED, PluginAuthorizedState.USER_GRANTED, PluginAuthorizedState.REQUIRED)
        for (state in nonOfficial) {
            assertFalse(state.name, PluginDefaultEnabledPolicy.shouldAutoEnableAfterInstall("yolo", state))
            assertFalse(state.name, PluginDefaultEnabledPolicy.shouldAutoEnableAfterInstall(" MCP-SERVER ", state))
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
