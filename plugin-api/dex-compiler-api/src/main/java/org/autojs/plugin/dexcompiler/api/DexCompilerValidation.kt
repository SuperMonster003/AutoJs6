package org.autojs.plugin.dexcompiler.api

import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

object DexCompilerNegotiation {
    val HOST_MIN = DexProtocolVersion(
        DexCompilerContract.PROTOCOL_MAJOR,
        0,
    )
    val HOST_MAX = DexProtocolVersion(
        DexCompilerContract.PROTOCOL_MAJOR,
        DexCompilerContract.PROTOCOL_MINOR,
    )

    fun negotiate(
        providerInfo: DexCompilerInfo,
        hostMin: DexProtocolVersion = HOST_MIN,
        hostMax: DexProtocolVersion = HOST_MAX,
    ): DexProtocolVersion? {
        DexCompilerValidation.validateProtocolRange(hostMin, hostMax, "host")
        DexCompilerValidation.validateInfo(providerInfo)
        val lower = maxOf(hostMin, providerInfo.protocolMin)
        val upper = minOf(hostMax, providerInfo.protocolMax)
        return upper.takeIf { lower <= upper }
    }

    fun requireNegotiated(
        providerInfo: DexCompilerInfo,
        hostMin: DexProtocolVersion = HOST_MIN,
        hostMax: DexProtocolVersion = HOST_MAX,
    ): DexProtocolVersion = negotiate(providerInfo, hostMin, hostMax)
        ?: throw DexCompilerContractException(
            DexCompilerContractViolation.PROTOCOL_INCOMPATIBLE,
            "Host and provider protocol ranges do not overlap",
        )
}

object DexCompilerValidation {
    private val PROVIDER_ID = Regex("[a-z0-9][a-z0-9._-]{0,127}")
    private val DIAGNOSTIC_CODE = Regex("[A-Z0-9][A-Z0-9._-]{0,127}")

    fun validateInfo(info: DexCompilerInfo) {
        validateProtocolRange(info.protocolMin, info.protocolMax, "provider")
        requireValue(PROVIDER_ID.matches(info.providerId)) { "Provider ID is invalid" }
        requireUtf8Text(info.providerVersionName, "Provider version name", DexCompilerContract.MAX_VERSION_TEXT_BYTES)
        requireValue(info.providerVersionCode > 0L) { "Provider version code must be positive" }
        info.minHostVersionCode?.let { requireValue(it > 0L) { "Minimum host version code must be positive" } }
        info.maxHostVersionCode?.let { requireValue(it > 0L) { "Maximum host version code must be positive" } }
        if (info.minHostVersionCode != null && info.maxHostVersionCode != null) {
            requireValue(info.minHostVersionCode <= info.maxHostVersionCode) {
                "Minimum host version code exceeds maximum host version code"
            }
        }
        requireValue(info.abiMode == DexCompilerAbiMode.ANY) { "DEX Compiler protocol v1 is ABI independent" }
    }

