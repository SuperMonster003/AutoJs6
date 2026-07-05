package org.autojs.plugin.apkbuilder.template

import android.os.Bundle
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class ApkBuilderTemplateInfo(
    var id: String = ApkBuilderTemplatePluginIds.ID,
    var name: String = "AutoJs6 APK Builder Template",
    var description: String? = null,
    var author: String = "AutoJs6",
    var versionName: String = "",
    var versionCode: Long = 0L,
    var versionDate: String? = null,
    var protocolVersion: Int = ApkBuilderTemplateProtocol.VERSION,
    var hostPackageName: String = "",
    var hostVersionName: String = "",
    var hostVersionCode: Long = 0L,
    var templatePackageName: String = "",
    var templateSha256: String? = null,
    var templateSizeBytes: Long = 0L,
    var capabilities: Bundle? = null,
) : Parcelable
