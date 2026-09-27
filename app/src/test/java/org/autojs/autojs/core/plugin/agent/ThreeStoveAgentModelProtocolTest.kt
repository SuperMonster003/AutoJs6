package org.autojs.autojs.core.plugin.agent

import com.google.gson.JsonParser
import org.autojs.autojs.core.plugin.ai.AiPluginAskFailure
import org.autojs.autojs.core.plugin.ai.AiPluginProviderFailureReason
import org.autojs.autojs.core.plugin.ai.AiPluginMessageRole
import org.autojs.autojs.core.plugin.ai.AiPluginUsage
import org.autojs.plugin.three.stove.agent.api.ThreeStoveAgentContract
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ThreeStoveAgentModelProtocolTest {
    @Test fun structuredRequestPreservesUtf8AndNeverPrintsPromptOrSchema() {
        val value = valid().apply {
            addProperty("structuredJson", true)
            add("responseSchema", JsonParser.parseString("""{"type":"object","description":"private-schema"}"""))
            addProperty("temperature", 0.2)
            addProperty("maximumOutputTokens", 1024)
        }
        val request = decode(value.toString())
        assertEquals("request-1", request.requestId)
        assertEquals("local:gemma", request.targetId)
        assertEquals(AiPluginMessageRole.USER, request.messages.single().role)
        assertEquals("private-prompt 中文", request.messages.single().content)
        assertTrue(request.inputBytes > request.messages.single().content.length)
        assertEquals(1024L, request.maximumOutputTokens)
        assertEquals(0.2, request.temperature!!, 0.0)
        assertFalse(request.toString().contains("private"))
    }

    @Test fun structuredModeHasObjectSchemaDefaultAndRejectsConflictingFlag() {
        assertEquals("{\"type\":\"object\"}", decode(valid().apply { addProperty("structuredJson", true) }.toString()).responseSchemaJson)
        assertNull(decode(valid().toString()).responseSchemaJson)
        rejected(valid().apply { add("responseSchema", JsonParser.parseString("{}")) }.toString())
    }

    @Test fun rolesOrderingFieldNamesAndTypesAreStrict() {
        listOf(
            """[{"role":"tool","content":"x"}]""",
            """[{"role":"assistant","content":"x"}]""",
            """[{"role":"user","content":4}]""",
            """[{"role":"user","content":"x","extra":true}]""",
            "[]",
        ).forEach { messages -> rejected(valid().apply { add("messages", JsonParser.parseString(messages)) }.toString()) }
        listOf("stream", "structuredJson").forEach { name -> rejected(valid().apply { addProperty(name, "true") }.toString()) }
        rejected(valid().apply { addProperty("unexpected", 1) }.toString())
    }

    @Test fun invalidIdentifiersTimeoutsAndSamplingControlsDoNotLeakText() {
        listOf("targetId" to "secret invalid target", "requestId" to "secret\nrequest").forEach { (key, value) ->
            rejected(valid().apply { addProperty(key, value) }.toString())
        }
        listOf(0, 999, 600001).forEach { rejected(valid().apply { addProperty("timeoutMs", it) }.toString()) }
        rejected(valid().apply { addProperty("timeoutMs", 1000.5) }.toString())
        rejected(valid().apply { addProperty("maximumOutputTokens", 0) }.toString())
        rejected(valid().apply { addProperty("temperature", -1) }.toString())
        val failure = assertThrows(ThreeStoveAgentModelException::class.java) { decode("private invalid JSON") }
        assertEquals(ThreeStoveAgentContract.ERROR_INVALID_REQUEST, failure.message)
        assertNull(failure.cause)
    }

    @Test fun toolDefinitionsRequireAnExplicitRoundLimit() {
        rejected(valid().apply { add("tools", JsonParser.parseString("[]")) }.toString())
        val toolRequest = valid().apply {
            add("tools", JsonParser.parseString("""[{"name":"ui_read","description":"Private description","inputSchema":{"type":"object"}}]"""))
            addProperty("maximumToolRounds", 16)
        }
        val decoded = decode(toolRequest.toString())
        assertEquals(16, decoded.maximumToolRounds)
        assertEquals("ui_read", decoded.tools.single().name)
        assertTrue(decoded.inputBytes > decode(valid().toString()).inputBytes)
        assertFalse(decoded.toString().contains("Private"))
        listOf(0, 17, -1).forEach { rounds -> rejected(toolRequest.deepCopy().apply { addProperty("maximumToolRounds", rounds) }.toString()) }
        rejected(toolRequest.deepCopy().apply { remove("maximumToolRounds") }.toString())
        rejected(toolRequest.deepCopy().apply { getAsJsonArray("tools").add(getAsJsonArray("tools")[0].deepCopy()) }.toString())
        rejected(toolRequest.deepCopy().apply { addProperty("maximumToolRounds", 1.5) }.toString())
    }

    @Test fun toolResultEnvelopesAreBoundedTypedAndContentFreeInDiagnostics() {
        val value = """{"requestId":"r","round":1,"results":[{"callId":"call-1","output":"private-output","isError":true}]}"""
        val decoded = ThreeStoveAgentModelRequests.toolResults(value)
        assertEquals("r", decoded.requestId)
        assertTrue(decoded.results.single().isError)
        assertFalse(decoded.toString().contains("private"))
        assertFalse(decoded.results.single().toString().contains("private"))
        listOf(value.replace("\"round\":1", "\"round\":0"), value.replace("true", "\"true\""),
            value.replace("\"private-output\"", "{}"), value.replace("call-1", "call\\n1"),
            value.replace("private-output", "x".repeat(65537))).forEach { invalid ->
            assertThrows(ThreeStoveAgentModelException::class.java) { ThreeStoveAgentModelRequests.toolResults(invalid) }
        }
    }

    @Test fun nativeRoundUsageReplacesEstimatesAndChargesEveryContinuationAgainstRateLimit() {
        val quota = ThreeStoveAgentModelQuota(3, 1000) { 0 }
        assertTrue(quota.reserve("native", 100, 10))
        val first = quota.settleCumulative("native", null, 100, 10, 0)
        assertEquals(44L, first.second)
        val lateUsage = quota.settleCumulative("native", AiPluginUsage(10, 5, 15, null), 100, 10,
            first.second, releaseReservation = false)
        assertEquals(15L, quota.consumedTokens())
        assertTrue(quota.reserve("native", 120, 10))
        val second = quota.settleCumulative("native", AiPluginUsage(20, 10, 30, null), 220, 20, lateUsage.second)
        assertEquals(30L, quota.consumedTokens())
        assertTrue(quota.reserve("native", 140, 10))
        assertTrue(quota.settleCumulative("native", AiPluginUsage(30, 15, 45, null), 360, 30, second.second).first)
        assertEquals(45L, quota.consumedTokens())
        assertFalse(quota.reserve("native", 1, 1))
    }

    @Test fun inputAndSchemaByteLimitsRejectBeforeProviderInvocation() {
        val json = valid().toString()
        val size = decode(json).inputBytes.toInt()
        assertEquals(size.toLong(), ThreeStoveAgentModelRequests.decode(json, size).inputBytes)
        assertEquals(ThreeStoveAgentContract.ERROR_QUOTA_EXCEEDED,
            assertThrows(ThreeStoveAgentModelException::class.java) { ThreeStoveAgentModelRequests.decode(json, size - 1) }.code)
        rejected(valid().apply {
            addProperty("structuredJson", true)
            add("responseSchema", JsonParser.parseString("""{"description":"${"x".repeat(16384)}"}"""))
        }.toString(), ThreeStoveAgentContract.ERROR_LIMIT_EXCEEDED)
    }

    @Test fun protocolRejectsCommentsDuplicateKeysAndTrailingData() {
        rejected("/*comment*/${valid()}")
        rejected("${valid()} {}")
        rejected(valid().toString().replace("\"requestId\":", "\"requestId\":\"duplicate\",\"requestId\":"))
    }

    @Test fun catalogAndCancellationAcceptOnlyPublicCorrelationFields() {
        assertEquals("request-1" to 30000L, ThreeStoveAgentModelRequests.catalog("""{"requestId":"request-1"}"""))
        assertEquals("request-1", ThreeStoveAgentModelRequests.reference("""{"requestId":"request-1","reason":"user"}"""))
        assertThrows(ThreeStoveAgentModelException::class.java) { ThreeStoveAgentModelRequests.catalog("""{"requestId":"r","profile":"secret"}""") }
        assertEquals("", ThreeStoveAgentModelRequests.recoverId("bad"))
    }

    @Test fun rollingMinuteQuotaCountsStartedCallsAndDoesNotRefundRateOnFailure() {
        var now = 0L
        val quota = ThreeStoveAgentModelQuota(2, 100000) { now }
        assertTrue(quota.reserve("a", 10, 10))
        quota.settle("a", null, 10, 0)
        now = 1000
        assertTrue(quota.reserve("b", 10, 10))
        quota.settle("b", null, 10, 0)
        assertFalse(quota.reserve("c", 10, 10))
        now = 60000
        assertTrue(quota.reserve("c", 10, 10))
        assertFalse(quota.reserve("d", 10, 10))
        now = 61000
        assertTrue(quota.reserve("d", 10, 10))
    }

    @Test fun reservationsPreventConcurrentOverspendAndSettleExactlyOnce() {
        val quota = ThreeStoveAgentModelQuota(30, 100) { 0 }
        assertTrue(quota.reserve("a", 100, 20))
        assertFalse(quota.reserve("a", 1, 1))
        assertFalse(quota.reserve("b", 100, 20))
        assertTrue(quota.settle("a", AiPluginUsage(10, 5, 15, null), 100, 30))
        quota.settle("a", null, 1000, 1000)
        assertEquals(15L, quota.consumedTokens())
        assertTrue(quota.reserve("b", 100, 20))
    }

    @Test fun absentAndPartialUsageUseUtf8ByteEstimateAndProviderOverflowClosesBudget() {
        val quota = ThreeStoveAgentModelQuota(30, 100) { 0 }
        assertTrue(quota.reserve("a", 10, 10))
        quota.settle("a", null, 10, 1)
        assertEquals(5L, quota.consumedTokens())
        assertTrue(quota.reserve("b", 10, 10))
        quota.settle("b", AiPluginUsage(2, null, null, null), 10, 5)
        assertEquals(9L, quota.consumedTokens())
        assertTrue(quota.reserve("c", 10, 10))
        assertFalse(quota.settle("c", AiPluginUsage(90, 90, 180, null), 10, 5))
        assertFalse(quota.reserve("d", 1, 1))
    }

    @Test fun eventSequenceAllowsOnlyOneStartAndOneTerminal() {
        val sequence = ThreeStoveAgentModelEventSequence()
        assertNull(sequence.next("chunk"))
        assertEquals(1L, sequence.next("started"))
        assertNull(sequence.next("started"))
        assertEquals(2L, sequence.next("chunk"))
        assertEquals(3L, sequence.next("chunk"))
        assertEquals(4L, sequence.next("usage"))
        assertEquals(5L, sequence.next("completed"))
        listOf("chunk", "failed", "cancelled", "completed", "started").forEach { assertNull(sequence.next(it)) }
    }

    @Test fun extremeProviderUsageCannotWrapTheRemainingTokenBudget() {
        val quota = ThreeStoveAgentModelQuota(30, 100) { 0 }
        assertTrue(quota.reserve("a", 1, 1))
        assertTrue(quota.reserve("b", 1, 1))
        assertFalse(quota.settle("a", AiPluginUsage(Long.MAX_VALUE, Long.MAX_VALUE, null, null), 1, 1))
        assertEquals(Long.MAX_VALUE, quota.consumedTokens())
        assertFalse(quota.reserve("c", 1, 1))
        assertFalse(quota.settle("b", AiPluginUsage(1, 1, 2, null), 1, 1))
        assertEquals(Long.MAX_VALUE, quota.consumedTokens())
    }

    @Test fun cancellationAndProviderErrorsUseStableCodes() {
        assertEquals(ThreeStoveAgentContract.ERROR_MODEL_TIMEOUT, AiPluginAskFailure.TIMED_OUT.agentModelError())
        assertEquals(ThreeStoveAgentContract.ERROR_CANCELLED, AiPluginAskFailure.CANCELLED.agentModelError())
        assertEquals(ThreeStoveAgentContract.ERROR_TARGET_UNSUPPORTED, AiPluginAskFailure.TARGET_CAPABILITY_MISMATCH.agentModelError())
        assertEquals(ThreeStoveAgentContract.ERROR_MODEL_FAILED, AiPluginAskFailure.PROVIDER_FAILED.agentModelError())
        val sequence = ThreeStoveAgentModelEventSequence()
        sequence.next("started")
        assertEquals(2L, sequence.next("cancelled"))
        assertNull(sequence.next("completed"))
    }

    @Test fun providerDiagnosticsDoNotChangePrimaryErrorsOrStructuredFallbackReasons() {
        val network = AiPluginProviderFailureReason.ONLINE_NETWORK_UNAVAILABLE
        assertEquals("ONLINE_NETWORK_UNAVAILABLE", AiPluginAskFailure.PROVIDER_FAILED.agentModelReason(network))
        assertEquals("PROVIDER_FAILED", AiPluginAskFailure.PROVIDER_FAILED.agentModelReason(null))
        assertEquals("ONLINE_REQUEST_REJECTED", AiPluginAskFailure.PROVIDER_FAILED.agentModelReason(
            AiPluginProviderFailureReason.ONLINE_REQUEST_REJECTED))
        assertEquals(ThreeStoveAgentContract.ERROR_MODEL_FAILED, AiPluginAskFailure.PROVIDER_FAILED.agentModelError())
        AiPluginAskFailure.entries.filter { it != AiPluginAskFailure.PROVIDER_FAILED }.forEach { failure ->
            assertEquals(failure.name, failure.agentModelReason(network))
        }
        assertEquals("TIMED_OUT", AiPluginAskFailure.TIMED_OUT.agentModelReason(network))
        assertEquals("CANCELLED", AiPluginAskFailure.CANCELLED.agentModelReason(network))
    }

    private fun valid() = JsonParser.parseString("""{"requestId":"request-1","targetId":"local:gemma","messages":[{"role":"user","content":"private-prompt 中文"}]}""").asJsonObject
    private fun decode(json: String) = ThreeStoveAgentModelRequests.decode(json, 128 * 1024)
    private fun rejected(json: String, code: String = ThreeStoveAgentContract.ERROR_INVALID_REQUEST) {
        assertEquals(code, assertThrows(ThreeStoveAgentModelException::class.java) { decode(json) }.code)
    }
}
