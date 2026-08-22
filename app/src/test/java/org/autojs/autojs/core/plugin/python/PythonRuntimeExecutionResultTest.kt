package org.autojs.autojs.core.plugin.python

import org.autojs.plugin.python.runtime.api.PythonExecutionResult
import org.autojs.plugin.python.runtime.api.PythonOutputArtifact
import org.autojs.plugin.python.runtime.api.PythonSha256
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.ByteArrayInputStream

class PythonRuntimeExecutionResultTest {
    @Test
    fun exactArtifactBytesAndDigestAreAcceptedWithDefensiveHostOwnership() {
        val source = "artifact-data\n".toByteArray()
        val metadata = artifact(source)
        val verified = PythonRuntimeResultPayloadVerifier.readExactly(
            metadata,
            ByteArrayInputStream(source),
        )
        val delivered = PythonRuntimeOutputArtifact(metadata.path, metadata.sha256, verified)
        verified.fill(0)

        assertArrayEquals(source, delivered.toByteArray())
        val callerCopy = delivered.toByteArray()
        callerCopy.fill(1)
        assertArrayEquals(source, delivered.toByteArray())
    }

    @Test
    fun earlyEofTrailingBytesAndDigestMismatchAreRejected() {
        val source = "payload".toByteArray()
        assertThrows(IllegalArgumentException::class.java) {
            PythonRuntimeResultPayloadVerifier.readExactly(
                artifact(source),
                ByteArrayInputStream(source.copyOf(source.size - 1)),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            PythonRuntimeResultPayloadVerifier.readExactly(
                artifact(source),
                ByteArrayInputStream(source + 0x01),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            PythonRuntimeResultPayloadVerifier.readExactly(
                artifact(source).copy(sha256 = PythonSha256.digest("different".toByteArray())),
                ByteArrayInputStream(source),
            )
        }
    }

    @Test
    fun structuredResultComesOnlyFromTheTypedTerminalField() {
        val terminal = PythonExecutionResult(
            requestId = PythonRuntimeHostTestFixtures.requestId,
            exitCode = 0,
            elapsedMillis = 1,
            stdoutBytes = 19,
            stderrBytes = 0,
            stdoutChunks = 1,
            stderrChunks = 0,
            structuredJson = "{\"typed\":true}",
        )
        val result = PythonRuntimeExecutionResult(terminal, emptyList())

        assertEquals("{\"typed\":true}", result.structuredJson)
        assertNotEquals("{\"stdout\":true}", result.structuredJson)
        assertEquals(19L, result.terminal.stdoutBytes)
    }

    private fun artifact(bytes: ByteArray) = PythonOutputArtifact(
        path = "reports/result.bin",
        descriptorIndex = 0,
        declaredLengthBytes = bytes.size.toLong(),
        sha256 = PythonSha256.digest(bytes),
    )
}
