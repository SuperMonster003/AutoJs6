package org.autojs.autojs.core.plugin.explorer

import android.content.Intent
import android.os.Parcel

/** Keeps Explorer Action activity envelopes well below Android's shared Binder buffer ceiling. */
internal object ExplorerActionIntentSizePolicy {

    data class Measurement(
        val byteCount: Int,
        val hasFileDescriptors: Boolean,
    )

    fun requireSafe(intent: Intent) {
        val measurement = measure(intent)
        require(!measurement.hasFileDescriptors) {
            "Explorer Action request must not carry file descriptors"
        }
        require(measurement.byteCount <= MAX_REQUEST_BYTES) {
            "Explorer Action request is too large: ${measurement.byteCount} bytes"
        }
    }

    fun isSafe(intent: Intent): Boolean = runCatching {
        val measurement = measure(intent)
        !measurement.hasFileDescriptors && measurement.byteCount <= MAX_REQUEST_BYTES
    }.getOrDefault(false)

    fun measure(intent: Intent): Measurement {
        val parcel = Parcel.obtain()
        return try {
            intent.writeToParcel(parcel, 0)
            Measurement(
                byteCount = parcel.dataSize(),
                hasFileDescriptors = parcel.hasFileDescriptors(),
            )
        } finally {
            parcel.recycle()
        }
    }

    /** Leaves headroom inside Binder's process-wide transaction buffer for framework overhead. */
    const val MAX_REQUEST_BYTES = 512 * 1024
}
