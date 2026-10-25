package org.autojs.autojs.runtime.api.modules.ai

import org.autojs.autojs.rhino.TopLevelScope
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.ai.AiService
import org.autojs.autojs.util.RhinoUtils
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mozilla.javascript.Context
import java.lang.reflect.Modifier

class AiInputParsingTest {

    private lateinit var context: Context
    private lateinit var scope: TopLevelScope

    @Before
    fun enterRhinoContext() {
        context = Context.enter().apply {
            languageVersion = Context.VERSION_ES6
            isInterpretedMode = true
        }
        scope = TopLevelScope().apply {
            initStandardObjects(context, false)
        }
        with(RhinoUtils) {
            context.registerStandardObjects(scope)
        }
    }

    @After
    fun exitRhinoContext() {
        Context.exit()
    }

    @Test
    fun promptStringBecomesOneUserMessage() {
        val input = Ai.parseRequestArguments(arrayOf("Hello"))
        val message = input.messages.single().asJsonObject

        assertEquals("user", message.get("role").asString)
        assertEquals("Hello", message.get("content").asString)
        assertEquals(0, input.options.size())
    }

    @Test
    fun requestObjectAndTrailingOptionsMergeWithoutMutatingMessages() {
        val request = evaluate(
            """
            ({
                messages: [
                    { role: "system", content: "Be concise" },
                    { role: "user", content: "Hello" },
                ],
                target: "local:primary",
                temperature: 0.2,
            })
            """.trimIndent(),
        )
        val overrides = evaluate(
            """
            ({
                temperature: 0.8,
                maxTokens: 64,
            })
            """.trimIndent(),
        )

        val input = Ai.parseRequestArguments(arrayOf(request, overrides))

        assertEquals(2, input.messages.size())
        assertEquals("local:primary", input.options.get("target").asString)
        assertEquals(0.8, input.options.get("temperature").asDouble, 0.0)
        assertEquals(64, input.options.get("maxTokens").asInt)
        assertFalse(input.options.has("messages"))
    }

    @Test
    fun oneMessageObjectIsAcceptedDirectly() {
        val message = evaluate("""({ role: "user", content: [{ type: "text", text: "Hi" }] })""")

        val input = Ai.parseRequestArguments(arrayOf(message))

        assertEquals(1, input.messages.size())
        assertEquals("user", input.messages[0].asJsonObject.get("role").asString)
        assertEquals(
            "Hi",
            input.messages[0].asJsonObject
                .getAsJsonArray("content")[0].asJsonObject
                .get("text").asString,
        )
    }

    @Test
    fun sessionOptionsCreateSystemPrefaceAndLocalPlanningMarker() {
        val options = evaluate(
            """({ system: "Keep answers short", temperature: 0.25 })""",
        )

        val input = Ai.parseSessionArguments(arrayOf(options))

        assertTrue(input.options.get("plugin").asBoolean)
        assertFalse(input.options.has("system"))
        assertEquals(0.25, input.options.get("temperature").asDouble, 0.0)
        assertEquals(2, input.messages.size())
        assertEquals("system", input.messages[0].asJsonObject.get("role").asString)
        assertEquals("Keep answers short", input.messages[0].asJsonObject.get("content").asString)
        assertEquals("user", input.messages[1].asJsonObject.get("role").asString)
        assertEquals("-", input.messages[1].asJsonObject.get("content").asString)
    }

    @Test
    fun sessionTurnAcceptsExactlyOneNewUserPrompt() {
        val message = Ai.parseSessionTurnArguments(arrayOf("Only this turn is serialized"))

        assertEquals("USER", message.role.name)
        assertEquals("Only this turn is serialized", message.content)
    }

    @Test
    fun aiServiceIsNotReachableThroughPublicScriptRuntimeMembers() {
        assertFalse(
            ScriptRuntime::class.java.methods.any { method ->
                method.returnType == AiService::class.java
            },
        )
        val askMethod = Ai::class.java.getMethod(
            "ask",
            ScriptRuntime::class.java,
            Array<Any>::class.java,
        )
        assertFalse(Modifier.isStatic(askMethod.modifiers))
    }

    private fun evaluate(source: String): Any {
        return requireNotNull(
            context.evaluateString(scope, source, "ai-input-test.js", 1, null),
        )
    }
}
