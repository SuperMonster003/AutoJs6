package org.autojs.plugin.ondeviceai.fake

import org.autojs.plugin.ai.common.api.AiValidation
import org.autojs.plugin.ondeviceai.api.OnDeviceAiMimeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeOnDeviceAiEngineTest {
    @Test
    fun echoPlanIsDeterministicAndReportsStableUsage() {
        val request = fakeRequest(prompt = "hello")
        val materialized = FakeMaterializedRequest("hello", 5L)

        val first = FakeOnDeviceAiEngine.plan(request, materialized)
        val second = FakeOnDeviceAiEngine.plan(request, materialized)

        assertEquals(first, second)
        assertEquals("hello", first.output)
        assertEquals(2L, first.usage.inputTokens)
        assertEquals(2L, first.usage.outputTokens)
        assertEquals(4L, first.usage.totalTokens)
    }

    @Test
    fun streamChunksReassembleUnicodeWithoutSplittingCodePoints() {
        val prompt = "a😀bc😀defghijklmnopqrstuvwxyz"
        val request = fakeRequest(
            modelId = FakeOnDeviceAiFixture.MODEL_STREAM,
            prompt = prompt,
            stream = true,
        )
        val plan = FakeOnDeviceAiEngine.plan(
            request,
            FakeMaterializedRequest(prompt, AiValidation.utf8Size(prompt).toLong()),
        )

        assertEquals(prompt, plan.chunks.joinToString(separator = "") { it.textDelta })
        assertTrue(plan.chunks.all { AiValidation.utf8Size(it.textDelta) <= 12 })
    }

    @Test
    fun reasoningPlanSeparatesReasoningFromVisibleText() {
        val request = fakeRequest(
            modelId = FakeOnDeviceAiFixture.MODEL_REASONING,
            stream = true,
            reasoning = true,
        )
        val plan = FakeOnDeviceAiEngine.plan(request, FakeMaterializedRequest("hello", 5L))

        assertEquals("deterministic reasoning", plan.chunks.first().reasoningDelta)
        assertEquals("hello", plan.chunks.drop(1).joinToString(separator = "") { it.textDelta })
        assertTrue(plan.reasoningSummary != null)
        assertEquals(2L, plan.usage.reasoningTokens)
    }

    @Test
    fun outputTokenAndReasoningByteLimitsAreHonoredTogether() {
        val tokenLimited = FakeOnDeviceAiEngine.plan(
            fakeRequest(prompt = "a long deterministic prompt", maximumOutputTokens = 1),
            FakeMaterializedRequest("a long deterministic prompt", 27L),
        )
        assertTrue(tokenLimited.outputPayload.declaredLengthBytes <= 4L)
        assertTrue(requireNotNull(tokenLimited.usage.outputTokens) <= 1L)

        val reasoningLimitedRequest = fakeRequest(
            modelId = FakeOnDeviceAiFixture.MODEL_REASONING,
            stream = true,
            reasoning = true,
            maximumOutputBytes = 16L,
        )
        val reasoningLimited = FakeOnDeviceAiEngine.plan(
            reasoningLimitedRequest,
            FakeMaterializedRequest("hello", 5L),
        )
        val streamedBytes = reasoningLimited.chunks.sumOf { chunk ->
            AiValidation.utf8Size(chunk.textDelta).toLong() + AiValidation.utf8Size(chunk.reasoningDelta).toLong()
        }
        val terminalBytes = reasoningLimited.outputPayload.declaredLengthBytes +
            (reasoningLimited.reasoningSummary?.declaredLengthBytes ?: 0L)
        assertTrue(streamedBytes <= reasoningLimitedRequest.options.maximumOutputBytes)
        assertTrue(terminalBytes <= reasoningLimitedRequest.options.maximumOutputBytes)
    }

    @Test
    fun jsonMimeAlwaysProducesJsonEvenWithoutStructuredFlag() {
        val request = fakeRequest(
            responseMimeType = OnDeviceAiMimeType.JSON,
            structuredJson = false,
            prompt = "quote \" and newline\n",
        )
        val plan = FakeOnDeviceAiEngine.plan(request, FakeMaterializedRequest("quote \" and newline\n", 20L))

        assertEquals("{\"echo\":\"quote \\\" and newline\\n\"}", plan.output)
        assertEquals(OnDeviceAiMimeType.JSON, plan.outputPayload.mimeType)
    }

    @Test
    fun tinyJsonLimitStillProducesAValidJsonValue() {
        val request = fakeRequest(
            responseMimeType = OnDeviceAiMimeType.JSON,
            maximumOutputBytes = 1L,
        )
        val plan = FakeOnDeviceAiEngine.plan(request, FakeMaterializedRequest("hello", 5L))

        assertEquals("0", plan.output)
        assertEquals(1, plan.outputPayload.inlineBytes?.size)
    }

    @Test
    fun toolCallAndResultOutputAreDeterministic() {
        val request = fakeRequest(
            modelId = FakeOnDeviceAiFixture.MODEL_TOOLS,
            stream = true,
            tools = listOf(fakeTool()),
        )
        val calls = FakeOnDeviceAiEngine.createToolCalls(request)
        val plan = FakeOnDeviceAiEngine.plan(request, FakeMaterializedRequest("hello", 5L), "result")

        assertEquals(FakeOnDeviceAiEngine.TOOL_CALL_ID, calls.calls.single().callId)
        assertEquals("lookup", calls.calls.single().name)
        assertEquals("tool:result", plan.output)
        assertFalse(plan.chunks.isEmpty())
    }
}
