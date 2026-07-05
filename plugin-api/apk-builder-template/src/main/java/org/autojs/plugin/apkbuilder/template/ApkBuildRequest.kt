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
    var nativeLibrariesArchiveFd: ParcelFileDescriptor? = null,
    var nativeLibrariesArchiveSizeBytes: Long = 0L,
    var nativeLibrariesArchiveSha256: String? = null,
    var projectConfigJson: String? = null,
    var keyStoreFd: ParcelFileDescriptor? = null,
    var keyStoreSizeBytes: Long = 0L,
    var keyStoreSha256: String? = null,
    var keyStorePassword: String? = null,
    var keyAlias: String? = null,
    var keyAliasPassword: String? = null,
    var outputFileName: String? = null,
    var allowRiskyBuild: Boolean = true,
    var extras: Bundle? = null,
) : Parcelable
