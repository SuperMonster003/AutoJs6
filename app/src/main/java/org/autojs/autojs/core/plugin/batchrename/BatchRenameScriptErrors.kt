package org.autojs.autojs.core.plugin.batchrename

import org.autojs.plugin.batchrename.api.BatchRenameErrorCodes

/**
 * Error codes of the `files.batchRename` script API (plugin roadmap D27); scripts read them as `error.code`.
 * zh-CN: `files.batchRename` 脚本 API 的错误码 (插件路线图 D27); 脚本以 `error.code` 读取.
 */
object BatchRenameScriptCodes {
    /** No Batch Rename plugin is installed. zh-CN: 未安装 Batch Rename 插件. */
    const val PLUGIN_UNAVAILABLE = "PLUGIN_UNAVAILABLE"

    /** The plugin is installed but disabled or not authorized in the Plugin Center. zh-CN: 插件已安装但未启用或未授权. */
    const val PLUGIN_DISABLED = "PLUGIN_DISABLED"

    /** The plugin needs a newer host, or speaks a contract version this host does not. zh-CN: 插件与宿主版本不兼容. */
    const val PLUGIN_INCOMPATIBLE = "PLUGIN_INCOMPATIBLE"
    const val INVALID_OPTIONS = "INVALID_OPTIONS"
    const val INVALID_PATHS = "INVALID_PATHS"
    const val LIMIT_EXCEEDED = "LIMIT_EXCEEDED"

    /** The plan has conflicting or invalid items and `onConflict` is `"fail"`. zh-CN: 方案含冲突或非法项且策略为 `"fail"`. */
    const val PLAN_REJECTED = "PLAN_REJECTED"

    /** The plan is unknown to this script, expired or already applied. zh-CN: 方案不属于本脚本, 已过期或已执行. */
    const val PLAN_STALE = "PLAN_STALE"
    const val IO_FAILED = "IO_FAILED"
    const val UNDO_UNAVAILABLE = "UNDO_UNAVAILABLE"

    /** A failure of the host or the plugin that the script cannot fix. zh-CN: 脚本无法处理的宿主或插件内部失败. */
    const val INTERNAL = "INTERNAL"

    /** The script code of an error code the plugin answered. zh-CN: 插件应答的错误码对应的脚本错误码. */
    fun fromPluginErrorCode(code: String?): String = when (code) {
        BatchRenameErrorCodes.INVALID_OPTIONS -> INVALID_OPTIONS
        BatchRenameErrorCodes.LIMIT_EXCEEDED -> LIMIT_EXCEEDED
        BatchRenameErrorCodes.UNSUPPORTED_CONTRACT_VERSION -> PLUGIN_INCOMPATIBLE
        else -> INTERNAL
    }
}

/**
 * A `files.batchRename` failure with its script error [code].
 * zh-CN: 带脚本错误码 [code] 的 `files.batchRename` 失败.
 */
class BatchRenameScriptException(val code: String, message: String, cause: Throwable? = null) : RuntimeException(message, cause)

internal fun batchRenameFailure(code: String, message: String): Nothing = throw BatchRenameScriptException(code, message)
