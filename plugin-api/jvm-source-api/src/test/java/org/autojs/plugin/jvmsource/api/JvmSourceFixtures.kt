package org.autojs.plugin.jvmsource.api

import java.nio.charset.StandardCharsets

internal object JvmSourceFixtures {
    val requestId: JvmRequestId = JvmRequestId.fromBytes(
        ByteArray(JvmRequestId.BYTE_COUNT) { it.toByte() },
    )
    val sourceSha256: JvmSha256 = JvmSha256.digest(
        "public final class Main {}".toByteArray(StandardCharsets.UTF_8),
    )
    val runtimeLibraryFingerprint: JvmSha256 = JvmSha256.digest(
        "jvm-entry-api-runtime-v1".toByteArray(StandardCharsets.UTF_8),
    )
    private val classArtifactBytes = "Main.class".toByteArray()
    private val dexArtifactBytes = "classes.dex".toByteArray()
    val classArtifactSizeBytes = classArtifactBytes.size.toLong()
    val classArtifactSha256: JvmSha256 = JvmSha256.digest(classArtifactBytes)
    val dexArtifactSizeBytes = dexArtifactBytes.size.toLong()
    val dexArtifactSha256: JvmSha256 = JvmSha256.digest(dexArtifactBytes)
    val emptyOutputSha256: JvmSha256 = JvmSha256.digest(ByteArray(0))

    const val HOST_VERSION_CODE = 5_300L
    const val JAVA_COMPILER_VERSION = "3.26.0"
    const val KOTLIN_COMPILER_VERSION = "2.3.0"
    const val D8_VERSION = "8.13.17"
    const val DEX_VERSION = "037"
    val loaderKind = JvmDexLoaderKind.PRIVATE_DEX_CLASS_LOADER
    const val COMPILER_PID = 31_001
    const val COMPILER_UID = 10_321
    const val COMPILER_PROCESS_NAME = "org.autojs.plugin.jvmsource.java:compiler"
    const val WORKER_PID = 31_002
    const val WORKER_UID = COMPILER_UID
    const val WORKER_PROCESS_NAME = "org.autojs.plugin.jvmsource.java:worker"
    const val WORKER_GENERATION = 17L

    fun providerInfo(
        min: JvmProtocolVersion = JvmProtocolVersion(1, 0),
        max: JvmProtocolVersion = JvmProtocolVersion(1, 0),
    ) = JvmProviderInfo(
        protocolMin = min,
        protocolMax = max,
        providerId = "ecj-java",
        providerVersionName = "0.1.0",
        providerVersionCode = 1L,
        entryApiVersion = JvmSourceContract.ENTRY_API_VERSION,
        minHostVersionCode = HOST_VERSION_CODE,
    )

    fun capabilities(
        language: JvmSourceLanguage = JvmSourceLanguage.JAVA,
    ): JvmSourceCapabilities {
        val compilerFamily = compilerFamily(language)
        val compilerVersion = compilerVersion(language)
        return JvmSourceCapabilities(
            languages = listOf(language),
            isolationCapabilities = JvmIsolationCapability.entries,
            sourceCompilerFamily = compilerFamily,
            sourceCompilerVersion = compilerVersion,
            d8Version = D8_VERSION,
            runtimeLibraryFingerprint = runtimeLibraryFingerprint,
            toolchainFingerprint = JvmToolchainFingerprint.compute(
                language,
                compilerFamily,
                compilerVersion,
                D8_VERSION,
                runtimeLibraryFingerprint,
            ),
            maxSourceBytes = JvmSourceContract.MAX_SOURCE_BYTES,
            maxStdoutBytes = JvmSourceContract.MAX_STDOUT_BYTES,
            maxStderrBytes = JvmSourceContract.MAX_STDERR_BYTES,
            maxDiagnosticBytes = JvmSourceContract.MAX_DIAGNOSTIC_BYTES,
            maxTimeoutMillis = JvmSourceContract.MAX_TIMEOUT_MILLIS,
            maxConcurrentSessions = JvmSourceContract.MAX_CONCURRENT_SESSIONS,
        )
    }

    fun request(
        language: JvmSourceLanguage = JvmSourceLanguage.JAVA,
        sourceFileName: String = "Main.java",
        sourceSizeBytes: Long = 4_096L,
        timeoutMillis: Long = JvmSourceContract.DEFAULT_TIMEOUT_MILLIS,
        expectedToolchainFingerprint: JvmSha256 = capabilities(language).toolchainFingerprint,
        maxStdoutBytes: Long = JvmSourceContract.MAX_STDOUT_BYTES,
        maxStderrBytes: Long = JvmSourceContract.MAX_STDERR_BYTES,
    ) = JvmSourceRequest(
        requestId = requestId,
        protocolVersion = JvmProtocolVersion(1, 0),
        language = language,
        sourceFileName = sourceFileName,
        sourceSizeBytes = sourceSizeBytes,
        sourceSha256 = sourceSha256,
        expectedToolchainFingerprint = expectedToolchainFingerprint,
        entryClassName = "Main",
        minApi = JvmSourceContract.MIN_ANDROID_API,
        timeoutMillis = timeoutMillis,
        maxStdoutBytes = maxStdoutBytes,
        maxStderrBytes = maxStderrBytes,
        diagnosticByteLimit = JvmSourceContract.MAX_DIAGNOSTIC_BYTES,
        allowedHostCalls = listOf("app.launch"),
    )

    fun result(
        dexVersion: String = DEX_VERSION,
        loaderKind: JvmDexLoaderKind = this.loaderKind,
    ) = JvmSourceResult(
        requestId = requestId,
        toolchainFingerprint = capabilities().toolchainFingerprint,
        classArtifactSizeBytes = classArtifactSizeBytes,
        classArtifactSha256 = classArtifactSha256,
        dexArtifactSizeBytes = dexArtifactSizeBytes,
        dexArtifactSha256 = dexArtifactSha256,
        dexVersion = dexVersion,
        loaderKind = loaderKind,
        resultJson = "null",
        stdoutSizeBytes = 0L,
        stdoutSha256 = emptyOutputSha256,
        stderrSizeBytes = 0L,
        stderrSha256 = emptyOutputSha256,
        workerPid = WORKER_PID,
        workerUid = WORKER_UID,
        workerProcessName = WORKER_PROCESS_NAME,
        workerGeneration = WORKER_GENERATION,
        internalBridgeCallerPid = WORKER_PID,
        internalBridgeCallerUid = WORKER_UID,
        internalBridgeCallCount = 1,
        compilationElapsedMillis = 1L,
        executionElapsedMillis = 1L,
        elapsedMillis = 2L,
    )

    private fun compilerFamily(language: JvmSourceLanguage): JvmSourceCompilerFamily = when (language) {
        JvmSourceLanguage.JAVA -> JvmSourceCompilerFamily.ECJ
        JvmSourceLanguage.KOTLIN -> JvmSourceCompilerFamily.KOTLIN_JVM
    }

    private fun compilerVersion(language: JvmSourceLanguage): String = when (language) {
        JvmSourceLanguage.JAVA -> JAVA_COMPILER_VERSION
        JvmSourceLanguage.KOTLIN -> KOTLIN_COMPILER_VERSION
    }
}
