package org.autojs.plugin.yolo.api

import org.autojs.plugin.protocol.wire.TaggedWireDocument
import org.autojs.plugin.protocol.wire.TaggedWireLimits
import org.autojs.plugin.protocol.wire.TaggedWireWriter

object YoloCodec {
    private val METADATA_LIMITS = TaggedWireLimits(
        maxDocumentBytes = YoloContract.MAX_METADATA_BYTES,
        maxFieldBytes = YoloContract.MAX_FIELD_BYTES,
        maxFields = YoloContract.MAX_WIRE_FIELDS,
    )

    fun encodeProviderInfo(value: YoloProviderInfo): ByteArray {
        YoloValidation.validateProviderInfo(value)
        return writer(YoloContract.SCHEMA_PROVIDER_INFO)
            .string(ProviderInfoTags.PROVIDER_ID, value.providerId, requiredForReader = true)
            .string(ProviderInfoTags.DISPLAY_NAME, value.displayName, requiredForReader = true)
            .string(ProviderInfoTags.AUTHOR, value.author, requiredForReader = true)
            .string(ProviderInfoTags.VERSION_NAME, value.versionName, requiredForReader = true)
            .int64(ProviderInfoTags.VERSION_CODE, value.versionCode, requiredForReader = true)
            .int32(ProviderInfoTags.BACKEND, value.backend.wireCode, requiredForReader = true)
            .strings(ProviderInfoTags.SUPPORTED_ABI, value.supportedAbis, requiredForReader = true)
            .apply {
                value.minHostVersionCode?.let { int64(ProviderInfoTags.MIN_HOST_VERSION_CODE, it) }
                value.maxHostVersionCode?.let { int64(ProviderInfoTags.MAX_HOST_VERSION_CODE, it) }
            }
            .encode()
    }

    fun decodeProviderInfo(bytes: ByteArray): YoloProviderInfo =
        document(
            bytes,
            YoloContract.SCHEMA_PROVIDER_INFO,
            ProviderInfoTags.ALL,
            ProviderInfoTags.REPEATED,
        ).let { document ->
            YoloProviderInfo(
                providerId = document.requireString(ProviderInfoTags.PROVIDER_ID),
                displayName = document.requireString(ProviderInfoTags.DISPLAY_NAME),
                author = document.requireString(ProviderInfoTags.AUTHOR),
                versionName = document.requireString(ProviderInfoTags.VERSION_NAME),
                versionCode = document.requireInt64(ProviderInfoTags.VERSION_CODE),
                backend = backend(document.requireInt32(ProviderInfoTags.BACKEND)),
                supportedAbis = document.strings(ProviderInfoTags.SUPPORTED_ABI),
                minHostVersionCode = document.optionalInt64(ProviderInfoTags.MIN_HOST_VERSION_CODE),
                maxHostVersionCode = document.optionalInt64(ProviderInfoTags.MAX_HOST_VERSION_CODE),
            ).also(YoloValidation::validateProviderInfo)
        }

    fun encodeCapabilities(value: YoloCapabilities): ByteArray {
        YoloValidation.validateCapabilities(value)
        return writer(YoloContract.SCHEMA_CAPABILITIES)
            .int32(CapabilitiesTags.PROTOCOL_MIN_MAJOR, value.protocolMin.major, requiredForReader = true)
            .int32(CapabilitiesTags.PROTOCOL_MIN_MINOR, value.protocolMin.minor, requiredForReader = true)
            .int32(CapabilitiesTags.PROTOCOL_MAX_MAJOR, value.protocolMax.major, requiredForReader = true)
            .int32(CapabilitiesTags.PROTOCOL_MAX_MINOR, value.protocolMax.minor, requiredForReader = true)
            .int32(CapabilitiesTags.BACKEND, value.backend.wireCode, requiredForReader = true)
            .string(CapabilitiesTags.BACKEND_VERSION, value.backendVersion, requiredForReader = true)
            .strings(CapabilitiesTags.TASK, value.tasks.map(::taskToken), requiredForReader = true)
            .strings(
                CapabilitiesTags.MODEL_FORMAT,
                value.modelFormats.map(::modelFormatToken),
                requiredForReader = true,
            )
            .strings(CapabilitiesTags.DEVICE, value.devices.map(::deviceToken), requiredForReader = true)
            .strings(
                CapabilitiesTags.PIXEL_FORMAT,
                value.pixelFormats.map(::pixelFormatToken),
                requiredForReader = true,
            )
            .strings(CapabilitiesTags.DECODER_ID, value.decoderIds, requiredForReader = true)
            .strings(CapabilitiesTags.ARTIFACT_ROLE, value.artifactRoles, requiredForReader = true)
            .document(CapabilitiesTags.LIMITS, encodeLimits(value.limits), requiredForReader = true)
            .encode()
    }

