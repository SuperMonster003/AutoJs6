package org.autojs.autojs.execution

import com.google.gson.JsonParser
import org.autojs.autojs.engine.ScriptEngine
import org.autojs.autojs.script.ScriptSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentScriptExecutionStateTest {
    @Test fun ordinaryExecutionCannotReportOrForgeMarkerThroughArguments() {
        val config = ExecutionConfig().apply {
            setArgument("agentRunId", "forged")
            setArgument("__autojs6AgentExecutionContext", "forged")
        }
        val execution = execution(config)
        assertNull(execution.agentContext)
        assertNull(execution.agentRunId)
        assertNull(execution.agentResult)
        assertFalse(execution.setAgentResult("{\"answer\":42}"))
    }

    @Test fun agentContextAndResultAreImmutableJsonSnapshotsOutsideExecArgv() {
        val config = ExecutionConfig().apply {
            agentExecutionState = AgentScriptExecutionState("run-123", """{"days":30,"dir":"private-directory"}""", "private-preset")
            setArgument("days", 30)
        }
        val execution = execution(config)
        assertEquals("run-123", execution.agentRunId)
        assertEquals("private-preset", execution.agentPresetName)
        assertEquals(30, JsonParser.parseString(execution.agentParametersJson).asJsonObject["days"].asInt)
        assertEquals(setOf("days"), config.arguments.keys)
        assertTrue(execution.setAgentResult("{\"removed\":2}"))
        assertEquals("{\"removed\":2}", execution.agentResult)
        assertTrue(execution.setAgentResult("null"))
        assertEquals("null", execution.agentResult)
        assertFalse(config.agentExecutionState.toString().contains("private"))
    }

    @Test fun resultLimitCountsUtf8BytesAndFailedWritePreservesPreviousResult() {
        val state = AgentScriptExecutionState("run", "{}", null)
        val maximum = "\"" + "x".repeat(AgentScriptExecutionState.MAX_RESULT_BYTES - 2) + "\""
        assertTrue(state.reportResult(maximum))
        assertThrows(IllegalArgumentException::class.java) { state.reportResult("\"" + "中".repeat(22000) + "\"") }
        assertEquals(maximum, state.getResultJson())
        assertThrows(IllegalArgumentException::class.java) { state.reportResult("{private invalid JSON}") }
        assertEquals(maximum, state.getResultJson())
    }

    @Test fun resultFreezeRejectsLateReportsAndNeverResurrectsCancelledInvocation() {
        val state = AgentScriptExecutionState("run", "{}", null)
        state.reportResult("42")
        state.finish()
        assertFalse(state.reportResult("43"))
        assertEquals("42", state.getResultJson())
        state.finish()
        assertFalse(state.reportResult("invalid json after finish"))
    }

    @Test fun contextValidationBoundsArgumentsAndRejectsAmbiguousJson() {
        listOf("", "bad run", "x".repeat(129)).forEach { run ->
            assertThrows(IllegalArgumentException::class.java) { AgentScriptExecutionState(run, "{}", null) }
        }
        listOf("[]", "{\"x\":1,\"x\":2}", "{\"secret\":\"${"x".repeat(16384)}\"}").forEach { parameters ->
            assertThrows(IllegalArgumentException::class.java) { AgentScriptExecutionState("run", parameters, null) }
        }
        assertThrows(IllegalArgumentException::class.java) { AgentScriptExecutionState("run", "{}", "bad\npreset") }
        assertEquals(setOf("runId", "parameters", "presetName"),
            JsonParser.parseString(AgentScriptExecutionState("run", "{}", null).contextJson()).asJsonObject.keySet())
    }

    private fun execution(config: ExecutionConfig) = object : ScriptExecution {
        override fun getEngine(): ScriptEngine<out ScriptSource>? = null
        override fun getSource(): ScriptSource? = null
        override fun getListener(): ScriptExecutionListener? = null
        override fun getConfig(): ExecutionConfig = config
        override fun getId(): Int = 1
    }
}
