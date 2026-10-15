package org.autojs.plugin.batchrename.api

/**
 * Hard ceilings both sides enforce. A request beyond them is answered with [BatchRenameErrorCodes.LIMIT_EXCEEDED]
 * instead of being truncated. The character budget keeps one request well below the Binder transaction buffer; the
 * host also maps a `TransactionTooLargeException` to the same error.
 *
 * zh-CN: 双方都执行的硬上限. 超限请求以 [BatchRenameErrorCodes.LIMIT_EXCEEDED] 应答, 不做截断. 字符预算使单个请求远低于
 * Binder 事务缓冲区; 宿主也把 `TransactionTooLargeException` 映射为同一错误.
 */
object BatchRenameLimits {

    /** Items of one plan, including excluded items. */
    const val MAX_ITEMS_PER_PLAN = 2000

    /** Distinct parent directories of one plan. */
    const val MAX_PARENTS_PER_PLAN = 512

    /** Existing entry names of all parents of one plan together. */
    const val MAX_EXISTING_NAMES_PER_PLAN = 20_000

    /** UTF-16 characters of one transported name (item name or existing name). */
    const val MAX_NAME_CHARS = 1024

    /** UTF-16 characters of `search`, `replace` and the other text options, each. */
    const val MAX_PATTERN_CHARS = 4096

    /** UTF-16 characters of all strings of one request together. */
    const val MAX_REQUEST_CHARS = 262_144

    /** Length of a `${rstring...=N}` random string. */
    const val MAX_RANDOM_LENGTH = 256

    /** Suffix number tried by the [BatchRenameValues.CONFLICT_NUMBER] policy before the item is reported as a conflict. */
    const val MAX_CONFLICT_NUMBER = 999

    /** UTF-8 bytes of a valid resulting name (ext4 / f2fs single path segment). */
    const val MAX_RESULT_NAME_BYTES = 255
}