    fun decodeCapabilities(bytes: ByteArray): YoloCapabilities =
        document(
            bytes,
            YoloContract.SCHEMA_CAPABILITIES,
            CapabilitiesTags.ALL,
            CapabilitiesTags.REPEATED,
        ).let { document ->
            YoloCapabilities(
                protocolMin = YoloProtocolVersion(
                    document.requireInt32(CapabilitiesTags.PROTOCOL_MIN_MAJOR),
                    document.requireInt32(CapabilitiesTags.PROTOCOL_MIN_MINOR),
                ),
                protocolMax = YoloProtocolVersion(
                    document.requireInt32(CapabilitiesTags.PROTOCOL_MAX_MAJOR),
                    document.requireInt32(CapabilitiesTags.PROTOCOL_MAX_MINOR),
                ),
                backend = backend(document.requireInt32(CapabilitiesTags.BACKEND)),
                backendVersion = document.requireString(CapabilitiesTags.BACKEND_VERSION),
                tasks = document.strings(CapabilitiesTags.TASK).map(::task),
                modelFormats = document.strings(CapabilitiesTags.MODEL_FORMAT).map(::modelFormat),
                devices = document.strings(CapabilitiesTags.DEVICE).map(::device),
                pixelFormats = document.strings(CapabilitiesTags.PIXEL_FORMAT).map(::pixelFormat),
                decoderIds = document.strings(CapabilitiesTags.DECODER_ID),
                artifactRoles = document.strings(CapabilitiesTags.ARTIFACT_ROLE),
                limits = decodeLimits(document.requireDocument(CapabilitiesTags.LIMITS)),
            ).also(YoloValidation::validateCapabilities)
        }

    fun encodeOpenSessionRequest(value: YoloOpenSessionRequest): ByteArray {
        YoloValidation.validateOpenSessionRequest(value, value.artifacts.size)
        return writer(YoloContract.SCHEMA_OPEN_SESSION_REQUEST)
            .bytes(OpenSessionTags.REQUEST_ID, value.requestId.toByteArray(), requiredForReader = true)
            .int32(OpenSessionTags.PROTOCOL_MAJOR, value.protocolVersion.major, requiredForReader = true)
            .int32(OpenSessionTags.PROTOCOL_MINOR, value.protocolVersion.minor, requiredForReader = true)
            .int32(OpenSessionTags.TASK, value.task.wireCode, requiredForReader = true)
            .int32(OpenSessionTags.MODEL_FORMAT, value.modelFormat.wireCode, requiredForReader = true)
            .int32(OpenSessionTags.DEVICE, value.device.wireCode, requiredForReader = true)
            .int32(OpenSessionTags.CPU_THREADS, value.cpuThreads, requiredForReader = true)
            .string(OpenSessionTags.DECODER_ID, value.decoderId, requiredForReader = true)
            .int64(OpenSessionTags.TIMEOUT_MILLIS, value.timeoutMillis, requiredForReader = true)
            .apply {
                value.artifacts.forEach { artifact ->
                    document(
                        OpenSessionTags.ARTIFACT,
                        encodeModelArtifact(artifact),
                        requiredForReader = true,
                    )
                }
            }
            .encode()
    }

