package org.autojs.plugin.yolo.api

import java.util.UUID

object YoloFixtures {
    val SESSION_REQUEST_ID: YoloRequestId =
        YoloRequestId.fromUuid(UUID.fromString("10000000-0000-0000-0000-000000000001"))
    val DETECT_REQUEST_ID: YoloRequestId =
        YoloRequestId.fromUuid(UUID.fromString("20000000-0000-0000-0000-000000000001"))
    val OTHER_DETECT_REQUEST_ID: YoloRequestId =
        YoloRequestId.fromUuid(UUID.fromString("20000000-0000-0000-0000-000000000002"))

    fun providerInfo() = YoloProviderInfo(
        providerId = "autojs6-yolo-ncnn",
        displayName = "AutoJs6 YOLO NCNN",
        author = "AutoJs6",
        versionName = "0.1.0",
        versionCode = 1L,
        backend = YoloBackend.NCNN,
        supportedAbis = listOf("arm64-v8a"),
        minHostVersionCode = 100L,
    )

    fun limits() = YoloResourceLimits(
        maxModelDescriptors = 8,
        maxModelArtifactBytes = 128L * 1024L * 1024L,
        maxTotalModelBytes = 256L * 1024L * 1024L,
        maxImageDimension = 4_096,
        maxImageBytes = 64L * 1024L * 1024L,
        maxDetections = 300,
        maxCpuThreads = 16,
        maxTimeoutMillis = 120_000L,
        maxConcurrentSessions = 1,
        maxQueuedDetections = 0,
    )

    fun capabilities(backendVersion: String = "20250503") = YoloCapabilities(
        protocolMin = YoloProtocolVersion(1, 0),
        protocolMax = YoloProtocolVersion(1, 0),
        backend = YoloBackend.NCNN,
        backendVersion = backendVersion,
        tasks = listOf(YoloTask.DETECT),
        modelFormats = listOf(YoloModelFormat.NCNN),
        devices = listOf(YoloDevice.CPU),
        pixelFormats = listOf(YoloPixelFormat.RGBA_8888),
        decoderIds = listOf("ultralytics-detect-v1"),
        artifactRoles = listOf("manifest", "graph", "weights"),
        limits = limits(),
    )

    fun openSessionRequest(
        artifacts: Collection<YoloModelArtifact> = artifacts(),
        timeoutMillis: Long = 60_000L,
    ) = YoloOpenSessionRequest(
        requestId = SESSION_REQUEST_ID,
        protocolVersion = YoloProtocolVersion(1, 0),
        task = YoloTask.DETECT,
        modelFormat = YoloModelFormat.NCNN,
        device = YoloDevice.CPU,
        cpuThreads = 4,
        decoderId = "ultralytics-detect-v1",
        timeoutMillis = timeoutMillis,
        artifacts = artifacts,
    )

    fun artifacts() = listOf(
        artifact("manifest", "model.json", "manifest", 0),
        artifact("graph", "model.ncnn.param", "graph", 1),
        artifact("weights", "model.ncnn.bin", "weights", 2),
    )

    fun artifact(
        role: String,
        logicalName: String,
        digestInput: String,
        descriptorIndex: Int,
        sizeBytes: Long = digestInput.toByteArray().size.toLong(),
    ) = YoloModelArtifact(
        role = role,
        logicalName = logicalName,
        sizeBytes = sizeBytes,
        sha256 = YoloSha256.digest(digestInput.toByteArray()),
        descriptorIndex = descriptorIndex,
    )

    fun detectRequest(
        requestId: YoloRequestId = DETECT_REQUEST_ID,
        sequence: Long = 1L,
        maxDetections: Int = 10,
    ) = YoloDetectRequest(
        requestId = requestId,
        sequence = sequence,
        image = YoloImageReference(
            pixelFormat = YoloPixelFormat.RGBA_8888,
            width = 4,
            height = 3,
            rowStride = 16,
            sizeBytes = 48L,
        ),
        confidenceThreshold = 0.35,
        iouThreshold = 0.45,
        maxDetections = maxDetections,
        timeoutMillis = 30_000L,
    )

    fun result(request: YoloDetectRequest = detectRequest()) = YoloDetectResult(
        requestId = request.requestId,
        sequence = request.sequence,
        imageWidth = request.image.width,
        imageHeight = request.image.height,
        elapsedMillis = 12L,
        detections = listOf(
            YoloDetection(
                classId = 0,
                label = "person",
                confidence = 0.9,
                bounds = YoloBounds(0.5, 0.25, 3.5, 3.0),
            ),
        ),
    )
}
