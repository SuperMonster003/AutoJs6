package org.autojs.plugin.typescript.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TypeScriptR2ContractTest {

    @Test
    fun ordinaryCompilationInputQuotaIsExactRevisionTwoAndNotTheTransportCeiling() {
        assertEquals(2, TypeScriptR2Contract.ORDINARY_COMPILATION_INPUT_QUOTA_REVISION)
        assertEquals(512, TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_INPUT_FILES)
        assertEquals(
            8L * 1024L * 1024L,
            TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_SINGLE_FILE_BYTES,
        )
        assertEquals(
            8L * 1024L * 1024L,
            TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_INPUT_BYTES,
        )
        assertTrue(
            TypeScriptCompilerContract.MAX_INPUT_FILES >
                TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_INPUT_FILES,
        )
        assertTrue(
            TypeScriptCompilerContract.MAX_INPUT_BYTES >
                TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_INPUT_BYTES,
        )
        assertEquals(
            TypeScriptCompilerContract.MAX_SINGLE_FILE_BYTES,
            TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_SINGLE_FILE_BYTES,
        )
    }

    @Test
    fun ordinaryCompilationOutputQuotaIsExactRevisionTwoAndNotTheTransportCeiling() {
        assertEquals(2, TypeScriptR2Contract.ORDINARY_COMPILATION_OUTPUT_QUOTA_REVISION)
        assertEquals(512, TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_OUTPUT_FILES)
        assertEquals(
            16L * 1024L * 1024L,
            TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_SINGLE_JAVASCRIPT_BYTES,
        )
        assertEquals(
            16L * 1024L * 1024L,
            TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_SINGLE_SOURCE_MAP_BYTES,
        )
        assertEquals(
            64L * 1024L * 1024L,
            TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_OUTPUT_BYTES,
        )
        assertTrue(
            TypeScriptCompilerContract.MAX_OUTPUT_FILES >
                TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_OUTPUT_FILES,
        )
        assertTrue(
            TypeScriptCompilerContract.MAX_OUTPUT_BYTES >
                TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_OUTPUT_BYTES,
        )
        assertTrue(
            TypeScriptCompilerContract.MAX_OUTPUT_ARCHIVE_BYTES >
                TypeScriptR2Contract.MAX_ORDINARY_COMPILATION_OUTPUT_BYTES,
        )
    }

    @Test
    fun capabilityPromotionCompatibilityRevisionAndKeyAreStable() {
        assertEquals(
            "capabilityPromotionCompatibilityRevision",
            TypeScriptR2Contract.KEY_CAPABILITY_PROMOTION_COMPATIBILITY_REVISION,
        )
        assertEquals(
            1,
            TypeScriptR2Contract.CAPABILITY_PROMOTION_COMPATIBILITY_REVISION,
        )
    }

    @Test
    fun revisionOneCapabilityPromotionCompatibilityAcceptsExactlySixMasks() {
        val expectedCompatibleMasks = setOf(0, 1, 3, 7, 11, 15)
        val actualCompatibleMasks = mutableSetOf<Int>()

        for (mask in 0 until 16) {
            val compilerAvailable = mask and 1 != 0
            val projectCompilation = mask and 2 != 0
            val semanticDiagnostics = mask and 4 != 0
            val sourceMapV3 = mask and 8 != 0
            assertEquals(
                mask,
                TypeScriptR2Contract.capabilityPromotionMask(
                    compilerAvailable = compilerAvailable,
                    projectCompilation = projectCompilation,
                    semanticDiagnostics = semanticDiagnostics,
                    sourceMapV3 = sourceMapV3,
                ),
            )
            if (
                TypeScriptR2Contract.isCapabilityPromotionStructurallyCompatible(
                    compatibilityRevision =
                        TypeScriptR2Contract.CAPABILITY_PROMOTION_COMPATIBILITY_REVISION,
                    compilerAvailable = compilerAvailable,
                    projectCompilation = projectCompilation,
                    semanticDiagnostics = semanticDiagnostics,
                    sourceMapV3 = sourceMapV3,
                    nodeRuntimeRequired = false,
                )
            ) {
                actualCompatibleMasks += mask
            }
        }

        assertEquals(expectedCompatibleMasks, actualCompatibleMasks)
    }

    @Test
    fun unknownCapabilityRevisionAndNodeRuntimeDependencyFailClosed() {
        for (mask in 0 until 16) {
            val compilerAvailable = mask and 1 != 0
            val projectCompilation = mask and 2 != 0
            val semanticDiagnostics = mask and 4 != 0
            val sourceMapV3 = mask and 8 != 0
            assertFalse(
                TypeScriptR2Contract.isCapabilityPromotionStructurallyCompatible(
                    compatibilityRevision = 2,
                    compilerAvailable = compilerAvailable,
                    projectCompilation = projectCompilation,
                    semanticDiagnostics = semanticDiagnostics,
                    sourceMapV3 = sourceMapV3,
                    nodeRuntimeRequired = false,
                ),
            )
            assertFalse(
                TypeScriptR2Contract.isCapabilityPromotionStructurallyCompatible(
                    compatibilityRevision =
                        TypeScriptR2Contract.CAPABILITY_PROMOTION_COMPATIBILITY_REVISION,
                    compilerAvailable = compilerAvailable,
                    projectCompilation = projectCompilation,
                    semanticDiagnostics = semanticDiagnostics,
                    sourceMapV3 = sourceMapV3,
                    nodeRuntimeRequired = true,
                ),
            )
        }
    }

    @Test
    fun optionalBundleKeysUseStablePublicNames() {
        assertEquals(
            "inputArchiveSchemaRevision",
            TypeScriptR2Contract.KEY_INPUT_ARCHIVE_SCHEMA_REVISION,
        )
        assertEquals(
            "inputArchiveSha256",
            TypeScriptR2Contract.KEY_INPUT_ARCHIVE_SHA256,
        )
        assertEquals(
            "requestManifestSha256",
            TypeScriptR2Contract.KEY_REQUEST_MANIFEST_SHA256,
        )
        assertEquals(
            "declarationProfileFingerprint",
            TypeScriptR2Contract.KEY_DECLARATION_PROFILE_FINGERPRINT,
        )
        assertEquals("profileRevision", TypeScriptR2Contract.KEY_PROFILE_REVISION)
        assertEquals(
            "nodeProfileRevision",
            TypeScriptR2Contract.KEY_NODE_PROFILE_REVISION,
        )
        assertEquals(
            "nodeConfigSchemaRevision",
            TypeScriptR2Contract.KEY_NODE_CONFIG_SCHEMA_REVISION,
        )
        assertEquals(
            "nodeConfigKind",
            TypeScriptR2Contract.KEY_NODE_CONFIG_KIND,
        )
        assertEquals(
            "nodeRuntimeCapabilitiesFingerprint",
            TypeScriptR2Contract.KEY_NODE_RUNTIME_CAPABILITIES_FINGERPRINT,
        )
        assertEquals(
            "nodeDeclarationProfileFingerprint",
            TypeScriptR2Contract.KEY_NODE_DECLARATION_PROFILE_FINGERPRINT,
        )
        assertEquals(
            "configSchemaRevision",
            TypeScriptR2Contract.KEY_CONFIG_SCHEMA_REVISION,
        )
        assertEquals("configKind", TypeScriptR2Contract.KEY_CONFIG_KIND)
        assertEquals(
            "configByteLength",
            TypeScriptR2Contract.KEY_CONFIG_BYTE_LENGTH,
        )
        assertEquals("configSha256", TypeScriptR2Contract.KEY_CONFIG_SHA256)
        assertEquals(
            "configPolicyRevision",
            TypeScriptR2Contract.KEY_CONFIG_POLICY_REVISION,
        )
        assertEquals(
            "configPolicyFingerprint",
            TypeScriptR2Contract.KEY_CONFIG_POLICY_FINGERPRINT,
        )
        assertEquals(
            "outputArchiveSchemaRevision",
            TypeScriptR2Contract.KEY_OUTPUT_ARCHIVE_SCHEMA_REVISION,
        )
        assertEquals(
            "diagnosticsSchemaRevision",
            TypeScriptR2Contract.KEY_DIAGNOSTICS_SCHEMA_REVISION,
        )
        assertEquals(
            "cancellationOperation",
            TypeScriptR2Contract.KEY_CANCELLATION_OPERATION,
        )
        assertEquals(
            "acknowledgementRequired",
            TypeScriptR2Contract.KEY_ACKNOWLEDGEMENT_REQUIRED,
        )
        assertEquals(
            "acknowledgementTimeoutMs",
            TypeScriptR2Contract.KEY_ACKNOWLEDGEMENT_TIMEOUT_MS,
        )
    }

    @Test
    fun inputArchiveSchemaV1IdentityIsStable() {
        assertEquals(
            "autojs6.typescript.input-archive",
            TypeScriptR2Contract.INPUT_ARCHIVE_SCHEMA,
        )
        assertEquals(1, TypeScriptR2Contract.INPUT_ARCHIVE_SCHEMA_REVISION)
        assertEquals(
            "request-manifest.json",
            TypeScriptR2Contract.INPUT_ARCHIVE_MANIFEST_PATH,
        )
    }

    @Test
    fun outputArchiveSchemaV1IdentityIsStableAndDistinctFromTransport() {
        assertEquals(
            "autojs6.typescript.output-archive",
            TypeScriptR2Contract.OUTPUT_ARCHIVE_SCHEMA,
        )
        assertEquals(1, TypeScriptR2Contract.OUTPUT_ARCHIVE_SCHEMA_REVISION)
        assertEquals(
            "compilation-manifest.json",
            TypeScriptR2Contract.OUTPUT_ARCHIVE_MANIFEST_PATH,
        )
        assertEquals(
            "diagnostics.json",
            TypeScriptR2Contract.OUTPUT_ARCHIVE_DIAGNOSTICS_PATH,
        )
        assertEquals(1, TypeScriptCompilerContract.OUTPUT_ARCHIVE_TRANSPORT_VERSION)
    }

    @Test
    fun diagnosticsSchemaV1IdentityCategoriesRangesAndQuotasAreStable() {
        assertEquals(
            "autojs6.typescript.diagnostics",
            TypeScriptR2Contract.DIAGNOSTICS_SCHEMA,
        )
        assertEquals(1, TypeScriptR2Contract.DIAGNOSTICS_SCHEMA_REVISION)
        assertEquals(1, TypeScriptR2Contract.DIAGNOSTICS_QUOTA_REVISION)
        assertEquals(1_000, TypeScriptR2Contract.MAX_DIAGNOSTICS_COUNT)
        assertEquals(
            512L * 1024L,
            TypeScriptR2Contract.MAX_DIAGNOSTICS_DOCUMENT_BYTES,
        )
        assertEquals(
            1_000,
            TypeScriptR2Contract.MAX_RELATED_INFORMATION_PER_DIAGNOSTIC,
        )
        assertEquals(1_000, TypeScriptR2Contract.MAX_RELATED_INFORMATION_TOTAL)
        assertEquals(
            TypeScriptCompilerContract.MAX_ERROR_MESSAGE_CHARS,
            TypeScriptR2Contract.MAX_DIAGNOSTIC_MESSAGE_CHARS,
        )
        assertEquals(
            "utf-16-code-unit",
            TypeScriptR2Contract.DIAGNOSTICS_POSITION_ENCODING,
        )
        assertEquals(1, TypeScriptR2Contract.DIAGNOSTICS_LINE_BASE)
        assertEquals(1, TypeScriptR2Contract.DIAGNOSTICS_COLUMN_BASE)
        assertEquals(
            listOf(0, 1, 2, 3),
            listOf(
                TypeScriptR2Contract.DIAGNOSTIC_CATEGORY_WARNING,
                TypeScriptR2Contract.DIAGNOSTIC_CATEGORY_ERROR,
                TypeScriptR2Contract.DIAGNOSTIC_CATEGORY_SUGGESTION,
                TypeScriptR2Contract.DIAGNOSTIC_CATEGORY_MESSAGE,
            ),
        )
        assertTrue(
            TypeScriptCompilerContract.MAX_DIAGNOSTICS >
                TypeScriptR2Contract.MAX_DIAGNOSTICS_COUNT,
        )
        assertTrue(
            TypeScriptCompilerContract.MAX_DIAGNOSTICS_BYTES >
                TypeScriptR2Contract.MAX_DIAGNOSTICS_DOCUMENT_BYTES,
        )
    }

    @Test
    fun cancellationOperationsAreExplicitAndDistinct() {
        assertEquals(
            "cancel",
            TypeScriptR2Contract.CANCELLATION_OPERATION_CANCEL,
        )
        assertEquals(
            "acknowledge",
            TypeScriptR2Contract.CANCELLATION_OPERATION_ACKNOWLEDGE,
        )
        assertNotEquals(
            TypeScriptR2Contract.CANCELLATION_OPERATION_CANCEL,
            TypeScriptR2Contract.CANCELLATION_OPERATION_ACKNOWLEDGE,
        )
    }

    @Test
    fun controlledTsConfigPolicyNamesLimitsAndCodesAreStable() {
        assertEquals("none", TypeScriptR2Contract.CONFIG_KIND_NONE)
        assertEquals("tsconfig", TypeScriptR2Contract.CONFIG_KIND_TSCONFIG)
        assertEquals(2, TypeScriptR2Contract.CONTROLLED_TSCONFIG_SCHEMA_REVISION)
        assertEquals(2, TypeScriptR2Contract.CONTROLLED_TSCONFIG_POLICY_REVISION)
        assertEquals(
            TypeScriptCompilerContract.MAX_COMPILER_OPTIONS_BYTES,
            TypeScriptR2Contract.MAX_CONTROLLED_TSCONFIG_BYTES,
        )
        assertEquals(512, TypeScriptR2Contract.MAX_CONTROLLED_TSCONFIG_SELECTORS)
        assertEquals(
            4 * 1024,
            TypeScriptR2Contract.MAX_CONTROLLED_TSCONFIG_SELECTOR_UTF8_BYTES,
        )
        assertEquals(16, TypeScriptR2Contract.MAX_CONTROLLED_TSCONFIG_JSON_DEPTH)
        assertEquals(4 * 1024, TypeScriptR2Contract.MAX_CONTROLLED_TSCONFIG_JSON_NODES)
        assertEquals(
            910_001,
            TypeScriptR2Contract.CONFIG_DIAGNOSTIC_CODE_IGNORED_TOP_LEVEL_PROPERTY,
        )
        assertEquals(
            910_002,
            TypeScriptR2Contract.CONFIG_DIAGNOSTIC_CODE_IGNORED_COMPILER_OPTION,
        )
        assertEquals(
            listOf(
                "CONFIG_ENCODING_INVALID",
                "CONFIG_SYNTAX_INVALID",
                "CONFIG_DUPLICATE_KEY",
                "CONFIG_UNKNOWN_PROPERTY",
                "CONFIG_INVALID_VALUE",
                "CONFIG_UNSUPPORTED_FEATURE",
                "CONFIG_PROFILE_CONFLICT",
                "CONFIG_PATH_INVALID",
                "CONFIG_COMPLEXITY_EXCEEDED",
                "CONFIG_NO_INPUTS",
                "CONFIG_ENTRY_NOT_SELECTED",
            ),
            listOf(
                TypeScriptR2Contract.CONFIG_POLICY_CODE_ENCODING_INVALID,
                TypeScriptR2Contract.CONFIG_POLICY_CODE_SYNTAX_INVALID,
                TypeScriptR2Contract.CONFIG_POLICY_CODE_DUPLICATE_KEY,
                TypeScriptR2Contract.CONFIG_POLICY_CODE_UNKNOWN_PROPERTY,
                TypeScriptR2Contract.CONFIG_POLICY_CODE_INVALID_VALUE,
                TypeScriptR2Contract.CONFIG_POLICY_CODE_UNSUPPORTED_FEATURE,
                TypeScriptR2Contract.CONFIG_POLICY_CODE_PROFILE_CONFLICT,
                TypeScriptR2Contract.CONFIG_POLICY_CODE_PATH_INVALID,
                TypeScriptR2Contract.CONFIG_POLICY_CODE_COMPLEXITY_EXCEEDED,
                TypeScriptR2Contract.CONFIG_POLICY_CODE_NO_INPUTS,
                TypeScriptR2Contract.CONFIG_POLICY_CODE_ENTRY_NOT_SELECTED,
            ),
        )
    }

    @Test
    fun acknowledgementBudgetAndPublicCompilationMinimumAreDistinct() {
        assertEquals(5_000L, TypeScriptR2Contract.MAX_ACKNOWLEDGEMENT_TIMEOUT_MS)
        assertEquals(
            6_000L,
            TypeScriptR2Contract.MIN_PUBLIC_COMPILATION_TIMEOUT_MS,
        )
    }

    @Test
    fun boundedProgressExtensionUsesStablePublicNames() {
        assertEquals(1, TypeScriptR2Contract.PROGRESS_SCHEMA_REVISION)
        assertEquals(
            "progressSchemaRevision",
            TypeScriptR2Contract.KEY_PROGRESS_SCHEMA_REVISION,
        )
        assertEquals("progressKind", TypeScriptR2Contract.KEY_PROGRESS_KIND)
        assertEquals("phase", TypeScriptR2Contract.PROGRESS_KIND_PHASE)
        assertEquals("work", TypeScriptR2Contract.PROGRESS_KIND_WORK)
        assertEquals("phase", TypeScriptR2Contract.KEY_PHASE)
        assertEquals("ingress", TypeScriptR2Contract.PHASE_INGRESS)
        assertEquals("parse", TypeScriptR2Contract.PHASE_PARSE)
        assertEquals("semantic", TypeScriptR2Contract.PHASE_SEMANTIC)
        assertEquals("emit", TypeScriptR2Contract.PHASE_EMIT)
        assertEquals("phaseState", TypeScriptR2Contract.KEY_PHASE_STATE)
        assertEquals("started", TypeScriptR2Contract.PHASE_STATE_STARTED)
        assertEquals("completed", TypeScriptR2Contract.PHASE_STATE_COMPLETED)
        assertEquals(
            "progressSequence",
            TypeScriptR2Contract.KEY_PROGRESS_SEQUENCE,
        )
        assertEquals("completedUnits", TypeScriptR2Contract.KEY_COMPLETED_UNITS)
        assertEquals("totalUnits", TypeScriptR2Contract.KEY_TOTAL_UNITS)
        assertEquals(
            "elapsedRealtimeNanosSinceRequestStart",
            TypeScriptR2Contract.KEY_ELAPSED_REALTIME_NANOS_SINCE_REQUEST_START,
        )
        assertEquals(14, TypeScriptR2Contract.MAX_PROGRESS_EVENTS_PER_COMPILATION)
    }
}