    fun decodeOpenSessionRequest(bytes: ByteArray, descriptorCount: Int): YoloOpenSessionRequest =
        document(
            bytes,
            YoloContract.SCHEMA_OPEN_SESSION_REQUEST,
            OpenSessionTags.ALL,
            OpenSessionTags.REPEATED,
        ).let { document ->
            YoloOpenSessionRequest(
                requestId = YoloRequestId.fromBytes(document.requireBytes(OpenSessionTags.REQUEST_ID)),
                protocolVersion = YoloProtocolVersion(
                    document.requireInt32(OpenSessionTags.PROTOCOL_MAJOR),
                    document.requireInt32(OpenSessionTags.PROTOCOL_MINOR),
                ),
                task = task(document.requireInt32(OpenSessionTags.TASK)),
                modelFormat = modelFormat(document.requireInt32(OpenSessionTags.MODEL_FORMAT)),
                device = device(document.requireInt32(OpenSessionTags.DEVICE)),
                cpuThreads = document.requireInt32(OpenSessionTags.CPU_THREADS),
                decoderId = document.requireString(OpenSessionTags.DECODER_ID),
                timeoutMillis = document.requireInt64(OpenSessionTags.TIMEOUT_MILLIS),
                artifacts = document.documents(OpenSessionTags.ARTIFACT).map(::decodeModelArtifact),
            ).also { YoloValidation.validateOpenSessionRequest(it, descriptorCount) }
        }

    fun encodeDetectRequest(value: YoloDetectRequest): ByteArray {
        YoloValidation.validateDetectRequest(value)
        return writer(YoloContract.SCHEMA_DETECT_REQUEST)
            .bytes(DetectRequestTags.REQUEST_ID, value.requestId.toByteArray(), requiredForReader = true)
            .int64(DetectRequestTags.SEQUENCE, value.sequence, requiredForReader = true)
            .document(DetectRequestTags.IMAGE, encodeImageReference(value.image), requiredForReader = true)
            .float64(
                DetectRequestTags.CONFIDENCE_THRESHOLD,
                value.confidenceThreshold,
                requiredForReader = true,
            )
            .float64(DetectRequestTags.IOU_THRESHOLD, value.iouThreshold, requiredForReader = true)
            .int32(DetectRequestTags.MAX_DETECTIONS, value.maxDetections, requiredForReader = true)
            .int64(DetectRequestTags.TIMEOUT_MILLIS, value.timeoutMillis, requiredForReader = true)
            .encode()
    }

    fun decodeDetectRequest(bytes: ByteArray): YoloDetectRequest =
        document(bytes, YoloContract.SCHEMA_DETECT_REQUEST, DetectRequestTags.ALL).let { document ->
            YoloDetectRequest(
                requestId = YoloRequestId.fromBytes(document.requireBytes(DetectRequestTags.REQUEST_ID)),
                sequence = document.requireInt64(DetectRequestTags.SEQUENCE),
                image = decodeImageReference(document.requireDocument(DetectRequestTags.IMAGE)),
                confidenceThreshold = document.requireFloat64(DetectRequestTags.CONFIDENCE_THRESHOLD),
                iouThreshold = document.requireFloat64(DetectRequestTags.IOU_THRESHOLD),
                maxDetections = document.requireInt32(DetectRequestTags.MAX_DETECTIONS),
                timeoutMillis = document.requireInt64(DetectRequestTags.TIMEOUT_MILLIS),
            ).also(YoloValidation::validateDetectRequest)
        }

    fun encodeDetectResult(value: YoloDetectResult): ByteArray {
        YoloValidation.validateDetectResult(value)
        return writer(YoloContract.SCHEMA_DETECT_RESULT)
            .bytes(DetectResultTags.REQUEST_ID, value.requestId.toByteArray(), requiredForReader = true)
            .int64(DetectResultTags.SEQUENCE, value.sequence, requiredForReader = true)
            .int32(DetectResultTags.IMAGE_WIDTH, value.imageWidth, requiredForReader = true)
            .int32(DetectResultTags.IMAGE_HEIGHT, value.imageHeight, requiredForReader = true)
            .int64(DetectResultTags.ELAPSED_MILLIS, value.elapsedMillis, requiredForReader = true)
            .apply {
                value.detections.forEach { detection ->
                    document(
                        DetectResultTags.DETECTION,
                        encodeDetection(detection),
                        requiredForReader = true,
                    )
                }
            }
            .encode()
    }