    fun validateCapabilities(capabilities: DexCompilerCapabilities) {
        requireValue(capabilities.compilerFamily == DexCompilerFamily.D8) {
            "DEX Compiler protocol v1 supports D8 only"
        }
        requireUtf8Text(
            capabilities.compilerVersion,
            "Compiler version",
            DexCompilerContract.MAX_VERSION_TEXT_BYTES,
        )
        requireDistinctNonEmpty(capabilities.inputFormats, "input formats")
        requireDistinctNonEmpty(capabilities.outputFormats, "output formats")
        requireDistinctNonEmpty(capabilities.modes, "compilation modes")
        requireValue(capabilities.inputFormats.all { it == DexCompilerInputFormat.JAR }) {
            "DEX Compiler protocol v1 accepts JAR input only"
        }
        requireValue(capabilities.outputFormats.all { it == DexCompilerOutputFormat.DEX_ZIP }) {
            "DEX Compiler protocol v1 produces DEX ZIP output only"
        }
        requireValue(capabilities.minApi > 0 && capabilities.minApi <= capabilities.maxApi) {
            "Supported minimum API range is invalid"
        }
        requireValue(capabilities.maxApi <= 10_000) { "Maximum API is unreasonable" }
        requireValue(capabilities.supportsMultiDex) { "DEX Compiler protocol v1 requires multi-DEX output" }
        requireValue(capabilities.determinismClaim == DexCompilerDeterminismClaim.NOT_CLAIMED) {
            "DEX Compiler protocol v1 does not negotiate deterministic output"
        }
        requireValue(capabilities.runtimeLibraryModel == DexRuntimeLibraryModel.DEVICE_RUNTIME_BOOTCLASSPATH_V1) {
            "Runtime-library model is unsupported"
        }
        requireValue(!capabilities.acceptsExternalClasspath) {
            "DEX Compiler protocol v1 forbids an external classpath"
        }
        requireValue(!capabilities.supportsDesugaredLibraryConfiguration) {
            "DEX Compiler protocol v1 forbids caller-supplied desugared-library configuration"
        }
        validateLimits(capabilities.limits)
        requireValue(capabilities.runtimeLibraryIdentities.isNotEmpty()) {
            "Runtime-library identity list must not be empty"
        }
        requireValue(capabilities.runtimeLibraryIdentities.size <= DexCompilerContract.MAX_RUNTIME_LIBRARY_IDENTITIES) {
            "Runtime-library identity count exceeds the protocol limit"
        }
        capabilities.runtimeLibraryIdentities.forEachIndexed { index, identity ->
            requireValue(identity.sizeBytes >= 0L) { "Runtime library $index has a negative size" }
        }
        requireValue(
            capabilities.runtimeLibraryFingerprint ==
                DexRuntimeLibraryFingerprint.compute(capabilities.runtimeLibraryIdentities)
        ) { "Runtime-library fingerprint does not match the ordered identities" }
        capabilities.classpathBundle?.let(::validateClasspathBundleCapability)
    }

    fun validateRequest(request: DexCompileRequest) {
        validateProtocolVersion(request.protocolVersion, "request")
        requireValue(request.programSizeBytes > 0L) { "Program archive must not be empty" }
        requireValue(request.programSizeBytes <= DexCompilerContract.MAX_COMPRESSED_PROGRAM_BYTES) {
            "Program archive exceeds the protocol limit"
        }
        requireValue(request.minApi > 0 && request.minApi <= 10_000) { "Requested minimum API is invalid" }
        requireValue(request.maxOutputBytes > 0L && request.maxOutputBytes <= DexCompilerContract.MAX_OUTPUT_BYTES) {
            "Requested output limit is invalid"
        }
        requireValue(request.runtimeLibraryModel == DexRuntimeLibraryModel.DEVICE_RUNTIME_BOOTCLASSPATH_V1) {
            "Requested runtime-library model is unsupported"
        }
        requireValue(request.canonicalizationPolicyVersion == DexCompilerContract.CANONICALIZATION_POLICY_VERSION) {
            "Canonicalization policy version is unsupported"
        }
        requireValue(request.diagnosticByteLimit in 0..DexCompilerContract.MAX_DIAGNOSTIC_BYTES) {
            "Diagnostic byte limit is invalid"
        }
        requireValue(request.timeoutMillis in 1L..DexCompilerContract.MAX_TIMEOUT_MILLIS) {
            "Compile timeout is invalid"
        }
        when (request.protocolVersion) {
            DexCompilerContract.PROTOCOL_V1_0 -> validateV1RequestExtensions(request)
            DexCompilerContract.PROTOCOL_V1_1 -> validateV11RequestExtensions(request)
            else -> throw DexCompilerContractException(
                DexCompilerContractViolation.PROTOCOL_INCOMPATIBLE,
                "Compile request protocol version is unsupported",
            )
        }
    }

