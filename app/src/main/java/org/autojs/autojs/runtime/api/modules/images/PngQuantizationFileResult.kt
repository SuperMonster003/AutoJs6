package org.autojs.autojs.runtime.api.modules.images

import org.autojs.autojs.runtime.api.PngQuantBridge

/** Script-facing metadata for an indexed PNG streamed directly to a file. */
class PngQuantizationFileResult(path: String, result: PngQuantBridge.OutputResult) {

    @JvmField
    val path: String = path

    @JvmField
    val size: Long = result.size

    @JvmField
    val quality: Int = result.quality

    @JvmField
    val quantizationError: Double = result.quantizationError

    /** Peak additional working memory in bytes, or -1 when an older runtime handled the call. */
    @JvmField
    val peakWorkingMemoryBytes: Long = result.peakWorkingMemoryBytes
}
