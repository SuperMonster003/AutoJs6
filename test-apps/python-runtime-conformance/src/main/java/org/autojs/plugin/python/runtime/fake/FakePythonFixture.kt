package org.autojs.plugin.python.runtime.fake

import org.autojs.plugin.python.runtime.api.PythonCancellationMode
import org.autojs.plugin.python.runtime.api.PythonImplementation
import org.autojs.plugin.python.runtime.api.PythonIsolationMode
import org.autojs.plugin.python.runtime.api.PythonProtocolVersion
import org.autojs.plugin.python.runtime.api.PythonRuntimeCapabilities
import org.autojs.plugin.python.runtime.api.PythonRuntimeContract
import org.autojs.plugin.python.runtime.api.PythonRuntimeInfo
import org.autojs.plugin.python.runtime.api.PythonRuntimeResourceLimits

object FakePythonFixture {
    const val HOST_PACKAGE_NAME = "org.autojs.autojs6"
    const val PYTHON_VERSION = "3.13.0"
    const val FIRST_OUTPUT_SEQUENCE = 100L
    const val RUNTIME_GENERATION = 1L

    val protocolVersion = PythonProtocolVersion(
        PythonRuntimeContract.PROTOCOL_MAJOR,
        PythonRuntimeContract.PROTOCOL_MINOR,
    )

    val runtimeInfo = PythonRuntimeInfo(
        protocolMin = protocolVersion,
        protocolMax = protocolVersion,
        providerId = "org.autojs.python.runtime.fake",
        providerVersionName = "1.0.0",
        providerVersionCode = 1L,
        implementation = PythonImplementation.CPYTHON,
        pythonVersion = PYTHON_VERSION,
        isolationMode = PythonIsolationMode.DEDICATED_PLUGIN_PROCESS,
    )

    val capabilities = PythonRuntimeCapabilities(
        implementation = PythonImplementation.CPYTHON,
        pythonVersion = PYTHON_VERSION,
        supportedAbis = listOf("arm64-v8a", "x86_64", "armeabi-v7a", "x86"),
        supportsWorkspaceArchive = false,
        supportsStdinSnapshot = true,
        supportsStructuredTraceback = true,
        supportsCooperativeCancellation = true,
        cancellationMode = PythonCancellationMode.COOPERATIVE_THEN_PROCESS_RESTART,
        limits = PythonRuntimeResourceLimits(
            maxSourceBytes = 1024L * 1024L,
            maxWorkspaceArchiveBytes = 0L,
            maxWorkspaceEntries = 0,
            maxWorkspaceUncompressedBytes = 0L,
            maxStdinBytes = 1024L * 1024L,
            maxOutputBytes = 1024L * 1024L,
            maxOutputChunkBytes = PythonRuntimeContract.MAX_OUTPUT_CHUNK_BYTES,
            maxOutputChunks = 4096L,
            maxOutstandingOutputCredits = PythonRuntimeContract.MAX_OUTSTANDING_OUTPUT_CREDITS,
            maxTimeoutMillis = 60_000L,
            maxConcurrentSessions = PythonRuntimeContract.MAX_CONCURRENT_SESSIONS,
        ),
    )
}
