package org.autojs.autojs.runtime.api

import android.util.Log
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import org.autojs.autojs.core.image.ImageWrapper
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import com.google.mlkit.vision.barcode.common.Barcode as MLKitBarcode

/**
 * Created by SuperMonster003 on Oct 10, 2023.
 */
class Barcode {

    fun detect(image: ImageWrapper?, formats: IntArray = intArrayOf(), enableAllPotentialBarcodes: Boolean = false, onlyOneResult: Boolean = false): List<WrappedBarcode> {

        image?.takeUnless { image.isRecycled } ?: return emptyList()

        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(MLKitBarcode.FORMAT_ALL_FORMATS, *formats)
            .apply { if (enableAllPotentialBarcodes) enableAllPotentialBarcodes() }
            .build()

        val scanner = BarcodeScanning.getClient(options)
        val inputImage = InputImage.fromBitmap(image.bitmap, 0)
        val scanCompletedSignal = CountDownLatch(1)

        var results: List<WrappedBarcode>? = null

        val resultScan = scanner.process(inputImage)
            .addOnSuccessListener { barcodes: MutableList<MLKitBarcode> ->
                when {
                    onlyOneResult -> barcodes.firstOrNull()?.let { results = listOf(WrappedBarcode(it)) }
                    else -> results = barcodes.map { WrappedBarcode(it) }
                }
                scanCompletedSignal.countDown()
            }
            .addOnFailureListener { e ->
                e.printStackTrace()
                scanCompletedSignal.countDown()
            }
            .addOnCanceledListener { scanCompletedSignal.countDown() }
            .addOnCompleteListener { scanCompletedSignal.countDown() }

        while (!resultScan.isComplete) {
            try {
                scanCompletedSignal.await(50, TimeUnit.MILLISECONDS)
            } catch (_: InterruptedException) {
                /* Ignored. */
            }
        }

        image.shoot()

        if (!resultScan.isSuccessful) {
            Log.w(TAG, "Barcode scanning is not successful")
        }
        return results ?: emptyList()
    }

    companion object {

        private val TAG: String = Companion::class.java.simpleName

        const val TYPE_NAME_UNKNOWN = "TYPE_NAME_UNKNOWN"
        const val FORMAT_NAME_UNKNOWN = "FORMAT_NAME_UNKNOWN"

    }

}