    fun decodeDetectResult(bytes: ByteArray): YoloDetectResult =
        document(
            bytes,
            YoloContract.SCHEMA_DETECT_RESULT,
            DetectResultTags.ALL,
            DetectResultTags.REPEATED,
        ).let { document ->
            YoloDetectResult(
                requestId = YoloRequestId.fromBytes(document.requireBytes(DetectResultTags.REQUEST_ID)),
                sequence = document.requireInt64(DetectResultTags.SEQUENCE),
                imageWidth = document.requireInt32(DetectResultTags.IMAGE_WIDTH),
                imageHeight = document.requireInt32(DetectResultTags.IMAGE_HEIGHT),
                elapsedMillis = document.requireInt64(DetectResultTags.ELAPSED_MILLIS),
                detections = document.documents(DetectResultTags.DETECTION).map(::decodeDetection),
            ).also(YoloValidation::validateDetectResult)
        }

    fun encodeError(value: YoloError): ByteArray {
        YoloValidation.validateError(value)
        return writer(YoloContract.SCHEMA_ERROR)
            .bytes(ErrorTags.REQUEST_ID, value.requestId.toByteArray(), requiredForReader = true)
            .int64(ErrorTags.SEQUENCE, value.sequence, requiredForReader = true)
            .int32(ErrorTags.CODE, value.code.wireCode, requiredForReader = true)
            .int32(ErrorTags.PHASE, value.phase.wireCode, requiredForReader = true)
            .string(ErrorTags.MESSAGE, value.message, requiredForReader = true)
            .boolean(
                ErrorTags.RETRYABLE_BEFORE_DISPATCH,
                value.retryableBeforeDispatch,
                requiredForReader = true,
            )
            .encode()
    }

    fun decodeError(bytes: ByteArray): YoloError =
        document(bytes, YoloContract.SCHEMA_ERROR, ErrorTags.ALL).let { document ->
            YoloError(
                requestId = YoloRequestId.fromBytes(document.requireBytes(ErrorTags.REQUEST_ID)),
                sequence = document.requireInt64(ErrorTags.SEQUENCE),
                code = errorCode(document.requireInt32(ErrorTags.CODE)),
                phase = failurePhase(document.requireInt32(ErrorTags.PHASE)),
                message = document.requireString(ErrorTags.MESSAGE),
                retryableBeforeDispatch = document.requireBoolean(ErrorTags.RETRYABLE_BEFORE_DISPATCH),
            ).also(YoloValidation::validateError)
        }

    fun encodeCancelRequest(value: YoloCancelRequest): ByteArray {
        YoloValidation.validateCancelRequest(value)
        return writer(YoloContract.SCHEMA_CANCEL_REQUEST)
            .bytes(CancelRequestTags.REQUEST_ID, value.requestId.toByteArray(), requiredForReader = true)
            .int64(CancelRequestTags.SEQUENCE, value.sequence, requiredForReader = true)
            .int32(CancelRequestTags.REASON, value.reason.wireCode, requiredForReader = true)
            .encode()
    }

    fun decodeCancelRequest(bytes: ByteArray): YoloCancelRequest =
        document(bytes, YoloContract.SCHEMA_CANCEL_REQUEST, CancelRequestTags.ALL).let { document ->
            YoloCancelRequest(
                requestId = YoloRequestId.fromBytes(document.requireBytes(CancelRequestTags.REQUEST_ID)),
                sequence = document.requireInt64(CancelRequestTags.SEQUENCE),
                reason = cancellationReason(document.requireInt32(CancelRequestTags.REASON)),
            ).also(YoloValidation::validateCancelRequest)
        }

    fun encodeCancellation(value: YoloCancellation): ByteArray {
        YoloValidation.validateCancellation(value)
        return writer(YoloContract.SCHEMA_CANCELLATION)
            .bytes(CancellationTags.REQUEST_ID, value.requestId.toByteArray(), requiredForReader = true)
            .int64(CancellationTags.SEQUENCE, value.sequence, requiredForReader = true)
            .int32(CancellationTags.REASON, value.reason.wireCode, requiredForReader = true)
            .int64(CancellationTags.ELAPSED_MILLIS, value.elapsedMillis, requiredForReader = true)
            .encode()
    }

