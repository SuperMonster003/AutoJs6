package org.autojs.plugin.dexcompiler.api

import org.autojs.plugin.protocol.wire.TaggedWireDocument
import org.autojs.plugin.protocol.wire.TaggedWireLimits
import org.autojs.plugin.protocol.wire.TaggedWireWriter

object DexCompilerCodec {
    private val WIRE_LIMITS = TaggedWireLimits(
        maxDocumentBytes = DexCompilerContract.MAX_METADATA_BYTES,
        maxFieldBytes = DexCompilerContract.MAX_METADATA_BYTES,
        maxFields = 1_024,
    )

    fun encodeInfo(value: DexCompilerInfo): ByteArray {
        DexCompilerValidation.validateInfo(value)
        return writer(DexCompilerContract.SCHEMA_COMPILER_INFO)
            .int32(InfoTags.PROTOCOL_MIN_MAJOR, value.protocolMin.major, requiredForReader = true)
            .int32(InfoTags.PROTOCOL_MIN_MINOR, value.protocolMin.minor, requiredForReader = true)
            .int32(InfoTags.PROTOCOL_MAX_MAJOR, value.protocolMax.major, requiredForReader = true)
            .int32(InfoTags.PROTOCOL_MAX_MINOR, value.protocolMax.minor, requiredForReader = true)
            .string(InfoTags.PROVIDER_ID, value.providerId, requiredForReader = true)
            .string(InfoTags.PROVIDER_VERSION_NAME, value.providerVersionName, requiredForReader = true)
            .int64(InfoTags.PROVIDER_VERSION_CODE, value.providerVersionCode, requiredForReader = true)
            .apply {
                value.minHostVersionCode?.let { int64(InfoTags.MIN_HOST_VERSION_CODE, it) }
                value.maxHostVersionCode?.let { int64(InfoTags.MAX_HOST_VERSION_CODE, it) }
            }
            .int32(InfoTags.ABI_MODE, value.abiMode.wireCode, requiredForReader = true)
            .encode()
    }

    fun decodeInfo(bytes: ByteArray): DexCompilerInfo {
        val document = document(bytes, DexCompilerContract.SCHEMA_COMPILER_INFO, InfoTags.ALL)
        return DexCompilerInfo(
            protocolMin = DexProtocolVersion(
                document.requireInt32(InfoTags.PROTOCOL_MIN_MAJOR),
                document.requireInt32(InfoTags.PROTOCOL_MIN_MINOR),
            ),
            protocolMax = DexProtocolVersion(
                document.requireInt32(InfoTags.PROTOCOL_MAX_MAJOR),
                document.requireInt32(InfoTags.PROTOCOL_MAX_MINOR),
            ),
            providerId = document.requireString(InfoTags.PROVIDER_ID),
            providerVersionName = document.requireString(InfoTags.PROVIDER_VERSION_NAME),
            providerVersionCode = document.requireInt64(InfoTags.PROVIDER_VERSION_CODE),
            minHostVersionCode = document.optionalInt64(InfoTags.MIN_HOST_VERSION_CODE),
            maxHostVersionCode = document.optionalInt64(InfoTags.MAX_HOST_VERSION_CODE),
            abiMode = enumByCode<DexCompilerAbiMode>(document.requireInt32(InfoTags.ABI_MODE), "ABI mode") {
                it.wireCode
            },
        ).also(DexCompilerValidation::validateInfo)
    }

