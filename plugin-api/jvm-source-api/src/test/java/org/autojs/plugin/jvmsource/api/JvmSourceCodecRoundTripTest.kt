package org.autojs.plugin.jvmsource.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class JvmSourceCodecRoundTripTest {

    @Test
    fun roundTripsProviderInfoAndCapabilities() {
        val providerInfo = JvmSourceFixtures.providerInfo()
        val decodedInfo = JvmSourceCodec.decodeProviderInfo(
            JvmSourceCodec.encodeProviderInfo(providerInfo),
        )
        assertEquals(providerInfo, decodedInfo)

        val capabilities = JvmSourceFixtures.capabilities()
        val decodedCapabilities = JvmSourceCodec.decodeCapabilities(
            JvmSourceCodec.encodeCapabilities(capabilities),
        )
        assertEquals(capabilities, decodedCapabilities)
    }

    @Test
    fun roundTripsRequestAndProgressMetadata() {
        val request = JvmSourceFixtures.request(
            argsJson = "{\"count\":2,\"nested\":[true,null]}",
        )
        assertEquals(
            request,
            JvmSourceCodec.decodeRequest(JvmSourceCodec.encodeRequest(request)),
        )

        val started = JvmSourceStarted(
            JvmSourceFixtures.requestId,
            JvmSourceLanguage.JAVA,
            "ecj-java",
            JvmSourceFixtures.capabilities().toolchainFingerprint,
            JvmSourceFixtures.COMPILER_PID,
            JvmSourceFixtures.COMPILER_UID,
            JvmSourceFixtures.COMPILER_PROCESS_NAME,
        )
        assertEquals(
            started,
            JvmSourceCodec.decodeStarted(JvmSourceCodec.encodeStarted(started)),
        )

        val diagnostic = JvmSourceDiagnostic(
            requestId = JvmSourceFixtures.requestId,
            severity = JvmDiagnosticSeverity.WARNING,
            code = "ECJ_WARNING",
            message = "Unused local variable",
            sourceFileName = "Main.java",
            line = 7,
            column = 9,
        )
        assertEquals(
            diagnostic,
            JvmSourceCodec.decodeDiagnostic(JvmSourceCodec.encodeDiagnostic(diagnostic)),
        )
        val packageDiagnostic = diagnostic.copy(sourceFileName = "demo/Helper.java")
        assertEquals(
            packageDiagnostic,
            JvmSourceCodec.decodeDiagnostic(JvmSourceCodec.encodeDiagnostic(packageDiagnostic)),
        )
    }

    @Test
    fun roundTripsResultErrorAndCancellationTerminals() {
        val result = JvmSourceResult(
            JvmSourceFixtures.requestId,
            toolchainFingerprint = JvmSourceFixtures.capabilities().toolchainFingerprint,
            classArtifactSizeBytes = JvmSourceFixtures.classArtifactSizeBytes,
            classArtifactSha256 = JvmSourceFixtures.classArtifactSha256,
            dexArtifactSizeBytes = JvmSourceFixtures.dexArtifactSizeBytes,
            dexArtifactSha256 = JvmSourceFixtures.dexArtifactSha256,
            dexVersion = JvmSourceFixtures.DEX_VERSION,
            loaderKind = JvmSourceFixtures.loaderKind,
            resultJson = "{\"launched\":true}",
            stdoutSizeBytes = 0L,
            stdoutSha256 = JvmSourceFixtures.emptyOutputSha256,
            stderrSizeBytes = 0L,
            stderrSha256 = JvmSourceFixtures.emptyOutputSha256,
            workerPid = JvmSourceFixtures.WORKER_PID,
            workerUid = JvmSourceFixtures.WORKER_UID,
            workerProcessName = JvmSourceFixtures.WORKER_PROCESS_NAME,
            workerGeneration = JvmSourceFixtures.WORKER_GENERATION,
            internalBridgeCallerPid = JvmSourceFixtures.WORKER_PID,
            internalBridgeCallerUid = JvmSourceFixtures.WORKER_UID,
            internalBridgeCallCount = 1,
            compilationElapsedMillis = 120L,
            executionElapsedMillis = 30L,
            elapsedMillis = 160L,
            observation = JvmSourceFixtures.observation(),
        )
        assertEquals(
            result,
            JvmSourceCodec.decodeResult(JvmSourceCodec.encodeResult(result)),
        )

        val error = JvmSourceError(
            JvmSourceFixtures.requestId,
            JvmSourceErrorCode.COMPILATION_FAILED,
            JvmSourceFailurePhase.COMPILATION,
            "ECJ rejected the source",
            retryable = false,
            observation = JvmSourceFixtures.observation(),
        )
        val decodedError = JvmSourceCodec.decodeError(JvmSourceCodec.encodeError(error))
        assertEquals(error, decodedError)
        assertFalse(decodedError.retryable)

        val cancellation = JvmSourceCancellation(
            JvmSourceFixtures.requestId,
            JvmCancellationReason.REQUESTED,
            JvmSourceFailurePhase.EXECUTION,
            elapsedMillis = 42L,
            observation = JvmSourceFixtures.observation(),
        )
        assertEquals(
            cancellation,
            JvmSourceCodec.decodeCancellation(JvmSourceCodec.encodeCancellation(cancellation)),
        )
    }

    @Test
    fun roundTripsStandaloneObservationAndFreezesResourceList() {
        val mutableResources = JvmSourceFixtures.observation().resources.toMutableList()
        val observation = JvmSourceFixtures.observation(resources = mutableResources)
        mutableResources.clear()

        assertEquals(2, observation.resources.size)
        assertEquals(
            observation,
            JvmSourceCodec.decodeObservation(JvmSourceCodec.encodeObservation(observation)),
        )
        assertThrows(UnsupportedOperationException::class.java) {
            (observation.resources as MutableList<JvmSourceResourceObservation>).clear()
        }
    }

    @Test
    fun roundTripsHostBridgeCallAndBothResponseShapes() {
        val call = JvmHostCall(
            JvmSourceFixtures.requestId,
            callId = 17L,
            method = "app.launch",
            payloadJson = "{\"packageName\":\"com.example.app\"}",
            timeoutMillis = 5_000L,
        )
        assertEquals(call, JvmSourceCodec.decodeHostCall(JvmSourceCodec.encodeHostCall(call)))

        val success = JvmHostResponse(
            JvmSourceFixtures.requestId,
            callId = 17L,
            succeeded = true,
            payloadJson = "true",
        )
        val decodedSuccess = JvmSourceCodec.decodeHostResponse(
            JvmSourceCodec.encodeHostResponse(success),
        )
        assertEquals(success, decodedSuccess)
        assertTrue(decodedSuccess.succeeded)
        assertNull(decodedSuccess.errorCode)

        val failure = JvmHostResponse(
            JvmSourceFixtures.requestId,
            callId = 18L,
            succeeded = false,
            errorCode = "HOST_CALL_REJECTED",
            errorMessage = "Method is outside the allowlist",
        )
        assertEquals(
            failure,
            JvmSourceCodec.decodeHostResponse(JvmSourceCodec.encodeHostResponse(failure)),
        )
    }
}