    fun decodeCancellation(bytes: ByteArray): YoloCancellation =
        document(bytes, YoloContract.SCHEMA_CANCELLATION, CancellationTags.ALL).let { document ->
            YoloCancellation(
                requestId = YoloRequestId.fromBytes(document.requireBytes(CancellationTags.REQUEST_ID)),
                sequence = document.requireInt64(CancellationTags.SEQUENCE),
                reason = cancellationReason(document.requireInt32(CancellationTags.REASON)),
                elapsedMillis = document.requireInt64(CancellationTags.ELAPSED_MILLIS),
            ).also(YoloValidation::validateCancellation)
        }

    private fun encodeLimits(value: YoloResourceLimits): ByteArray =
        writer(YoloContract.SCHEMA_RESOURCE_LIMITS)
            .int32(LimitsTags.MAX_MODEL_DESCRIPTORS, value.maxModelDescriptors, requiredForReader = true)
            .int64(
                LimitsTags.MAX_MODEL_ARTIFACT_BYTES,
                value.maxModelArtifactBytes,
                requiredForReader = true,
            )
            .int64(LimitsTags.MAX_TOTAL_MODEL_BYTES, value.maxTotalModelBytes, requiredForReader = true)
            .int32(LimitsTags.MAX_IMAGE_DIMENSION, value.maxImageDimension, requiredForReader = true)
            .int64(LimitsTags.MAX_IMAGE_BYTES, value.maxImageBytes, requiredForReader = true)
            .int32(LimitsTags.MAX_DETECTIONS, value.maxDetections, requiredForReader = true)
            .int32(LimitsTags.MAX_CPU_THREADS, value.maxCpuThreads, requiredForReader = true)
            .int64(LimitsTags.MAX_TIMEOUT_MILLIS, value.maxTimeoutMillis, requiredForReader = true)
            .int32(
                LimitsTags.MAX_CONCURRENT_SESSIONS,
                value.maxConcurrentSessions,
                requiredForReader = true,
            )
            .int32(
                LimitsTags.MAX_QUEUED_DETECTIONS,
                value.maxQueuedDetections,
                requiredForReader = true,
            )
            .encode()

    private fun decodeLimits(bytes: ByteArray): YoloResourceLimits =
        document(bytes, YoloContract.SCHEMA_RESOURCE_LIMITS, LimitsTags.ALL).let { document ->
            YoloResourceLimits(
                maxModelDescriptors = document.requireInt32(LimitsTags.MAX_MODEL_DESCRIPTORS),
                maxModelArtifactBytes = document.requireInt64(LimitsTags.MAX_MODEL_ARTIFACT_BYTES),
                maxTotalModelBytes = document.requireInt64(LimitsTags.MAX_TOTAL_MODEL_BYTES),
                maxImageDimension = document.requireInt32(LimitsTags.MAX_IMAGE_DIMENSION),
                maxImageBytes = document.requireInt64(LimitsTags.MAX_IMAGE_BYTES),
                maxDetections = document.requireInt32(LimitsTags.MAX_DETECTIONS),
                maxCpuThreads = document.requireInt32(LimitsTags.MAX_CPU_THREADS),
                maxTimeoutMillis = document.requireInt64(LimitsTags.MAX_TIMEOUT_MILLIS),
                maxConcurrentSessions = document.requireInt32(LimitsTags.MAX_CONCURRENT_SESSIONS),
                maxQueuedDetections = document.requireInt32(LimitsTags.MAX_QUEUED_DETECTIONS),
            )
        }

    private fun encodeModelArtifact(value: YoloModelArtifact): ByteArray =
        writer(YoloContract.SCHEMA_MODEL_ARTIFACT)
            .string(ModelArtifactTags.ROLE, value.role, requiredForReader = true)
            .string(ModelArtifactTags.LOGICAL_NAME, value.logicalName, requiredForReader = true)
            .int64(ModelArtifactTags.SIZE_BYTES, value.sizeBytes, requiredForReader = true)
            .bytes(ModelArtifactTags.SHA256, value.sha256.toByteArray(), requiredForReader = true)
            .fileDescriptorRef(
                ModelArtifactTags.DESCRIPTOR_INDEX,
                value.descriptorIndex,
                requiredForReader = true,
            )
            .encode()

