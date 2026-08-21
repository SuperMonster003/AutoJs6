package org.autojs.plugin.yolo.api

import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets

object YoloNegotiation {
    val HOST_MIN = YoloProtocolVersion(YoloContract.PROTOCOL_MAJOR, 0)
    val HOST_MAX = YoloProtocolVersion(YoloContract.PROTOCOL_MAJOR, YoloContract.PROTOCOL_MINOR)

    fun negotiate(
        capabilities: YoloCapabilities,
        hostMin: YoloProtocolVersion = HOST_MIN,
        hostMax: YoloProtocolVersion = HOST_MAX,
    ): YoloProtocolVersion? {
        YoloValidation.validateProtocolRange(hostMin, hostMax, "host")
        YoloValidation.validateCapabilities(capabilities)
        val lower = maxOf(hostMin, capabilities.protocolMin)
        val upper = minOf(hostMax, capabilities.protocolMax)
        return upper.takeIf { lower <= upper }
    }

    fun requireNegotiated(
        capabilities: YoloCapabilities,
        hostMin: YoloProtocolVersion = HOST_MIN,
        hostMax: YoloProtocolVersion = HOST_MAX,
    ): YoloProtocolVersion = negotiate(capabilities, hostMin, hostMax)
        ?: throw YoloContractException(
            YoloContractViolation.PROTOCOL_INCOMPATIBLE,
            "Host and YOLO provider protocol ranges do not overlap",
        )
}

