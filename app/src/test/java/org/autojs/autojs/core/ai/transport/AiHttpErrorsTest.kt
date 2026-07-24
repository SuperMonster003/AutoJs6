package org.autojs.autojs.core.ai.transport

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AiHttpErrorsTest {

    @Test
    fun redactsKeyFromEveryExposedExceptionFieldAndMessage() {
        val key = """sk-"quoted"-secret"""
        val config = AiProviderConfig(
            providerId = key,
            baseUrl = "https://example.test/v1",
            model = "test-model",
            apiKey = key,
        )
        val body = """
            {
              "error":{
                "type":"type-$key",
                "code":"code-$key",
                "message":"Provider echoed $key"
              }
            }
        """.trimIndent()
        val response = response(
            code = 401,
            body = body,
            requestId = "request-$key",
        )

        val exception = AiHttpErrors.fromResponse(response, config)

        assertEquals(401, exception.statusCode)
        assertFalse(exception.providerId.contains(key))
        assertFalse(exception.errorType.orEmpty().contains(key))
        assertFalse(exception.errorCode.orEmpty().contains(key))
        assertFalse(exception.requestId.orEmpty().contains(key))
        assertFalse(exception.responsePreview.contains(key))
        assertFalse(exception.message.orEmpty().contains(key))
        assertFalse(exception.stackTraceToString().contains(key))
        assertTrue(exception.responsePreview.contains("<redacted>"))
    }

    @Test
    fun boundsErrorPreviewAndMarksTruncation() {
        val response = response(
            code = 503,
            body = "0123456789ABCDEFGHIJKLMNOPQRSTUVWXYZ",
        )

        val exception = AiHttpErrors.fromResponse(
            response = response,
            config = config(),
            maxErrorBytes = 10,
        )

        assertEquals("0123456789", exception.responsePreview)
        assertTrue(exception.responseTruncated)
        assertEquals(10, exception.responsePreview.toByteArray().size)
    }

    @Test
    fun readsStructuredTopLevelFallbackMessageWithoutReturningRawBodyInMessage() {
        val response = response(
            code = 429,
            body = """{"message":"Slow down","unrelated":"private prompt"}""",
        )

        val exception = AiHttpErrors.fromResponse(response, config())

        assertTrue(exception.message.orEmpty().contains("Slow down"))
        assertFalse(exception.message.orEmpty().contains("private prompt"))
        assertEquals("private prompt", Regex("private prompt").find(exception.responsePreview)?.value)
    }

    @Test
    fun collapsesControlCharactersAndBoundsEveryLogFacingMetadataField() {
        val longType = "type\r\n\t" + "T".repeat(600)
        val longCode = "code\u0000\u001F" + "C".repeat(600)
        val longMessage = "message\r\n\t" + "M".repeat(600)
        val body = com.google.gson.JsonObject().apply {
            add("error", com.google.gson.JsonObject().apply {
                addProperty("type", longType)
                addProperty("code", longCode)
                addProperty("message", longMessage)
            })
        }.toString()
        val response = response(
            code = 400,
            body = body,
            requestId = "request\t" + "R".repeat(600),
        )

        val exception = AiHttpErrors.fromResponse(response, config())

        listOf(
            exception.errorType,
            exception.errorCode,
            exception.requestId,
        ).forEach { value ->
            requireNotNull(value)
            assertTrue(value.length <= 512)
            assertFalse(value.any { Character.isISOControl(it) })
        }
        assertFalse(exception.message.orEmpty().any { Character.isISOControl(it) })
        assertFalse(exception.message.orEmpty().contains("M".repeat(513)))
    }

    private fun response(
        code: Int,
        body: String,
        requestId: String? = null,
    ): Response {
        return Response.Builder()
            .request(Request.Builder().url("https://example.test/v1/messages").build())
            .protocol(Protocol.HTTP_1_1)
            .code(code)
            .message("HTTP $code")
            .apply {
                if (requestId != null) {
                    header("x-request-id", requestId)
                }
            }
            .body(body.toResponseBody("application/json".toMediaType()))
            .build()
    }

    private fun config(): AiProviderConfig {
        return AiProviderConfig(
            providerId = "openai",
            baseUrl = "https://example.test/v1",
            model = "test-model",
            apiKey = "sk-test-secret",
        )
    }

}