    private fun decodeModelArtifact(bytes: ByteArray): YoloModelArtifact =
        document(bytes, YoloContract.SCHEMA_MODEL_ARTIFACT, ModelArtifactTags.ALL).let { document ->
            YoloModelArtifact(
                role = document.requireString(ModelArtifactTags.ROLE),
                logicalName = document.requireString(ModelArtifactTags.LOGICAL_NAME),
                sizeBytes = document.requireInt64(ModelArtifactTags.SIZE_BYTES),
                sha256 = YoloSha256.fromBytes(document.requireBytes(ModelArtifactTags.SHA256)),
                descriptorIndex = document.requireFileDescriptorRef(ModelArtifactTags.DESCRIPTOR_INDEX),
            )
        }

    private fun encodeImageReference(value: YoloImageReference): ByteArray =
        writer(YoloContract.SCHEMA_IMAGE_REFERENCE)
            .int32(ImageTags.PIXEL_FORMAT, value.pixelFormat.wireCode, requiredForReader = true)
            .int32(ImageTags.WIDTH, value.width, requiredForReader = true)
            .int32(ImageTags.HEIGHT, value.height, requiredForReader = true)
            .int32(ImageTags.ROW_STRIDE, value.rowStride, requiredForReader = true)
            .int64(ImageTags.SIZE_BYTES, value.sizeBytes, requiredForReader = true)
            .encode()

    private fun decodeImageReference(bytes: ByteArray): YoloImageReference =
        document(bytes, YoloContract.SCHEMA_IMAGE_REFERENCE, ImageTags.ALL).let { document ->
            YoloImageReference(
                pixelFormat = pixelFormat(document.requireInt32(ImageTags.PIXEL_FORMAT)),
                width = document.requireInt32(ImageTags.WIDTH),
                height = document.requireInt32(ImageTags.HEIGHT),
                rowStride = document.requireInt32(ImageTags.ROW_STRIDE),
                sizeBytes = document.requireInt64(ImageTags.SIZE_BYTES),
            )
        }

    private fun encodeBounds(value: YoloBounds): ByteArray =
        writer(YoloContract.SCHEMA_BOUNDS)
            .float64(BoundsTags.LEFT, value.left, requiredForReader = true)
            .float64(BoundsTags.TOP, value.top, requiredForReader = true)
            .float64(BoundsTags.RIGHT, value.right, requiredForReader = true)
            .float64(BoundsTags.BOTTOM, value.bottom, requiredForReader = true)
            .encode()

    private fun decodeBounds(bytes: ByteArray): YoloBounds =
        document(bytes, YoloContract.SCHEMA_BOUNDS, BoundsTags.ALL).let { document ->
            YoloBounds(
                left = document.requireFloat64(BoundsTags.LEFT),
                top = document.requireFloat64(BoundsTags.TOP),
                right = document.requireFloat64(BoundsTags.RIGHT),
                bottom = document.requireFloat64(BoundsTags.BOTTOM),
            )
        }

    private fun encodeDetection(value: YoloDetection): ByteArray =
        writer(YoloContract.SCHEMA_DETECTION)
            .int32(DetectionTags.CLASS_ID, value.classId, requiredForReader = true)
            .string(DetectionTags.LABEL, value.label, requiredForReader = true)
            .float64(DetectionTags.CONFIDENCE, value.confidence, requiredForReader = true)
            .document(DetectionTags.BOUNDS, encodeBounds(value.bounds), requiredForReader = true)
            .encode()

    private fun decodeDetection(bytes: ByteArray): YoloDetection =
        document(bytes, YoloContract.SCHEMA_DETECTION, DetectionTags.ALL).let { document ->
            YoloDetection(
                classId = document.requireInt32(DetectionTags.CLASS_ID),
                label = document.requireString(DetectionTags.LABEL),
                confidence = document.requireFloat64(DetectionTags.CONFIDENCE),
                bounds = decodeBounds(document.requireDocument(DetectionTags.BOUNDS)),
            )
        }

