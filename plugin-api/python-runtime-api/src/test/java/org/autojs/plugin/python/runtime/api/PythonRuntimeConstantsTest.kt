package org.autojs.plugin.python.runtime.api

import org.junit.Assert.assertEquals
import org.junit.Test

class PythonRuntimeConstantsTest {
    @Test
    fun stableDiscoveryAndEngineIdentifiersDoNotDrift() {
        assertEquals("python", PythonRuntimeContract.ENGINE_ID)
        assertEquals("org.autojs.plugin.INFO", PythonRuntimeContract.INFO_ACTION)
        assertEquals("org.autojs.plugin.python.RUNTIME", PythonRuntimeContract.RUNTIME_ACTION)
        assertEquals("org.autojs.permission.PLUGIN", PythonRuntimeContract.PLUGIN_PERMISSION)
        assertEquals("text/x-python", PythonRuntimeContract.SOURCE_MIME_TYPE)
        assertEquals(1, PythonRuntimeContract.PROTOCOL_MAJOR)
        assertEquals(1, PythonRuntimeContract.PROTOCOL_MINOR)
        assertEquals(1, PythonRuntimeContract.SCHEMA_MAJOR)
        assertEquals(0, PythonRuntimeContract.SCHEMA_MINOR)
        assertEquals(64L * 1024L, PythonRuntimeContract.MAX_HOST_CAPABILITY_SNAPSHOT_BYTES)
    }

    @Test
    fun schemaIdentifiersAreExactlyFrozen() {
        assertEquals(
            listOf(
                0x5059_0001,
                0x5059_0002,
                0x5059_0003,
                0x5059_0004,
                0x5059_0010,
                0x5059_0011,
                0x5059_0012,
                0x5059_0013,
                0x5059_0014,
                0x5059_0015,
                0x5059_0016,
            ),
            listOf(
                PythonRuntimeContract.SCHEMA_RUNTIME_INFO,
                PythonRuntimeContract.SCHEMA_CAPABILITIES,
                PythonRuntimeContract.SCHEMA_RESOURCE_LIMITS,
                PythonRuntimeContract.SCHEMA_PAYLOAD_REFERENCE,
                PythonRuntimeContract.SCHEMA_EXECUTION_REQUEST,
                PythonRuntimeContract.SCHEMA_SESSION_STARTED,
                PythonRuntimeContract.SCHEMA_OUTPUT_CHUNK,
                PythonRuntimeContract.SCHEMA_EXECUTION_RESULT,
                PythonRuntimeContract.SCHEMA_TRACEBACK_FRAME,
                PythonRuntimeContract.SCHEMA_EXECUTION_ERROR,
                PythonRuntimeContract.SCHEMA_CANCELLATION,
            ),
        )
    }

    @Test
    fun everyEnumWireCodeIsExactlyFrozen() {
        assertEquals(listOf("CPYTHON" to 1), PythonImplementation.entries.map { it.name to it.wireCode })
        assertEquals(
            listOf("DEDICATED_PLUGIN_PROCESS" to 1),
            PythonIsolationMode.entries.map { it.name to it.wireCode },
        )
        assertEquals(
            listOf("PROCESS_RESTART_ONLY" to 1, "COOPERATIVE_THEN_PROCESS_RESTART" to 2),
            PythonCancellationMode.entries.map { it.name to it.wireCode },
        )
        assertEquals(
            listOf(
                "SOURCE" to 1,
                "WORKSPACE_ARCHIVE" to 2,
                "STDIN" to 3,
                "HOST_CAPABILITY_SNAPSHOT" to 4,
            ),
            PythonPayloadKind.entries.map { it.name to it.wireCode },
        )
        assertEquals(
            listOf("STDOUT" to 1, "STDERR" to 2),
            PythonOutputStream.entries.map { it.name to it.wireCode },
        )
        assertEquals(
            listOf("PROJECT" to 1, "STDLIB" to 2, "PACKAGE" to 3, "GENERATED" to 4),
            PythonTracebackOrigin.entries.map { it.name to it.wireCode },
        )
        assertEquals(
            listOf(
                "INVALID_REQUEST" to 1,
                "UNSUPPORTED_PROTOCOL" to 2,
                "UNSUPPORTED_CAPABILITY" to 3,
                "BUSY" to 4,
                "SOURCE_REJECTED" to 5,
                "WORKSPACE_REJECTED" to 6,
                "PYTHON_EXCEPTION" to 7,
                "TIMEOUT" to 8,
                "OUTPUT_LIMIT_EXCEEDED" to 9,
                "CANCELLATION_FAILED" to 10,
                "RUNTIME_CRASHED" to 11,
                "STORAGE_EXHAUSTED" to 12,
                "INTERNAL" to 13,
                "SESSION_OPEN_FAILED" to 14,
                "STDIN_REJECTED" to 15,
                "HOST_CAPABILITY_SNAPSHOT_REJECTED" to 16,
            ),
            PythonErrorCode.entries.map { it.name to it.wireCode },
        )
        assertEquals(
            listOf(
                "NEGOTIATION" to 1,
                "SESSION_OPEN" to 2,
                "INPUT_VALIDATION" to 3,
                "RUNTIME_START" to 4,
                "EXECUTION" to 5,
                "OUTPUT" to 6,
                "CANCELLATION" to 7,
                "CLEANUP" to 8,
            ),
            PythonFailurePhase.entries.map { it.name to it.wireCode },
        )
        assertEquals(
            listOf("REQUESTED" to 1),
            PythonCancellationReason.entries.map { it.name to it.wireCode },
        )
    }
}