    fun encodeCapabilities(value: DexCompilerCapabilities): ByteArray {
        DexCompilerValidation.validateCapabilities(value)
        return writer(
            DexCompilerContract.SCHEMA_CAPABILITIES,
            schemaMinor = if (value.classpathBundle == null) {
                DexCompilerContract.SCHEMA_MINOR
            } else {
                DexCompilerContract.PROTOCOL_CLASSPATH_BUNDLE_MINOR
            },
        )
            .int32(CapabilityTags.COMPILER_FAMILY, value.compilerFamily.wireCode, requiredForReader = true)
            .string(CapabilityTags.COMPILER_VERSION, value.compilerVersion, requiredForReader = true)
            .strings(CapabilityTags.INPUT_FORMATS, value.inputFormats.map { it.wireName }, requiredForReader = true)
            .strings(CapabilityTags.OUTPUT_FORMATS, value.outputFormats.map { it.wireName }, requiredForReader = true)
            .strings(CapabilityTags.MODES, value.modes.map { it.wireName }, requiredForReader = true)
            .int32(CapabilityTags.MIN_API, value.minApi, requiredForReader = true)
            .int32(CapabilityTags.MAX_API, value.maxApi, requiredForReader = true)
            .boolean(CapabilityTags.SUPPORTS_MULTIDEX, value.supportsMultiDex, requiredForReader = true)
            .int32(CapabilityTags.DETERMINISM_CLAIM, value.determinismClaim.wireCode, requiredForReader = true)
            .int32(CapabilityTags.RUNTIME_LIBRARY_MODEL, value.runtimeLibraryModel.wireCode, requiredForReader = true)
            .boolean(CapabilityTags.ACCEPTS_EXTERNAL_CLASSPATH, value.acceptsExternalClasspath, requiredForReader = true)
            .boolean(
                CapabilityTags.SUPPORTS_DESUGARED_LIBRARY_CONFIGURATION,
                value.supportsDesugaredLibraryConfiguration,
                requiredForReader = true,
            )
            .int64(
                CapabilityTags.MAX_COMPRESSED_PROGRAM_BYTES,
                value.limits.maxCompressedProgramBytes,
                requiredForReader = true,
            )
            .int32(CapabilityTags.MAX_ARCHIVE_ENTRIES, value.limits.maxArchiveEntries, requiredForReader = true)
            .int64(
                CapabilityTags.MAX_UNCOMPRESSED_PROGRAM_BYTES,
                value.limits.maxUncompressedProgramBytes,
                requiredForReader = true,
            )
            .int64(CapabilityTags.MAX_TOTAL_CLASS_BYTES, value.limits.maxTotalClassBytes, requiredForReader = true)
            .int64(CapabilityTags.MAX_SINGLE_CLASS_BYTES, value.limits.maxSingleClassBytes, requiredForReader = true)
            .int64(CapabilityTags.MAX_OUTPUT_BYTES, value.limits.maxOutputBytes, requiredForReader = true)
            .int32(CapabilityTags.MAX_DEX_ENTRIES, value.limits.maxDexEntries, requiredForReader = true)
            .int32(CapabilityTags.MAX_DIAGNOSTIC_BYTES, value.limits.maxDiagnosticBytes, requiredForReader = true)
            .int32(
                CapabilityTags.MAX_CONCURRENT_SESSIONS,
                value.limits.maxConcurrentSessions,
                requiredForReader = true,
            )
            .apply {
                value.runtimeLibraryIdentities.forEach { identity ->
                    document(
                        CapabilityTags.RUNTIME_LIBRARY_IDENTITIES,
                        encodeRuntimeLibraryIdentity(identity),
                        requiredForReader = true,
                    )
                }
            }
            .bytes(
                CapabilityTags.RUNTIME_LIBRARY_FINGERPRINT,
                value.runtimeLibraryFingerprint.toByteArray(),
                requiredForReader = true,
            )
            .apply {
                value.classpathBundle?.let { capability ->
                    // These fields must stay optional to readers so a frozen V1 host can retain
                    // the legacy tags 1..23 view and negotiate protocol 1.0 with this provider.
                    int32(CapabilityTags.CLASSPATH_INPUT_LAYOUT, capability.inputLayout.wireCode)
                    int32(
                        CapabilityTags.MAX_CLASSPATH_JAR_COUNT,
                        capability.limits.maxClasspathJarCount,
                    )
                    int64(
                        CapabilityTags.MAX_COMPRESSED_CLASSPATH_JAR_BYTES,
                        capability.limits.maxCompressedClasspathJarBytes,
                    )
                    int64(
                        CapabilityTags.MAX_TOTAL_COMPRESSED_CLASSPATH_BYTES,
                        capability.limits.maxTotalCompressedClasspathBytes,
                    )
                    int64(CapabilityTags.MAX_INPUT_BUNDLE_BYTES, capability.limits.maxInputBundleBytes)
                    int32(
                        CapabilityTags.MAX_TOTAL_INPUT_ARCHIVE_ENTRIES,
                        capability.limits.maxTotalInputArchiveEntries,
                    )
                    int64(
                        CapabilityTags.MAX_TOTAL_UNCOMPRESSED_INPUT_BYTES,
                        capability.limits.maxTotalUncompressedInputBytes,
                    )
                    int64(
                        CapabilityTags.MAX_TOTAL_INPUT_CLASS_BYTES,
                        capability.limits.maxTotalInputClassBytes,
                    )
                }
            }
            .encode()
    }

    fun decodeCapabilities(bytes: ByteArray): DexCompilerCapabilities {
        val document = document(
            bytes,
            DexCompilerContract.SCHEMA_CAPABILITIES,
            CapabilityTags.ALL,
            CapabilityTags.REPEATED,
        )
        return DexCompilerCapabilities(
            compilerFamily = enumByCode<DexCompilerFamily>(
                document.requireInt32(CapabilityTags.COMPILER_FAMILY),
                "compiler family",
            ) { it.wireCode },
            compilerVersion = document.requireString(CapabilityTags.COMPILER_VERSION),
            inputFormats = document.strings(CapabilityTags.INPUT_FORMATS).map { wireName ->
                enumByName<DexCompilerInputFormat>(wireName, "input format") { it.wireName }
            },
            outputFormats = document.strings(CapabilityTags.OUTPUT_FORMATS).map { wireName ->
                enumByName<DexCompilerOutputFormat>(wireName, "output format") { it.wireName }
            },
            modes = document.strings(CapabilityTags.MODES).map { wireName ->
                enumByName<DexCompilerMode>(wireName, "compilation mode") { it.wireName }
            },
            minApi = document.requireInt32(CapabilityTags.MIN_API),
            maxApi = document.requireInt32(CapabilityTags.MAX_API),
            supportsMultiDex = document.requireBoolean(CapabilityTags.SUPPORTS_MULTIDEX),
            determinismClaim = enumByCode<DexCompilerDeterminismClaim>(
                document.requireInt32(CapabilityTags.DETERMINISM_CLAIM),
                "determinism claim",
            ) { it.wireCode },
            runtimeLibraryModel = enumByCode<DexRuntimeLibraryModel>(
                document.requireInt32(CapabilityTags.RUNTIME_LIBRARY_MODEL),
                "runtime-library model",
            ) { it.wireCode },
            acceptsExternalClasspath = document.requireBoolean(CapabilityTags.ACCEPTS_EXTERNAL_CLASSPATH),
            supportsDesugaredLibraryConfiguration = document.requireBoolean(
                CapabilityTags.SUPPORTS_DESUGARED_LIBRARY_CONFIGURATION
            ),
            limits = DexCompilerResourceLimits(
                maxCompressedProgramBytes = document.requireInt64(CapabilityTags.MAX_COMPRESSED_PROGRAM_BYTES),
                maxArchiveEntries = document.requireInt32(CapabilityTags.MAX_ARCHIVE_ENTRIES),
                maxUncompressedProgramBytes = document.requireInt64(CapabilityTags.MAX_UNCOMPRESSED_PROGRAM_BYTES),
                maxTotalClassBytes = document.requireInt64(CapabilityTags.MAX_TOTAL_CLASS_BYTES),
                maxSingleClassBytes = document.requireInt64(CapabilityTags.MAX_SINGLE_CLASS_BYTES),
                maxOutputBytes = document.requireInt64(CapabilityTags.MAX_OUTPUT_BYTES),
                maxDexEntries = document.requireInt32(CapabilityTags.MAX_DEX_ENTRIES),
                maxDiagnosticBytes = document.requireInt32(CapabilityTags.MAX_DIAGNOSTIC_BYTES),
                maxConcurrentSessions = document.requireInt32(CapabilityTags.MAX_CONCURRENT_SESSIONS),
            ),
            runtimeLibraryIdentities = document.documents(CapabilityTags.RUNTIME_LIBRARY_IDENTITIES)
                .map(::decodeRuntimeLibraryIdentity),
            runtimeLibraryFingerprint = DexSha256.fromBytes(
                document.requireBytes(CapabilityTags.RUNTIME_LIBRARY_FINGERPRINT)
            ),
            classpathBundle = decodeClasspathBundle(document),
        ).also(DexCompilerValidation::validateCapabilities)
    }

