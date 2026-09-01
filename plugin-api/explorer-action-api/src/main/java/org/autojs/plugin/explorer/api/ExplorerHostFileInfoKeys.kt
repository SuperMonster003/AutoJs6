package org.autojs.plugin.explorer.api

/** Keys shared by the host-file-information request and its bounded synchronous result. */
object ExplorerHostFileInfoKeys {
    const val VERSION = "version"

    const val ACTION_ID = "actionId"
    const val DISPLAY_NAME = "displayName"
    const val SIZE = "size"
    const val LAST_MODIFIED = "lastModified"
    const val EXPECTED_SOURCE_SHA256 = "expectedSourceSha256"

    const val ERROR_CODE = "errorCode"
    const val ERROR_MESSAGE = "errorMessage"
    const val TITLE = "title"
    const val SUMMARY = "summary"
    const val SOURCE_SHA256 = "sourceSha256"
}
