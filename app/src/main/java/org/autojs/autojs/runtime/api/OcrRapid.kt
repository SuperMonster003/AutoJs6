package org.autojs.autojs.runtime.api

import android.graphics.Bitmap
import android.graphics.Rect
import com.benjaminwan.ocrlibrary.TextBlock
import org.autojs.autojs.AutoJs
import org.autojs.autojs.core.image.ImageWrapper
import java.util.ArrayList
import com.benjaminwan.ocrlibrary.OcrResult as RapidOcrResult

class OcrRapid {

    private val mEmptyOcrResult by lazy {
        RapidOcrResult(
            dbNetTime = 0.0,
            textBlocks = ArrayList(),
            boxImg = newEmptyOutputBitmap(),
            detectTime = 0.0,
            strRes = "",
        )
    }

    // TODO by SuperMonster003 on Sep 29, 2024.
    //  ! Internal logic of this method pending verification.
    //  ! zh-CN: 方法内部逻辑待验证.
    fun detect(image: ImageWrapper?): List<OcrResult> {
        // 获取原始检测的文本块
        val blocks: ArrayList<TextBlock> = detectRaw(image).textBlocks

        // 转换 TextBlock 列表为 OcrResult 列表
        return blocks.map { block ->
            // 假设 boxPoint 是按照 (top-left, top-right, bottom-right, bottom-left) 顺序排列
            val topLeft = block.boxPoint[0]
            val bottomRight = block.boxPoint[2]
            val rect = Rect(topLeft.x, topLeft.y, bottomRight.x, bottomRight.y)

            // 创建 OcrResult 对象
            OcrResult(
                label = block.text,
                confidence = block.boxScore,
                bounds = rect,
            )
        }
    }

    fun recognizeText(image: ImageWrapper?): List<String> {
        return detectRaw(image).textBlocks.map { it.text }
    }

    private fun OcrRapid.detectRaw(image: ImageWrapper?): RapidOcrResult {
        image ?: return mEmptyOcrResult
        val bitmap = image.bitmap
        if (bitmap.isRecycled) {
            return mEmptyOcrResult
        }
        val emptyOutputBitmap = newEmptyOutputBitmap()
        return AutoJs.instance.rapidOcrEngine.detect(
            input = bitmap,
            output = emptyOutputBitmap,
            maxSideLen = 1024,
            padding = 50,
            boxScoreThresh = 0.5f,
            boxThresh = 0.3f,
            unClipRatio = 2.0f,
            doAngle = false,
            mostAngle = false,
        )
    }

    private fun newEmptyOutputBitmap() = Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)

}