    fun validateRequestAgainst(
        request: DexCompileRequest,
        capabilities: DexCompilerCapabilities,
        negotiatedVersion: DexProtocolVersion,
    ) {
        validateRequest(request)
        validateCapabilities(capabilities)
        validateProtocolVersion(negotiatedVersion, "negotiated")
        requireCapability(request.protocolVersion == negotiatedVersion) { "Request protocol version was not negotiated" }
        requireCapability(request.inputFormat in capabilities.inputFormats) { "Input format is unsupported" }
        requireCapability(request.outputFormat in capabilities.outputFormats) { "Output format is unsupported" }
        requireCapability(request.mode in capabilities.modes) { "Compilation mode is unsupported" }
        requireCapability(request.minApi in capabilities.minApi..capabilities.maxApi) {
            "Requested minimum API is unsupported"
        }
        requireCapability(request.programSizeBytes <= capabilities.limits.maxCompressedProgramBytes) {
            "Program archive exceeds the provider limit"
        }
        requireCapability(request.maxOutputBytes <= capabilities.limits.maxOutputBytes) {
            "Requested output limit exceeds the provider limit"
        }
        requireCapability(request.diagnosticByteLimit <= capabilities.limits.maxDiagnosticBytes) {
            "Requested diagnostic limit exceeds the provider limit"
        }
        requireCapability(request.runtimeLibraryModel == capabilities.runtimeLibraryModel) {
            "Runtime-library model is incompatible"
        }
        requireCapability(request.expectedRuntimeLibraryFingerprint == capabilities.runtimeLibraryFingerprint) {
            "Runtime-library fingerprint is incompatible"
        }
        if (request.protocolVersion == DexCompilerContract.PROTOCOL_V1_1) {
            val classpathBundle = capabilities.classpathBundle
                ?: throw DexCompilerContractException(
                    DexCompilerContractViolation.CAPABILITY_INCOMPATIBLE,
                    "Provider does not advertise ordered classpath bundles",
                )
            requireCapability(request.inputLayout == classpathBundle.inputLayout) {
                "Input bundle layout is unsupported"
            }
            val classpath = request.inputIdentities.drop(1)
            val limits = classpathBundle.limits
            requireCapability(classpath.size <= limits.maxClasspathJarCount) {
                "Classpath JAR count exceeds the provider limit"
            }
            requireCapability(classpath.all { it.sizeBytes <= limits.maxCompressedClasspathJarBytes }) {
                "A classpath JAR exceeds the provider limit"
            }
            requireCapability(classpath.sumSizes() <= limits.maxTotalCompressedClasspathBytes) {
                "Classpath JARs exceed the provider aggregate limit"
            }
            requireCapability(request.inputBundleSizeBytes!! <= limits.maxInputBundleBytes) {
                "Input bundle exceeds the provider limit"
            }
        }
    }

    fun validateStarted(started: DexCompileStarted) {
        requireValue(started.sequence >= 0L) { "Started sequence must not be negative" }
        validateProtocolVersion(started.protocolVersion, "started")
        requireValue(started.compilerFamily == DexCompilerFamily.D8) {
            "DEX Compiler protocol v1 supports D8 only"
        }
        requireUtf8Text(started.compilerVersion, "Compiler version", DexCompilerContract.MAX_VERSION_TEXT_BYTES)
        requireValue(started.runtimeLibraryModel == DexRuntimeLibraryModel.DEVICE_RUNTIME_BOOTCLASSPATH_V1) {
            "Started runtime-library model is unsupported"
        }
        requireValue(started.queueElapsedMillis >= 0L) { "Queue elapsed time must not be negative" }
    }

    fun validateProgress(progress: DexCompileProgress) {
        requireValue(progress.sequence >= 0L) { "Progress sequence must not be negative" }
        val hasCurrent = progress.current != null
        val hasTotal = progress.total != null
        requireValue(hasCurrent == hasTotal) { "Progress current and total must be present together" }
        if (hasCurrent) {
            val current = progress.current!!
            val total = progress.total!!
            requireValue(total >= 0L && current in 0L..total) { "Progress range is invalid" }
        }
    }

