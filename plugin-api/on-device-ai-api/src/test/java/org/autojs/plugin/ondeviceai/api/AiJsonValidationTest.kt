package org.autojs.plugin.ondeviceai.api

import org.autojs.plugin.ai.common.api.AiPayloadReference
import org.autojs.plugin.ai.common.api.AiProtocolVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class AiJsonValidationTest {
    @Test
    fun acceptsCompleteJsonGrammarAndRejectsMalformedOrTooDeepInput() {
        AiJsonSyntaxValidator.requireValid(
            "{\"array\":[null,true,false,-12.5e+2,\"\\uD83D\\uDE00\"],\"object\":{}}",
        )
        listOf("", "{", "[1,]", "{\"x\":01}", "\"\\uD800\"", "true false").forEach { malformed ->
            assertThrows(IllegalArgumentException::class.java) {
                AiJsonSyntaxValidator.requireValid(malformed)
            }
        }
        val maximumDepth = "[".repeat(OnDeviceAiLimits.MAX_JSON_NESTING_DEPTH) + "0" +
            "]".repeat(OnDeviceAiLimits.MAX_JSON_NESTING_DEPTH)
        AiJsonSyntaxValidator.requireValid(maximumDepth)
        val tooDeep = "[".repeat(OnDeviceAiLimits.MAX_JSON_NESTING_DEPTH + 1) + "0" +
            "]".repeat(OnDeviceAiLimits.MAX_JSON_NESTING_DEPTH + 1)
        assertThrows(IllegalArgumentException::class.java) {
            AiJsonSyntaxValidator.requireValid(tooDeep)
        }
    }

    @Test
    fun rejectsUnicodeDigitsInEveryJsonNumberComponent() {
        listOf(
            "1\u0661",
            "1.\u0661",
            "1e\u0661",
        ).forEach { malformed ->
            assertThrows(IllegalArgumentException::class.java) {
                AiJsonSyntaxValidator.requireValid(malformed)
            }
        }
        listOf("\"\\u\uFF11\uFF12\uFF13\uFF14\"", "\"\\u\u0661\u0662\u0663\u0664\"").forEach { malformed ->
            assertThrows(IllegalArgumentException::class.java) {
                AiJsonSyntaxValidator.requireValid(malformed)
            }
        }
    }

    @Test
    fun enforcesNodeLimitWithValuesAndObjectKeysAsNodes() {
        fun array(elementCount: Int) = List(elementCount) { "0" }.joinToString(",", "[", "]")
        fun objectValue(memberCount: Int) =
            List(memberCount) { index -> "\"k$index\":0" }.joinToString(",", "{", "}")

        AiJsonSyntaxValidator.requireValid(array(OnDeviceAiLimits.MAX_JSON_NODES - 1))
        assertThrows(IllegalArgumentException::class.java) {
            AiJsonSyntaxValidator.requireValid(array(OnDeviceAiLimits.MAX_JSON_NODES))
        }
        AiJsonSyntaxValidator.requireValid(objectValue((OnDeviceAiLimits.MAX_JSON_NODES - 1) / 2))
        assertThrows(IllegalArgumentException::class.java) {
            AiJsonSyntaxValidator.requireValid(objectValue((OnDeviceAiLimits.MAX_JSON_NODES + 1) / 2))
        }
    }

    @Test
    fun rejectsDuplicateDecodedObjectKeysIncludingEscapeEquivalentSpellings() {
        listOf(
            "{\"same\":1,\"same\":2}",
            "{\"a\":1,\"\\u0061\":2}",
            "{\"/\":1,\"\\/\":2}",
        ).forEach { duplicate ->
            assertThrows(IllegalArgumentException::class.java) {
                AiJsonSyntaxValidator.requireValid(duplicate)
            }
        }

        AiJsonSyntaxValidator.requireValid("{\"outer\":{\"same\":1},\"same\":2}")
    }

    @Test
    fun enforcesDecodedUtf8ByteLimitForStringValuesAndObjectKeys() {
        val escapedAtLimit = "\\u0061".repeat(OnDeviceAiLimits.MAX_JSON_STRING_BYTES)
        AiJsonSyntaxValidator.requireValid("\"$escapedAtLimit\"")
        assertThrows(IllegalArgumentException::class.java) {
            AiJsonSyntaxValidator.requireValid("\"${escapedAtLimit}a\"")
        }

        val keyAtLimit = "a".repeat(OnDeviceAiLimits.MAX_JSON_STRING_BYTES)
        AiJsonSyntaxValidator.requireValid("{\"$keyAtLimit\":null}")
        assertThrows(IllegalArgumentException::class.java) {
            AiJsonSyntaxValidator.requireValid("{\"${keyAtLimit}a\":null}")
        }
    }

    @Test
    fun inlineSchemaToolResultAndCompletionJsonAreValidatedAtConstruction() {
        assertThrows(IllegalArgumentException::class.java) {
            AiToolDefinition("lookup", "Lookup", jsonPayload("{]"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            AiToolCall("call-1", "lookup", jsonPayload("[1,]"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            AiToolResult("call-1", jsonPayload("not-json"))
        }
        assertThrows(IllegalArgumentException::class.java) {
            AiCompletionResult(jsonPayload("{\"missing\":}"), AiFinishReason.STOP)
        }
    }

    @Test
    fun descriptorBackedSchemaToolAndResultJsonRequireMaterializedSyntaxValidation() {
        val malformed = "{]".toByteArray()
        val references = listOf(
            AiToolDefinition("lookup", "Lookup", descriptorJson(malformed.size, 0)).inputSchema,
            AiToolCall("call-1", "lookup", descriptorJson(malformed.size, 0)).arguments,
            AiToolResult("call-1", descriptorJson(malformed.size, 0)).output,
        )

        references.forEach { reference ->
            assertThrows(IllegalArgumentException::class.java) {
                OnDeviceAiPayloadPolicy.validateMaterialized(reference, malformed)
            }
        }
    }

    @Test
    fun terminalValidatorParsesDescriptorBackedStructuredJsonExactlyOnce() {
        val request = jsonRequest()
        val valid = "{\"ok\":true}".toByteArray()
        val result = AiCompletionResult(
            output = descriptorJson(valid.size, 0),
            finishReason = AiFinishReason.STOP,
        )

        val snapshot = OnDeviceAiQuotaPolicy.validateCompletion(
            request = request,
            result = result,
            descriptorCount = 1,
            maximumDescriptors = 1,
            readDescriptor = { index, length ->
                assertEquals(0, index)
                assertEquals(valid.size.toLong(), length)
                valid
            },
        )
        assertEquals("{\"ok\":true}", snapshot.outputText)

        val malformed = "{]".toByteArray()
        assertThrows(IllegalArgumentException::class.java) {
            OnDeviceAiQuotaPolicy.validateCompletion(
                request = request,
                result = AiCompletionResult(descriptorJson(malformed.size, 0), AiFinishReason.STOP),
                descriptorCount = 1,
                maximumDescriptors = 1,
                readDescriptor = { _, _ -> malformed },
            )
        }
    }

    private fun jsonRequest() = OnDeviceAiRequest(
        requestId = "json-request",
        protocolVersion = AiProtocolVersion(1, 0),
        providerId = "fake.local",
        modelId = "fake.echo",
        messages = listOf(AiMessage(AiMessageRole.USER, listOf(AiContentPart(textPayload("hello"))))),
        options = AiGenerationOptions(
            stream = false,
            includeReasoning = false,
            structuredJson = true,
            reportUsage = false,
            maximumOutputBytes = 1024,
            timeoutMillis = 30_000,
            responseMimeType = OnDeviceAiMimeType.JSON,
        ),
    )

    private fun descriptorJson(length: Int, index: Int) = AiPayloadReference(
        mimeType = OnDeviceAiMimeType.JSON,
        declaredLengthBytes = length.toLong(),
        descriptorIndex = index,
    )
}
