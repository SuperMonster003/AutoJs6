package org.autojs.autojs.runtime.api

import android.graphics.Rect
import org.autojs.autojs.runtime.api.modules.ocr.OcrLineMerger
import org.autojs.autojs.runtime.api.modules.ocr.toOcrBox
import org.autojs.autojs.util.RhinoUtils.newNativeObject
import org.mozilla.javascript.NativeObject

// @Reference to com.baidu.paddle.lite.ocr.OcrResult by SuperMonster003 on Mar 18, 2023.
//  ! https://github.com/PaddlePaddle/PaddleOCR
/**
 * Represents a result from Optical Character Recognition (OCR) processing.
 *
 * @property text The recognized text.
 * @property confidence The confidence score of the recognition, ranging from 0 (least confident) to 1 (most confident).
 * @property bounds The bounding box coordinates of the recognized text region.
 * @property extras The engine's result extras as a script object (see [OcrResultExtras]), empty when the engine gives none.
 */
class OcrResult @JvmOverloads constructor(
    @JvmField val text: String,
    @JvmField val confidence: Float,
    @JvmField val bounds: Rect,
    @JvmField val extras: NativeObject = newNativeObject(),
) : Comparable<OcrResult> {

    @JvmField
    val label: String = text

    override fun compareTo(other: OcrResult) = OcrLineMerger.compare(bounds.toOcrBox(), other.bounds.toOcrBox())

    override fun toString() =
        "${OcrResult::class.java.simpleName}@${Integer.toHexString(hashCode())}" +
        "{text=$text, confidence=$confidence, bounds=$bounds}"

}