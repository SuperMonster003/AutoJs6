package org.autojs.autojs.core.plugin.compose

import org.autojs.plugin.compose.api.ComposeUiContract

/** PackageManager/trust seam. Methods are deliberately lazy so a failed gate never loads code. */
internal interface ComposePluginInventory {
    fun installed(): Boolean
    fun systemEnabled(): Boolean
    fun centerEnabled(): Boolean
    fun authorized(): Boolean
    fun requiredHostVersion(): Long
    fun hasFactoryAndInfoService(): Boolean
}

internal object ComposeUiPluginSelection {
    fun beforeLoad(inventory: ComposePluginInventory, hostVersion: Long): SelectionFailure? = when {
        !inventory.installed() -> SelectionFailure.MISSING
        !inventory.systemEnabled() -> SelectionFailure.SYSTEM_DISABLED
        !inventory.centerEnabled() -> SelectionFailure.CENTER_DISABLED
        !inventory.authorized() -> SelectionFailure.UNAUTHORIZED
        inventory.requiredHostVersion().let { it <= 0 || it > hostVersion } -> SelectionFailure.HOST_TOO_OLD
        !inventory.hasFactoryAndInfoService() -> SelectionFailure.DISCOVERY
        else -> null
    }

    fun afterLoad(version: Int, fingerprint: () -> String?, expected: String): SelectionFailure? = when {
        !ComposeUiContract.supports(version) -> SelectionFailure.CONTRACT
        fingerprint() != expected -> SelectionFailure.FINGERPRINT
        else -> null
    }
}
