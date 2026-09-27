package org.autojs.autojs.core.plugin.agent

import com.google.gson.JsonParser
import org.autojs.autojs.core.plugin.ai.AiPluginUsage
import org.autojs.plugin.three.stove.agent.api.ThreeStoveAgentContract
import org.junit.Assert.*
import org.junit.Test

class ThreeStoveAgentModelVisionTest {
    private fun image(index: Int = 0, message: String = ",\"messageIndex\":0") =
        """{"descriptorIndex":$index,"mimeType":"image/png","byteLength":100,"width":100,"height":100,"sha256":"${"a".repeat(64)}"$message}"""
    private fun request(refs: String = "[${image()}]") =
        """{"requestId":"vision","targetId":"local:vision","messages":[{"role":"user","content":"look"}],"imageRefs":$refs}"""
    private fun decode(value: String) = ThreeStoveAgentModelRequests.decode(value, 4096)

    @Test fun imageRequestOptsIntoVisionButPreservesIndependentTextAccounting() {
        val value = decode(request())
        assertTrue(value.vision)
        assertEquals(4L, value.inputBytes)
        assertEquals(1088L, value.imageTokens)
        assertEquals(0, value.imageRefs.single().messageIndex)
        assertFalse(value.toString().contains("aaaa"))
        assertFalse(decode(request("[]")).vision)
        assertTrue(decode(request("[]").dropLast(1) + ",\"vision\":true}").vision)
    }

    @Test fun rejectsWrongIndicesMimeHashDimensionsAndUntypedReferences() {
        val original = request()
        val invalid = listOf(request("[${image(1)}]"), request("[${image()},${image()}]"),
            original.replace("\"messageIndex\":0", "\"messageIndex\":1"),
            original.replace("\"messageIndex\":0", "\"messageIndex\":0.5"),
            original.replace("\"width\":100", "\"width\":4097"),
            original.replace("image/png", "image/webp"), original.replace("a".repeat(64), "secret-digest"),
            original.replace("\"byteLength\":100", "\"byteLength\":4194305"),
            original.replace("\"height\":100", "\"height\":\"100\""),
            request("[${image(message = "")}]"), request("{}"),
            request("[${image().dropLast(1)},\"base64\":\"secret-image\"}]"))
        invalid.forEach { json ->
            val failure = assertThrows(ThreeStoveAgentModelException::class.java) { decode(json) }
            assertEquals(ThreeStoveAgentContract.ERROR_INVALID_REQUEST, failure.message)
            assertNull(failure.cause)
        }
    }

    @Test fun nativeResultsShareOneDescriptorIndexSpaceAndRequireExactReferences() {
        val json = """{"requestId":"vision","round":1,"results":[
            {"callId":"a","output":"one","imageRefs":[${image(message = "")}]},
            {"callId":"b","output":"two","imageRefs":[${image(1, "")}]}]}"""
        val result = ThreeStoveAgentModelRequests.toolResults(json)
        assertEquals(listOf(0, 1), result.imageRefs.map { it.descriptorIndex })
        assertEquals(6L, result.inputBytes)
        assertEquals(2176L, result.imageTokens)
        assertThrows(ThreeStoveAgentModelException::class.java) {
            ThreeStoveAgentModelRequests.toolResults(json.replace("\"descriptorIndex\":1", "\"descriptorIndex\":0"))
        }
        assertThrows(IllegalArgumentException::class.java) { ThreeStoveAgentModelImages.validate(result.imageRefs, 1) }
    }

    @Test fun imageEstimatesAreReservedAcrossContinuationsAndReconciledWithProviderUsage() {
        val quota = ThreeStoveAgentModelQuota(10, 5000) { 0 }
        assertFalse(quota.reserve("too-large", 10, 10, 5000))
        assertTrue(quota.reserve("vision", 10, 10, 1088))
        val first = quota.settleCumulative("vision", null, 10, 10, 0, estimatedImageTokens = 1088)
        assertEquals(1096L, first.second)
        assertTrue(quota.reserve("vision", 20, 10, 2176))
        val second = quota.settleCumulative("vision", null, 30, 20, first.second, estimatedImageTokens = 3264)
        assertEquals(3284L, second.second)
        val actual = quota.settleCumulative("vision", AiPluginUsage(180, 20, 200, null), 30, 20,
            second.second, releaseReservation = false, estimatedImageTokens = 3264)
        assertEquals(200L, actual.second)
        assertEquals(200L, quota.consumedTokens())
    }
}
