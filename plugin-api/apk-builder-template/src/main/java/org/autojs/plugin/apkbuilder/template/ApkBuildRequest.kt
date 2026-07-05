package org.autojs.plugin.apkbuilder.template

import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.os.Parcelable
import kotlinx.parcelize.Parcelize

@Parcelize
data class ApkBuildRequest(
    var hostPackageName: String = "",
    var hostVersionName: String = "",
    var hostVersionCode: Long = 0L,
    var requiredProtocolVersion: Int = ApkBuilderTemplateProtocol.REMOTE_BUILD_VERSION,
    var projectArchiveFd: ParcelFileDescriptor? = null,
    var projectArchiveSizeBytes: Long = 0L,
    var projectArchiveSha256: String? = null,
    var projectConfigJson: String? = null,
    var outputFileName: String? = null,
    var allowRiskyBuild: Boolean = true,
    var extras: Bundle? = null,
) : Parcelable