object YoloValidation {
    private val SYMBOLIC_ID = Regex("[a-z0-9][a-z0-9._-]{0,127}")
    private val ABI_NAME = Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,63}")

    fun validateProviderInfo(info: YoloProviderInfo) {
        validateSymbolicId(info.providerId, "Provider ID", YoloContract.MAX_PROVIDER_ID_BYTES)
        requireUtf8Text(info.displayName, "Provider display name", YoloContract.MAX_DISPLAY_NAME_BYTES)
        requireUtf8Text(info.author, "Provider author", YoloContract.MAX_AUTHOR_BYTES)
        requireUtf8Text(info.versionName, "Provider version name", YoloContract.MAX_VERSION_TEXT_BYTES)
        requireValue(info.versionCode > 0L) { "Provider version code must be positive" }
        requireDistinctNonEmpty(info.supportedAbis, "ABIs")
        requireValue(info.supportedAbis.size <= YoloContract.MAX_SUPPORTED_ABIS) {
            "Supported ABI count exceeds the protocol limit"
        }
        info.supportedAbis.forEachIndexed { index, abi ->
            requireValue(utf8Size(abi, "ABI $index") <= YoloContract.MAX_ABI_NAME_BYTES) {
                "ABI $index exceeds the UTF-8 limit"
            }
            requireValue(ABI_NAME.matches(abi)) { "ABI $index is invalid" }
        }
        info.minHostVersionCode?.let { minimum ->
            requireValue(minimum > 0L) { "Minimum host version code must be positive" }
        }
        info.maxHostVersionCode?.let { maximum ->
            requireValue(maximum > 0L) { "Maximum host version code must be positive" }
        }
        if (info.minHostVersionCode != null && info.maxHostVersionCode != null) {
            requireValue(info.minHostVersionCode <= info.maxHostVersionCode) {
                "Minimum host version code exceeds maximum host version code"
            }
        }
    }

    fun validateCapabilitiesAgainstInfo(capabilities: YoloCapabilities, info: YoloProviderInfo) {
        validateProviderInfo(info)
        validateCapabilities(capabilities)
        requireCapability(capabilities.backend == info.backend) {
            "Capabilities backend does not match provider identity"
        }
    }

    fun validateCapabilities(capabilities: YoloCapabilities) {
        validateProtocolRange(capabilities.protocolMin, capabilities.protocolMax, "provider")
        requireUtf8Text(
            capabilities.backendVersion,
            "Backend version",
            YoloContract.MAX_VERSION_TEXT_BYTES,
        )
        requireDistinctNonEmpty(capabilities.tasks, "tasks")
        requireDistinctNonEmpty(capabilities.modelFormats, "model formats")
        requireDistinctNonEmpty(capabilities.devices, "devices")
        requireDistinctNonEmpty(capabilities.pixelFormats, "pixel formats")
        requireDistinctNonEmpty(capabilities.decoderIds, "decoder IDs")
        requireDistinctNonEmpty(capabilities.artifactRoles, "artifact roles")
        requireValue(capabilities.tasks.all { it == YoloTask.DETECT }) {
            "YOLO protocol v1 is detect-only"
        }
        requireValue(capabilities.pixelFormats.all { it == YoloPixelFormat.RGBA_8888 }) {
            "YOLO protocol v1 accepts RGBA_8888 only"
        }
        capabilities.decoderIds.forEachIndexed { index, value ->
            validateSymbolicId(value, "Decoder ID $index", YoloContract.MAX_DECODER_ID_BYTES)
        }
        capabilities.artifactRoles.forEachIndexed { index, value ->
            validateSymbolicId(value, "Artifact role $index", YoloContract.MAX_ROLE_BYTES)
        }
        requireValue(YoloContract.REQUIRED_MANIFEST_ROLE in capabilities.artifactRoles) {
            "Provider must accept a model manifest artifact"
        }
        validateLimits(capabilities.limits)
    }

    fun validateOpenSessionRequest(request: YoloOpenSessionRequest, descriptorCount: Int) {
        validateProtocolVersion(request.protocolVersion, "request")
        requireValue(request.task == YoloTask.DETECT) { "YOLO protocol v1 is detect-only" }
        requireValue(request.cpuThreads in 1..YoloContract.MAX_CPU_THREADS) {
            "CPU thread count is invalid"
        }
        validateSymbolicId(request.decoderId, "Decoder ID", YoloContract.MAX_DECODER_ID_BYTES)
        requireValue(request.timeoutMillis in 1L..YoloContract.MAX_TIMEOUT_MILLIS) {
            "Session-open timeout is invalid"
        }
        requireValue(request.artifacts.isNotEmpty()) { "At least one model artifact is required" }
        requireValue(request.artifacts.size <= YoloContract.MAX_MODEL_DESCRIPTORS) {
            "Model artifact count exceeds the protocol limit"
        }
        requireDescriptor(descriptorCount == request.artifacts.size) {
            "Model descriptor count does not match the request"
        }

        val descriptorIndices = linkedSetOf<Int>()
        val logicalNames = linkedSetOf<String>()
        var totalBytes = 0L
        var manifestCount = 0
        request.artifacts.forEachIndexed { index, artifact ->
            validateSymbolicId(artifact.role, "Artifact $index role", YoloContract.MAX_ROLE_BYTES)
            validateLogicalName(artifact.logicalName, index)
            requireValue(artifact.sizeBytes in 1L..YoloContract.MAX_MODEL_ARTIFACT_BYTES) {
                "Artifact $index length is invalid"
            }
            requireDescriptor(artifact.descriptorIndex in 0 until descriptorCount) {
                "Artifact $index descriptor index is out of range"
            }
            requireDescriptor(descriptorIndices.add(artifact.descriptorIndex)) {
                "Model descriptor index ${artifact.descriptorIndex} is duplicated"
            }
            requireValue(logicalNames.add(artifact.logicalName)) {
                "Model artifact logical name is duplicated"
            }
            if (artifact.role == YoloContract.REQUIRED_MANIFEST_ROLE) manifestCount += 1
            if (artifact.role == YoloContract.REQUIRED_MANIFEST_ROLE) {
                requireValue(artifact.sizeBytes <= YoloContract.MAX_MANIFEST_BYTES) {
                    "Model manifest exceeds the dedicated parser-input limit"
                }
            }
            totalBytes = addExact(totalBytes, artifact.sizeBytes, "Total model length overflows")
            requireValue(totalBytes <= YoloContract.MAX_TOTAL_MODEL_BYTES) {
                "Total model length exceeds the protocol limit"
            }
        }
        requireDescriptor(descriptorIndices == (0 until descriptorCount).toSet()) {
            "Model descriptor indices must be contiguous and consume every descriptor"
        }
        requireValue(manifestCount == 1) { "Exactly one model manifest artifact is required" }
    }

    fun validateOpenSessionRequestAgainst(
        request: YoloOpenSessionRequest,
        descriptorCount: Int,
        capabilities: YoloCapabilities,
        negotiatedVersion: YoloProtocolVersion,
    ) {
        validateOpenSessionRequest(request, descriptorCount)
        validateCapabilities(capabilities)
        validateProtocolVersion(negotiatedVersion, "negotiated")
        requireCapability(request.protocolVersion == negotiatedVersion) {
            "Open-session request uses a protocol version that was not negotiated"
        }
        requireCapability(request.task in capabilities.tasks) { "Requested YOLO task is unsupported" }
        requireCapability(request.modelFormat in capabilities.modelFormats) { "Model format is unsupported" }
        requireCapability(request.device in capabilities.devices) { "Inference device is unsupported" }
        requireCapability(request.decoderId in capabilities.decoderIds) { "Decoder is unsupported" }
        requireCapability(request.cpuThreads <= capabilities.limits.maxCpuThreads) {
            "CPU thread count exceeds the provider limit"
        }
        requireCapability(request.timeoutMillis <= capabilities.limits.maxTimeoutMillis) {
            "Session-open timeout exceeds the provider limit"
        }
        requireCapability(descriptorCount <= capabilities.limits.maxModelDescriptors) {
            "Model descriptor count exceeds the provider limit"
        }
        var totalBytes = 0L
        request.artifacts.forEachIndexed { index, artifact ->
            requireCapability(artifact.role in capabilities.artifactRoles) {
                "Artifact $index role is unsupported"
            }
            requireCapability(artifact.sizeBytes <= capabilities.limits.maxModelArtifactBytes) {
                "Artifact $index exceeds the provider limit"
            }
            totalBytes = addExact(totalBytes, artifact.sizeBytes, "Total model length overflows")
        }
        requireCapability(totalBytes <= capabilities.limits.maxTotalModelBytes) {
            "Total model length exceeds the provider limit"
        }
    }

    fun validateDetectRequest(request: YoloDetectRequest) {
        requireValue(request.sequence > 0L) { "Detection sequence must be positive" }
        validateImageReference(request.image)
        requireValue(request.confidenceThreshold.isFinite() && request.confidenceThreshold in 0.0..1.0) {
            "Confidence threshold must be finite and within [0, 1]"
        }
        requireValue(request.iouThreshold.isFinite() && request.iouThreshold in 0.0..1.0) {
            "IoU threshold must be finite and within [0, 1]"
        }
        requireValue(request.maxDetections in 1..YoloContract.MAX_DETECTIONS) {
            "Maximum detections is invalid"
        }
        requireValue(request.timeoutMillis in 1L..YoloContract.MAX_TIMEOUT_MILLIS) {
            "Detection timeout is invalid"
        }
    }

    fun validateDetectRequestAgainst(request: YoloDetectRequest, capabilities: YoloCapabilities) {
        validateDetectRequest(request)
        validateCapabilities(capabilities)
        requireCapability(request.image.pixelFormat in capabilities.pixelFormats) {
            "Image pixel format is unsupported"
        }
        requireCapability(
            request.image.width <= capabilities.limits.maxImageDimension &&
                request.image.height <= capabilities.limits.maxImageDimension,
        ) { "Image dimensions exceed the provider limit" }
        requireCapability(request.image.sizeBytes <= capabilities.limits.maxImageBytes) {
            "Image length exceeds the provider limit"
        }
        requireCapability(request.maxDetections <= capabilities.limits.maxDetections) {
            "Requested detection count exceeds the provider limit"
        }
        requireCapability(request.timeoutMillis <= capabilities.limits.maxTimeoutMillis) {
            "Detection timeout exceeds the provider limit"
        }
    }

    fun validateDetectResult(result: YoloDetectResult) {
        requireValue(result.sequence > 0L) { "Result sequence must be positive" }
        requireValue(result.imageWidth in 1..YoloContract.MAX_IMAGE_DIMENSION) {
            "Result image width is invalid"
        }
        requireValue(result.imageHeight in 1..YoloContract.MAX_IMAGE_DIMENSION) {
            "Result image height is invalid"
        }
        requireValue(result.elapsedMillis >= 0L) { "Result elapsed time must not be negative" }
        requireValue(result.detections.size <= YoloContract.MAX_DETECTIONS) {
            "Result detection count exceeds the protocol limit"
        }
        result.detections.forEachIndexed { index, detection ->
            validateDetection(detection, result.imageWidth, result.imageHeight, index)
        }
    }

    fun validateDetectResultAgainst(result: YoloDetectResult, request: YoloDetectRequest) {
        validateDetectRequest(request)
        validateDetectResult(result)
        requireValue(result.requestId == request.requestId) { "Result request ID does not match" }
        requireValue(result.sequence == request.sequence) { "Result sequence does not match" }
        requireValue(result.imageWidth == request.image.width && result.imageHeight == request.image.height) {
            "Result dimensions do not match the original input image"
        }
        requireValue(result.detections.size <= request.maxDetections) {
            "Result detection count exceeds the requested limit"
        }
    }

    fun validateError(error: YoloError) {
        requireValue(error.sequence > 0L) { "Error sequence must be positive" }
        requireUtf8Text(error.message, "Error message", YoloContract.MAX_ERROR_MESSAGE_BYTES)
    }

    fun validateCancelRequest(request: YoloCancelRequest) {
        requireValue(request.sequence > 0L) { "Cancellation request sequence must be positive" }
        requireValue(
            request.reason == YoloCancellationReason.REQUESTED ||
                request.reason == YoloCancellationReason.TIMEOUT,
        ) { "Host cancellation reason is invalid" }
    }

    fun validateCancellation(cancellation: YoloCancellation) {
        requireValue(cancellation.sequence > 0L) { "Cancellation sequence must be positive" }
        requireValue(cancellation.elapsedMillis >= 0L) { "Cancellation elapsed time must not be negative" }
    }

    fun validateProtocolRange(min: YoloProtocolVersion, max: YoloProtocolVersion, label: String) {
        validateProtocolVersion(min, "$label minimum")
        validateProtocolVersion(max, "$label maximum")
        requireValue(min <= max) { "$label protocol range is reversed" }
    }

    fun validateProtocolVersion(version: YoloProtocolVersion, label: String) {
        requireValue(version.major > 0 && version.minor >= 0) { "$label protocol version is invalid" }
    }

    private fun validateLimits(limits: YoloResourceLimits) {
        requireValue(limits.maxModelDescriptors in 1..YoloContract.MAX_MODEL_DESCRIPTORS) {
            "Model descriptor limit is invalid"
        }
        requireValue(limits.maxModelArtifactBytes in 1L..YoloContract.MAX_MODEL_ARTIFACT_BYTES) {
            "Model artifact byte limit is invalid"
        }
        requireValue(limits.maxTotalModelBytes in 1L..YoloContract.MAX_TOTAL_MODEL_BYTES) {
            "Total model byte limit is invalid"
        }
        requireValue(limits.maxModelArtifactBytes <= limits.maxTotalModelBytes) {
            "Per-artifact model limit exceeds the total model limit"
        }
        requireValue(limits.maxImageDimension in 1..YoloContract.MAX_IMAGE_DIMENSION) {
            "Image dimension limit is invalid"
        }
        requireValue(limits.maxImageBytes in 1L..YoloContract.MAX_IMAGE_BYTES) {
            "Image byte limit is invalid"
        }
        requireValue(limits.maxDetections in 1..YoloContract.MAX_DETECTIONS) {
            "Detection limit is invalid"
        }
        requireValue(limits.maxCpuThreads in 1..YoloContract.MAX_CPU_THREADS) {
            "CPU thread limit is invalid"
        }
        requireValue(limits.maxTimeoutMillis in 1L..YoloContract.MAX_TIMEOUT_MILLIS) {
            "Timeout limit is invalid"
        }
        requireValue(limits.maxConcurrentSessions in 1..YoloContract.MAX_CONCURRENT_SESSIONS) {
            "Concurrent session limit is invalid"
        }
        requireValue(limits.maxQueuedDetections == YoloContract.MAX_QUEUED_DETECTIONS) {
            "YOLO protocol v1 permits no queued detections"
        }
    }

    private fun validateImageReference(image: YoloImageReference) {
        requireValue(image.pixelFormat == YoloPixelFormat.RGBA_8888) {
            "YOLO protocol v1 accepts RGBA_8888 only"
        }
        requireValue(image.width in 1..YoloContract.MAX_IMAGE_DIMENSION) { "Image width is invalid" }
        requireValue(image.height in 1..YoloContract.MAX_IMAGE_DIMENSION) { "Image height is invalid" }
        val minimumStride = image.width.toLong() * 4L
        requireValue(image.rowStride.toLong() >= minimumStride) { "RGBA row stride is too small" }
        val expectedBytes = multiplyExact(
            image.rowStride.toLong(),
            image.height.toLong(),
            "Image byte length overflows",
        )
        requireValue(image.sizeBytes == expectedBytes) {
            "Image length must equal rowStride * height"
        }
        requireValue(image.sizeBytes in 1L..YoloContract.MAX_IMAGE_BYTES) {
            "Image length exceeds the protocol limit"
        }
    }

    private fun validateDetection(
        detection: YoloDetection,
        imageWidth: Int,
        imageHeight: Int,
        index: Int,
    ) {
        requireValue(detection.classId >= 0) { "Detection $index class ID is negative" }
        requireUtf8Text(detection.label, "Detection $index label", YoloContract.MAX_LABEL_BYTES)
        requireValue(detection.confidence.isFinite() && detection.confidence in 0.0..1.0) {
            "Detection $index confidence is invalid"
        }
        val bounds = detection.bounds
        requireValue(
            bounds.left.isFinite() && bounds.top.isFinite() &&
                bounds.right.isFinite() && bounds.bottom.isFinite(),
        ) { "Detection $index bounds are not finite" }
        requireValue(bounds.left <= bounds.right && bounds.top <= bounds.bottom) {
            "Detection $index bounds are reversed"
        }
        requireValue(
            bounds.left >= 0.0 && bounds.top >= 0.0 &&
                bounds.right <= imageWidth.toDouble() && bounds.bottom <= imageHeight.toDouble(),
        ) { "Detection $index bounds are outside the original image" }
    }

    private fun validateLogicalName(value: String, index: Int) {
        requireUtf8Text(value, "Artifact $index logical name", YoloContract.MAX_LOGICAL_NAME_BYTES)
        requireValue(value != "." && value != "..") { "Artifact $index logical name is not a file name" }
        requireValue('/' !in value && '\\' !in value && '\u0000' !in value) {
            "Artifact $index logical name must be a single safe logical file name"
        }
    }

    private fun validateSymbolicId(value: String, label: String, maxBytes: Int) {
        requireUtf8Text(value, label, maxBytes)
        requireValue(SYMBOLIC_ID.matches(value)) { "$label is invalid" }
    }

    private fun <T> requireDistinctNonEmpty(values: Collection<T>, label: String) {
        requireValue(values.isNotEmpty()) { "Supported $label must not be empty" }
        requireValue(values.size == values.toSet().size) { "Supported $label contain duplicates" }
    }

    private fun requireUtf8Text(value: String, label: String, maxBytes: Int) {
        requireValue(value.isNotBlank()) { "$label must not be blank" }
        requireValue(utf8Size(value, label) <= maxBytes) { "$label exceeds $maxBytes UTF-8 bytes" }
    }

    private fun utf8Size(value: String, label: String): Int = try {
        StandardCharsets.UTF_8.newEncoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT)
            .encode(java.nio.CharBuffer.wrap(value))
            .remaining()
    } catch (error: Exception) {
        throw YoloContractException(
            YoloContractViolation.INVALID_VALUE,
            "$label is not valid Unicode text",
            error,
        )
    }

    private fun addExact(left: Long, right: Long, message: String): Long = try {
        Math.addExact(left, right)
    } catch (error: ArithmeticException) {
        throw YoloContractException(YoloContractViolation.INVALID_VALUE, message, error)
    }

    private fun multiplyExact(left: Long, right: Long, message: String): Long = try {
        Math.multiplyExact(left, right)
    } catch (error: ArithmeticException) {
        throw YoloContractException(YoloContractViolation.INVALID_VALUE, message, error)
    }

    private inline fun requireValue(condition: Boolean, lazyMessage: () -> String) {
        if (!condition) {
            throw YoloContractException(YoloContractViolation.INVALID_VALUE, lazyMessage())
        }
    }

    private inline fun requireCapability(condition: Boolean, lazyMessage: () -> String) {
        if (!condition) {
            throw YoloContractException(YoloContractViolation.CAPABILITY_INCOMPATIBLE, lazyMessage())
        }
    }

    private inline fun requireDescriptor(condition: Boolean, lazyMessage: () -> String) {
        if (!condition) {
            throw YoloContractException(YoloContractViolation.DESCRIPTOR_MISMATCH, lazyMessage())
        }
    }
}