    fun encodeRequest(value: DexCompileRequest): ByteArray {
        DexCompilerValidation.validateRequest(value)
        return writer(
            DexCompilerContract.SCHEMA_COMPILE_REQUEST,
            schemaMinor = if (value.protocolVersion == DexCompilerContract.PROTOCOL_V1_1) {
                DexCompilerContract.PROTOCOL_CLASSPATH_BUNDLE_MINOR
            } else {
                DexCompilerContract.SCHEMA_MINOR
            },
        )
            .bytes(RequestTags.REQUEST_ID, value.requestId.toByteArray(), requiredForReader = true)
            .int32(RequestTags.PROTOCOL_MAJOR, value.protocolVersion.major, requiredForReader = true)
            .int32(RequestTags.PROTOCOL_MINOR, value.protocolVersion.minor, requiredForReader = true)
            .string(RequestTags.INPUT_FORMAT, value.inputFormat.wireName, requiredForReader = true)
            .string(RequestTags.OUTPUT_FORMAT, value.outputFormat.wireName, requiredForReader = true)
            .string(RequestTags.MODE, value.mode.wireName, requiredForReader = true)
            .int32(RequestTags.MIN_API, value.minApi, requiredForReader = true)
            .int64(RequestTags.PROGRAM_SIZE_BYTES, value.programSizeBytes, requiredForReader = true)
            .bytes(RequestTags.PROGRAM_SHA256, value.programSha256.toByteArray(), requiredForReader = true)
            .int64(RequestTags.MAX_OUTPUT_BYTES, value.maxOutputBytes, requiredForReader = true)
            .int32(RequestTags.RUNTIME_LIBRARY_MODEL, value.runtimeLibraryModel.wireCode, requiredForReader = true)
            .bytes(
                RequestTags.EXPECTED_RUNTIME_LIBRARY_FINGERPRINT,
                value.expectedRuntimeLibraryFingerprint.toByteArray(),
                requiredForReader = true,
            )
            .int32(
                RequestTags.CANONICALIZATION_POLICY_VERSION,
                value.canonicalizationPolicyVersion,
                requiredForReader = true,
            )
            .int32(RequestTags.DIAGNOSTIC_BYTE_LIMIT, value.diagnosticByteLimit, requiredForReader = true)
            .int64(RequestTags.TIMEOUT_MILLIS, value.timeoutMillis, requiredForReader = true)
            .apply {
                if (value.protocolVersion == DexCompilerContract.PROTOCOL_V1_1) {
                    int32(
                        RequestTags.INPUT_LAYOUT,
                        value.inputLayout!!.wireCode,
                        requiredForReader = true,
                    )
                    value.inputIdentities.forEach { identity ->
                        document(
                            RequestTags.INPUT_IDENTITIES,
                            encodeInputIdentity(identity),
                            requiredForReader = true,
                        )
                    }
                    bytes(
                        RequestTags.INPUT_SET_FINGERPRINT,
                        value.inputSetFingerprint!!.toByteArray(),
                        requiredForReader = true,
                    )
                    int64(
                        RequestTags.INPUT_BUNDLE_SIZE_BYTES,
                        value.inputBundleSizeBytes!!,
                        requiredForReader = true,
                    )
                    bytes(
                        RequestTags.INPUT_BUNDLE_SHA256,
                        value.inputBundleSha256!!.toByteArray(),
                        requiredForReader = true,
                    )
                }
            }
            .encode()
    }

