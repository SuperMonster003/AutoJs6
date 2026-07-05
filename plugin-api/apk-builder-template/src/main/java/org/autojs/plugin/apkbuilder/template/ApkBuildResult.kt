package org.autojs.plugin.apkbuilder.template

import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class ApkBuildResult(
    var status: Int = STATUS_OK,
    var outputApkFd: ParcelFileDescriptor? = null,
    var outputFileName: String? = null,
    var outputSizeBytes: Long = 0L,
    var outputSha256: String? = null,
    var updatedProjectConfigJson: String? = null,
    var compatibilityLevel: Int = ApkBuilderTemplateResult.LEVEL_OK,
    var warnings: ArrayList<String> = arrayListOf(),
    var errors: ArrayList<String> = arrayListOf(),
    var extras: Bundle? = null,
) : Parcelable {
    companion object {
        const val STATUS_OK = 0
        const val STATUS_FAILED = 1
        const val STATUS_CANCELLED = 2
        const val STATUS_UNSUPPORTED = 3
    }
}
