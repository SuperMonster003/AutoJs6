package org.autojs.autojs.runtime.api

import android.graphics.Rect
import org.autojs.autojs.util.RhinoUtils.newNativeObject
import org.mozilla.javascript.NativeObject
import kotlin.math.abs

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

    override fun compareTo(other: OcrResult): Int {
        // 上下差距小于二分之一的高度 判定为同一行
        val deviation = bounds.height().coerceAtLeast(other.bounds.height()) / 2
        // 通过垂直中心点的距离判定
        return if (abs((bounds.top + bounds.bottom) / 2 - (other.bounds.top + other.bounds.bottom) / 2) < deviation) {
            bounds.left - other.bounds.left
        } else {
            bounds.bottom - other.bounds.bottom
        }
    }

    override fun toString() =
        "${OcrResult::class.java.simpleName}@${Integer.toHexString(hashCode())}" +
        "{text=$text, confidence=$confidence, bounds=$bounds}"

}