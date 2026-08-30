package org.autojs.autojs.runtime.api.augment.images

import org.autojs.autojs.runtime.api.PngQuantBridge

/** Script-facing PNG bytes and the palette quality metrics reported by libimagequant. */
class PngQuantizationResult(result: PngQuantBridge.Result) {

    @JvmField
    val bytes: ByteArray = result.bytes

    @JvmField
    val size: Int = result.size

    @JvmField
    val quality: Int = result.quality

    @JvmField
    val quantizationError: Double = result.quantizationError
}
