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

    /** Int: oldest supported version. A V1-compatible plugin keeps advertising 1 here. */
    const val CONTRACT_VERSION = "installerContractVersion"

    /** Int: optional newest supported version; absent means CONTRACT_VERSION only. */
    const val MAX_CONTRACT_VERSION = "installerMaxContractVersion"

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
    /** V2: notification presentation for installation sessions. */
    const val FEATURE_NOTIFICATION_INSTALL = "notification-install"
    /** V2: setDefaultInstallerV2 can manage the persistent scope. */
    const val FEATURE_PERSISTENT_DEFAULT_INSTALLER = "persistent-default-installer"
    /** V3: the five advanced installation options, with platform/authorizer checks at execution. */
    const val FEATURE_ADVANCED_INSTALL_OPTIONS = "advanced-install-options"

    /** Known optional features; only implemented features may be advertised by a plugin. */
    val FEATURES: List<String> = listOf(
        FEATURE_BATCH,
        FEATURE_SPLITS,
        FEATURE_DELETE_SOURCE,
        FEATURE_SILENT_UNINSTALL,
        FEATURE_DEFAULT_INSTALLER,
        FEATURE_USERS,
        FEATURE_INSPECT,
        FEATURE_NOTIFICATION_INSTALL,
        FEATURE_PERSISTENT_DEFAULT_INSTALLER,
        FEATURE_ADVANCED_INSTALL_OPTIONS,
    )
}
