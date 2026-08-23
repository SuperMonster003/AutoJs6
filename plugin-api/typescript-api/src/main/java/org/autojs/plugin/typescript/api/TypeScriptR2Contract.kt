package org.autojs.plugin.typescript.api

/**
 * Optional R2 Bundle extensions layered on top of the frozen v1 compiler contract.
 *
 * Peers that do not understand these keys must continue to ignore them according to the v1
 * Bundle-envelope rules. The Binder surface and [TypeScriptCompilerContract] remain unchanged.
 */
object TypeScriptR2Contract {

    const val KEY_INPUT_ARCHIVE_SCHEMA_REVISION = "inputArchiveSchemaRevision"
    const val KEY_INPUT_ARCHIVE_SHA256 = "inputArchiveSha256"
    const val KEY_REQUEST_MANIFEST_SHA256 = "requestManifestSha256"
    const val KEY_DECLARATION_PROFILE_FINGERPRINT = "declarationProfileFingerprint"
    const val KEY_PROFILE_REVISION = "profileRevision"
    const val KEY_CONFIG_SCHEMA_REVISION = "configSchemaRevision"
    const val KEY_CONFIG_KIND = "configKind"
    const val KEY_CONFIG_BYTE_LENGTH = "configByteLength"
    const val KEY_CONFIG_SHA256 = "configSha256"
    const val KEY_CONFIG_POLICY_REVISION = "configPolicyRevision"
    const val KEY_CONFIG_POLICY_FINGERPRINT = "configPolicyFingerprint"
    /* Optional public NodeNext revision-2 authority for controlled package.json metadata. */
    const val KEY_PACKAGE_METADATA_SCHEMA_REVISION = "packageMetadataSchemaRevision"
    const val KEY_PACKAGE_METADATA_POLICY_REVISION = "packageMetadataPolicyRevision"
    const val KEY_PACKAGE_METADATA_POLICY_FINGERPRINT =
        "packageMetadataPolicyFingerprint"
    const val KEY_PACKAGE_METADATA_INVENTORY_FINGERPRINT =
        "packageMetadataInventoryFingerprint"
    /* Exact public NodeNext revision-2 authority advertised alongside default Rhino metadata. */
    const val KEY_NODE_PROFILE_REVISION = "nodeProfileRevision"
    const val KEY_NODE_CONFIG_SCHEMA_REVISION = "nodeConfigSchemaRevision"
    const val KEY_NODE_CONFIG_KIND = "nodeConfigKind"
    const val KEY_NODE_RUNTIME_CAPABILITIES_FINGERPRINT =
        "nodeRuntimeCapabilitiesFingerprint"
    const val KEY_NODE_DECLARATION_PROFILE_FINGERPRINT =
        "nodeDeclarationProfileFingerprint"
    const val KEY_OUTPUT_ARCHIVE_SCHEMA_REVISION = "outputArchiveSchemaRevision"
    const val KEY_DIAGNOSTICS_SCHEMA_REVISION = "diagnosticsSchemaRevision"

    const val INPUT_ARCHIVE_SCHEMA = "autojs6.typescript.input-archive"
    const val INPUT_ARCHIVE_SCHEMA_REVISION = 1
    const val INPUT_ARCHIVE_MANIFEST_PATH = "request-manifest.json"

    /**
     * Frozen R2 output-archive schema identity.
     *
     * This revision describes the compilation outcome, accepted-input/compiler provenance,
     * effective-options binding, module inventory, and exact packaged-artifact authority. It is
     * independent from [TypeScriptCompilerContract.OUTPUT_ARCHIVE_TRANSPORT_VERSION], which only
     * identifies the ZIP/PFD transport. The diagnostics artifact has its own independently
     * versioned [DIAGNOSTICS_SCHEMA] identity.
     */
    const val OUTPUT_ARCHIVE_SCHEMA = "autojs6.typescript.output-archive"
    const val OUTPUT_ARCHIVE_SCHEMA_REVISION = 1
    const val OUTPUT_ARCHIVE_MANIFEST_PATH = "compilation-manifest.json"
    const val OUTPUT_ARCHIVE_DIAGNOSTICS_PATH = "diagnostics.json"

