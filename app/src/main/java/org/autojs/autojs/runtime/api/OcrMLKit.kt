package org.autojs.autojs.runtime.api

import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import org.autojs.autojs.core.image.ImageWrapper

/**
 * Created by SuperMonster003 on Mar 18, 2023.
 */
// @Reference to TonyJiangWJ/Auto.js (https://github.com/TonyJiangWJ/Auto.js) on Mar 18, 2023.
class OcrMLKit {

    private var recognizer: TextRecognizer? = null

    private fun initIfNeeded() {
        recognizer?:let {
            recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())
        }
    }

    fun release() {
        recognizer?.close()
    }

    fun detect(image: ImageWrapper?): List<OcrResult> {
        initIfNeeded()

        image?.takeUnless { image.isRecycled } ?: return emptyList()

        val bitmap = image.bitmap
        if (bitmap.isRecycled) return emptyList<OcrResult>().also { image.shoot() }

        val inputImage = InputImage.fromBitmap(bitmap, 0)
        val result = recognizer!!.process(inputImage)
            .addOnCanceledListener { lockNotify() }
            .addOnCompleteListener { lockNotify() }
            .addOnSuccessListener { lockNotify() }
            .addOnFailureListener { e: Exception ->
                Log.w(TAG, "Failed to detect: ${e.message}")
                e.printStackTrace()
                lockNotify()
            }
        while (!result.isComplete) {
            synchronized(lock) {
                try {
                    lock.wait(50)
                } catch (_: InterruptedException) {
                    // Ignored.
                }
            }
        }
        image.shoot()
        if (!result.isSuccessful) {
            Log.w(TAG, "Detection is not successful")
            return emptyList()
        }
        val ocrResults = ArrayList<OcrResult>()
        result.result.textBlocks.forEach { block ->
            block.lines.forEach { line ->
                OcrResult(line.text, line.confidence, line.boundingBox!!).run {
                    ocrResults.add(this)
                }
            }
        }
        return ocrResults
    }

    fun recognizeText(image: ImageWrapper?): Array<String> {
        initIfNeeded()
        val words = detect(image).sorted()
        val output = mutableListOf<String>()
        words.indices.forEach { i -> words[i].label.let { output.add(it) } }
        return output.toTypedArray()
    }

    private fun lockNotify() = synchronized(lock) { lock.notify() }

    companion object {

        private val lock = Object()

        private val TAG: String = Companion::class.java.simpleName

    }

}