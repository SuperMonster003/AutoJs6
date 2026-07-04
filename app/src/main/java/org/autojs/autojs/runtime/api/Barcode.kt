package org.autojs.autojs.runtime.api

import android.util.Log
import kotlinx.coroutines.runBlocking
import org.autojs.autojs.AutoJs
import org.autojs.autojs.core.image.ImageWrapper
import org.autojs.autojs.core.plugin.barcode.BarcodePluginHost
import org.autojs.plugin.mlkit.barcode.api.BarcodeFormats
import org.autojs.plugin.mlkit.barcode.api.BarcodeOptions
import org.autojs.plugin.mlkit.barcode.api.BarcodeValueTypes

/**
 * Created by SuperMonster003 on Oct 10, 2023.
 */
class Barcode {

    fun detect(
        image: ImageWrapper?,
        formats: IntArray = intArrayOf(),
        enableAllPotentialBarcodes: Boolean = false,
        onlyOneResult: Boolean = false,
    ): List<WrappedBarcode> {
        image?.takeUnless { image.isRecycled } ?: return emptyList()

        val context = AutoJs.instance.applicationContext
        val options = BarcodeOptions().apply {
            this.formats = formats
            this.enableAllPotentialBarcodes = enableAllPotentialBarcodes
            this.maxResults = if (onlyOneResult) 1 else MAX_RESULTS
        }

        return try {
            val target = runBlocking {
                BarcodePluginHost.selectOrThrow(
                    context = context,
                    engine = ENGINE_MLKIT_BARCODE,
                )
            }
            runBlocking {
                BarcodePluginHost.detect(context, target, image.bitmap, options)
            }
                .let { if (onlyOneResult) it.take(1) else it }
                .map { WrappedBarcode(it) }
        } catch (e: Throwable) {
            Log.w(TAG, "Barcode plugin scanning failed: ${e.message}")
            throw e
        } finally {
            image.shoot()
        }
    }

    companion object {

        private val TAG: String = Companion::class.java.simpleName

        private const val MAX_RESULTS = 128
        private const val ENGINE_MLKIT_BARCODE = "mlkit-barcode"

        const val TYPE_NAME_UNKNOWN = BarcodeValueTypes.TYPE_NAME_UNKNOWN
        const val FORMAT_NAME_UNKNOWN = BarcodeFormats.FORMAT_NAME_UNKNOWN
    }
}
