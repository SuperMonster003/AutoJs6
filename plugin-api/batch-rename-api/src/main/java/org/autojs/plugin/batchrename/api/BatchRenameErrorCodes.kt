package org.autojs.plugin.batchrename.api

/**
 * Error codes of a failed [IBatchRenamePlugin.plan] call ([BatchRenameResultKeys.ERROR_CODE]). The host maps them to the
 * script error codes of `files.batchRename` (roadmap D27).
 *
 * zh-CN: [IBatchRenamePlugin.plan] 调用失败时的错误码 ([BatchRenameResultKeys.ERROR_CODE]). 宿主把它们映射为
 * `files.batchRename` 的脚本错误码 (路线图 D27).
 */
object BatchRenameErrorCodes {

    /** The request's contract version is outside the plugin's supported range. */
    const val UNSUPPORTED_CONTRACT_VERSION = "UNSUPPORTED_CONTRACT_VERSION"

    /** A required key is missing, has the wrong type or refers to an unknown parent. */
    const val INVALID_REQUEST = "INVALID_REQUEST"

    /** An option has an unknown value or the regular expression does not compile. */
    const val INVALID_OPTIONS = "INVALID_OPTIONS"

    /** A [BatchRenameLimits] ceiling is exceeded. */
    const val LIMIT_EXCEEDED = "LIMIT_EXCEEDED"

    /** The plugin failed unexpectedly; the message carries the detail. */
    const val INTERNAL_ERROR = "INTERNAL_ERROR"

    val ALL: List<String> = listOf(UNSUPPORTED_CONTRACT_VERSION, INVALID_REQUEST, INVALID_OPTIONS, LIMIT_EXCEEDED, INTERNAL_ERROR)
}
