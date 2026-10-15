package org.autojs.plugin.batchrename.api

import org.autojs.plugin.common.api.PluginCapabilityKeys

/**
 * Keys of the `PluginInfo.capabilities` Bundle of the INFO and [IBatchRenamePlugin] services.
 *
 * zh-CN: INFO 服务与 [IBatchRenamePlugin] 服务的 `PluginInfo.capabilities` Bundle 键.
 */
object BatchRenameCapabilityKeys {

    /** Long: host `versionCode` the plugin requires (read type-agnostically by the host). */
    const val REQUIRES_HOST_VERSION = PluginCapabilityKeys.REQUIRES_HOST_VERSION

    /** Int: contract version the plugin implements ([BatchRenameContract.CONTRACT_VERSION]). */
    const val CONTRACT_VERSION = "batchRenameContractVersion"

    /** String array: optional features, a subset of [BatchRenameValues.FEATURES]. */
    const val FEATURES = "batchRenameFeatures"

    /** Int: the largest item count one plan accepts; at most [BatchRenameLimits.MAX_ITEMS_PER_PLAN]. */
    const val MAX_ITEMS = "batchRenameMaxItems"

    /** String: regular expression dialect, [BatchRenameContract.REGEX_DIALECT_ECMASCRIPT] in version 1. */
    const val REGEX_DIALECT = "batchRenameRegexDialect"
}
