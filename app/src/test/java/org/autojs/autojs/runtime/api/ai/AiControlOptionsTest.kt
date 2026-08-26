package org.autojs.autojs.runtime.api.ai

import com.google.gson.JsonParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AiControlOptionsTest {

    @Test
    fun omittedSelectorUsesTheOfficialPluginDefaultTarget() {
        val options = JsonParser.parseString("""{"timeout":1500}""").asJsonObject

        val control = AiControlOptions.takeFrom(options)

        assertEquals(officialSelection(), control.pluginSelection)
        assertNull(control.targetId)
        assertEquals(1_500L, control.timeoutMillis)
        assertTrue(options.entrySet().isEmpty())
    }

    @Test
    fun exactTargetUsesTheOfficialPlugin() {
        val control = AiControlOptions.takeFrom(
            JsonParser.parseString("""{"target":"profile:work"}""").asJsonObject,
        )

        assertEquals(officialSelection(), control.pluginSelection)
        assertEquals("profile:work", control.targetId)
    }

    @Test
    fun exactPluginComponentAndTargetAreConsumed() {
        val options = pluginOptions(", \"timeout\": 1500")

        val control = AiControlOptions.takeFrom(options)

        assertEquals(
            AiPluginSelection(
                packageName = "org.example.provider",
                className = "org.example.provider.AiProviderService",
                providerId = "example.provider",
            ),
            control.pluginSelection,
        )
        assertEquals("local:example-model", control.targetId)
        assertEquals(1_500L, control.timeoutMillis)
        assertTrue(options.entrySet().isEmpty())
    }

    @Test
    fun malformedPluginSelectionIsRejectedAtTheContractBoundary() {
        data class Case(val pluginJson: String, val expectedMessage: String)

        val cases = listOf(
            Case("null", "AI plugin selection must be an object"),
            Case("[]", "AI plugin selection must be an object"),
            Case("false", "AI plugin selection must not be false"),
            Case("""{"component":{}}""", "AI plugin providerId is required"),
            Case(
                """{"component":{},"providerId":1}""",
                "AI plugin providerId must be a string",
            ),
            Case(
                """{"component":{},"providerId":"bad/id"}""",
                "AI plugin providerId is invalid",
            ),
            Case(
                """{"component":[],"providerId":"example.provider"}""",
                "AI plugin component must be an object",
            ),
            Case(
                """{"component":{"packageName":"org.example.provider"},"providerId":"example.provider"}""",
                "AI plugin component className is required",
            ),
            Case(
                """{"component":{"packageName":"org.example.provider","className":"org.example.\u0001Service"},"providerId":"example.provider"}""",
                "AI plugin component className contains control characters",
            ),
            Case(
                """{"component":{"packageName":"org.example.provider","className":"Service","extra":true},"providerId":"example.provider"}""",
                "AI plugin component contains unsupported fields",
            ),
            Case(
                """{"component":{},"providerId":"example.provider","extra":true}""",
                "AI plugin selection contains unsupported fields",
            ),
        )

        cases.forEach { case ->
            val options = JsonParser.parseString("""{"plugin":${case.pluginJson}}""").asJsonObject
            val error = assertThrows(IllegalArgumentException::class.java) {
                AiControlOptions.takeFrom(options)
            }
            assertEquals(case.expectedMessage, error.message)
        }
    }

    @Test
    fun unknownFacadeControlsAreRejected() {
        listOf(
            """{"unexpected":true}""",
            """{"nested":{"unexpected":true}}""",
        ).forEach { source ->
            val error = assertThrows(IllegalArgumentException::class.java) {
                AiControlOptions.takeFrom(JsonParser.parseString(source).asJsonObject)
            }
            assertEquals("AI request contains unsupported options", error.message)
        }
    }

    @Test
    fun timeoutMustBeAnExactIntegerInRange() {
        listOf("1.5", "1e100", "999", "600001").forEach { timeout ->
            val options = JsonParser.parseString("""{"timeout":$timeout}""").asJsonObject
            assertThrows(IllegalArgumentException::class.java) {
                AiControlOptions.takeFrom(options)
            }
        }

        val scientificInteger = JsonParser.parseString("""{"timeout":1e3}""").asJsonObject
        assertEquals(1_000L, AiControlOptions.takeFrom(scientificInteger).timeoutMillis)
    }

    private fun officialSelection(): AiPluginSelection {
        val packageName = "io.github.supermonster003.autojs6.plugin.threestoneai"
        return AiPluginSelection(
            packageName = packageName,
            className = "$packageName.provider.ThreeStoneAiProviderService",
            providerId = "autojs6.three-stone-ai",
        )
    }

    private fun pluginOptions(extra: String = "") = JsonParser.parseString(
        """
        {
          "plugin": {
            "component": {
              "packageName": "org.example.provider",
              "className": "org.example.provider.AiProviderService"
            },
            "providerId": "example.provider"
          },
          "target": "local:example-model"
          $extra
        }
        """,
    ).asJsonObject
}