    /**
     * Frozen R2 diagnostics-document schema identity and revision-1 semantic quota.
     *
     * A document contains at most [MAX_DIAGNOSTICS_COUNT] primary diagnostics and at most
     * [MAX_RELATED_INFORMATION_TOTAL] related-information records. Each diagnostic may carry at
     * most [MAX_RELATED_INFORMATION_PER_DIAGNOSTIC] related records. The complete canonical UTF-8
     * document, including its envelope and all related information, is bounded by
     * [MAX_DIAGNOSTICS_DOCUMENT_BYTES]. These are ordinary compilation/schema limits; the larger
     * v1 ZIP validation ceilings in [TypeScriptCompilerContract] remain transport-only.
     *
     * Ranges use UTF-16 code-unit offsets and lengths, matching the TypeScript Compiler API.
     * Human-facing line and column values are one-based. A null URI requires a null range; a URI
     * may have a null range for a file-associated diagnostic without an exact source span.
     */
    const val DIAGNOSTICS_SCHEMA = "autojs6.typescript.diagnostics"
    const val DIAGNOSTICS_SCHEMA_REVISION = 1
    const val DIAGNOSTICS_QUOTA_REVISION = 1
    const val MAX_DIAGNOSTICS_COUNT = 1_000
    const val MAX_DIAGNOSTICS_DOCUMENT_BYTES = 512L * 1024L
    const val MAX_RELATED_INFORMATION_PER_DIAGNOSTIC = 1_000
    const val MAX_RELATED_INFORMATION_TOTAL = 1_000
    const val MAX_DIAGNOSTIC_MESSAGE_CHARS =
        TypeScriptCompilerContract.MAX_ERROR_MESSAGE_CHARS
    const val DIAGNOSTICS_POSITION_ENCODING = "utf-16-code-unit"
    const val DIAGNOSTICS_LINE_BASE = 1
    const val DIAGNOSTICS_COLUMN_BASE = 1

    /* Stable TypeScript DiagnosticCategory numeric identities. */
    const val DIAGNOSTIC_CATEGORY_WARNING = 0
    const val DIAGNOSTIC_CATEGORY_ERROR = 1
    const val DIAGNOSTIC_CATEGORY_SUGGESTION = 2
    const val DIAGNOSTIC_CATEGORY_MESSAGE = 3

    /**
     * Revision 2 ordinary-compilation caller-input quota.
     *
     * The file and byte totals cover caller sources, the optional raw config, package metadata,
     * and package targets (v1 or v2). Plugin-owned trusted declarations are deliberately outside
     * this caller domain and are governed by the compiler's separate retained-input guardrail.
     * The transport-owned request-manifest.json entry is also outside this accounting domain.
     *
     * These values are deliberately independent from the larger v1 archive transport and ZIP
     * validation ceilings in [TypeScriptCompilerContract]. A payload being transport-valid does
     * not make it eligible for ordinary compilation.
     */
    const val ORDINARY_COMPILATION_INPUT_QUOTA_REVISION = 2
    const val MAX_ORDINARY_COMPILATION_INPUT_FILES = 512
    const val MAX_ORDINARY_COMPILATION_SINGLE_FILE_BYTES = 8L * 1024L * 1024L
    const val MAX_ORDINARY_COMPILATION_INPUT_BYTES = 8L * 1024L * 1024L

    /**
     * Revision 2 ordinary-compilation output quota.
     *
     * File count, single-artifact bytes, and aggregate bytes cover emitted JavaScript and Source
     * Map artifacts after the private packager's canonical `sourceMappingURL` rewrite. The
     * generated compilation manifest,
     * diagnostics document, ZIP metadata, and container bytes are outside this semantic domain
     * and remain subject to their own schema and transport/validation ceilings.
     *
     * The Worker and native protocol parser apply the same numeric limits to raw compiler text as
     * a conservative early prefilter. The private packager is the final authority because it
     * measures bytes again after rewriting. These values deliberately retain the shipped heap
     * envelope. The larger v1 output archive constants in [TypeScriptCompilerContract] remain
     * frozen transport and ZIP validation ceilings.
     */
    const val ORDINARY_COMPILATION_OUTPUT_QUOTA_REVISION = 2
    const val MAX_ORDINARY_COMPILATION_OUTPUT_FILES = 512
    const val MAX_ORDINARY_COMPILATION_SINGLE_JAVASCRIPT_BYTES = 16L * 1024L * 1024L
    const val MAX_ORDINARY_COMPILATION_SINGLE_SOURCE_MAP_BYTES = 16L * 1024L * 1024L
    const val MAX_ORDINARY_COMPILATION_OUTPUT_BYTES = 64L * 1024L * 1024L

