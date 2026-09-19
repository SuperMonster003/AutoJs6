package org.autojs.autojs.core.plugin.r8

import org.autojs.plugin.r8compiler.api.R8CompilerCodec
import org.autojs.plugin.r8compiler.api.R8CompilerContract
import org.autojs.plugin.r8compiler.api.R8ProtocolVersion
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class R8CompilerProtocolGateTest {
    @Test
    fun exactIdentityProtocolProviderAndCapabilitiesNegotiate() {
        val candidate = R8CompilerHostFixtures.candidate()
        val capabilities = R8CompilerHostFixtures.capabilities()
        val negotiation = R8CompilerProtocolGate.negotiate(
            candidate.pin(),
            candidate,
            R8CompilerCodec.encodeInfo(R8CompilerHostFixtures.info()),
            R8CompilerCodec.encodeCapabilities(capabilities),
            hostVersionCode = 5_270L,
            minApi = 24,
        )
        assertEquals(R8CompilerContract.PROTOCOL_V1, negotiation.info.protocolMin)
        assertEquals("8.13.17", negotiation.capabilities.compilerVersion)
        assertEquals(candidate.pin(), negotiation.identity)
    }

    @Test
    fun packageVersionOrSignerDriftIsRejectedBeforeHandshake() {
        expectRejected {
            negotiate(current = R8CompilerHostFixtures.candidate(updateTime = 3L))
        }
        expectRejected {
            negotiate(current = R8CompilerHostFixtures.candidate(sameSigner = false))
        }
    }

    @Test
    fun providerIdVersionAndProtocolArePinned() {
        expectRejected { negotiate(info = R8CompilerHostFixtures.info(providerId = "not-autojs6-r8")) }
        expectRejected { negotiate(info = R8CompilerHostFixtures.info(versionCode = 2L)) }
        expectRejected {
            negotiate(
                info = R8CompilerHostFixtures.info(
                    protocolMin = R8ProtocolVersion(2, 0),
                    protocolMax = R8ProtocolVersion(2, 0),
                ),
            )
        }
    }

    @Test
    fun hostAndMinApiCompatibilityArePinned() {
        expectRejected { negotiate(hostVersionCode = 5_269L) }
        expectRejected { negotiate(minApi = 23) }
        expectRejected {
            negotiate(info = R8CompilerHostFixtures.info(maxHostVersionCode = 5_269L))
        }
    }

    private fun negotiate(
        current: R8CompilerServiceCandidate = R8CompilerHostFixtures.candidate(),
        info: org.autojs.plugin.r8compiler.api.R8CompilerInfo = R8CompilerHostFixtures.info(),
        hostVersionCode: Long = 5_270L,
        minApi: Int = 24,
    ) = R8CompilerProtocolGate.negotiate(
        R8CompilerHostFixtures.identity(),
        current,
        R8CompilerCodec.encodeInfo(info),
        R8CompilerCodec.encodeCapabilities(R8CompilerHostFixtures.capabilities()),
        hostVersionCode,
        minApi,
    )

    private fun expectRejected(action: () -> Unit) {
        try {
            action()
            fail("Expected R8 negotiation rejection")
        } catch (_: IllegalArgumentException) {
        }
    }
}
