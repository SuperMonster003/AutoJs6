package org.autojs.plugin.apkbuilder.template.impl

class RemoteApkBuildUnsupportedException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)
