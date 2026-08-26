package org.autojs.plugin.explorer.api

/** v11 service-specific errors that invite a bounded host-side open retry. */
object ExplorerArchiveOpenValues {
    const val ERROR_NONE = 0
    const val ERROR_UNRECOVERABLE = 11_000
    const val ERROR_PASSWORD_REQUIRED = 11_001
    const val ERROR_WRONG_PASSWORD = 11_002
}