    private fun writer(schemaId: Int) = TaggedWireWriter(
        schemaId = schemaId,
        schemaMajor = YoloContract.SCHEMA_MAJOR,
        schemaMinor = YoloContract.SCHEMA_MINOR,
        limits = METADATA_LIMITS,
    )

    private fun document(
        bytes: ByteArray,
        schemaId: Int,
        knownTags: Set<Int>,
        repeatedTags: Set<Int> = emptySet(),
    ): TaggedWireDocument = TaggedWireDocument.decode(bytes, METADATA_LIMITS)
        .requireSchema(schemaId, YoloContract.SCHEMA_MAJOR)
        .rejectUnknownRequiredFields(knownTags)
        .validateKnownCardinality(knownTags, repeatedTags)

    private fun backend(code: Int) = enumValue(YoloBackend.values().asList(), code, "backend") { it.wireCode }
    private fun task(code: Int) = enumValue(YoloTask.values().asList(), code, "task") { it.wireCode }
    private fun modelFormat(code: Int) =
        enumValue(YoloModelFormat.values().asList(), code, "model format") { it.wireCode }
    private fun device(code: Int) = enumValue(YoloDevice.values().asList(), code, "device") { it.wireCode }
    private fun pixelFormat(code: Int) =
        enumValue(YoloPixelFormat.values().asList(), code, "pixel format") { it.wireCode }
    private fun errorCode(code: Int) =
        enumValue(YoloErrorCode.values().asList(), code, "error code") { it.wireCode }
    private fun failurePhase(code: Int) =
        enumValue(YoloFailurePhase.values().asList(), code, "failure phase") { it.wireCode }
    private fun cancellationReason(code: Int) =
        enumValue(YoloCancellationReason.values().asList(), code, "cancellation reason") { it.wireCode }

    private fun task(value: String) = tokenValue(YoloTask.values().asList(), value, "task", ::taskToken)
    private fun modelFormat(value: String) =
        tokenValue(YoloModelFormat.values().asList(), value, "model format", ::modelFormatToken)
    private fun device(value: String) =
        tokenValue(YoloDevice.values().asList(), value, "device", ::deviceToken)
    private fun pixelFormat(value: String) =
        tokenValue(YoloPixelFormat.values().asList(), value, "pixel format", ::pixelFormatToken)

    private fun taskToken(value: YoloTask): String = value.name.lowercase()
    private fun modelFormatToken(value: YoloModelFormat): String = value.name.lowercase()
    private fun deviceToken(value: YoloDevice): String = value.name.lowercase()
    private fun pixelFormatToken(value: YoloPixelFormat): String = value.name.lowercase()

    private inline fun <T> enumValue(
        values: Iterable<T>,
        code: Int,
        label: String,
        wireCode: (T) -> Int,
    ): T = values.firstOrNull { wireCode(it) == code }
        ?: throw YoloContractException(
            YoloContractViolation.UNKNOWN_ENUM,
            "Unknown YOLO $label value $code",
        )

    private inline fun <T> tokenValue(
        values: Iterable<T>,
        token: String,
        label: String,
        wireToken: (T) -> String,
    ): T = values.firstOrNull { wireToken(it) == token }
        ?: throw YoloContractException(
            YoloContractViolation.UNKNOWN_ENUM,
            "Unknown YOLO $label value $token",
        )

    private object ProviderInfoTags {
        const val PROVIDER_ID = 1
        const val DISPLAY_NAME = 2
        const val AUTHOR = 3
        const val VERSION_NAME = 4
        const val VERSION_CODE = 5
        const val BACKEND = 6
        const val SUPPORTED_ABI = 7
        const val MIN_HOST_VERSION_CODE = 8
        const val MAX_HOST_VERSION_CODE = 9
        val ALL = (1..9).toSet()
        val REPEATED = setOf(SUPPORTED_ABI)
    }