    fun validateResult(result: DexCompileResult) {
        requireValue(result.compilerFamily == DexCompilerFamily.D8) {
            "DEX Compiler protocol v1 supports D8 only"
        }
        requireUtf8Text(result.compilerVersion, "Compiler version", DexCompilerContract.MAX_VERSION_TEXT_BYTES)
        requireValue(result.minApi > 0 && result.minApi <= 10_000) { "Result minimum API is invalid" }
        requireValue(result.outputFormat == DexCompilerOutputFormat.DEX_ZIP) {
            "DEX Compiler protocol v1 produces DEX ZIP output only"
        }
        requireValue(result.outputSizeBytes in 1L..DexCompilerContract.MAX_OUTPUT_BYTES) {
            "Result output size is invalid"
        }
        requireValue(result.dexEntryCount in 1..DexCompilerContract.MAX_DEX_ENTRIES) {
            "Result DEX entry count is invalid"
        }
        requireValue(result.elapsedMillis >= 0L) { "Result elapsed time must not be negative" }
        validateDiagnostics(result.diagnostics)
    }

    fun validateError(error: DexCompileError) {
        requireCodePointLimit(
            error.message,
            "Error message",
            DexCompilerContract.MAX_ERROR_MESSAGE_CODE_POINTS,
        )
        requireUtf8Text(error.message, "Error message", DexCompilerContract.MAX_DIAGNOSTIC_BYTES)
        validateDiagnostics(error.diagnostics)
    }

    fun validateCancellation(cancellation: DexCompileCancellation) {
        requireValue(cancellation.elapsedMillis >= 0L) { "Cancellation elapsed time must not be negative" }
    }

    fun validateProtocolRange(min: DexProtocolVersion, max: DexProtocolVersion, label: String) {
        validateProtocolVersion(min, "$label minimum")
        validateProtocolVersion(max, "$label maximum")
        requireValue(min <= max) { "$label protocol range is reversed" }
    }

    fun validateProtocolVersion(version: DexProtocolVersion, label: String) {
        requireValue(version.major > 0 && version.minor >= 0) { "$label protocol version is invalid" }
    }

    fun validateDiagnostics(diagnostics: Collection<DexCompilerDiagnostic>) {
        requireValue(diagnostics.size <= 512) { "Diagnostic count exceeds the protocol limit" }
        var retainedBytes = 0L
        diagnostics.forEachIndexed { index, diagnostic ->
            requireValue(DIAGNOSTIC_CODE.matches(diagnostic.code)) { "Diagnostic $index code is invalid" }
            requireValue(diagnostic.message.isNotBlank()) { "Diagnostic $index message must not be blank" }
            requireCodePointLimit(
                diagnostic.message,
                "Diagnostic $index message",
                DexCompilerContract.MAX_DIAGNOSTIC_MESSAGE_CODE_POINTS,
            )
            val codeBytes = utf8Size(diagnostic.code, "Diagnostic $index code")
            val messageBytes = utf8Size(diagnostic.message, "Diagnostic $index message")
            retainedBytes = Math.addExact(retainedBytes, codeBytes.toLong())
            retainedBytes = Math.addExact(retainedBytes, messageBytes.toLong())
            requireValue(retainedBytes <= DexCompilerContract.MAX_DIAGNOSTIC_BYTES) {
                "Diagnostics exceed the retained byte limit"
            }
        }
    }

