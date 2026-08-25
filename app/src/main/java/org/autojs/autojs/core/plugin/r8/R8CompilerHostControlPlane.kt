package org.autojs.autojs.core.plugin.r8

import org.autojs.plugin.r8compiler.api.R8CompileRequest
import org.autojs.plugin.r8compiler.api.R8CompilerContract
import org.autojs.plugin.r8compiler.api.R8CompilerValidation

/**
 * Composes the default-off store, exact PackageManager transport, post-bind identity inspection,
 * protocol gate, and R8-only cache namespace. This class owns selection and negotiation; the
 * process-wide dispatcher owns the explicit R8 dispatch and never authorizes semantic fallback.
 */
internal class R8CompilerHostControlPlane(
    private val transport: R8CompilerTransport,
    private val selectionOptions: () -> R8CompilerSelectionOptions,
    private val hostVersionCode: Long,
    private val hostRuntimeApi: Int,
) {
    init {
        require(hostVersionCode > 0L)
        require(hostRuntimeApi > 0)
    }

    fun select(): R8CompilerSelectionDecision {
        val options = try {
            selectionOptions()
        } catch (_: Exception) {
            return R8CompilerSelectionDecision.Disabled
        }
        return R8CompilerSelectionPolicy.decide(
            options = options,
            discoverCandidates = {
                transport.discoverExactAction(R8CompilerContract.SERVICE_ACTION)
            },
        )
    }

    fun bindExact(
        identity: R8CompilerPinnedIdentity,
        callback: R8CompilerBindingCallback,
    ): R8CompilerBinding = transport.bindExact(identity, callback)

    /** Must be called only for the connection returned by [bindExact]. */
    fun negotiateConnected(
        identity: R8CompilerPinnedIdentity,
        connection: R8CompilerRemoteConnection,
        minApi: Int = hostRuntimeApi,
    ): R8CompilerNegotiation {
        val currentCandidate = transport.inspectExact(identity.component)
        require(R8CompilerSelectionPolicy.revalidate(identity, currentCandidate)) {
            "R8 compiler package identity changed before remote handshake"
        }
        val encodedInfo = connection.getCompilerInfo()
        val encodedCapabilities = connection.getCapabilities()
        return R8CompilerProtocolGate.negotiate(
            identity = identity,
            currentCandidate = currentCandidate,
            encodedInfo = encodedInfo,
            encodedCapabilities = encodedCapabilities,
            hostVersionCode = hostVersionCode,
            minApi = minApi,
        )
    }

    fun semanticCacheKey(
        request: R8CompileRequest,
        negotiation: R8CompilerNegotiation,
    ): String {
        R8CompilerValidation.validateRequestAgainst(request, negotiation.capabilities)
        return R8CompilerSemanticCacheKey.sha256(
            request,
            negotiation.capabilities,
            negotiation.identity,
        )
    }
}
