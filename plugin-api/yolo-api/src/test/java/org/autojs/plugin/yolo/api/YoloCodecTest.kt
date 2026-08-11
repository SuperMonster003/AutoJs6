package org.autojs.plugin.yolo.api

import org.autojs.plugin.protocol.wire.TaggedWireError
import org.autojs.plugin.protocol.wire.TaggedWireException
import org.autojs.plugin.protocol.wire.TaggedWireProtocol
import org.autojs.plugin.protocol.wire.TaggedWireWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder

class YoloCodecTest {
    @Test
    fun semanticFieldsCarryRequiredReaderFlagsOnTheRawWire() {
        val providerFlags = rawFieldFlags(YoloCodec.encodeProviderInfo(YoloFixtures.providerInfo()))
        (1..7).forEach { tag ->
            assertEquals(
                listOf(TaggedWireProtocol.FLAG_REQUIRED_FOR_READER),
                providerFlags.getValue(tag),
            )
        }
        assertEquals(listOf(0), providerFlags.getValue(8))

        val detectFlags = rawFieldFlags(YoloCodec.encodeDetectRequest(YoloFixtures.detectRequest()))
        detectFlags.values.flatten().forEach { flags ->
            assertEquals(TaggedWireProtocol.FLAG_REQUIRED_FOR_READER, flags)
        }
    }

    @Test
    fun unknownRequiredFieldIsRejectedBeforeMessageInterpretation() {
        val bytes = TaggedWireWriter(
            schemaId = YoloContract.SCHEMA_DETECT_REQUEST,
            schemaMajor = YoloContract.SCHEMA_MAJOR,
            schemaMinor = YoloContract.SCHEMA_MINOR,
        ).int32(999, 1, requiredForReader = true).encode()

        val error = assertThrows(TaggedWireException::class.java) {
            YoloCodec.decodeDetectRequest(bytes)
        }
        assertEquals(TaggedWireError.UNKNOWN_REQUIRED_FIELD, error.error)
    }

    @Test
    fun maximumDetectionCountWithMaximumLabelsFitsTheMetadataEnvelope() {
        val request = YoloFixtures.detectRequest(maxDetections = YoloContract.MAX_DETECTIONS)
        val maximumLabel = "x".repeat(YoloContract.MAX_LABEL_BYTES)
        val result = YoloDetectResult(
            requestId = request.requestId,
            sequence = request.sequence,
            imageWidth = request.image.width,
            imageHeight = request.image.height,
            elapsedMillis = 1L,
            detections = (0 until YoloContract.MAX_DETECTIONS).map { classId ->
                YoloDetection(
                    classId = classId,
                    label = maximumLabel,
                    confidence = 0.5,
                    bounds = YoloBounds(0.0, 0.0, 1.0, 1.0),
                )
            },
        )

        val encoded = YoloCodec.encodeDetectResult(result)
        assertTrue(encoded.size <= YoloContract.MAX_METADATA_BYTES)
        assertEquals(YoloContract.MAX_DETECTIONS, YoloCodec.decodeDetectResult(encoded).detections.size)
    }

    @Test
    fun providerInfoAndCapabilitiesRoundTrip() {
        val info = YoloFixtures.providerInfo()
        val capabilities = YoloFixtures.capabilities()

        assertEquals(info, YoloCodec.decodeProviderInfo(YoloCodec.encodeProviderInfo(info)))
        assertEquals(
            capabilities,
            YoloCodec.decodeCapabilities(YoloCodec.encodeCapabilities(capabilities)),
        )
    }

    @Test
    fun openSessionAndDetectRequestsRoundTrip() {
        val open = YoloFixtures.openSessionRequest()
        val detect = YoloFixtures.detectRequest()

        assertEquals(
            open,
            YoloCodec.decodeOpenSessionRequest(
                YoloCodec.encodeOpenSessionRequest(open),
                descriptorCount = open.artifacts.size,
            ),
        )
        assertEquals(detect, YoloCodec.decodeDetectRequest(YoloCodec.encodeDetectRequest(detect)))
    }

    @Test
    fun terminalMessagesRoundTrip() {
        val request = YoloFixtures.detectRequest()
        val result = YoloFixtures.result(request)
        val error = YoloError(
            requestId = request.requestId,
            sequence = request.sequence,
            code = YoloErrorCode.BUSY,
            phase = YoloFailurePhase.ADMISSION,
            message = "Another detection is active",
            retryableBeforeDispatch = true,
        )
        val cancelRequest = YoloCancelRequest(
            request.requestId,
            request.sequence,
            YoloCancellationReason.REQUESTED,
        )
        val cancellation = YoloCancellation(
            request.requestId,
            request.sequence,
            YoloCancellationReason.REQUESTED,
            7L,
        )

        assertEquals(result, YoloCodec.decodeDetectResult(YoloCodec.encodeDetectResult(result)))
        assertEquals(error, YoloCodec.decodeError(YoloCodec.encodeError(error)))
        assertEquals(
            cancelRequest,
            YoloCodec.decodeCancelRequest(YoloCodec.encodeCancelRequest(cancelRequest)),
        )
        assertEquals(
            cancellation,
            YoloCodec.decodeCancellation(YoloCodec.encodeCancellation(cancellation)),
        )
    }

    private fun rawFieldFlags(bytes: ByteArray): Map<Int, List<Int>> {
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.BIG_ENDIAN)
        buffer.position(6 * Int.SIZE_BYTES)
        val fieldCount = buffer.int
        val flagsByTag = linkedMapOf<Int, MutableList<Int>>()
        repeat(fieldCount) {
            val tag = buffer.int
            buffer.int // type
            val flags = buffer.int
            val length = buffer.int
            flagsByTag.getOrPut(tag) { mutableListOf() } += flags
            buffer.position(buffer.position() + length)
        }
        return flagsByTag
    }
}
