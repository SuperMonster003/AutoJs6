package org.autojs.plugin.python.runtime.api

import java.util.UUID

internal object PythonRuntimeFixtures {
    val requestId: PythonRequestId = PythonRequestId.fromUuid(
        UUID.fromString("12345678-1234-5678-9abc-def012345678"),
    )
    val sourceBytes = "print('hello')\n".toByteArray()
    val workspaceBytes = byteArrayOf(0x50, 0x4b, 0x03, 0x04)
    val stdinBytes = "input\n".toByteArray()
    val capabilitySnapshotBytes = "{\"schema\":\"autojs6-host-capability-snapshot-v1\"}".toByteArray()

    val runtimeInfo = PythonRuntimeInfo(
        protocolMin = PythonProtocolVersion(1, 0),
        protocolMax = PythonProtocolVersion(1, 0),
        providerId = "org.autojs.python.fake",
        providerVersionName = "1.0.0",
        providerVersionCode = 1,
        implementation = PythonImplementation.CPYTHON,
        pythonVersion = "3.13.9",
        isolationMode = PythonIsolationMode.DEDICATED_PLUGIN_PROCESS,
        minHostVersionCode = 5_000,
    )

    val limits = PythonRuntimeResourceLimits(
        maxSourceBytes = 1024 * 1024,
        maxWorkspaceArchiveBytes = 8L * 1024L * 1024L,
        maxWorkspaceEntries = 1_000,
        maxWorkspaceUncompressedBytes = 16L * 1024L * 1024L,
        maxStdinBytes = 1024 * 1024,
        maxOutputBytes = 4L * 1024L * 1024L,
        maxOutputChunkBytes = 16 * 1024,
        maxOutputChunks = 4_096,
        maxOutstandingOutputCredits = 8,
        maxTimeoutMillis = 300_000,
        maxConcurrentSessions = 1,
    )

    val capabilities = PythonRuntimeCapabilities(
        implementation = PythonImplementation.CPYTHON,
        pythonVersion = "3.13.9",
        supportedAbis = listOf("arm64-v8a", "x86_64"),
        supportsWorkspaceArchive = true,
        supportsStdinSnapshot = true,
        supportsStructuredTraceback = true,
        supportsCooperativeCancellation = true,
        cancellationMode = PythonCancellationMode.COOPERATIVE_THEN_PROCESS_RESTART,
        limits = limits,
    )

    val r5Capabilities = PythonRuntimeCapabilities(
        implementation = PythonImplementation.CPYTHON,
        pythonVersion = "3.13.9",
        supportedAbis = listOf("arm64-v8a", "x86_64"),
        supportsWorkspaceArchive = true,
        supportsStdinSnapshot = true,
        supportsStructuredTraceback = true,
        supportsCooperativeCancellation = true,
        cancellationMode = PythonCancellationMode.COOPERATIVE_THEN_PROCESS_RESTART,
        limits = limits.copy(
            maxHostCapabilitySnapshotBytes = PythonRuntimeContract.MAX_HOST_CAPABILITY_SNAPSHOT_BYTES,
        ),
        supportsHostCapabilitySnapshot = true,
    )

    val source = PythonPayloadReference(
        kind = PythonPayloadKind.SOURCE,
        descriptorIndex = 0,
        declaredLengthBytes = sourceBytes.size.toLong(),
        sha256 = PythonSha256.digest(sourceBytes),
    )
    val workspace = PythonPayloadReference(
        kind = PythonPayloadKind.WORKSPACE_ARCHIVE,
        descriptorIndex = 1,
        declaredLengthBytes = workspaceBytes.size.toLong(),
        sha256 = PythonSha256.digest(workspaceBytes),
    )
    val stdin = PythonPayloadReference(
        kind = PythonPayloadKind.STDIN,
        descriptorIndex = 2,
        declaredLengthBytes = stdinBytes.size.toLong(),
        sha256 = PythonSha256.digest(stdinBytes),
    )
    val capabilitySnapshot = PythonPayloadReference(
        kind = PythonPayloadKind.HOST_CAPABILITY_SNAPSHOT,
        descriptorIndex = 3,
        declaredLengthBytes = capabilitySnapshotBytes.size.toLong(),
        sha256 = PythonSha256.digest(capabilitySnapshotBytes),
    )

    val request = PythonExecutionRequest(
        requestId = requestId,
        protocolVersion = PythonProtocolVersion(1, 0),
        entryPoint = "src/main.py",
        source = source,
        workspaceArchive = workspace,
        stdin = stdin,
        arguments = listOf("--mode", "test"),
        timeoutMillis = 30_000,
        maxOutputBytes = 1024,
        maxOutputChunkBytes = 256,
        maxOutputChunks = 128,
    )

    val r5Request = PythonExecutionRequest(
        requestId = requestId,
        protocolVersion = PythonProtocolVersion(1, 1),
        entryPoint = "src/main.py",
        source = source,
        workspaceArchive = workspace,
        stdin = stdin,
        hostCapabilitySnapshot = capabilitySnapshot,
        arguments = listOf("--mode", "test"),
        timeoutMillis = 30_000,
        maxOutputBytes = 1024,
        maxOutputChunkBytes = 256,
        maxOutputChunks = 128,
    )

    val started = PythonSessionStarted(
        requestId = requestId,
        protocolVersion = PythonProtocolVersion(1, 0),
        pythonVersion = "3.13.9",
        firstOutputSequence = 7,
        effectiveMaxOutputBytes = 1024,
        effectiveMaxOutputChunkBytes = 256,
        effectiveMaxOutputChunks = 128,
        runtimeGeneration = 2,
    )

    fun sourceOnlyRequest(
        maxOutputBytes: Long = 1024,
        maxOutputChunkBytes: Int = 256,
        maxOutputChunks: Long = minOf(PythonRuntimeContract.MAX_OUTPUT_CHUNKS, maxOutputBytes),
    ) = PythonExecutionRequest(
        requestId = requestId,
        protocolVersion = PythonProtocolVersion(1, 0),
        entryPoint = "main.py",
        source = source,
        timeoutMillis = 30_000,
        maxOutputBytes = maxOutputBytes,
        maxOutputChunkBytes = maxOutputChunkBytes,
        maxOutputChunks = maxOutputChunks,
    )
}
