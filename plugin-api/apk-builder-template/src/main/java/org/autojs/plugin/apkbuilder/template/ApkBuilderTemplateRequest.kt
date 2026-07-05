package org.autojs.plugin.apkbuilder.template

import android.os.Bundle
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class ApkBuilderTemplateRequest(
    var hostPackageName: String = "",
    var hostVersionName: String = "",
    var hostVersionCode: Long = 0L,
    var requiredProtocolVersion: Int = ApkBuilderTemplateProtocol.VERSION,
    var allowRiskyTemplate: Boolean = true,
    var extras: Bundle? = null,
) : Parcelable
