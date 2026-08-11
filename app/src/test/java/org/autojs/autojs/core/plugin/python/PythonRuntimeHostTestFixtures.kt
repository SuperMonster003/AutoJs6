package org.autojs.autojs.core.plugin.python

import org.autojs.plugin.python.runtime.api.PythonCancellationMode
import org.autojs.plugin.python.runtime.api.PythonExecutionRequest
import org.autojs.plugin.python.runtime.api.PythonImplementation
import org.autojs.plugin.python.runtime.api.PythonIsolationMode
import org.autojs.plugin.python.runtime.api.PythonPayloadKind
import org.autojs.plugin.python.runtime.api.PythonPayloadReference
import org.autojs.plugin.python.runtime.api.PythonProtocolVersion
import org.autojs.plugin.python.runtime.api.PythonRequestId
import org.autojs.plugin.python.runtime.api.PythonRuntimeCapabilities
import org.autojs.plugin.python.runtime.api.PythonRuntimeContract
import org.autojs.plugin.python.runtime.api.PythonRuntimeInfo
import org.autojs.plugin.python.runtime.api.PythonRuntimeResourceLimits
import org.autojs.plugin.python.runtime.api.PythonSha256
import java.util.UUID

internal object PythonRuntimeHostTestFixtures {
    const val SIGNER_A = "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"
    const val SIGNER_B = "bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb"

    fun runtimeInfo(
        protocolMin: PythonProtocolVersion = PythonProtocolVersion(1, 0),
        protocolMax: PythonProtocolVersion = PythonProtocolVersion(1, 0),
        pythonVersion: String = "3.13.9",
        minHostVersionCode: Long? = 5_000L,
        maxHostVersionCode: Long? = 7_000L,
    ) = PythonRuntimeInfo(
        protocolMin = protocolMin,
        protocolMax = protocolMax,
        providerId = "fixture.python.runtime",
        providerVersionName = "1.0.0",
        providerVersionCode = 7L,
        implementation = PythonImplementation.CPYTHON,
        pythonVersion = pythonVersion,
        isolationMode = PythonIsolationMode.DEDICATED_PLUGIN_PROCESS,
        minHostVersionCode = minHostVersionCode,
        maxHostVersionCode = maxHostVersionCode,
    )

    fun capabilities(
        pythonVersion: String = "3.13.9",
        supportedAbis: Collection<String> = listOf("arm64-v8a", "x86_64"),
        supportsWorkspaceArchive: Boolean = true,
        supportsStdinSnapshot: Boolean = true,
        supportsHostCapabilitySnapshot: Boolean = false,
        supportsCooperativeCancellation: Boolean = true,
        maxOutputBytes: Long = 4_096L,
    ): PythonRuntimeCapabilities {
        val limits = PythonRuntimeResourceLimits(
            maxSourceBytes = 4_096L,
            maxWorkspaceArchiveBytes = if (supportsWorkspaceArchive) 8_192L else 0L,
            maxWorkspaceEntries = if (supportsWorkspaceArchive) 32 else 0,
            maxWorkspaceUncompressedBytes = if (supportsWorkspaceArchive) 16_384L else 0L,
            maxStdinBytes = if (supportsStdinSnapshot) 2_048L else 0L,
            maxOutputBytes = maxOutputBytes,
            maxOutputChunkBytes = minOf(1_024L, maxOutputBytes).toInt(),
            maxOutputChunks = maxOutputBytes,
            maxOutstandingOutputCredits = 4,
            maxTimeoutMillis = 10_000L,
            maxConcurrentSessions = PythonRuntimeContract.MAX_CONCURRENT_SESSIONS,
            maxHostCapabilitySnapshotBytes = if (supportsHostCapabilitySnapshot) {
                PythonRuntimeContract.MAX_HOST_CAPABILITY_SNAPSHOT_BYTES.toLong()
            } else {
                0L
            },
        )
        return PythonRuntimeCapabilities(
            implementation = PythonImplementation.CPYTHON,
            pythonVersion = pythonVersion,
            supportedAbis = supportedAbis,
            supportsWorkspaceArchive = supportsWorkspaceArchive,
            supportsStdinSnapshot = supportsStdinSnapshot,
            supportsStructuredTraceback = true,
            supportsCooperativeCancellation = supportsCooperativeCancellation,
            cancellationMode = if (supportsCooperativeCancellation) {
                PythonCancellationMode.COOPERATIVE_THEN_PROCESS_RESTART
            } else {
                PythonCancellationMode.PROCESS_RESTART_ONLY
            },
            limits = limits,
            supportsHostCapabilitySnapshot = supportsHostCapabilitySnapshot,
        )
    }

