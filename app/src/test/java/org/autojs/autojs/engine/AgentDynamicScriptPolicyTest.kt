package org.autojs.autojs.engine

import org.junit.Assert.assertThrows
import org.junit.Test

class AgentDynamicScriptPolicyTest {
    @Test fun sourceBudgetUsesUtf8AndRejectsMalformedUnicode() {
        AgentDynamicScriptPolicy.validateSource("agent-123.js", "a".repeat(8192))
        AgentDynamicScriptPolicy.validateSource("agent-123", "😀".repeat(2048))
        for (source in listOf("", " \n", "a".repeat(8193), "中".repeat(2731), "\uD800", "\uDC00", "1;\u0000")) {
            assertThrows(IllegalArgumentException::class.java) { AgentDynamicScriptPolicy.validateSource("agent-123", source) }
        }
    }

    @Test fun dynamicNamesCannotSelectAnotherRuntimeByExtensionOrPath() {
        for (name in listOf("../agent.js", "agent.ts", "agent.node.js", "agent.bun.js", "agent.quickjs.js", "a".repeat(125), "\u0000")) {
            assertThrows(IllegalArgumentException::class.java) { AgentDynamicScriptPolicy.validateSource(name, "1;") }
        }
    }

    @Test fun onlyOwnedSingleExecutionOptionsAreAccepted() {
        val mandatory = setOf("agentInvocationId", "timeoutMs")
        AgentDynamicScriptPolicy.validateOptions(mandatory)
        AgentDynamicScriptPolicy.validateOptions(mandatory + setOf("agentRunId", "presetName", "captureConsole"))
        for (key in listOf("cwd", "delay", "loopTimes", "interval", "arguments", "waitMs", "__autojs6BaseCwd")) {
            assertThrows(IllegalArgumentException::class.java) { AgentDynamicScriptPolicy.validateOptions(mandatory + key) }
        }
        assertThrows(IllegalArgumentException::class.java) { AgentDynamicScriptPolicy.validateOptions(setOf("timeoutMs")) }
    }
}
