package org.autojs.autojs.core.plugin.python

import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.plugin.python.runtime.api.PythonRuntimeContract
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test
import java.nio.charset.StandardCharsets

class PythonExecutionInputPolicyTest {

    @Test
    fun absentArgumentDoesNotCreateAStdinDescriptorButExplicitEmptyInputDoes() {
        assertNull(PythonExecutionInputPolicy.resolveStdinSnapshot(ExecutionConfig()))

        val empty = ExecutionConfig().apply {
            setArgument(PythonExecutionInputPolicy.STDIN_SNAPSHOT_ARGUMENT, "")
        }
        assertEquals(0, requireNotNull(PythonExecutionInputPolicy.resolveStdinSnapshot(empty)).size)
    }

    @Test
    fun byteArraysAndCharacterSequencesAreDefensivelySnapshotted() {
        val input = "first\n".toByteArray(StandardCharsets.UTF_8)
        val bytesConfig = ExecutionConfig().apply {
            setArgument(PythonExecutionInputPolicy.STDIN_SNAPSHOT_ARGUMENT, input)
        }
        val bytesSnapshot = requireNotNull(PythonExecutionInputPolicy.resolveStdinSnapshot(bytesConfig))
        assertNotSame(input, bytesSnapshot)
        input[0] = 'X'.code.toByte()
        assertArrayEquals("first\n".toByteArray(StandardCharsets.UTF_8), bytesSnapshot)

        val text = StringBuilder("雪\n")
        val textConfig = ExecutionConfig().apply {
            setArgument(PythonExecutionInputPolicy.STDIN_SNAPSHOT_ARGUMENT, text)
        }
        val textSnapshot = requireNotNull(PythonExecutionInputPolicy.resolveStdinSnapshot(textConfig))
        text.clear()
        assertArrayEquals("雪\n".toByteArray(StandardCharsets.UTF_8), textSnapshot)
    }

    @Test
    fun unsupportedMalformedAndOversizedInputsFailWithTheStableStdinError() {
        assertStdinInvalid(ExecutionConfig().apply {
            setArgument(PythonExecutionInputPolicy.STDIN_SNAPSHOT_ARGUMENT, 7)
        })
        assertStdinInvalid(ExecutionConfig().apply {
            setArgument(PythonExecutionInputPolicy.STDIN_SNAPSHOT_ARGUMENT, "\uD800")
        })
        assertStdinInvalid(ExecutionConfig().apply {
            setArgument(
                PythonExecutionInputPolicy.STDIN_SNAPSHOT_ARGUMENT,
                ByteArray(PythonRuntimeContract.MAX_STDIN_BYTES.toInt() + 1),
            )
        })
    }

    @Test
    fun descriptorLayoutKeepsStdinBetweenWorkspaceAndHostCapabilities() {
        assertEquals(
            PythonRuntimeDescriptorLayout(0, null, null, null, 1),
            PythonRuntimeDescriptorLayout.create(
                hasWorkspace = false,
                hasStdin = false,
                hasHostCapabilities = false,
            ),
        )
        assertEquals(
            PythonRuntimeDescriptorLayout(0, null, 1, 2, 3),
            PythonRuntimeDescriptorLayout.create(
                hasWorkspace = false,
                hasStdin = true,
                hasHostCapabilities = true,
            ),
        )
        assertEquals(
            PythonRuntimeDescriptorLayout(0, 1, 2, 3, 4),
            PythonRuntimeDescriptorLayout.create(
                hasWorkspace = true,
                hasStdin = true,
                hasHostCapabilities = true,
            ),
        )
    }

    private fun assertStdinInvalid(config: ExecutionConfig) {
        val error = assertThrows(PythonRuntimeHostException::class.java) {
            PythonExecutionInputPolicy.resolveStdinSnapshot(config)
        }
        assertEquals(PythonRuntimeHostError.STDIN_INVALID, error.error)
    }
}
