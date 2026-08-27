package org.autojs.autojs.core.plugin.r8

import org.autojs.plugin.r8compiler.api.R8CompilerCodec
import org.autojs.plugin.r8compiler.api.R8CompilerContract
import org.autojs.plugin.r8compiler.api.R8CompilerInfo
import org.autojs.plugin.r8compiler.api.R8CompilerValidation
import org.autojs.plugin.r8compiler.api.R8RetraceCapabilities
import org.autojs.plugin.r8compiler.api.R8RetraceCodec
import org.autojs.plugin.r8compiler.api.R8RetraceValidation

internal data class R8RetraceNegotiation(
    val identity: R8CompilerPinnedIdentity,
    val info: R8CompilerInfo,
    val capabilities: R8RetraceCapabilities,
)

/** Pins protocol 1.1 and the complete retrace capability fingerprint after identity revalidation. */
internal object R8RetraceProtocolGate {
    fun negotiate(
        identity: R8CompilerPinnedIdentity,
        currentCandidate: R8CompilerServiceCandidate?,
        encodedInfo: ByteArray,
        encodedCapabilities: ByteArray,
        hostVersionCode: Long,
    ): R8RetraceNegotiation {
        require(R8CompilerSelectionPolicy.revalidate(identity, currentCandidate)) {
            "R8 provider package identity changed before retrace negotiation"
        }
        val info = R8CompilerCodec.decodeInfo(encodedInfo)
        val capabilities = R8RetraceCodec.decodeCapabilities(encodedCapabilities)
        R8CompilerValidation.validateInfo(info)
        R8RetraceValidation.validateCapabilities(capabilities)
        require(info.providerId == R8CompilerContract.PROVIDER_ID) {
            "R8 retrace provider ID is incompatible"
        }
        require(info.providerVersionCode == identity.versionCode) {
            "R8 retrace provider version changed"
        }
        require(R8CompilerContract.PROTOCOL_V1_1 in info.protocolMin..info.protocolMax) {
            "R8 retrace protocol 1.1 is not supported"
        }
        require(info.minHostVersionCode == null || hostVersionCode >= info.minHostVersionCode!!) {
            "AutoJs6 is older than the R8 retrace provider minimum"
        }
        require(info.maxHostVersionCode == null || hostVersionCode <= info.maxHostVersionCode!!) {
            "AutoJs6 is newer than the R8 retrace provider maximum"
        }
        return R8RetraceNegotiation(identity, info, capabilities)
    }
}
