package org.autojs.plugin.yolo.api

import org.junit.Assert.assertThrows
import org.junit.Test

class YoloValidationTest {
    @Test
    fun backendProvenanceAndSessionOpenTimeoutAreRequired() {
        assertThrows(YoloContractException::class.java) {
            YoloValidation.validateCapabilities(YoloFixtures.capabilities(backendVersion = ""))
        }
        assertThrows(YoloContractException::class.java) {
            val request = YoloFixtures.openSessionRequest(timeoutMillis = 0L)
            YoloValidation.validateOpenSessionRequest(request, descriptorCount = request.artifacts.size)
        }
    }

    @Test
    fun modelDescriptorsAreExactAndManifestHasItsOwnSmallLimit() {
        val request = YoloFixtures.openSessionRequest()
        assertThrows(YoloContractException::class.java) {
            YoloValidation.validateOpenSessionRequest(request, descriptorCount = request.artifacts.size + 1)
        }

        val oversizedManifest = YoloFixtures.artifact(
            role = "manifest",
            logicalName = "model.json",
            digestInput = "manifest",
            descriptorIndex = 0,
            sizeBytes = YoloContract.MAX_MANIFEST_BYTES + 1L,
        )
        val oversized = YoloFixtures.openSessionRequest(
            artifacts = listOf(
                oversizedManifest,
                YoloFixtures.artifact("graph", "model.param", "graph", 1),
                YoloFixtures.artifact("weights", "model.bin", "weights", 2),
            ),
        )
        assertThrows(YoloContractException::class.java) {
            YoloValidation.validateOpenSessionRequest(oversized, descriptorCount = 3)
        }
    }

    @Test
    fun rawRgbaGeometryAndThresholdsFailClosed() {
        val request = YoloFixtures.detectRequest()
        assertThrows(YoloContractException::class.java) {
            YoloValidation.validateDetectRequest(
                request.copy(image = request.image.copy(rowStride = 15, sizeBytes = 45L)),
            )
        }
        assertThrows(YoloContractException::class.java) {
            YoloValidation.validateDetectRequest(request.copy(confidenceThreshold = Double.NaN))
        }
        assertThrows(YoloContractException::class.java) {
            YoloValidation.validateDetectRequest(request.copy(iouThreshold = 1.1))
        }
        assertThrows(YoloContractException::class.java) {
            YoloValidation.validateDetectRequest(request.copy(sequence = 0L))
        }
        assertThrows(YoloContractException::class.java) {
            YoloValidation.validateDetectRequest(request.copy(sequence = -1L))
        }
    }

    @Test
    fun resultBoundsUseOriginalImageAndRespectRequestedCount() {
        val request = YoloFixtures.detectRequest(maxDetections = 1)
        val outOfBounds = YoloDetectResult(
            requestId = request.requestId,
            sequence = request.sequence,
            imageWidth = request.image.width,
            imageHeight = request.image.height,
            elapsedMillis = 1L,
            detections = listOf(
                YoloDetection(0, "person", 0.9, YoloBounds(0.0, 0.0, 5.0, 3.0)),
            ),
        )
        assertThrows(YoloContractException::class.java) {
            YoloValidation.validateDetectResultAgainst(outOfBounds, request)
        }

        val tooMany = YoloDetectResult(
            requestId = request.requestId,
            sequence = request.sequence,
            imageWidth = request.image.width,
            imageHeight = request.image.height,
            elapsedMillis = 1L,
            detections = listOf(
                YoloDetection(0, "person", 0.9, YoloBounds(0.0, 0.0, 1.0, 1.0)),
                YoloDetection(1, "car", 0.8, YoloBounds(1.0, 1.0, 2.0, 2.0)),
            ),
        )
        assertThrows(YoloContractException::class.java) {
            YoloValidation.validateDetectResultAgainst(tooMany, request)
        }
    }
}