    private object CapabilitiesTags {
        const val PROTOCOL_MIN_MAJOR = 1
        const val PROTOCOL_MIN_MINOR = 2
        const val PROTOCOL_MAX_MAJOR = 3
        const val PROTOCOL_MAX_MINOR = 4
        const val BACKEND = 5
        const val BACKEND_VERSION = 6
        const val TASK = 7
        const val MODEL_FORMAT = 8
        const val DEVICE = 9
        const val PIXEL_FORMAT = 10
        const val DECODER_ID = 11
        const val ARTIFACT_ROLE = 12
        const val LIMITS = 13
        val ALL = (1..13).toSet()
        val REPEATED = setOf(TASK, MODEL_FORMAT, DEVICE, PIXEL_FORMAT, DECODER_ID, ARTIFACT_ROLE)
    }

    private object LimitsTags {
        const val MAX_MODEL_DESCRIPTORS = 1
        const val MAX_MODEL_ARTIFACT_BYTES = 2
        const val MAX_TOTAL_MODEL_BYTES = 3
        const val MAX_IMAGE_DIMENSION = 4
        const val MAX_IMAGE_BYTES = 5
        const val MAX_DETECTIONS = 6
        const val MAX_CPU_THREADS = 7
        const val MAX_TIMEOUT_MILLIS = 8
        const val MAX_CONCURRENT_SESSIONS = 9
        const val MAX_QUEUED_DETECTIONS = 10
        val ALL = (1..10).toSet()
    }

    private object ModelArtifactTags {
        const val ROLE = 1
        const val LOGICAL_NAME = 2
        const val SIZE_BYTES = 3
        const val SHA256 = 4
        const val DESCRIPTOR_INDEX = 5
        val ALL = (1..5).toSet()
    }

    private object OpenSessionTags {
        const val REQUEST_ID = 1
        const val PROTOCOL_MAJOR = 2
        const val PROTOCOL_MINOR = 3
        const val TASK = 4
        const val MODEL_FORMAT = 5
        const val DEVICE = 6
        const val CPU_THREADS = 7
        const val DECODER_ID = 8
        const val TIMEOUT_MILLIS = 9
        const val ARTIFACT = 10
        val ALL = (1..10).toSet()
        val REPEATED = setOf(ARTIFACT)
    }

    private object ImageTags {
        const val PIXEL_FORMAT = 1
        const val WIDTH = 2
        const val HEIGHT = 3
        const val ROW_STRIDE = 4
        const val SIZE_BYTES = 5
        val ALL = (1..5).toSet()
    }

    private object DetectRequestTags {
        const val REQUEST_ID = 1
        const val SEQUENCE = 2
        const val IMAGE = 3
        const val CONFIDENCE_THRESHOLD = 4
        const val IOU_THRESHOLD = 5
        const val MAX_DETECTIONS = 6
        const val TIMEOUT_MILLIS = 7
        val ALL = (1..7).toSet()
    }

    private object BoundsTags {
        const val LEFT = 1
        const val TOP = 2
        const val RIGHT = 3
        const val BOTTOM = 4
        val ALL = (1..4).toSet()
    }

    private object DetectionTags {
        const val CLASS_ID = 1
        const val LABEL = 2
        const val CONFIDENCE = 3
        const val BOUNDS = 4
        val ALL = (1..4).toSet()
    }

    private object DetectResultTags {
        const val REQUEST_ID = 1
        const val SEQUENCE = 2
        const val IMAGE_WIDTH = 3
        const val IMAGE_HEIGHT = 4
        const val ELAPSED_MILLIS = 5
        const val DETECTION = 6
        val ALL = (1..6).toSet()
        val REPEATED = setOf(DETECTION)
    }

    private object ErrorTags {
        const val REQUEST_ID = 1
        const val SEQUENCE = 2
        const val CODE = 3
        const val PHASE = 4
        const val MESSAGE = 5
        const val RETRYABLE_BEFORE_DISPATCH = 6
        val ALL = (1..6).toSet()
    }

    private object CancelRequestTags {
        const val REQUEST_ID = 1
        const val SEQUENCE = 2
        const val REASON = 3
        val ALL = (1..3).toSet()
    }

    private object CancellationTags {
        const val REQUEST_ID = 1
        const val SEQUENCE = 2
        const val REASON = 3
        const val ELAPSED_MILLIS = 4
        val ALL = (1..4).toSet()
    }
}
