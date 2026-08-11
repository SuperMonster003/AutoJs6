package org.autojs.plugin.dexcompiler.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DexCompilerCodecRoundTripTest {

    @Test
    fun roundTripsCompilerInfo() {
        val decoded = DexCompilerCodec.decodeInfo(DexCompilerCodec.encodeInfo(DexCompilerFixtures.info()))

        assertEquals(DexProtocolVersion(1, 0), decoded.protocolMin)
        assertEquals(DexProtocolVersion(1, 0), decoded.protocolMax)
        assertEquals("autojs6-d8", decoded.providerId)
        assertEquals("1.0.0", decoded.providerVersionName)
        assertEquals(4L, decoded.providerVersionCode)
        assertEquals(5_270L, decoded.minHostVersionCode ?: -1L)
        assertNull(decoded.maxHostVersionCode)
        assertEquals(DexCompilerAbiMode.ANY, decoded.abiMode)
    }

    @Test
    fun roundTripsCapabilitiesAndOrderedRuntimeLibraries() {
        val decoded = DexCompilerCodec.decodeCapabilities(
            DexCompilerCodec.encodeCapabilities(DexCompilerFixtures.capabilities())
        )

        assertEquals(DexCompilerFamily.D8, decoded.compilerFamily)
        assertEquals("8.13.17", decoded.compilerVersion)
        assertEquals(listOf(DexCompilerInputFormat.JAR), decoded.inputFormats)
        assertEquals(listOf(DexCompilerOutputFormat.DEX_ZIP), decoded.outputFormats)
        assertEquals(listOf(DexCompilerMode.DEBUG, DexCompilerMode.RELEASE), decoded.modes)
        assertEquals(DexCompilerFixtures.runtimeLibraries, decoded.runtimeLibraryIdentities)
        assertEquals(DexCompilerFixtures.runtimeFingerprint, decoded.runtimeLibraryFingerprint)
        assertTrue(decoded.supportsMultiDex)
        assertFalse(decoded.acceptsExternalClasspath)
        assertFalse(decoded.supportsDesugaredLibraryConfiguration)
        assertNull(decoded.classpathBundle)
    }

    @Test
    fun roundTripsOptionalProtocol11ClasspathCapability() {
        val expected = DexCompilerFixtures.classpathBundleCapability()
        val decoded = DexCompilerCodec.decodeCapabilities(
            DexCompilerCodec.encodeCapabilities(
                DexCompilerFixtures.capabilities(classpathBundle = expected)
            )
        )

        assertEquals(expected, decoded.classpathBundle)
        assertFalse(decoded.acceptsExternalClasspath)
    }

    @Test
    fun roundTripsCompileRequest() {
        val decoded = DexCompilerCodec.decodeRequest(DexCompilerCodec.encodeRequest(DexCompilerFixtures.request()))

        assertEquals(DexCompilerFixtures.requestId, decoded.requestId)
        assertEquals(DexProtocolVersion(1, 0), decoded.protocolVersion)
        assertEquals(DexCompilerMode.RELEASE, decoded.mode)
        assertEquals(24, decoded.minApi)
        assertEquals(4_096L, decoded.programSizeBytes)
        assertEquals(DexCompilerFixtures.programSha256, decoded.programSha256)
        assertEquals(DexCompilerFixtures.runtimeFingerprint, decoded.expectedRuntimeLibraryFingerprint)
        assertEquals(DexCompilerContract.DEFAULT_TIMEOUT_MILLIS, decoded.timeoutMillis)
        assertTrue(decoded.inputIdentities.isEmpty())
        assertNull(decoded.inputLayout)
    }

    @Test
    fun roundTripsProtocol11OrderedInputMetadata() {
        val expected = DexCompilerFixtures.v11Request()
        val decoded = DexCompilerCodec.decodeRequest(DexCompilerCodec.encodeRequest(expected))

        assertEquals(DexCompilerContract.PROTOCOL_V1_1, decoded.protocolVersion)
        assertEquals(expected.inputLayout, decoded.inputLayout)
        assertEquals(expected.inputIdentities, decoded.inputIdentities)
        assertEquals(expected.inputSetFingerprint, decoded.inputSetFingerprint)
        assertEquals(expected.inputBundleSizeBytes, decoded.inputBundleSizeBytes)
        assertEquals(expected.inputBundleSha256, decoded.inputBundleSha256)
    }

    @Test
    fun roundTripsStartedAndProgressCallbacks() {
        val started = DexCompileStarted(
            requestId = DexCompilerFixtures.requestId,
            sequence = 0L,
            protocolVersion = DexProtocolVersion(1, 0),
            compilerFamily = DexCompilerFamily.D8,
            compilerVersion = "8.13.17",
            runtimeLibraryModel = DexRuntimeLibraryModel.DEVICE_RUNTIME_BOOTCLASSPATH_V1,
            runtimeLibraryFingerprint = DexCompilerFixtures.runtimeFingerprint,
            queueElapsedMillis = 12L,
        )
        val decodedStarted = DexCompilerCodec.decodeStarted(DexCompilerCodec.encodeStarted(started))
        assertEquals(started.requestId, decodedStarted.requestId)
        assertEquals(started.compilerVersion, decodedStarted.compilerVersion)
        assertEquals(12L, decodedStarted.queueElapsedMillis)

        val progress = DexCompileProgress(
            requestId = DexCompilerFixtures.requestId,
            sequence = 1L,
            stage = DexCompilerProgressStage.WRITING,
            current = 5L,
            total = 10L,
        )
        val decodedProgress = DexCompilerCodec.decodeProgress(DexCompilerCodec.encodeProgress(progress))
        assertEquals(DexCompilerProgressStage.WRITING, decodedProgress.stage)
        assertEquals(5L, decodedProgress.current ?: -1L)
        assertEquals(10L, decodedProgress.total ?: -1L)
    }

    @Test
    fun roundTripsSuccessfulResultAndDiagnostics() {
        val result = DexCompileResult(
            requestId = DexCompilerFixtures.requestId,
            compilerFamily = DexCompilerFamily.D8,
            compilerVersion = "8.13.17",
            mode = DexCompilerMode.RELEASE,
            minApi = 24,
            outputFormat = DexCompilerOutputFormat.DEX_ZIP,
            runtimeLibraryFingerprint = DexCompilerFixtures.runtimeFingerprint,
            outputSizeBytes = 8_192L,
            outputSha256 = DexCompilerFixtures.outputSha256,
            dexEntryCount = 2,
            elapsedMillis = 321L,
            diagnostics = DexCompilerFixtures.diagnostics(),
        )
        val decoded = DexCompilerCodec.decodeResult(DexCompilerCodec.encodeResult(result))

        assertEquals(DexCompilerFixtures.requestId, decoded.requestId)
        assertEquals(8_192L, decoded.outputSizeBytes)
        assertEquals(2, decoded.dexEntryCount)
        assertEquals(DexCompilerFixtures.outputSha256, decoded.outputSha256)
        assertEquals(DexCompilerFixtures.diagnostics(), decoded.diagnostics)
    }

    @Test
    fun roundTripsFailureAndCancellationTerminals() {
        val error = DexCompileError(
            requestId = DexCompilerFixtures.requestId,
            code = DexCompilerErrorCode.COMPILATION_FAILED,
            phase = DexCompilerFailurePhase.COMPILATION,
            message = "D8 rejected the fixture",
            retryable = false,
            diagnostics = DexCompilerFixtures.diagnostics(),
        )
        val decodedError = DexCompilerCodec.decodeError(DexCompilerCodec.encodeError(error))
        assertEquals(DexCompilerErrorCode.COMPILATION_FAILED, decodedError.code)
        assertEquals(DexCompilerFailurePhase.COMPILATION, decodedError.phase)
        assertEquals("D8 rejected the fixture", decodedError.message)
        assertFalse(decodedError.retryable)
        assertEquals(DexCompilerFixtures.diagnostics(), decodedError.diagnostics)

        val cancellation = DexCompileCancellation(
            requestId = DexCompilerFixtures.requestId,
            reason = DexCompilerCancellationReason.REQUESTED,
            phase = DexCompilerFailurePhase.COMPILATION,
            elapsedMillis = 42L,
        )
        val decodedCancellation = DexCompilerCodec.decodeCancellation(
            DexCompilerCodec.encodeCancellation(cancellation)
        )
        assertEquals(DexCompilerCancellationReason.REQUESTED, decodedCancellation.reason)
        assertEquals(DexCompilerFailurePhase.COMPILATION, decodedCancellation.phase)
        assertEquals(42L, decodedCancellation.elapsedMillis)
    }

    @Test
    fun capabilityCollectionsAndFixedBytesAreDefensivelyCopied() {
        val modes = mutableListOf(DexCompilerMode.DEBUG, DexCompilerMode.RELEASE)
        val capabilities = DexCompilerFixtures.capabilities(modes = modes)
        modes.clear()
        assertEquals(listOf(DexCompilerMode.DEBUG, DexCompilerMode.RELEASE), capabilities.modes)

        val source = ByteArray(DexSha256.BYTE_COUNT) { it.toByte() }
        val digest = DexSha256.fromBytes(source)
        source.fill(0)
        assertTrue(digest.toByteArray().any { it.toInt() != 0 })
        val exposed = digest.toByteArray()
        exposed.fill(0)
        assertTrue(digest.toByteArray().any { it.toInt() != 0 })
    }

    @Test
    fun requestSnapshotsInputIdentitiesAcrossConstructionAndCopy() {
        val constructorSource = DexCompilerFixtures.inputIdentities().toMutableList()
        val request = DexCompilerFixtures.v11Request(identities = constructorSource)
        val expected = request.inputIdentities.toList()
        constructorSource.clear()
        assertEquals(expected, request.inputIdentities)
        assertEquals(expected, request.component16())
        assertThrows(UnsupportedOperationException::class.java) {
            (request.inputIdentities as MutableList).clear()
        }

        val copySource = DexCompilerFixtures.inputIdentities().toMutableList()
        val copied = request.copy(inputIdentities = copySource)
        copySource.removeAt(copySource.lastIndex)
        assertEquals(expected, copied.inputIdentities)
        assertEquals(request, request.copy())
        assertEquals(request.hashCode(), request.copy().hashCode())
    }
}
