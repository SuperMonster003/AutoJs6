package org.autojs.plugin.host.capability.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HostCapabilityContractTest {
    @Test
    fun versionOneDataPlaneStaysIndependentOfFamilyControlVersions() {
        assertEquals(1, HostCapabilityContract.CONTRACT_VERSION)
        assertTrue(HostCapabilityContract.supportsContractVersion(1))
        assertTrue(HostCapabilityContract.supportsHostCapabilityBrokerContractVersion(1))
        assertFalse(HostCapabilityContract.supportsContractVersion(0))
        assertFalse(HostCapabilityContract.supportsContractVersion(2))
    }

    @Test
    fun bundleKeysAreUniqueAndPreserveBridgeWireLiterals() {
        val keys = HostCapabilityContract::class.java.fields
            .filter { it.name.startsWith("KEY_") }
            .map { it.get(null) as String }
        assertEquals(keys.size, keys.toSet().size)
        keys.forEach { assertTrue(it, it.matches(Regex("[a-z][a-zA-Z0-9]+"))) }
        assertEquals("bridgeRequestJson", HostCapabilityContract.KEY_BRIDGE_REQUEST_JSON)
        assertEquals("bridgeResponseJson", HostCapabilityContract.KEY_BRIDGE_RESPONSE_JSON)
        assertEquals("bridgePayloadFd", HostCapabilityContract.KEY_BRIDGE_PAYLOAD_FD)
        assertEquals("hostCapabilityBrokerVersion", HostCapabilityContract.KEY_HOST_CAPABILITY_BROKER_VERSION)
        assertEquals("grantJson", HostCapabilityContract.KEY_GRANT_JSON)
        assertEquals("reasonJson", HostCapabilityContract.KEY_REASON_JSON)
    }

    @Test
    fun errorCategoriesMatchTheClosedNodeBridgeVocabulary() {
        assertEquals(
            setOf("unavailable", "permission-denied", "capability-denied", "provider-failed",
                "invalid-request", "resource-limit", "rate-limited", "timeout", "process-dead", "runtime-error"),
            HostCapabilityContract.ERROR_CATEGORIES,
        )
    }

    @Test
    fun ceilingsKeepInlineTransactionsBelowBinderLimitAndPayloadsBounded() {
        assertEquals(512 * 1024, HostCapabilityContract.MAX_BRIDGE_INLINE_JSON_BYTES)
        assertEquals(8 * 1024 * 1024, HostCapabilityContract.MAX_BRIDGE_PAYLOAD_BYTES)
        assertTrue(HostCapabilityContract.MAX_BRIDGE_INLINE_JSON_BYTES < 1024 * 1024)
        assertTrue(HostCapabilityContract.MAX_BRIDGE_INLINE_JSON_BYTES < HostCapabilityContract.MAX_BRIDGE_PAYLOAD_BYTES)
        assertTrue(HostCapabilityContract.DEFAULT_TOOL_TIMEOUT_MS <= HostCapabilityContract.MAX_TOOL_TIMEOUT_MS)
        assertTrue(HostCapabilityContract.MAX_DUMP_TEXT_BYTES <= HostCapabilityContract.MAX_BRIDGE_INLINE_JSON_BYTES)
        assertTrue(HostCapabilityContract.MAX_SCREENSHOT_ENCODED_BYTES <= HostCapabilityContract.MAX_BRIDGE_PAYLOAD_BYTES)
        HostCapabilityContract::class.java.fields
            .filter { it.name.startsWith("MAX_") || it.name.startsWith("DEFAULT_") }
            .forEach { assertTrue(it.name, (it.get(null) as Number).toLong() > 0) }
    }
}
