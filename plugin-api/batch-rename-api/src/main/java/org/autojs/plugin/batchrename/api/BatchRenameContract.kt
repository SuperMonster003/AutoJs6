package org.autojs.plugin.batchrename.api

/**
 * Version of the Batch Rename contract (docs/dev/batch-rename-plugin-protocol-v1.md). The host writes its version into
 * every request; a plugin answers requests whose version it supports and reports
 * [BatchRenameErrorCodes.UNSUPPORTED_CONTRACT_VERSION] otherwise. Later versions may only append keys, values and
 * AIDL transactions.
 *
 * zh-CN: Batch Rename 契约版本. 宿主在每个请求中写入自身版本; 插件应答其支持的版本, 否则返回
 * [BatchRenameErrorCodes.UNSUPPORTED_CONTRACT_VERSION]. 后续版本只能追加键, 取值与 AIDL 事务.
 */
object BatchRenameContract {

    const val CONTRACT_VERSION = 1
    const val MIN_CONTRACT_VERSION = 1
    const val MAX_CONTRACT_VERSION = 1

    /** The only regular expression dialect of version 1: ECMAScript, evaluated like `RegExp` in scripts. */
    const val REGEX_DIALECT_ECMASCRIPT = "ecmascript"

    fun supportsContractVersion(version: Int): Boolean = version in MIN_CONTRACT_VERSION..MAX_CONTRACT_VERSION
}