    /**
     * Identifies the closed capability-promotion compatibility relation advertised by a peer.
     *
     * Revision 1 models the four compiler capability flags as a dependency graph:
     * `compilerAvailable` is the root; `projectCompilation` requires that root; and
     * `semanticDiagnostics` plus `sourceMapV3` independently require project compilation. The
     * current TypeScript compiler is self-contained, so revision 1 also requires
     * `nodeRuntimeRequired=false`.
     *
     * Passing [isCapabilityPromotionStructurallyCompatible] proves only that an advertisement has
     * a recognized structure. It does not prove runtime availability, release/device evidence,
     * host admission, or readiness to promote any capability from `false` to `true`.
     */
    const val KEY_CAPABILITY_PROMOTION_COMPATIBILITY_REVISION =
        "capabilityPromotionCompatibilityRevision"
    const val CAPABILITY_PROMOTION_COMPATIBILITY_REVISION = 1

    private const val CAPABILITY_MASK_COMPILER_AVAILABLE = 1
    private const val CAPABILITY_MASK_PROJECT_COMPILATION = 2
    private const val CAPABILITY_MASK_SEMANTIC_DIAGNOSTICS = 4
    private const val CAPABILITY_MASK_SOURCE_MAP_V3 = 8

    /**
     * Returns a deterministic four-bit representation of the compiler capability flags.
     *
     * This is a pure compatibility helper, not a serialized Bundle field or promotion decision.
     */
    fun capabilityPromotionMask(
        compilerAvailable: Boolean,
        projectCompilation: Boolean,
        semanticDiagnostics: Boolean,
        sourceMapV3: Boolean,
    ): Int =
        (if (compilerAvailable) CAPABILITY_MASK_COMPILER_AVAILABLE else 0) or
            (if (projectCompilation) CAPABILITY_MASK_PROJECT_COMPILATION else 0) or
            (if (semanticDiagnostics) CAPABILITY_MASK_SEMANTIC_DIAGNOSTICS else 0) or
            (if (sourceMapV3) CAPABILITY_MASK_SOURCE_MAP_V3 else 0)

    /**
     * Returns whether the supplied capability advertisement has a recognized dependency shape.
     *
     * Revision 1 accepts exactly masks `0`, `1`, `3`, `7`, `11`, and `15`, and requires
     * `nodeRuntimeRequired=false`. Unknown revisions fail closed. Structural compatibility does
     * not establish evidence sufficiency, runtime health, host admission, or promotion readiness.
     */
    fun isCapabilityPromotionStructurallyCompatible(
        compatibilityRevision: Int,
        compilerAvailable: Boolean,
        projectCompilation: Boolean,
        semanticDiagnostics: Boolean,
        sourceMapV3: Boolean,
        nodeRuntimeRequired: Boolean,
    ): Boolean {
        if (
            compatibilityRevision != CAPABILITY_PROMOTION_COMPATIBILITY_REVISION ||
            nodeRuntimeRequired
        ) {
            return false
        }
        return when (
            capabilityPromotionMask(
                compilerAvailable = compilerAvailable,
                projectCompilation = projectCompilation,
                semanticDiagnostics = semanticDiagnostics,
                sourceMapV3 = sourceMapV3,
            )
        ) {
            0,
            1,
            3,
            7,
            11,
            15,
            -> true
            else -> false
        }
    }

    /*
     * Controlled project-config policy. Revision 2 admits raw tsconfig.json for the public Rhino
     * compilation path. Its 18 Boolean options are effective compiler inputs; unclassified keys
     * are ignored with bounded warning diagnostics, while profile, output, resolution, and
     * transformer escape surfaces remain fail-closed.
     */
    const val CONFIG_KIND_NONE = "none"
    const val CONFIG_KIND_TSCONFIG = "tsconfig"
    const val CONTROLLED_TSCONFIG_SCHEMA_REVISION = 2
    const val CONTROLLED_TSCONFIG_POLICY_REVISION = 2
    const val MAX_CONTROLLED_TSCONFIG_BYTES =
        TypeScriptCompilerContract.MAX_COMPILER_OPTIONS_BYTES
    const val MAX_CONTROLLED_TSCONFIG_SELECTORS = 512
    const val MAX_CONTROLLED_TSCONFIG_SELECTOR_UTF8_BYTES = 4 * 1024
    const val MAX_CONTROLLED_TSCONFIG_JSON_DEPTH = 16
    const val MAX_CONTROLLED_TSCONFIG_JSON_NODES = 4 * 1024