    fun decodeRequest(bytes: ByteArray): DexCompileRequest {
        val document = document(
            bytes,
            DexCompilerContract.SCHEMA_COMPILE_REQUEST,
            RequestTags.ALL,
            RequestTags.REPEATED,
        )
        return DexCompileRequest(
            requestId = DexRequestId.fromBytes(document.requireBytes(RequestTags.REQUEST_ID)),
            protocolVersion = DexProtocolVersion(
                document.requireInt32(RequestTags.PROTOCOL_MAJOR),
                document.requireInt32(RequestTags.PROTOCOL_MINOR),
            ),
            inputFormat = enumByName<DexCompilerInputFormat>(
                document.requireString(RequestTags.INPUT_FORMAT),
                "input format",
            ) { it.wireName },
            outputFormat = enumByName<DexCompilerOutputFormat>(
                document.requireString(RequestTags.OUTPUT_FORMAT),
                "output format",
            ) { it.wireName },
            mode = enumByName<DexCompilerMode>(document.requireString(RequestTags.MODE), "compilation mode") {
                it.wireName
            },
            minApi = document.requireInt32(RequestTags.MIN_API),
            programSizeBytes = document.requireInt64(RequestTags.PROGRAM_SIZE_BYTES),
            programSha256 = DexSha256.fromBytes(document.requireBytes(RequestTags.PROGRAM_SHA256)),
            maxOutputBytes = document.requireInt64(RequestTags.MAX_OUTPUT_BYTES),
            runtimeLibraryModel = enumByCode<DexRuntimeLibraryModel>(
                document.requireInt32(RequestTags.RUNTIME_LIBRARY_MODEL),
                "runtime-library model",
            ) { it.wireCode },
            expectedRuntimeLibraryFingerprint = DexSha256.fromBytes(
                document.requireBytes(RequestTags.EXPECTED_RUNTIME_LIBRARY_FINGERPRINT)
            ),
            canonicalizationPolicyVersion = document.requireInt32(RequestTags.CANONICALIZATION_POLICY_VERSION),
            diagnosticByteLimit = document.requireInt32(RequestTags.DIAGNOSTIC_BYTE_LIMIT),
            timeoutMillis = document.requireInt64(RequestTags.TIMEOUT_MILLIS),
            inputLayout = document.optionalInt32(RequestTags.INPUT_LAYOUT)?.let { value ->
                enumByCode<DexCompilerInputLayout>(value, "input layout") { it.wireCode }
            },
            inputIdentities = document.documents(RequestTags.INPUT_IDENTITIES).map(::decodeInputIdentity),
            inputSetFingerprint = document.optionalBytes(RequestTags.INPUT_SET_FINGERPRINT)?.let {
                DexSha256.fromBytes(it)
            },
            inputBundleSizeBytes = document.optionalInt64(RequestTags.INPUT_BUNDLE_SIZE_BYTES),
            inputBundleSha256 = document.optionalBytes(RequestTags.INPUT_BUNDLE_SHA256)?.let {
                DexSha256.fromBytes(it)
            },
        ).also(DexCompilerValidation::validateRequest)
    }

    fun encodeStarted(value: DexCompileStarted): ByteArray {
        DexCompilerValidation.validateStarted(value)
        return writer(DexCompilerContract.SCHEMA_STARTED)
            .bytes(StartedTags.REQUEST_ID, value.requestId.toByteArray(), requiredForReader = true)
            .int64(StartedTags.SEQUENCE, value.sequence, requiredForReader = true)
            .int32(StartedTags.PROTOCOL_MAJOR, value.protocolVersion.major, requiredForReader = true)
            .int32(StartedTags.PROTOCOL_MINOR, value.protocolVersion.minor, requiredForReader = true)
            .int32(StartedTags.COMPILER_FAMILY, value.compilerFamily.wireCode, requiredForReader = true)
            .string(StartedTags.COMPILER_VERSION, value.compilerVersion, requiredForReader = true)
            .int32(StartedTags.RUNTIME_LIBRARY_MODEL, value.runtimeLibraryModel.wireCode, requiredForReader = true)
            .bytes(
                StartedTags.RUNTIME_LIBRARY_FINGERPRINT,
                value.runtimeLibraryFingerprint.toByteArray(),
                requiredForReader = true,
            )
            .int64(StartedTags.QUEUE_ELAPSED_MILLIS, value.queueElapsedMillis, requiredForReader = true)
            .encode()
    }

    fun decodeStarted(bytes: ByteArray): DexCompileStarted {
        val document = document(bytes, DexCompilerContract.SCHEMA_STARTED, StartedTags.ALL)
        return DexCompileStarted(
            requestId = DexRequestId.fromBytes(document.requireBytes(StartedTags.REQUEST_ID)),
            sequence = document.requireInt64(StartedTags.SEQUENCE),
            protocolVersion = DexProtocolVersion(
                document.requireInt32(StartedTags.PROTOCOL_MAJOR),
                document.requireInt32(StartedTags.PROTOCOL_MINOR),
            ),
            compilerFamily = enumByCode<DexCompilerFamily>(
                document.requireInt32(StartedTags.COMPILER_FAMILY),
                "compiler family",
            ) { it.wireCode },
            compilerVersion = document.requireString(StartedTags.COMPILER_VERSION),
            runtimeLibraryModel = enumByCode<DexRuntimeLibraryModel>(
                document.requireInt32(StartedTags.RUNTIME_LIBRARY_MODEL),
                "runtime-library model",
            ) { it.wireCode },
            runtimeLibraryFingerprint = DexSha256.fromBytes(
                document.requireBytes(StartedTags.RUNTIME_LIBRARY_FINGERPRINT)
            ),
            queueElapsedMillis = document.requireInt64(StartedTags.QUEUE_ELAPSED_MILLIS),
        ).also(DexCompilerValidation::validateStarted)
    }

    fun encodeProgress(value: DexCompileProgress): ByteArray {
        DexCompilerValidation.validateProgress(value)
        return writer(DexCompilerContract.SCHEMA_PROGRESS)
            .bytes(ProgressTags.REQUEST_ID, value.requestId.toByteArray(), requiredForReader = true)
            .int64(ProgressTags.SEQUENCE, value.sequence, requiredForReader = true)
            .int32(ProgressTags.STAGE, value.stage.wireCode, requiredForReader = true)
            .apply {
                value.current?.let { int64(ProgressTags.CURRENT, it) }
                value.total?.let { int64(ProgressTags.TOTAL, it) }
            }
            .encode()
    }

    fun decodeProgress(bytes: ByteArray): DexCompileProgress {
        val document = document(bytes, DexCompilerContract.SCHEMA_PROGRESS, ProgressTags.ALL)
        return DexCompileProgress(
            requestId = DexRequestId.fromBytes(document.requireBytes(ProgressTags.REQUEST_ID)),
            sequence = document.requireInt64(ProgressTags.SEQUENCE),
            stage = enumByCode<DexCompilerProgressStage>(
                document.requireInt32(ProgressTags.STAGE),
                "progress stage",
            ) { it.wireCode },
            current = document.optionalInt64(ProgressTags.CURRENT),
            total = document.optionalInt64(ProgressTags.TOTAL),
        ).also(DexCompilerValidation::validateProgress)
    }

