package org.autojs.plugin.jvmsource.api

import org.autojs.plugin.protocol.wire.TaggedWireDocument
import org.autojs.plugin.protocol.wire.TaggedWireError
import org.autojs.plugin.protocol.wire.TaggedWireException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class JvmSourceObservationLimitsTest {

    @Test
    fun admitsExactFieldBoundariesAndSixDistinctSamples() {
        val samples = listOf(
            sample(JvmObservationProcess.COMPILER, JvmObservationPhase.COMPILE),
            sample(JvmObservationProcess.COMPILER, JvmObservationPhase.D8),
            sample(JvmObservationProcess.COMPILER, JvmObservationPhase.TERMINATION),
            sample(JvmObservationProcess.WORKER, JvmObservationPhase.LOAD),
            sample(JvmObservationProcess.WORKER, JvmObservationPhase.RUN),
            sample(JvmObservationProcess.WORKER, JvmObservationPhase.TERMINATION),
        )
        val value = JvmSourceFixtures.observation(resources = samples).copy(
            sessionElapsedMillis = JvmSourceContract.MAX_OBSERVATION_SESSION_ELAPSED_MILLIS,
            workerStartupDurationMillis = JvmSourceContract.MAX_OBSERVATION_SESSION_ELAPSED_MILLIS,
            compileDurationMillis = JvmSourceContract.MAX_OBSERVATION_PHASE_DURATION_MILLIS,
            d8DurationMillis = JvmSourceContract.MAX_OBSERVATION_PHASE_DURATION_MILLIS,
            loadDurationMillis = JvmSourceContract.MAX_OBSERVATION_PHASE_DURATION_MILLIS,
            runDurationMillis = JvmSourceContract.MAX_OBSERVATION_PHASE_DURATION_MILLIS,
            terminationDurationMillis = JvmSourceContract.MAX_OBSERVATION_TERMINATION_DURATION_MILLIS,
        )

        val encoded = JvmSourceCodec.encodeObservation(value)
        assertTrue(encoded.size <= JvmSourceContract.MAX_OBSERVATION_BYTES)
        assertEquals(value, JvmSourceCodec.decodeObservation(encoded))
    }

    @Test
    fun rejectsDurationCacheAndSampleShapeViolations() {
        expectInvalid {
            JvmSourceFixtures.observation().copy(
                sessionElapsedMillis = JvmSourceContract.MAX_OBSERVATION_SESSION_ELAPSED_MILLIS + 1L,
            )
        }
        expectInvalid {
            JvmSourceFixtures.observation().copy(workerStartupDurationMillis = 1_210L)
        }
        expectInvalid {
            JvmSourceFixtures.observation().copy(
                compileDurationMillis = JvmSourceContract.MAX_OBSERVATION_PHASE_DURATION_MILLIS + 1L,
            )
        }
        expectInvalid {
            JvmSourceFixtures.observation().copy(
                terminationDurationMillis =
                    JvmSourceContract.MAX_OBSERVATION_TERMINATION_DURATION_MILLIS + 1L,
            )
        }
        expectInvalid {
            JvmSourceFixtures.observation().copy(cacheMissReason = null)
        }
        expectInvalid {
            JvmSourceFixtures.observation().copy(
                cacheOutcome = JvmObservationCacheOutcome.HIT,
                cacheMissReason = null,
            )
        }
        expectInvalid {
            JvmSourceFixtures.observation(resources = List(7) { index ->
                sample(
                    JvmObservationProcess.COMPILER,
                    if (index % 2 == 0) JvmObservationPhase.COMPILE else JvmObservationPhase.D8,
                )
            })
        }
        val duplicate = sample(JvmObservationProcess.COMPILER, JvmObservationPhase.COMPILE)
        expectInvalid { JvmSourceFixtures.observation(resources = listOf(duplicate, duplicate)) }
        expectInvalid {
            JvmSourceFixtures.observation(
                resources = listOf(sample(JvmObservationProcess.COMPILER, JvmObservationPhase.RUN)),
            )
        }
        expectInvalid {
            JvmSourceFixtures.observation(
                resources = listOf(
                    JvmSourceResourceObservation(
                        JvmObservationProcess.WORKER,
                        JvmObservationPhase.RUN,
                    ),
                ),
            )
        }
    }

    @Test
    fun rejectsEveryResourceMetricAboveItsBoundary() {
        val base = sample(JvmObservationProcess.WORKER, JvmObservationPhase.RUN)
        listOf(
            base.copy(rssBytes = JvmSourceContract.MAX_OBSERVATION_RSS_BYTES + 1L),
            base.copy(
                openFileDescriptorCount = JvmSourceContract.MAX_OBSERVATION_OPEN_FILE_DESCRIPTORS + 1,
            ),
            base.copy(
                temporaryStorageBytes = JvmSourceContract.MAX_OBSERVATION_TEMPORARY_STORAGE_BYTES + 1L,
            ),
            base.copy(outputBytes = JvmSourceContract.MAX_OBSERVATION_OUTPUT_BYTES + 1L),
        ).forEach { resource ->
            expectInvalid { JvmSourceFixtures.observation(resources = listOf(resource)) }
        }
    }

    @Test
    fun terminalObservationPresenceIsBoundToNegotiatedMinor() {
        val currentRequest = JvmSourceFixtures.request()
        val legacyRequest = currentRequest.copy(protocolVersion = JvmProtocolVersion(1, 4))
        val observation = JvmSourceFixtures.observation()

        JvmSourceValidation.validateTerminalObservationAgainst(observation, currentRequest)
        JvmSourceValidation.validateTerminalObservationAgainst(null, legacyRequest)
        expectCapability {
            JvmSourceValidation.validateTerminalObservationAgainst(null, currentRequest)
        }
        expectCapability {
            JvmSourceValidation.validateTerminalObservationAgainst(observation, legacyRequest)
        }
        expectCapability {
            JvmSourceValidation.validateTerminalObservationAgainst(
                observation.copy(requestId = JvmRequestId.fromBytes(ByteArray(JvmRequestId.BYTE_COUNT) { 9 })),
                currentRequest,
            )
        }
    }

    @Test
    fun allProtocol15TerminalFieldsAreRequiredForProtocol14Readers() {
        val observation = JvmSourceFixtures.observation()
        val documents = listOf(
            JvmSourceCodec.encodeResult(JvmSourceFixtures.result(observation = observation)) to (1..23).toSet(),
            JvmSourceCodec.encodeError(
                JvmSourceError(
                    JvmSourceFixtures.requestId,
                    JvmSourceErrorCode.INTERNAL,
                    JvmSourceFailurePhase.CLEANUP,
                    "bounded failure",
                    retryable = false,
                    observation = observation,
                ),
            ) to (1..5).toSet(),
            JvmSourceCodec.encodeCancellation(
                JvmSourceCancellation(
                    JvmSourceFixtures.requestId,
                    JvmCancellationReason.REQUESTED,
                    JvmSourceFailurePhase.EXECUTION,
                    1L,
                    observation,
                ),
            ) to (1..4).toSet(),
        )

        documents.forEach { (encoded, legacyTags) ->
            val failure = assertThrows(TaggedWireException::class.java) {
                TaggedWireDocument.decode(encoded).rejectUnknownRequiredFields(legacyTags)
            }
            assertEquals(TaggedWireError.UNKNOWN_REQUIRED_FIELD, failure.error)
        }
    }

    @Test
    fun rejectsObservationDocumentAboveDedicatedEnvelope() {
        expectInvalid {
            JvmSourceCodec.decodeObservation(ByteArray(JvmSourceContract.MAX_OBSERVATION_BYTES + 1))
        }
    }

    private fun sample(
        process: JvmObservationProcess,
        phase: JvmObservationPhase,
    ) = JvmSourceResourceObservation(
        process = process,
        phase = phase,
        rssBytes = JvmSourceContract.MAX_OBSERVATION_RSS_BYTES,
        openFileDescriptorCount = JvmSourceContract.MAX_OBSERVATION_OPEN_FILE_DESCRIPTORS,
        temporaryStorageBytes = JvmSourceContract.MAX_OBSERVATION_TEMPORARY_STORAGE_BYTES,
        outputBytes = JvmSourceContract.MAX_OBSERVATION_OUTPUT_BYTES,
    )

    private fun expectInvalid(value: () -> Any?) {
        assertEquals(
            JvmSourceContractViolation.INVALID_VALUE,
            assertThrows(JvmSourceContractException::class.java) {
                value().let { observation ->
                    if (observation is JvmSourceObservation) JvmSourceValidation.validateObservation(observation)
                }
            }.violation,
        )
    }

    private fun expectCapability(block: () -> Unit) {
        assertEquals(
            JvmSourceContractViolation.CAPABILITY_INCOMPATIBLE,
            assertThrows(JvmSourceContractException::class.java, block).violation,
        )
    }
}
