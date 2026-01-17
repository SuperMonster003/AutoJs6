package org.autojs.autojs.core.plugin.center

data class PluginIndexRelease(
    val versionName: String,
    val versionCode: Long = 0L,
    val versionDate: String? = null,

    val apkUrl: String? = null,
    val apkSha256: String? = null,
    val apkSizeBytes: Long? = null,

    val changelogUrl: String? = null,
    val changelogText: String? = null,
)
