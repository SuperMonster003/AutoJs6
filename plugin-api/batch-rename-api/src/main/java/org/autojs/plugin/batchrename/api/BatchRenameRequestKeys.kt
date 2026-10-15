package org.autojs.plugin.batchrename.api

/**
 * Keys of the request Bundle of [IBatchRenamePlugin.plan].
 *
 * zh-CN: [IBatchRenamePlugin.plan] 请求 Bundle 的键.
 */
object BatchRenameRequestKeys {

    /** Int: the host's [BatchRenameContract.CONTRACT_VERSION]. */
    const val CONTRACT_VERSION = "contractVersion"

    /** Bundle: the rename options, keyed by [BatchRenameOptionKeys]. */
    const val OPTIONS = "options"

    /** ArrayList of Bundle: one entry per parent directory, keyed by [BatchRenameParentKeys]. */
    const val PARENTS = "parents"

    /** ArrayList of Bundle: the targets in enumeration order, keyed by [BatchRenameItemKeys]. */
    const val ITEMS = "items"

    /** String: BCP 47 tag of the locale for month names, weekday names and case conversion. */
    const val LOCALE = "locale"

    /** String: IANA time zone id used to format modification times and [NOW]. */
    const val TIME_ZONE = "timeZone"

    /** Long: epoch milliseconds the `now` date source uses, fixed by the host for the whole plan. */
    const val NOW = "now"
}