    fun encodeResult(value: DexCompileResult): ByteArray {
        DexCompilerValidation.validateResult(value)
        return writer(DexCompilerContract.SCHEMA_RESULT)
            .bytes(ResultTags.REQUEST_ID, value.requestId.toByteArray(), requiredForReader = true)
            .int32(ResultTags.COMPILER_FAMILY, value.compilerFamily.wireCode, requiredForReader = true)
            .string(ResultTags.COMPILER_VERSION, value.compilerVersion, requiredForReader = true)
            .string(ResultTags.MODE, value.mode.wireName, requiredForReader = true)
            .int32(ResultTags.MIN_API, value.minApi, requiredForReader = true)
            .string(ResultTags.OUTPUT_FORMAT, value.outputFormat.wireName, requiredForReader = true)
            .bytes(
                ResultTags.RUNTIME_LIBRARY_FINGERPRINT,
                value.runtimeLibraryFingerprint.toByteArray(),
                requiredForReader = true,
            )
            .int64(ResultTags.OUTPUT_SIZE_BYTES, value.outputSizeBytes, requiredForReader = true)
            .bytes(ResultTags.OUTPUT_SHA256, value.outputSha256.toByteArray(), requiredForReader = true)
            .int32(ResultTags.DEX_ENTRY_COUNT, value.dexEntryCount, requiredForReader = true)
            .int64(ResultTags.ELAPSED_MILLIS, value.elapsedMillis, requiredForReader = true)
            .apply {
                value.diagnostics.forEach { diagnostic ->
                    document(ResultTags.DIAGNOSTICS, encodeDiagnostic(diagnostic))
                }
            }
            .encode()
    }

    fun decodeResult(bytes: ByteArray): DexCompileResult {
        val document = document(
            bytes,
            DexCompilerContract.SCHEMA_RESULT,
            ResultTags.ALL,
            ResultTags.REPEATED,
        )
        return DexCompileResult(
            requestId = DexRequestId.fromBytes(document.requireBytes(ResultTags.REQUEST_ID)),
            compilerFamily = enumByCode<DexCompilerFamily>(
                document.requireInt32(ResultTags.COMPILER_FAMILY),
                "compiler family",
            ) { it.wireCode },
            compilerVersion = document.requireString(ResultTags.COMPILER_VERSION),
            mode = enumByName<DexCompilerMode>(document.requireString(ResultTags.MODE), "compilation mode") {
                it.wireName
            },
            minApi = document.requireInt32(ResultTags.MIN_API),
            outputFormat = enumByName<DexCompilerOutputFormat>(
                document.requireString(ResultTags.OUTPUT_FORMAT),
                "output format",
            ) { it.wireName },
            runtimeLibraryFingerprint = DexSha256.fromBytes(
                document.requireBytes(ResultTags.RUNTIME_LIBRARY_FINGERPRINT)
            ),
            outputSizeBytes = document.requireInt64(ResultTags.OUTPUT_SIZE_BYTES),
            outputSha256 = DexSha256.fromBytes(document.requireBytes(ResultTags.OUTPUT_SHA256)),
            dexEntryCount = document.requireInt32(ResultTags.DEX_ENTRY_COUNT),
            elapsedMillis = document.requireInt64(ResultTags.ELAPSED_MILLIS),
            diagnostics = document.documents(ResultTags.DIAGNOSTICS).map(::decodeDiagnostic),
        ).also(DexCompilerValidation::validateResult)
    }

    fun encodeError(value: DexCompileError): ByteArray {
        DexCompilerValidation.validateError(value)
        return writer(DexCompilerContract.SCHEMA_ERROR)
            .bytes(ErrorTags.REQUEST_ID, value.requestId.toByteArray(), requiredForReader = true)
            .int32(ErrorTags.CODE, value.code.wireCode, requiredForReader = true)
            .int32(ErrorTags.PHASE, value.phase.wireCode, requiredForReader = true)
            .string(ErrorTags.MESSAGE, value.message, requiredForReader = true)
            .boolean(ErrorTags.RETRYABLE, value.retryable, requiredForReader = true)
            .apply {
                value.diagnostics.forEach { diagnostic ->
                    document(ErrorTags.DIAGNOSTICS, encodeDiagnostic(diagnostic))
                }
            }
            .encode()
    }

    fun decodeError(bytes: ByteArray): DexCompileError {
        val document = document(
            bytes,
            DexCompilerContract.SCHEMA_ERROR,
            ErrorTags.ALL,
            ErrorTags.REPEATED,
        )
        return DexCompileError(
            requestId = DexRequestId.fromBytes(document.requireBytes(ErrorTags.REQUEST_ID)),
            code = enumByCode<DexCompilerErrorCode>(document.requireInt32(ErrorTags.CODE), "error code") {
                it.wireCode
            },
            phase = enumByCode<DexCompilerFailurePhase>(document.requireInt32(ErrorTags.PHASE), "failure phase") {
                it.wireCode
            },
            message = document.requireString(ErrorTags.MESSAGE),
            retryable = document.requireBoolean(ErrorTags.RETRYABLE),
            diagnostics = document.documents(ErrorTags.DIAGNOSTICS).map(::decodeDiagnostic),
        ).also(DexCompilerValidation::validateError)
    }

