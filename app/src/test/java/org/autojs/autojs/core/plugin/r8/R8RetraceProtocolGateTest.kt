package org.autojs.autojs.core.plugin.r8

import org.autojs.plugin.r8compiler.api.R8CompilerCodec
import org.autojs.plugin.r8compiler.api.R8CompilerContract
import org.autojs.plugin.r8compiler.api.R8ProtocolVersion
import org.autojs.plugin.r8compiler.api.R8RetraceCodec
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class R8RetraceProtocolGateTest {
    @Test
    fun exactIdentityAndProtocol11ArePinned() {
        val negotiation = negotiate()

        assertEquals(R8CompilerContract.PROTOCOL_V1_1, negotiation.info.protocolMax)
        assertEquals(
            R8RetraceHostFixtures.capabilities().capabilityFingerprint,
            negotiation.capabilities.capabilityFingerprint,
        )
    }

    @Test
    fun protocol10AndIdentityDriftAreRejectedBeforeDispatch() {
        assertThrows(IllegalArgumentException::class.java) {
            negotiate(
                info = R8CompilerHostFixtures.info(
                    protocolMin = R8CompilerContract.PROTOCOL_V1,
                    protocolMax = R8CompilerContract.PROTOCOL_V1,
                ),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            negotiate(current = R8CompilerHostFixtures.candidate(updateTime = 3L))
        }
        assertThrows(IllegalArgumentException::class.java) {
            negotiate(
                info = R8CompilerHostFixtures.info(
                    protocolMin = R8ProtocolVersion(2, 0),
                    protocolMax = R8ProtocolVersion(2, 0),
                ),
            )
        }
    }

    @Test
    fun hostVersionBoundsRemainEnforcedForRetrace() {
        assertThrows(IllegalArgumentException::class.java) { negotiate(hostVersionCode = 5_269L) }
        assertThrows(IllegalArgumentException::class.java) {
            negotiate(
                info = R8CompilerHostFixtures.info(
                    protocolMax = R8CompilerContract.PROTOCOL_V1_1,
                    maxHostVersionCode = 5_269L,
                ),
            )
        }
    }

    private fun negotiate(
        current: R8CompilerServiceCandidate = R8CompilerHostFixtures.candidate(),
        info: org.autojs.plugin.r8compiler.api.R8CompilerInfo = R8CompilerHostFixtures.info(
            protocolMax = R8CompilerContract.PROTOCOL_V1_1,
        ),
        hostVersionCode: Long = 5_270L,
    ) = R8RetraceProtocolGate.negotiate(
        identity = R8CompilerHostFixtures.identity(),
        currentCandidate = current,
        encodedInfo = R8CompilerCodec.encodeInfo(info),
        encodedCapabilities = R8RetraceCodec.encodeCapabilities(R8RetraceHostFixtures.capabilities()),
        hostVersionCode = hostVersionCode,
    )
}
