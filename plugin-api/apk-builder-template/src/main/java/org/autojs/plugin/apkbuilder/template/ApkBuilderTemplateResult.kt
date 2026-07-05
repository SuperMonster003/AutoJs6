package org.autojs.plugin.apkbuilder.template

import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class ApkBuilderTemplateResult(
    var info: ApkBuilderTemplateInfo? = null,
    var templateFd: ParcelFileDescriptor? = null,
    var compatibilityLevel: Int = LEVEL_OK,
    var warnings: ArrayList<String> = arrayListOf(),
    var errors: ArrayList<String> = arrayListOf(),
    var extras: Bundle? = null,
) : Parcelable {
    companion object {
        const val LEVEL_OK = 0
        const val LEVEL_WARN = 1
        const val LEVEL_BLOCK = 2
    }
}