    private fun validateLimits(limits: DexCompilerResourceLimits) {
        requireValue(limits.maxCompressedProgramBytes in 1L..DexCompilerContract.MAX_COMPRESSED_PROGRAM_BYTES) {
            "Compressed program limit is invalid"
        }
        requireValue(limits.maxArchiveEntries in 1..DexCompilerContract.MAX_ARCHIVE_ENTRIES) {
            "Archive entry limit is invalid"
        }
        requireValue(limits.maxUncompressedProgramBytes in 1L..DexCompilerContract.MAX_UNCOMPRESSED_PROGRAM_BYTES) {
            "Uncompressed program limit is invalid"
        }
        requireValue(limits.maxTotalClassBytes in 1L..DexCompilerContract.MAX_TOTAL_CLASS_BYTES) {
            "Total class byte limit is invalid"
        }
        requireValue(limits.maxSingleClassBytes in 1L..DexCompilerContract.MAX_SINGLE_CLASS_BYTES) {
            "Single class byte limit is invalid"
        }
        requireValue(limits.maxSingleClassBytes <= limits.maxTotalClassBytes) {
            "Single class byte limit exceeds the total class limit"
        }
        requireValue(limits.maxTotalClassBytes <= limits.maxUncompressedProgramBytes) {
            "Total class byte limit exceeds the uncompressed program limit"
        }
        requireValue(limits.maxOutputBytes in 1L..DexCompilerContract.MAX_OUTPUT_BYTES) {
            "Output byte limit is invalid"
        }
        requireValue(limits.maxDexEntries in 1..DexCompilerContract.MAX_DEX_ENTRIES) {
            "DEX entry limit is invalid"
        }
        requireValue(limits.maxDiagnosticBytes in 1..DexCompilerContract.MAX_DIAGNOSTIC_BYTES) {
            "Diagnostic byte limit is invalid"
        }
        requireValue(limits.maxConcurrentSessions == DexCompilerContract.MAX_CONCURRENT_SESSIONS) {
            "DEX Compiler protocol v1 permits exactly one concurrent session"
        }
    }

    private fun validateClasspathBundleCapability(capability: DexCompilerClasspathBundleCapability) {
        requireValue(
            capability.inputLayout == DexCompilerInputLayout.PROGRAM_AND_ORDERED_CLASSPATH_BUNDLE_V1
        ) { "Classpath input layout is unsupported" }
        val limits = capability.limits
        requireValue(limits.maxClasspathJarCount in 1..DexCompilerContract.MAX_CLASSPATH_JARS) {
            "Classpath JAR count limit is invalid"
        }
        requireValue(
            limits.maxCompressedClasspathJarBytes in
                1L..DexCompilerContract.MAX_COMPRESSED_CLASSPATH_JAR_BYTES
        ) { "Classpath JAR byte limit is invalid" }
        requireValue(
            limits.maxTotalCompressedClasspathBytes in
                limits.maxCompressedClasspathJarBytes..DexCompilerContract.MAX_TOTAL_COMPRESSED_CLASSPATH_BYTES
        ) { "Classpath aggregate byte limit is invalid" }
        requireValue(
            limits.maxInputBundleBytes in 1L..DexCompilerContract.MAX_COMPRESSED_INPUT_BUNDLE_BYTES
        ) { "Input bundle byte limit is invalid" }
        requireValue(
            limits.maxTotalInputArchiveEntries in 1..DexCompilerContract.MAX_TOTAL_INPUT_ARCHIVE_ENTRIES
        ) { "Aggregate archive-entry limit is invalid" }
        requireValue(
            limits.maxTotalUncompressedInputBytes in 1L..DexCompilerContract.MAX_TOTAL_UNCOMPRESSED_INPUT_BYTES
        ) { "Aggregate uncompressed-input limit is invalid" }
        requireValue(
            limits.maxTotalInputClassBytes in 1L..DexCompilerContract.MAX_TOTAL_INPUT_CLASS_BYTES
        ) { "Aggregate class-byte limit is invalid" }
        requireValue(limits.maxTotalInputClassBytes <= limits.maxTotalUncompressedInputBytes) {
            "Aggregate class-byte limit exceeds the uncompressed-input limit"
        }
    }

    private fun validateV1RequestExtensions(request: DexCompileRequest) {
        requireValue(request.inputLayout == null) { "Protocol 1.0 request must not declare an input layout" }
        requireValue(request.inputIdentities.isEmpty()) {
            "Protocol 1.0 request must not declare bundled input identities"
        }
        requireValue(request.inputSetFingerprint == null) {
            "Protocol 1.0 request must not declare an input-set fingerprint"
        }
        requireValue(request.inputBundleSizeBytes == null) {
            "Protocol 1.0 request must not declare an input-bundle size"
        }
        requireValue(request.inputBundleSha256 == null) {
            "Protocol 1.0 request must not declare an input-bundle SHA-256"
        }
    }

