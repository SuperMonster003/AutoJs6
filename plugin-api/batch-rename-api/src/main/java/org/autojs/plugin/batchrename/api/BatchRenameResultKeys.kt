package org.autojs.plugin.batchrename.api

/**
 * Keys of the answer Bundle of [IBatchRenamePlugin.plan]. A failed call carries [ERROR_CODE] and [ERROR_MESSAGE] only;
 * a successful plan carries every other key and never [ERROR_CODE].
 *
 * zh-CN: [IBatchRenamePlugin.plan] 应答 Bundle 的键. 失败的调用只带 [ERROR_CODE] 与 [ERROR_MESSAGE]; 成功的方案带其余全部键,
 * 且从不带 [ERROR_CODE].
 */
object BatchRenameResultKeys {

    /** Int: contract version of the answer, equal to the request's. */
    const val CONTRACT_VERSION = "contractVersion"

    /** String: one of [BatchRenameErrorCodes]; present only when the call failed. */
    const val ERROR_CODE = "errorCode"

    /** String: English detail of [ERROR_CODE] for logs and script errors, at most 1024 characters. */
    const val ERROR_MESSAGE = "errorMessage"

    /** Boolean: the plan must not be executed (a conflict under [BatchRenameValues.CONFLICT_FAIL], or any invalid item). */
    const val REJECTED = "rejected"

    /** ArrayList of Bundle: one answer item per request item, in request order, keyed by [BatchRenameItemKeys]. */
    const val ITEMS = "items"

    /** Bundle: item count per `STATUS_*` value of [BatchRenameValues] (Int, every status present). */
    const val COUNTS = "counts"

    /**
     * Bundle: the complete options the plan used, keyed by [BatchRenameOptionKeys] without [BatchRenameOptionKeys.PRESET]
     * (defaults, then the preset, then the request). Plugins with [BatchRenameValues.FEATURE_PRESETS] answer it with
     * every plan; sending these options back reproduces the plan without the preset.
     */
    const val OPTIONS = "options"
}
