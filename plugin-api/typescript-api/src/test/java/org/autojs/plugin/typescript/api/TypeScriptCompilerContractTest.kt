package org.autojs.plugin.typescript.api

import java.security.MessageDigest
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TypeScriptCompilerContractTest {

    @Test
    fun contractUsesVersionedArchiveTransport() {
        assertEquals(1, TypeScriptCompilerContract.VERSION)
        assertEquals(64, TypeScriptCompilerContract.HASH.length)
        assertEquals(
            TypeScriptCompilerContract.HASH,
            MessageDigest.getInstance("SHA-256")
                .digest(TypeScriptCompilerContract.HASH_INPUT.toByteArray(Charsets.UTF_8))
                .joinToString("") { "%02x".format(it) },
        )
        assertEquals(1, TypeScriptCompilerContract.INPUT_ARCHIVE_TRANSPORT_VERSION)
        assertEquals(1, TypeScriptCompilerContract.OUTPUT_ARCHIVE_TRANSPORT_VERSION)
    }

    @Test
    fun compilerTransactionsRemainAppendOnlyAndKeepRawParcelOffsets() {
        val expectedTransactionPrefix = listOf(
            "getContractVersion()->int",
            "getContractHash()->String",
            "getCompilerInfo()->Bundle",
            "prewarm(Bundle)->Bundle",
            "startCompilation(Bundle,ParcelFileDescriptor,ParcelFileDescriptor," +
                "ITypeScriptCompilationCallback)->Bundle",
            "cancelCompilation(Bundle)->boolean",
        )
        val transactionDescriptors = TypeScriptCompilerContract.HASH_INPUT
            .split('|')
            .drop(1)
            .takeWhile { descriptor -> !descriptor.startsWith("callback:") }

        assertTrue(transactionDescriptors.size >= expectedTransactionPrefix.size)
        assertEquals(
            expectedTransactionPrefix,
            transactionDescriptors.take(expectedTransactionPrefix.size),
        )
        assertEquals("prewarm(Bundle)->Bundle", transactionDescriptors[3])
        assertTrue(transactionDescriptors[4].startsWith("startCompilation("))
        assertEquals("cancelCompilation(Bundle)->boolean", transactionDescriptors[5])
    }

    @Test
    fun supportedSourceKindsExcludeDeclarationsAndTsx() {
        val extensions = TypeScriptCompilerContract.SUPPORTED_EXTENSIONS.toSet()
        assertTrue(".ts" in extensions)
        assertTrue(".mts" in extensions)
        assertTrue(".cts" in extensions)
        assertFalse(".tsx" in extensions)
        assertFalse(".d.ts" in extensions)
    }

    @Test
    fun targetProfilesAreExecutionEngineNeutral() {
        assertArrayEquals(
            arrayOf(
                TypeScriptCompilerContract.PROFILE_RHINO,
                TypeScriptCompilerContract.PROFILE_NODE,
            ),
            TypeScriptCompilerContract.TARGET_PROFILES,
        )
    }

    @Test
    fun initialExtensionMatrixKeepsMtsAndCtsNodeOnly() {
        assertArrayEquals(
            arrayOf(".ts"),
            TypeScriptCompilerContract.RHINO_SUPPORTED_EXTENSIONS,
        )
        assertArrayEquals(
            arrayOf(".ts", ".mts", ".cts"),
            TypeScriptCompilerContract.NODE_SUPPORTED_EXTENSIONS,
        )
    }

    @Test
    fun compilationModesAreExplicit() {
        assertArrayEquals(
            arrayOf(
                TypeScriptCompilerContract.MODE_DEVELOPMENT,
                TypeScriptCompilerContract.MODE_RELEASE,
            ),
            TypeScriptCompilerContract.COMPILATION_MODES,
        )
    }

    @Test
    fun pluginVariantIsDerivedFromThePinnedCompilerVersion() {
        assertEquals(
            "typescript_6_0_3",
            TypeScriptPluginIds.variant("6.0.3"),
        )
    }

    @Test
    fun terminalOutcomesDistinguishDiagnosticsFromTransportFailure() {
        assertArrayEquals(
            arrayOf(
                TypeScriptCompilerContract.OUTCOME_EMITTED,
                TypeScriptCompilerContract.OUTCOME_DIAGNOSTICS,
                TypeScriptCompilerContract.OUTCOME_CANCELLED,
                TypeScriptCompilerContract.OUTCOME_TIMED_OUT,
                TypeScriptCompilerContract.OUTCOME_FAILED,
            ),
            TypeScriptCompilerContract.TERMINAL_OUTCOMES,
        )
    }

    @Test
    fun transportBudgetsAreFinite() {
        assertTrue(TypeScriptCompilerContract.MAX_INPUT_FILES > 0)
        assertTrue(
            TypeScriptCompilerContract.MAX_COMPILER_OPTIONS_BYTES <
                TypeScriptCompilerContract.MAX_METADATA_BYTES,
        )
        assertTrue(TypeScriptCompilerContract.MAX_SINGLE_FILE_BYTES > 0L)
        assertTrue(
            TypeScriptCompilerContract.MAX_INPUT_BYTES >
                TypeScriptCompilerContract.MAX_SINGLE_FILE_BYTES,
        )
        assertTrue(
            TypeScriptCompilerContract.MAX_OUTPUT_BYTES >
                TypeScriptCompilerContract.MAX_SINGLE_SOURCE_MAP_BYTES,
        )
        assertTrue(
            TypeScriptCompilerContract.MAX_OUTPUT_ARCHIVE_BYTES <
                TypeScriptCompilerContract.MAX_OUTPUT_BYTES,
        )
        assertTrue(
            TypeScriptCompilerContract.MAX_PRIVATE_STAGING_BYTES >
                TypeScriptCompilerContract.MAX_OUTPUT_ARCHIVE_BYTES,
        )
        assertTrue(TypeScriptCompilerContract.MAX_DIAGNOSTICS > 0)
        assertTrue(
            TypeScriptCompilerContract.MAX_TIMEOUT_MS >=
                TypeScriptCompilerContract.DEFAULT_TIMEOUT_MS,
        )
    }
}