    fun encodeCancellation(value: DexCompileCancellation): ByteArray {
        DexCompilerValidation.validateCancellation(value)
        return writer(DexCompilerContract.SCHEMA_CANCELLATION)
            .bytes(CancellationTags.REQUEST_ID, value.requestId.toByteArray(), requiredForReader = true)
            .int32(CancellationTags.REASON, value.reason.wireCode, requiredForReader = true)
            .int32(CancellationTags.PHASE, value.phase.wireCode, requiredForReader = true)
            .int64(CancellationTags.ELAPSED_MILLIS, value.elapsedMillis, requiredForReader = true)
            .encode()
    }

    fun decodeCancellation(bytes: ByteArray): DexCompileCancellation {
        val document = document(bytes, DexCompilerContract.SCHEMA_CANCELLATION, CancellationTags.ALL)
        return DexCompileCancellation(
            requestId = DexRequestId.fromBytes(document.requireBytes(CancellationTags.REQUEST_ID)),
            reason = enumByCode<DexCompilerCancellationReason>(
                document.requireInt32(CancellationTags.REASON),
                "cancellation reason",
            ) { it.wireCode },
            phase = enumByCode<DexCompilerFailurePhase>(
                document.requireInt32(CancellationTags.PHASE),
                "failure phase",
            ) { it.wireCode },
            elapsedMillis = document.requireInt64(CancellationTags.ELAPSED_MILLIS),
        ).also(DexCompilerValidation::validateCancellation)
    }

    private fun encodeRuntimeLibraryIdentity(value: DexRuntimeLibraryIdentity): ByteArray =
        writer(DexCompilerContract.SCHEMA_RUNTIME_LIBRARY_IDENTITY)
            .int64(RuntimeLibraryIdentityTags.SIZE_BYTES, value.sizeBytes, requiredForReader = true)
            .bytes(
                RuntimeLibraryIdentityTags.CONTENT_SHA256,
                value.contentSha256.toByteArray(),
                requiredForReader = true,
            )
            .encode()

    private fun decodeRuntimeLibraryIdentity(bytes: ByteArray): DexRuntimeLibraryIdentity {
        val document = document(
            bytes,
            DexCompilerContract.SCHEMA_RUNTIME_LIBRARY_IDENTITY,
            RuntimeLibraryIdentityTags.ALL,
        )
        return DexRuntimeLibraryIdentity(
            sizeBytes = document.requireInt64(RuntimeLibraryIdentityTags.SIZE_BYTES),
            contentSha256 = DexSha256.fromBytes(document.requireBytes(RuntimeLibraryIdentityTags.CONTENT_SHA256)),
        )
    }

    private fun encodeInputIdentity(value: DexCompilerInputIdentity): ByteArray =
        writer(
            DexCompilerContract.SCHEMA_INPUT_IDENTITY,
            DexCompilerContract.PROTOCOL_CLASSPATH_BUNDLE_MINOR,
        )
            .int32(InputIdentityTags.ROLE, value.role.wireCode, requiredForReader = true)
            .int32(InputIdentityTags.ORDINAL, value.ordinal, requiredForReader = true)
            .int64(InputIdentityTags.SIZE_BYTES, value.sizeBytes, requiredForReader = true)
            .bytes(
                InputIdentityTags.CONTENT_SHA256,
                value.contentSha256.toByteArray(),
                requiredForReader = true,
            )
            .encode()

    private fun decodeInputIdentity(bytes: ByteArray): DexCompilerInputIdentity {
        val document = document(
            bytes,
            DexCompilerContract.SCHEMA_INPUT_IDENTITY,
            InputIdentityTags.ALL,
        )
        return DexCompilerInputIdentity(
            role = enumByCode<DexCompilerInputRole>(
                document.requireInt32(InputIdentityTags.ROLE),
                "input role",
            ) { it.wireCode },
            ordinal = document.requireInt32(InputIdentityTags.ORDINAL),
            sizeBytes = document.requireInt64(InputIdentityTags.SIZE_BYTES),
            contentSha256 = DexSha256.fromBytes(document.requireBytes(InputIdentityTags.CONTENT_SHA256)),
        )
    }

    private fun decodeClasspathBundle(
        document: TaggedWireDocument,
    ): DexCompilerClasspathBundleCapability? {
        val present = CapabilityTags.CLASSPATH_EXTENSION.any { it in document.tags }
        if (!present) return null
        return DexCompilerClasspathBundleCapability(
            inputLayout = enumByCode<DexCompilerInputLayout>(
                document.requireInt32(CapabilityTags.CLASSPATH_INPUT_LAYOUT),
                "classpath input layout",
            ) { it.wireCode },
            limits = DexCompilerClasspathBundleLimits(
                maxClasspathJarCount = document.requireInt32(CapabilityTags.MAX_CLASSPATH_JAR_COUNT),
                maxCompressedClasspathJarBytes = document.requireInt64(
                    CapabilityTags.MAX_COMPRESSED_CLASSPATH_JAR_BYTES
                ),
                maxTotalCompressedClasspathBytes = document.requireInt64(
                    CapabilityTags.MAX_TOTAL_COMPRESSED_CLASSPATH_BYTES
                ),
                maxInputBundleBytes = document.requireInt64(CapabilityTags.MAX_INPUT_BUNDLE_BYTES),
                maxTotalInputArchiveEntries = document.requireInt32(
                    CapabilityTags.MAX_TOTAL_INPUT_ARCHIVE_ENTRIES
                ),
                maxTotalUncompressedInputBytes = document.requireInt64(
                    CapabilityTags.MAX_TOTAL_UNCOMPRESSED_INPUT_BYTES
                ),
                maxTotalInputClassBytes = document.requireInt64(
                    CapabilityTags.MAX_TOTAL_INPUT_CLASS_BYTES
                ),
            ),
        )
    }

