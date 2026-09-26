package org.autojs.autojs.runtime.api.ai

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test

class AiAgentArgumentsTest {
    private fun json(value: String) = JsonParser.parseString(value).asJsonObject
    @Test fun goalUsesUtf8ByteLimitsAndRejectsUnpairedSurrogates() {
        assertEquals("中".repeat(1365), AiAgentArguments.goal("中".repeat(1365)))
        for (text in listOf("", " \n", "中".repeat(1366), "\uD800", "\uDC00")) assertThrows(IllegalArgumentException::class.java) { AiAgentArguments.goal(text) }
        assertEquals("😀", AiAgentArguments.goal("😀"))
    }
    @Test fun invalidOptionsNeverReachTransport() {
        for (value in listOf("{\"unknown\":true}", "{\"detached\":1}", "{\"memory\":null}", "{\"target\":\"https://example.test\"}",
            "{\"interaction\":\"auto\"}", "{\"confirm\":\"never\"}", "{\"tools\":[\"shell\",\"shell\"]}", "{\"tools\":[\"bogus\"]}",
            "{\"tools\":{\"all\":true}}", "{\"scriptRoots\":[\"/sdcard/../data\"]}", "{\"scriptRoots\":[\"relative\"]}", "{\"parameters\":[]}"))
            assertThrows(value, IllegalArgumentException::class.java) { AiAgentArguments.options(json(value)) }
    }
    @Test fun budgetsRejectFractionalCoercedAndOutOfRangeValues() {
        for (field in listOf("maxSteps", "maxModelCalls", "maxDurationMs", "maxTotalTokens")) {
            for (value in listOf("0", "-1", "1.5", "\"2\"", "null", "1e99"))
                assertThrows("$field=$value", IllegalArgumentException::class.java) { AiAgentArguments.options(json("{\"budget\":{\"$field\":$value}}")) }
        }
        assertThrows(IllegalArgumentException::class.java) { AiAgentArguments.options(json("{\"budget\":{\"maxSteps\":201}}")) }
        assertThrows(IllegalArgumentException::class.java) { AiAgentArguments.options(json("{\"budget\":{\"maxDurationMs\":1800001}}")) }
        AiAgentArguments.options(json("{\"detached\":true,\"budget\":{\"maxDurationMs\":3600000}}"))
    }
    @Test fun contextAndPrefillHaveACombinedByteCeiling() {
        assertThrows(IllegalArgumentException::class.java) { AiAgentArguments.options(json("{\"context\":\"${"a".repeat(8192)}\",\"parameters\":{\"days\":30}}")) }
        AiAgentArguments.options(json("{\"context\":\"\",\"parameters\":{\"days\":30}}"))
    }
    @Test fun dynamicScriptGroupIsRecognizedButNeverEnabledByDefault() {
        AiAgentArguments.options(json("""{"tools":["script_dynamic"]}"""))
        val inherited = AiAgentArguments.merge(json("""{"tools":{}}"""), json("""{"tools":{"disable":["shell"]}}"""))
        assertFalse(inherited.getAsJsonArray("tools").any { it.asString == "script_dynamic" })
        assertThrows(IllegalArgumentException::class.java) {
            AiAgentArguments.merge(json("""{"tools":["observe"]}"""), json("""{"tools":["script_dynamic"]}"""))
        }
    }
    @Test fun mcpGroupIsExplicitAndAssistantRestrictionsCannotBeExpanded() {
        AiAgentArguments.options(json("""{"tools":["mcp","user"]}"""))
        assertFalse(AiAgentArguments.effectiveTools(json("{}")).contains("mcp"))
        val narrowed = AiAgentArguments.merge(json("""{"tools":["mcp","observe"]}"""), json("""{"tools":{"disable":["observe"]}}"""))
        assertEquals(setOf("mcp"), AiAgentArguments.effectiveTools(narrowed["tools"]))
        assertThrows(IllegalArgumentException::class.java) {
            AiAgentArguments.merge(json("""{"tools":{}}"""), json("""{"tools":["mcp"]}"""))
        }
        assertThrows(IllegalArgumentException::class.java) {
            AiAgentArguments.options(json("""{"tools":["mcp","mcp"]}"""))
        }
    }
    @Test fun assistantOverridesPreserveAndNarrowRestrictionsWithoutMutatingInput() {
        val base = json("""{"budget":{"maxSteps":20,"maxModelCalls":30},"tools":["observe","script","user"],"confirm":"cautious","scriptRoots":["/sdcard/Scripts/agent"]} """)
        val original = base.toString()
        val merged = AiAgentArguments.merge(base, json("""{"budget":{"maxSteps":10},"tools":{"disable":["script"]}}"""))
        assertEquals(30, merged.getAsJsonObject("budget")["maxModelCalls"].asInt)
        assertEquals(setOf("observe", "user"), merged.getAsJsonArray("tools").map { it.asString }.toSet())
        assertEquals(original, base.toString())
        for (overrides in listOf("""{"budget":{"maxSteps":21}}""", """{"tools":["act"]}""", """{"confirm":"default"}""", """{"scriptRoots":["/sdcard/other"]}"""))
            assertThrows(IllegalArgumentException::class.java) { AiAgentArguments.merge(base, json(overrides)) }
    }
    @Test fun filtersAreTypedBoundedAndInclusive() {
        val filter = AiAgentArguments.filter(json("""{"state":["completed","partial"],"preset":"default","since":100,"until":200,"limit":50}"""))
        assertTrue(AiAgentArguments.matches(json("""{"state":"completed","startedAt":100}"""), filter))
        assertFalse(AiAgentArguments.matches(json("""{"state":"completed","startedAt":201}"""), filter))
        assertFalse(AiAgentArguments.matches(json("""{"state":"failed","startedAt":150}"""), filter))
        for (value in listOf("""{"limit":51}""", """{"since":200,"until":100}""", """{"state":"wat"}""", """{"state":[]}"""))
            assertThrows(IllegalArgumentException::class.java) { AiAgentArguments.filter(json(value)) }
    }
}
