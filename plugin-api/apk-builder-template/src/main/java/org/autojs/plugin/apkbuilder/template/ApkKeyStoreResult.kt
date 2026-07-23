package org.autojs.plugin.apkbuilder.template

import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class ApkKeyStoreResult(
    var status: Int = STATUS_FAILED,
    var message: String? = null,
) : Parcelable {

    val isSuccess: Boolean
        get() = status == STATUS_OK

    companion object {
        const val STATUS_OK = 0
        const val STATUS_FAILED = 1
        const val STATUS_UNSUPPORTED = 2
        const val STATUS_UNAVAILABLE = 3
    }
}