    private fun encodeDiagnostic(value: DexCompilerDiagnostic): ByteArray =
        writer(DexCompilerContract.SCHEMA_DIAGNOSTIC)
            .int32(DiagnosticTags.SEVERITY, value.severity.wireCode, requiredForReader = true)
            .string(DiagnosticTags.CODE, value.code, requiredForReader = true)
            .string(DiagnosticTags.MESSAGE, value.message, requiredForReader = true)
            .apply {
                value.origin?.let { string(DiagnosticTags.ORIGIN, it) }
                value.entry?.let { string(DiagnosticTags.ENTRY, it) }
                value.position?.let { document(DiagnosticTags.POSITION, encodeDiagnosticPosition(it)) }
            }
            .encode()

    private fun decodeDiagnostic(bytes: ByteArray): DexCompilerDiagnostic {
        val document = document(bytes, DexCompilerContract.SCHEMA_DIAGNOSTIC, DiagnosticTags.ALL)
        return DexCompilerDiagnostic(
            severity = enumByCode<DexCompilerDiagnosticSeverity>(
                document.requireInt32(DiagnosticTags.SEVERITY),
                "diagnostic severity",
            ) { it.wireCode },
            code = document.requireString(DiagnosticTags.CODE),
            message = document.requireString(DiagnosticTags.MESSAGE),
            origin = document.optionalString(DiagnosticTags.ORIGIN),
            entry = document.optionalString(DiagnosticTags.ENTRY),
            position = document.optionalDocument(DiagnosticTags.POSITION)?.let(::decodeDiagnosticPosition),
        )
    }

    private fun encodeDiagnosticPosition(value: DexCompilerDiagnosticPosition): ByteArray =
        writer(DexCompilerContract.SCHEMA_DIAGNOSTIC_POSITION)
            .apply {
                value.line?.let { int32(DiagnosticPositionTags.LINE, it) }
                value.column?.let { int32(DiagnosticPositionTags.COLUMN, it) }
                value.offset?.let { int64(DiagnosticPositionTags.OFFSET, it) }
                value.endLine?.let { int32(DiagnosticPositionTags.END_LINE, it) }
                value.endColumn?.let { int32(DiagnosticPositionTags.END_COLUMN, it) }
                value.endOffset?.let { int64(DiagnosticPositionTags.END_OFFSET, it) }
            }
            .encode()

    private fun decodeDiagnosticPosition(bytes: ByteArray): DexCompilerDiagnosticPosition {
        val document = document(
            bytes,
            DexCompilerContract.SCHEMA_DIAGNOSTIC_POSITION,
            DiagnosticPositionTags.ALL,
        )
        return DexCompilerDiagnosticPosition(
            line = document.optionalInt32(DiagnosticPositionTags.LINE),
            column = document.optionalInt32(DiagnosticPositionTags.COLUMN),
            offset = document.optionalInt64(DiagnosticPositionTags.OFFSET),
            endLine = document.optionalInt32(DiagnosticPositionTags.END_LINE),
            endColumn = document.optionalInt32(DiagnosticPositionTags.END_COLUMN),
            endOffset = document.optionalInt64(DiagnosticPositionTags.END_OFFSET),
        )
    }

    private fun writer(
        schemaId: Int,
        schemaMinor: Int = DexCompilerContract.SCHEMA_MINOR,
    ): TaggedWireWriter = TaggedWireWriter(
        schemaId = schemaId,
        schemaMajor = DexCompilerContract.SCHEMA_MAJOR,
        schemaMinor = schemaMinor,
        limits = WIRE_LIMITS,
    )

    private fun document(
        bytes: ByteArray,
        schemaId: Int,
        knownTags: Set<Int>,
        repeatedTags: Set<Int> = emptySet(),
    ): TaggedWireDocument = TaggedWireDocument.decode(bytes, WIRE_LIMITS)
        .requireSchema(schemaId, DexCompilerContract.SCHEMA_MAJOR)
        .rejectUnknownRequiredFields(knownTags)
        .validateKnownCardinality(knownTags, repeatedTags)

    private inline fun <reified T : Enum<T>> enumByCode(
        value: Int,
        label: String,
        crossinline code: (T) -> Int,
    ): T = enumValues<T>().firstOrNull { code(it) == value }
        ?: throw DexCompilerContractException(
            DexCompilerContractViolation.UNKNOWN_ENUM,
            "Unknown $label value $value",
        )

    private inline fun <reified T : Enum<T>> enumByName(
        value: String,
        label: String,
        crossinline name: (T) -> String,
    ): T = enumValues<T>().firstOrNull { name(it) == value }
        ?: throw DexCompilerContractException(
            DexCompilerContractViolation.UNKNOWN_ENUM,
            "Unknown $label value $value",
        )

    private object InfoTags {
        const val PROTOCOL_MIN_MAJOR = 1
        const val PROTOCOL_MIN_MINOR = 2
        const val PROTOCOL_MAX_MAJOR = 3
        const val PROTOCOL_MAX_MINOR = 4
        const val PROVIDER_ID = 5
        const val PROVIDER_VERSION_NAME = 6
        const val PROVIDER_VERSION_CODE = 7
        const val MIN_HOST_VERSION_CODE = 8
        const val MAX_HOST_VERSION_CODE = 9
        const val ABI_MODE = 10
        val ALL = (1..10).toSet()
    }

