package org.autojs.plugin.yolo.api

import java.util.ArrayList
import java.util.Collections

class YoloProviderInfo(
    val providerId: String,
    val displayName: String,
    val author: String,
    val versionName: String,
    val versionCode: Long,
    val backend: YoloBackend,
    supportedAbis: Collection<String>,
    val minHostVersionCode: Long? = null,
    val maxHostVersionCode: Long? = null,
) {
    val supportedAbis: List<String> = immutableList(supportedAbis)

    override fun equals(other: Any?): Boolean = other is YoloProviderInfo &&
        providerId == other.providerId &&
        displayName == other.displayName &&
        author == other.author &&
        versionName == other.versionName &&
        versionCode == other.versionCode &&
        backend == other.backend &&
        supportedAbis == other.supportedAbis &&
        minHostVersionCode == other.minHostVersionCode &&
        maxHostVersionCode == other.maxHostVersionCode

    override fun hashCode(): Int = listOf(
        providerId,
        displayName,
        author,
        versionName,
        versionCode,
        backend,
        supportedAbis,
        minHostVersionCode,
        maxHostVersionCode,
    ).hashCode()
}

data class YoloResourceLimits(
    val maxModelDescriptors: Int,
    val maxModelArtifactBytes: Long,
    val maxTotalModelBytes: Long,
    val maxImageDimension: Int,
    val maxImageBytes: Long,
    val maxDetections: Int,
    val maxCpuThreads: Int,
    val maxTimeoutMillis: Long,
    val maxConcurrentSessions: Int,
    val maxQueuedDetections: Int,
)

class YoloCapabilities(
    val protocolMin: YoloProtocolVersion,
    val protocolMax: YoloProtocolVersion,
    val backend: YoloBackend,
    val backendVersion: String,
    tasks: Collection<YoloTask>,
    modelFormats: Collection<YoloModelFormat>,
    devices: Collection<YoloDevice>,
    pixelFormats: Collection<YoloPixelFormat>,
    decoderIds: Collection<String>,
    artifactRoles: Collection<String>,
    val limits: YoloResourceLimits,
) {
    val tasks: List<YoloTask> = immutableList(tasks)
    val modelFormats: List<YoloModelFormat> = immutableList(modelFormats)
    val devices: List<YoloDevice> = immutableList(devices)
    val pixelFormats: List<YoloPixelFormat> = immutableList(pixelFormats)
    val decoderIds: List<String> = immutableList(decoderIds)
    val artifactRoles: List<String> = immutableList(artifactRoles)

    override fun equals(other: Any?): Boolean = other is YoloCapabilities &&
        protocolMin == other.protocolMin &&
        protocolMax == other.protocolMax &&
        backend == other.backend &&
        backendVersion == other.backendVersion &&
        tasks == other.tasks &&
        modelFormats == other.modelFormats &&
        devices == other.devices &&
        pixelFormats == other.pixelFormats &&
        decoderIds == other.decoderIds &&
        artifactRoles == other.artifactRoles &&
        limits == other.limits

    override fun hashCode(): Int = listOf(
        protocolMin,
        protocolMax,
        backend,
        backendVersion,
        tasks,
        modelFormats,
        devices,
        pixelFormats,
        decoderIds,
        artifactRoles,
        limits,
    ).hashCode()
}

data class YoloModelArtifact(
    val role: String,
    val logicalName: String,
    val sizeBytes: Long,
    val sha256: YoloSha256,
    val descriptorIndex: Int,
)

class YoloOpenSessionRequest(
    val requestId: YoloRequestId,
    val protocolVersion: YoloProtocolVersion,
    val task: YoloTask,
    val modelFormat: YoloModelFormat,
    val device: YoloDevice,
    val cpuThreads: Int,
    val decoderId: String,
    val timeoutMillis: Long,
    artifacts: Collection<YoloModelArtifact>,
) {
    val artifacts: List<YoloModelArtifact> = immutableList(artifacts)

    override fun equals(other: Any?): Boolean = other is YoloOpenSessionRequest &&
        requestId == other.requestId &&
        protocolVersion == other.protocolVersion &&
        task == other.task &&
        modelFormat == other.modelFormat &&
        device == other.device &&
        cpuThreads == other.cpuThreads &&
        decoderId == other.decoderId &&
        timeoutMillis == other.timeoutMillis &&
        artifacts == other.artifacts

    override fun hashCode(): Int = listOf(
        requestId,
        protocolVersion,
        task,
        modelFormat,
        device,
        cpuThreads,
        decoderId,
        timeoutMillis,
        artifacts,
    ).hashCode()
}

data class YoloImageReference(
    val pixelFormat: YoloPixelFormat,
    val width: Int,
    val height: Int,
    val rowStride: Int,
    val sizeBytes: Long,
)

data class YoloDetectRequest(
    val requestId: YoloRequestId,
    val sequence: Long,
    val image: YoloImageReference,
    val confidenceThreshold: Double,
    val iouThreshold: Double,
    val maxDetections: Int,
    val timeoutMillis: Long = YoloContract.DEFAULT_TIMEOUT_MILLIS,
)

data class YoloBounds(
    val left: Double,
    val top: Double,
    val right: Double,
    val bottom: Double,
)

data class YoloDetection(
    val classId: Int,
    val label: String,
    val confidence: Double,
    val bounds: YoloBounds,
)

class YoloDetectResult(
    val requestId: YoloRequestId,
    val sequence: Long,
    val imageWidth: Int,
    val imageHeight: Int,
    val elapsedMillis: Long,
    detections: Collection<YoloDetection>,
) {
    val detections: List<YoloDetection> = immutableList(detections)

    override fun equals(other: Any?): Boolean = other is YoloDetectResult &&
        requestId == other.requestId &&
        sequence == other.sequence &&
        imageWidth == other.imageWidth &&
        imageHeight == other.imageHeight &&
        elapsedMillis == other.elapsedMillis &&
        detections == other.detections

    override fun hashCode(): Int = listOf(
        requestId,
        sequence,
        imageWidth,
        imageHeight,
        elapsedMillis,
        detections,
    ).hashCode()
}

data class YoloError(
    val requestId: YoloRequestId,
    val sequence: Long,
    val code: YoloErrorCode,
    val phase: YoloFailurePhase,
    val message: String,
    val retryableBeforeDispatch: Boolean,
)

data class YoloCancelRequest(
    val requestId: YoloRequestId,
    val sequence: Long,
    val reason: YoloCancellationReason,
)

data class YoloCancellation(
    val requestId: YoloRequestId,
    val sequence: Long,
    val reason: YoloCancellationReason,
    val elapsedMillis: Long,
)

private fun <T> immutableList(values: Collection<T>): List<T> =
    Collections.unmodifiableList(ArrayList(values))
