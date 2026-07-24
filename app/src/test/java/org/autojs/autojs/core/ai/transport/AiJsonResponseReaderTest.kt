package org.autojs.autojs.core.ai.transport

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class AiJsonResponseReaderTest {

    @Test
    fun readsBomPrefixedJsonAndCanNormalizeItDirectly() {
        val response = AiJsonResponseReader.parse(
            providerId = "openai",
            body = (
                "\uFEFF" +
                    """{"choices":[{"message":{"content":"Hello"},"finish_reason":"stop"}]}"""
                ).jsonBody(),
        )

        assertEquals("Hello", response.text)
        assertEquals("stop", response.finishReason)
    }

    @Test
    fun rejectsBodyLargerThanConfiguredByteLimitWithoutIncludingPayload() {
        val secret = "response-secret-value"
        val exception = assertThrows(AiResponseLimitExceededException::class.java) {
            AiJsonResponseReader.read(
                """{"value":"$secret"}""".jsonBody(),
                maxResponseBytes = 8,
            )
        }

        assertEquals(8L, exception.limitBytes)
        assertFalse(exception.message.orEmpty().contains(secret))
        assertFalse(exception.stackTraceToString().contains(secret))
    }

    @Test
    fun invalidJsonErrorDoesNotRetainProviderPayloadOrParserCause() {
        val secret = "malformed-response-secret"
        val exception = assertThrows(AiResponseProtocolException::class.java) {
            AiJsonResponseReader.read("""{"value":"$secret"""".jsonBody())
        }

        assertFalse(exception.message.orEmpty().contains(secret))
        assertFalse(exception.stackTraceToString().contains(secret))
        assertNull(exception.cause)
    }

    @Test
    fun exactByteLimitIsAccepted() {
        val body = "{}"

        val parsed = AiJsonResponseReader.read(
            body.jsonBody(),
            maxResponseBytes = body.toByteArray().size.toLong(),
        )

        assertTrue(parsed.isJsonObject)
    }

    @Test
    fun rejectsSuccessfulJsonWithoutProviderResponseShape() {
        val secret = "provider-shape-secret"
        val bodies = listOf(
            "openai" to """{"usage":{},"private":"$secret"}""",
            "anthropic" to """{"stop_reason":"end_turn","private":"$secret"}""",
            "gemini" to """{"promptFeedback":{"blockReason":"SAFETY"},"private":"$secret"}""",
        )

        bodies.forEach { (providerId, json) ->
            val exception = assertThrows(AiResponseProtocolException::class.java) {
                AiJsonResponseReader.parse(providerId, json.jsonBody())
            }
            assertFalse(exception.message.orEmpty().contains(secret))
            assertFalse(exception.stackTraceToString().contains(secret))
            if (providerId == "gemini") {
                assertTrue(exception.message.orEmpty().contains("SAFETY"))
            }
        }
    }

    private fun String.jsonBody() = toResponseBody("application/json".toMediaType())

}
