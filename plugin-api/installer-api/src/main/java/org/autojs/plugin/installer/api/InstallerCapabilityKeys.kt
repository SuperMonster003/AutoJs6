package org.autojs.plugin.installer.api

import org.autojs.plugin.common.api.PluginCapabilityKeys

/**
 * Keys of the `PluginInfo.capabilities` Bundle and of [IInstallerPlugin.getCapabilities]. Every
 * value is readable without opening a session, so the plugin center can show compatibility up
 * front and the host can choose its code path before the first request.
 */
object InstallerCapabilityKeys {

    /** Long: host `versionCode` the plugin requires (read type-agnostically by the host). */
    const val REQUIRES_HOST_VERSION = PluginCapabilityKeys.REQUIRES_HOST_VERSION

    /** Int: [InstallerContract.CONTRACT_VERSION] implemented by the plugin. */
    const val CONTRACT_VERSION = "installerContractVersion"

    /** String array: authorizers the plugin implements, a subset of [InstallerContract.AUTHORIZERS] (never `auto`). */
    const val AUTHORIZERS = "installerAuthorizers"

    /** String array: optional features, a subset of [FEATURES]. */
    const val FEATURES_KEY = "installerFeatures"

    /** Int: largest number of independent packages one `openSession` accepts (at most [InstallerContract.MAX_BATCH_SOURCES]). */
    const val MAX_BATCH = "installerMaxBatch"

    /** Int: largest split set one package may consist of (at most [InstallerContract.MAX_SPLITS_PER_PACKAGE]). */
    const val MAX_SPLITS = "installerMaxSplits"

    /** Int: optional settings extension; version 1 supports [InstallerActions.OPEN_SETTINGS]. */
    const val SETTINGS_VERSION = "installerSettingsVersion"

    const val FEATURE_BATCH = "batch"
    const val FEATURE_SPLITS = "splits"
    const val FEATURE_DELETE_SOURCE = "delete-source"
    const val FEATURE_SILENT_UNINSTALL = "silent-uninstall"
    const val FEATURE_DEFAULT_INSTALLER = "default-installer"
    const val FEATURE_USERS = "users"
    const val FEATURE_INSPECT = "inspect"

    /** Every optional feature of contract version 1. */
    val FEATURES: List<String> = listOf(
        FEATURE_BATCH,
        FEATURE_SPLITS,
        FEATURE_DELETE_SOURCE,
        FEATURE_SILENT_UNINSTALL,
        FEATURE_DEFAULT_INSTALLER,
        FEATURE_USERS,
        FEATURE_INSPECT,
    )
}