    private fun validateV11RequestExtensions(request: DexCompileRequest) {
        requireValue(
            request.inputLayout == DexCompilerInputLayout.PROGRAM_AND_ORDERED_CLASSPATH_BUNDLE_V1
        ) { "Protocol 1.1 requires the canonical ordered-classpath bundle layout" }
        requireValue(request.inputIdentities.size in 2..DexCompilerContract.MAX_CLASSPATH_JARS + 1) {
            "Protocol 1.1 requires one program and at least one classpath JAR"
        }
        DexCompilerInputIdentityValidation.requireCanonical(request.inputIdentities)
        val program = request.inputIdentities.first()
        requireValue(program.sizeBytes == request.programSizeBytes) {
            "Bundled program size differs from the request"
        }
        requireValue(program.contentSha256 == request.programSha256) {
            "Bundled program SHA-256 differs from the request"
        }
        requireValue(
            request.inputSetFingerprint == DexCompilerInputSetFingerprint.compute(request.inputIdentities)
        ) { "Input-set fingerprint does not match the ordered identities" }
        val bundleSize = request.inputBundleSizeBytes
            ?: throw DexCompilerContractException(
                DexCompilerContractViolation.INVALID_VALUE,
                "Input bundle size is missing",
            )
        requireValue(bundleSize > 0L) { "Input bundle size is invalid" }
        requireValue(bundleSize <= DexCompilerContract.MAX_COMPRESSED_INPUT_BUNDLE_BYTES) {
            "Input bundle exceeds the protocol limit"
        }
        requireValue(bundleSize == DexCompilerInputBundleCodec.encodedSize(request.inputIdentities)) {
            "Input bundle size does not match the canonical framing"
        }
        requireValue(request.inputBundleSha256 != null) { "Input bundle SHA-256 is missing" }
    }

    private fun Collection<DexCompilerInputIdentity>.sumSizes(): Long = try {
        fold(0L) { total, identity -> Math.addExact(total, identity.sizeBytes) }
    } catch (error: ArithmeticException) {
        throw DexCompilerContractException(
            DexCompilerContractViolation.INVALID_VALUE,
            "Input sizes overflow",
            error,
        )
    }

    private fun <T> requireDistinctNonEmpty(values: Collection<T>, label: String) {
        requireValue(values.isNotEmpty()) { "Supported $label must not be empty" }
        requireValue(values.size == values.toSet().size) { "Supported $label contain duplicates" }
    }

    private fun requireUtf8Text(value: String, label: String, maxBytes: Int) {
        requireValue(value.isNotBlank()) { "$label must not be blank" }
        requireValue(utf8Size(value, label) <= maxBytes) { "$label exceeds $maxBytes UTF-8 bytes" }
    }

    private fun requireCodePointLimit(value: String, label: String, maxCodePoints: Int) {
        val count = value.codePointCount(0, value.length)
        requireValue(count <= maxCodePoints) { "$label exceeds $maxCodePoints code points" }
    }

    private fun utf8Size(value: String, label: String): Int = try {
        StandardCharsets.UTF_8.newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .encode(java.nio.CharBuffer.wrap(value))
            .remaining()
    } catch (error: Exception) {
        throw DexCompilerContractException(
            DexCompilerContractViolation.INVALID_VALUE,
            "$label is not valid Unicode text",
            error,
        )
    }

    private inline fun requireValue(condition: Boolean, lazyMessage: () -> String) {
        if (!condition) {
            throw DexCompilerContractException(
                DexCompilerContractViolation.INVALID_VALUE,
                lazyMessage(),
            )
        }
    }

    private inline fun requireCapability(condition: Boolean, lazyMessage: () -> String) {
        if (!condition) {
            throw DexCompilerContractException(
                DexCompilerContractViolation.CAPABILITY_INCOMPATIBLE,
                lazyMessage(),
            )
        }
    }
}