    /* Stable config-policy detail codes. Error messages are deliberately non-contractual. */
    const val CONFIG_POLICY_CODE_ENCODING_INVALID = "CONFIG_ENCODING_INVALID"
    const val CONFIG_POLICY_CODE_SYNTAX_INVALID = "CONFIG_SYNTAX_INVALID"
    const val CONFIG_POLICY_CODE_DUPLICATE_KEY = "CONFIG_DUPLICATE_KEY"
    const val CONFIG_POLICY_CODE_UNKNOWN_PROPERTY = "CONFIG_UNKNOWN_PROPERTY"
    const val CONFIG_POLICY_CODE_INVALID_VALUE = "CONFIG_INVALID_VALUE"
    const val CONFIG_POLICY_CODE_UNSUPPORTED_FEATURE = "CONFIG_UNSUPPORTED_FEATURE"
    const val CONFIG_POLICY_CODE_PROFILE_CONFLICT = "CONFIG_PROFILE_CONFLICT"
    const val CONFIG_POLICY_CODE_PATH_INVALID = "CONFIG_PATH_INVALID"
    const val CONFIG_POLICY_CODE_COMPLEXITY_EXCEEDED = "CONFIG_COMPLEXITY_EXCEEDED"
    const val CONFIG_POLICY_CODE_NO_INPUTS = "CONFIG_NO_INPUTS"
    const val CONFIG_POLICY_CODE_ENTRY_NOT_SELECTED = "CONFIG_ENTRY_NOT_SELECTED"

    /* Stable non-TypeScript diagnostic codes emitted by controlled-config policy revision 2. */
    const val CONFIG_DIAGNOSTIC_CODE_IGNORED_TOP_LEVEL_PROPERTY = 910_001
    const val CONFIG_DIAGNOSTIC_CODE_IGNORED_COMPILER_OPTION = 910_002

    const val KEY_CANCELLATION_OPERATION = "cancellationOperation"
    const val CANCELLATION_OPERATION_CANCEL = "cancel"
    const val CANCELLATION_OPERATION_ACKNOWLEDGE = "acknowledge"

    const val KEY_ACKNOWLEDGEMENT_REQUIRED = "acknowledgementRequired"
    const val KEY_ACKNOWLEDGEMENT_TIMEOUT_MS = "acknowledgementTimeoutMs"

    /*
     * Additive, bounded progress Bundles delivered through the v1 onProgress(Bundle) method.
     * These values expose no source path or diagnostic text. The nanosecond offset is measured
     * from the irreversible server-side request-start boundary, so a caller can compare progress
     * with the request's absolute timeout without relying on Binder delivery latency.
     */
    const val PROGRESS_SCHEMA_REVISION = 1
    const val KEY_PROGRESS_SCHEMA_REVISION = "progressSchemaRevision"
    const val KEY_PROGRESS_KIND = "progressKind"
    const val PROGRESS_KIND_PHASE = "phase"
    const val PROGRESS_KIND_WORK = "work"
    const val KEY_PHASE = "phase"
    const val PHASE_INGRESS = "ingress"
    const val PHASE_PARSE = "parse"
    const val PHASE_SEMANTIC = "semantic"
    const val PHASE_EMIT = "emit"
    const val KEY_PHASE_STATE = "phaseState"
    const val PHASE_STATE_STARTED = "started"
    const val PHASE_STATE_COMPLETED = "completed"
    const val KEY_PROGRESS_SEQUENCE = "progressSequence"
    const val KEY_COMPLETED_UNITS = "completedUnits"
    const val KEY_TOTAL_UNITS = "totalUnits"
    const val KEY_ELAPSED_REALTIME_NANOS_SINCE_REQUEST_START =
        "elapsedRealtimeNanosSinceRequestStart"

    const val MAX_PROGRESS_EVENTS_PER_COMPILATION = 14

    /**
     * Public-preview callers receive a fixed five-second server-side acknowledgement budget.
     * The extra second in the minimum compilation timeout keeps that admission budget distinct
     * from the project compilation deadline.
     */
    const val MAX_ACKNOWLEDGEMENT_TIMEOUT_MS = 5_000L
    const val MIN_PUBLIC_COMPILATION_TIMEOUT_MS = 6_000L
}