    private object CapabilityTags {
        const val COMPILER_FAMILY = 1
        const val COMPILER_VERSION = 2
        const val INPUT_FORMATS = 3
        const val OUTPUT_FORMATS = 4
        const val MODES = 5
        const val MIN_API = 6
        const val MAX_API = 7
        const val SUPPORTS_MULTIDEX = 8
        const val DETERMINISM_CLAIM = 9
        const val RUNTIME_LIBRARY_MODEL = 10
        const val ACCEPTS_EXTERNAL_CLASSPATH = 11
        const val SUPPORTS_DESUGARED_LIBRARY_CONFIGURATION = 12
        const val MAX_COMPRESSED_PROGRAM_BYTES = 13
        const val MAX_ARCHIVE_ENTRIES = 14
        const val MAX_UNCOMPRESSED_PROGRAM_BYTES = 15
        const val MAX_TOTAL_CLASS_BYTES = 16
        const val MAX_SINGLE_CLASS_BYTES = 17
        const val MAX_OUTPUT_BYTES = 18
        const val MAX_DEX_ENTRIES = 19
        const val MAX_DIAGNOSTIC_BYTES = 20
        const val MAX_CONCURRENT_SESSIONS = 21
        const val RUNTIME_LIBRARY_IDENTITIES = 22
        const val RUNTIME_LIBRARY_FINGERPRINT = 23
        const val CLASSPATH_INPUT_LAYOUT = 24
        const val MAX_CLASSPATH_JAR_COUNT = 25
        const val MAX_COMPRESSED_CLASSPATH_JAR_BYTES = 26
        const val MAX_TOTAL_COMPRESSED_CLASSPATH_BYTES = 27
        const val MAX_INPUT_BUNDLE_BYTES = 28
        const val MAX_TOTAL_INPUT_ARCHIVE_ENTRIES = 29
        const val MAX_TOTAL_UNCOMPRESSED_INPUT_BYTES = 30
        const val MAX_TOTAL_INPUT_CLASS_BYTES = 31
        val ALL = (1..31).toSet()
        val REPEATED = setOf(INPUT_FORMATS, OUTPUT_FORMATS, MODES, RUNTIME_LIBRARY_IDENTITIES)
        val CLASSPATH_EXTENSION = (24..31).toSet()
    }

    private object RuntimeLibraryIdentityTags {
        const val SIZE_BYTES = 1
        const val CONTENT_SHA256 = 2
        val ALL = setOf(SIZE_BYTES, CONTENT_SHA256)
    }

    private object InputIdentityTags {
        const val ROLE = 1
        const val ORDINAL = 2
        const val SIZE_BYTES = 3
        const val CONTENT_SHA256 = 4
        val ALL = (1..4).toSet()
    }

    private object RequestTags {
        const val REQUEST_ID = 1
        const val PROTOCOL_MAJOR = 2
        const val PROTOCOL_MINOR = 3
        const val INPUT_FORMAT = 4
        const val OUTPUT_FORMAT = 5
        const val MODE = 6
        const val MIN_API = 7
        const val PROGRAM_SIZE_BYTES = 8
        const val PROGRAM_SHA256 = 9
        const val MAX_OUTPUT_BYTES = 10
        const val RUNTIME_LIBRARY_MODEL = 11
        const val EXPECTED_RUNTIME_LIBRARY_FINGERPRINT = 12
        const val CANONICALIZATION_POLICY_VERSION = 13
        const val DIAGNOSTIC_BYTE_LIMIT = 14
        const val TIMEOUT_MILLIS = 15
        const val INPUT_LAYOUT = 16
        const val INPUT_IDENTITIES = 17
        const val INPUT_SET_FINGERPRINT = 18
        const val INPUT_BUNDLE_SIZE_BYTES = 19
        const val INPUT_BUNDLE_SHA256 = 20
        val ALL = (1..20).toSet()
        val REPEATED = setOf(INPUT_IDENTITIES)
    }

    private object StartedTags {
        const val REQUEST_ID = 1
        const val SEQUENCE = 2
        const val PROTOCOL_MAJOR = 3
        const val PROTOCOL_MINOR = 4
        const val COMPILER_FAMILY = 5
        const val COMPILER_VERSION = 6
        const val RUNTIME_LIBRARY_MODEL = 7
        const val RUNTIME_LIBRARY_FINGERPRINT = 8
        const val QUEUE_ELAPSED_MILLIS = 9
        val ALL = (1..9).toSet()
    }

    private object ProgressTags {
        const val REQUEST_ID = 1
        const val SEQUENCE = 2
        const val STAGE = 3
        const val CURRENT = 4
        const val TOTAL = 5
        val ALL = (1..5).toSet()
    }

    private object ResultTags {
        const val REQUEST_ID = 1
        const val COMPILER_FAMILY = 2
        const val COMPILER_VERSION = 3
        const val MODE = 4
        const val MIN_API = 5
        const val OUTPUT_FORMAT = 6
        const val RUNTIME_LIBRARY_FINGERPRINT = 7
        const val OUTPUT_SIZE_BYTES = 8
        const val OUTPUT_SHA256 = 9
        const val DEX_ENTRY_COUNT = 10
        const val ELAPSED_MILLIS = 11
        const val DIAGNOSTICS = 12
        val ALL = (1..12).toSet()
        val REPEATED = setOf(DIAGNOSTICS)
    }

    private object ErrorTags {
        const val REQUEST_ID = 1
        const val CODE = 2
        const val PHASE = 3
        const val MESSAGE = 4
        const val RETRYABLE = 5
        const val DIAGNOSTICS = 6
        val ALL = (1..6).toSet()
        val REPEATED = setOf(DIAGNOSTICS)
    }

    private object CancellationTags {
        const val REQUEST_ID = 1
        const val REASON = 2
        const val PHASE = 3
        const val ELAPSED_MILLIS = 4
        val ALL = (1..4).toSet()
    }

    private object DiagnosticTags {
        const val SEVERITY = 1
        const val CODE = 2
        const val MESSAGE = 3
        const val ORIGIN = 4
        const val ENTRY = 5
        const val POSITION = 6
        val ALL = (1..6).toSet()
    }

    private object DiagnosticPositionTags {
        const val LINE = 1
        const val COLUMN = 2
        const val OFFSET = 3
        const val END_LINE = 4
        const val END_COLUMN = 5
        const val END_OFFSET = 6
        val ALL = (1..6).toSet()
    }
}