    fun candidate(
        packageName: String = "example.python.runtime",
        className: String = "PythonRuntimeService",
        action: String = PythonRuntimeContract.RUNTIME_ACTION,
        exported: Boolean = true,
        applicationEnabled: Boolean = true,
        serviceEnabled: Boolean = true,
        permission: String? = PythonRuntimeContract.PLUGIN_PERMISSION,
        packageUid: Int = 10_001,
        versionCode: Long = 7L,
        lastUpdateTime: Long = 123L,
        signers: Collection<String> = listOf(SIGNER_A),
        trustLevel: PythonRuntimeProviderTrustLevel = PythonRuntimeProviderTrustLevel.OFFICIAL,
        sameSignerAsHost: Boolean = false,
        runtimeInfo: PythonRuntimeInfo = runtimeInfo(),
        capabilities: PythonRuntimeCapabilities = capabilities(),
    ) = PythonRuntimeProviderCandidate(
        component = PythonRuntimeProviderComponent(packageName, className),
        declaredAction = action,
        exported = exported,
        applicationEnabled = applicationEnabled,
        serviceEnabled = serviceEnabled,
        requiredPermission = permission,
        packageUid = packageUid,
        versionCode = versionCode,
        lastUpdateTime = lastUpdateTime,
        signerSha256 = signers,
        trustLevel = trustLevel,
        sameSignerAsHost = sameSignerAsHost,
        runtimeInfo = runtimeInfo,
        capabilities = capabilities,
    )

    fun environment(
        hostVersionCode: Long = 6_000L,
        currentAbi: String = "arm64-v8a",
        releaseBuild: Boolean = true,
        allowUserApprovedProviders: Boolean = false,
    ) = PythonRuntimeProviderEnvironment(
        hostVersionCode = hostVersionCode,
        currentAbi = currentAbi,
        releaseBuild = releaseBuild,
        allowUserApprovedProviders = allowUserApprovedProviders,
    )

    val requestId: PythonRequestId =
        PythonRequestId.fromUuid(UUID.fromString("123e4567-e89b-12d3-a456-426614174000"))

    fun request(
        protocolVersion: PythonProtocolVersion = PythonProtocolVersion(1, 0),
        workspaceArchive: Boolean = false,
        hostCapabilitySnapshot: Boolean = false,
    ) = PythonExecutionRequest(
        requestId = requestId,
        protocolVersion = protocolVersion,
        entryPoint = "main.py",
        source = PythonPayloadReference(
            kind = PythonPayloadKind.SOURCE,
            descriptorIndex = 0,
            declaredLengthBytes = 16L,
            sha256 = PythonSha256.digest("print('hello')\n".toByteArray()),
        ),
        workspaceArchive = if (workspaceArchive) {
            PythonPayloadReference(
                kind = PythonPayloadKind.WORKSPACE_ARCHIVE,
                descriptorIndex = 1,
                declaredLengthBytes = 4L,
                sha256 = PythonSha256.digest(byteArrayOf(0x50, 0x4b, 0x05, 0x06)),
            )
        } else {
            null
        },
        hostCapabilitySnapshot = if (hostCapabilitySnapshot) {
            PythonPayloadReference(
                kind = PythonPayloadKind.HOST_CAPABILITY_SNAPSHOT,
                descriptorIndex = if (workspaceArchive) 2 else 1,
                declaredLengthBytes = 32L,
                sha256 = PythonSha256.digest(ByteArray(32) { it.toByte() }),
            )
        } else {
            null
        },
        timeoutMillis = 1_000L,
        maxOutputBytes = 1_024L,
        maxOutputChunkBytes = 256,
        maxOutputChunks = 1_024L,
    )
}
