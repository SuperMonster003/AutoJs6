package org.autojs.autojs.runtime.api

import android.util.Log
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import org.autojs.autojs.core.image.ImageWrapper
import com.google.mlkit.vision.barcode.common.Barcode as MLKitBarcode

/**
 * Created by SuperMonster003 on Oct 10, 2023.
 */
class Barcode {

    @JvmOverloads
    fun detect(image: ImageWrapper?, formats: IntArray = intArrayOf(), enableAllPotentialBarcodes: Boolean = false): Array<WrappedBarcode> {
        image?.takeUnless { image.isRecycled } ?: return emptyArray()

        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(MLKitBarcode.FORMAT_ALL_FORMATS, *formats)
            .apply { if (enableAllPotentialBarcodes) enableAllPotentialBarcodes() }
            .build()

        val scanner = BarcodeScanning.getClient(options)
        val inputImage = InputImage.fromBitmap(image.bitmap, 0)

        var results: Array<WrappedBarcode>? = null

        val resultScan = scanner.process(inputImage)
            .addOnSuccessListener { barcodes: MutableList<MLKitBarcode> ->
                results = barcodes.map { WrappedBarcode(it) }.toTypedArray()
                lockNotify()
            }
            .addOnFailureListener { e ->
                e.printStackTrace()
                lockNotify()
            }
            .addOnCanceledListener { lockNotify() }
            .addOnCompleteListener { lockNotify() }

        while (!resultScan.isComplete) {
            synchronized(lock) {
                try {
                    lock.wait(50)
                } catch (_: InterruptedException) {
                    // Ignored.
                }
            }
        }

        image.shoot()

        if (!resultScan.isSuccessful) {
            Log.w(TAG, "Barcode scanning is not successful")
        }
        return results ?: emptyArray()
    }

    private fun lockNotify() = synchronized(lock) { lock.notify() }

    companion object {

        private val lock = Object()

        private val TAG: String = Companion::class.java.simpleName

        const val TYPE_NAME_UNKNOWN = "TYPE_NAME_UNKNOWN"
        const val FORMAT_NAME_UNKNOWN = "FORMAT_NAME_UNKNOWN"

    }

}