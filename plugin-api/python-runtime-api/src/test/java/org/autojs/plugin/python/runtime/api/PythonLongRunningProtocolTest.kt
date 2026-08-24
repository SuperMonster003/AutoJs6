package org.autojs.plugin.python.runtime.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class PythonLongRunningProtocolTest {
    @Test
    fun protocol16CapabilityAndRequestValidateTogether() {
        PythonRuntimeValidation.validateCapabilitiesAgainstInfo(
            PythonRuntimeFixtures.longRunningCapabilities,
            PythonRuntimeFixtures.longRunningRuntimeInfo,
        )
        PythonRuntimeValidation.validateRequestAgainst(
            PythonRuntimeFixtures.longRunningRequest,
            PythonRuntimeFixtures.longRunningCapabilities,
            PythonProtocolVersion(1, 6),
            descriptorCount = 4,
        )
    }

    @Test
    fun protocolAndTimeoutCannotAccidentallySelectLongRunningExecution() {
        assertThrows(PythonRuntimeContractException::class.java) {
            PythonRuntimeValidation.validateRequest(
                PythonRuntimeFixtures.makeLongRunningRequest(
                    protocolVersion = PythonProtocolVersion(1, 5),
                ),
            )
        }
        assertThrows(PythonRuntimeContractException::class.java) {
            PythonRuntimeValidation.validateRequest(
                PythonRuntimeFixtures.makeLongRunningRequest(timeoutMillis = 1),
            )
        }
        assertThrows(PythonRuntimeContractException::class.java) {
            PythonRuntimeValidation.validateRequest(
                PythonExecutionRequest(
                    requestId = PythonRuntimeFixtures.requestId,
                    protocolVersion = PythonProtocolVersion(1, 0),
                    entryPoint = "main.py",
                    source = PythonRuntimeFixtures.source,
                    timeoutMillis = 0,
                    maxOutputBytes = 1_024,
                    maxOutputChunkBytes = 256,
                    maxOutputChunks = 128,
                ),
            )
        }
    }

    @Test
    fun providerMustAdvertiseTheProtocol16Capability() {
        assertThrows(PythonRuntimeContractException::class.java) {
            PythonRuntimeValidation.validateCapabilitiesAgainstInfo(
                PythonRuntimeFixtures.brokerCapabilities,
                PythonRuntimeFixtures.longRunningRuntimeInfo,
            )
        }
        assertThrows(PythonRuntimeContractException::class.java) {
            PythonRuntimeValidation.validateRequestAgainst(
                PythonRuntimeFixtures.longRunningRequest,
                PythonRuntimeFixtures.brokerCapabilities,
                PythonProtocolVersion(1, 6),
                descriptorCount = 4,
            )
        }
    }

    @Test
    fun heartbeatValuesArePositiveAndNonNegative() {
        PythonRuntimeValidation.validateHeartbeat(PythonRuntimeFixtures.heartbeat)
        assertThrows(PythonRuntimeContractException::class.java) {
            PythonRuntimeValidation.validateHeartbeat(PythonRuntimeFixtures.heartbeat.copy(sequence = 0))
        }
        assertThrows(PythonRuntimeContractException::class.java) {
            PythonRuntimeValidation.validateHeartbeat(PythonRuntimeFixtures.heartbeat.copy(elapsedMillis = -1))
        }
    }

    @Test
    fun longRunningSessionAcceptsOnlyOrderedMonotonicHeartbeats() {
        val accepted = startedLongRunningPolicy()
        accepted.onHeartbeat(PythonRuntimeFixtures.heartbeat)
        accepted.onHeartbeat(PythonRuntimeFixtures.heartbeat.copy(sequence = 2, elapsedMillis = 15_000))
        assertEquals(PythonExecutionSessionPolicy.State.STARTED, accepted.state)

        val skipped = startedLongRunningPolicy()
        assertThrows(IllegalStateException::class.java) {
            skipped.onHeartbeat(PythonRuntimeFixtures.heartbeat.copy(sequence = 2))
        }
        assertEquals(PythonExecutionSessionPolicy.State.CLOSED, skipped.state)

        val backwards = startedLongRunningPolicy()
        backwards.onHeartbeat(PythonRuntimeFixtures.heartbeat.copy(elapsedMillis = 20_000))
        assertThrows(IllegalStateException::class.java) {
            backwards.onHeartbeat(
                PythonRuntimeFixtures.heartbeat.copy(sequence = 2, elapsedMillis = 19_999),
            )
        }
        assertEquals(PythonExecutionSessionPolicy.State.CLOSED, backwards.state)
    }

    @Test
    fun boundedAndTerminalSessionsRejectHeartbeats() {
        val bounded = PythonExecutionSessionPolicy(
            PythonRuntimeFixtures.request,
            maximumCredits = 4,
            expectedPythonVersion = PythonRuntimeFixtures.runtimeInfo.pythonVersion,
        ).apply {
            requestStart()
            onStarted(PythonRuntimeFixtures.started)
        }
        assertThrows(IllegalArgumentException::class.java) {
            bounded.onHeartbeat(PythonRuntimeFixtures.heartbeat)
        }
        assertEquals(PythonExecutionSessionPolicy.State.CLOSED, bounded.state)

        val terminal = startedLongRunningPolicy()
        terminal.onResult(PythonRuntimeFixtures.structuredResult, descriptorCount = 1)
        assertThrows(IllegalStateException::class.java) {
            terminal.onHeartbeat(PythonRuntimeFixtures.heartbeat)
        }
    }

    private fun startedLongRunningPolicy() = PythonExecutionSessionPolicy(
        PythonRuntimeFixtures.longRunningRequest,
        maximumCredits = 4,
        expectedPythonVersion = PythonRuntimeFixtures.runtimeInfo.pythonVersion,
    ).apply {
        requestStart()
        onStarted(
            PythonRuntimeFixtures.started.copy(
                protocolVersion = PythonProtocolVersion(1, 6),
                effectiveMaxOutputChunks = 128,
            ),
        )
    }
}
