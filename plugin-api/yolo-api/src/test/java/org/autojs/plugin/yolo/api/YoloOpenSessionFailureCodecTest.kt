package org.autojs.plugin.yolo.api

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class YoloOpenSessionFailureCodecTest {
    @Test
    fun everyErrorCodeRoundTripsThroughPublicBinderExceptionMessage() {
        YoloErrorCode.values().forEach { code ->
            val exception = YoloOpenSessionFailureCodec.exception(code, "failure: ${code.name}")
            assertEquals(
                YoloOpenSessionFailure(code, "failure: ${code.name}"),
                YoloOpenSessionFailureCodec.decode(exception),
            )
        }
    }

    @Test
    fun foreignOrMalformedMessagesAreNotClaimed() {
        assertNull(YoloOpenSessionFailureCodec.decode(IllegalArgumentException("foreign")))
        assertNull(YoloOpenSessionFailureCodec.decodeMessage("AUTOJS_YOLO_OPEN_V1:999:unknown"))
        assertNull(YoloOpenSessionFailureCodec.decodeMessage("AUTOJS_YOLO_OPEN_V1:1"))
    }

    @Test
    fun invalidMessagesAreRejectedBeforeTransport() {
        assertThrows(YoloContractException::class.java) {
            YoloOpenSessionFailureCodec.exception(YoloErrorCode.INTERNAL, "\u0000")
        }
        assertThrows(YoloContractException::class.java) {
            YoloOpenSessionFailureCodec.exception(
                YoloErrorCode.INTERNAL,
                "x".repeat(YoloContract.MAX_ERROR_MESSAGE_BYTES + 1),
            )
        }
    }
}
