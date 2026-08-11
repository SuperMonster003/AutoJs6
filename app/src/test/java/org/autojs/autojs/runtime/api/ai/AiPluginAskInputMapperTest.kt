package org.autojs.autojs.runtime.api.ai

import com.google.gson.JsonParser
import org.autojs.autojs.core.plugin.ai.AiTextProviderComponent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AiPluginAskInputMapperTest {

    @Test
    fun exactPluginAskMapsMultilinePromptPinsAndTimeoutWithoutLeakingPrompt() {
        val prompt = "first line\nsecond line"
        val input = input(
            messages = """[{"role":"user","content":"first line\nsecond line"}]""",
            optionSuffix = ", \"timeout\": 2500",
        )

        val request = AiPluginAskInputMapper.map(input, defaultTimeoutMillis = 9_000L)

        assertEquals(
            AiTextProviderComponent(
                packageName = "org.example.provider",
                className = "org.example.provider.AiTextService",
            ),
            request.component,
        )
        assertEquals("example.provider", request.providerId)
        assertEquals("example-model", request.modelId)
        assertEquals(prompt, request.prompt)
        assertEquals(2_500L, request.timeoutMillis)
        val rendered = request.toString()
        assertTrue("promptBytes=${prompt.toByteArray(Charsets.UTF_8).size}" in rendered)
        assertFalse(prompt in rendered)
        assertFalse("first line" in rendered)
        assertFalse("second line" in rendered)
    }

    @Test
    fun pluginAskRequiresExactlyOneUserTextMessage() {
        data class Case(val messages: String, val expectedMessage: String)

        val cases = listOf(
            Case("[]", "AI plugin ask requires exactly one user text message"),
            Case(
                """[
                    {"role":"user","content":"one"},
                    {"role":"user","content":"two"}
                ]""",
                "AI plugin ask requires exactly one user text message",
            ),
            Case("[42]", "AI plugin ask requires exactly one user text message"),
            Case(
                """[{"role":"assistant","content":"answer"}]""",
                "AI plugin ask requires exactly one user text message",
            ),
            Case(
                """[{"role":"user","content":42}]""",
                "AI plugin message content must be a string",
            ),
        )

        cases.forEach { case ->
            val error = assertThrows(IllegalArgumentException::class.java) {
                AiPluginAskInputMapper.map(input(case.messages), defaultTimeoutMillis = 9_000L)
            }
            assertEquals(case.expectedMessage, error.message)
        }
    }

    @Test
    fun pluginAskRejectsUnknownMessageAndOptionFields() {
        val messageError = assertThrows(IllegalArgumentException::class.java) {
            AiPluginAskInputMapper.map(
                input("""[{"role":"user","content":"prompt","name":"unexpected"}]"""),
                defaultTimeoutMillis = 9_000L,
            )
        }
        assertEquals("AI plugin ask message contains unsupported fields", messageError.message)

        val optionError = assertThrows(IllegalArgumentException::class.java) {
            AiPluginAskInputMapper.map(
                input(
                    messages = """[{"role":"user","content":"prompt"}]""",
                    optionSuffix = ", \"temperature\": 0.25",
                ),
                defaultTimeoutMillis = 9_000L,
            )
        }
        assertEquals("AI plugin selection contains unsupported options", optionError.message)
    }

    private fun input(
        messages: String,
        optionSuffix: String = "",
    ) = AiRequestInput(
        messages = JsonParser.parseString(messages).asJsonArray,
        options = JsonParser.parseString(
            """
            {
              "plugin": {
                "component": {
                  "packageName": "org.example.provider",
                  "className": "org.example.provider.AiTextService"
                },
                "providerId": "example.provider",
                "modelId": "example-model"
              }
              $optionSuffix
            }
            """,
        ).asJsonObject,
    )
}
