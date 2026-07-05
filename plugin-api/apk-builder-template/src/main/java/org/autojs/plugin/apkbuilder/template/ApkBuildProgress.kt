package org.autojs.plugin.apkbuilder.template

import android.os.Bundle
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class ApkBuildProgress(
    var step: Int = STEP_IDLE,
    var title: String? = null,
    var detail: String? = null,
    var current: Long = -1L,
    var total: Long = -1L,
    var percent: Int = -1,
    var extras: Bundle? = null,
) : Parcelable {
    companion object {
        const val STEP_IDLE = 0
        const val STEP_PREPARE = 1
        const val STEP_BUILD = 2
        const val STEP_SIGN = 3
        const val STEP_CLEAN = 4
        const val STEP_FINISH = 5
    }
}
