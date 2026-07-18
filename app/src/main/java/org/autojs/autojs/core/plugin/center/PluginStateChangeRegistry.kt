package org.autojs.autojs.core.plugin.center

import java.util.concurrent.CopyOnWriteArraySet

/**
 * Process-local invalidation bus for consumers that cache plugin selection.
 *
 * PackageManager changes are observed separately by each consumer. This bus
 * covers host-owned state such as enablement and signer authorization, whose
 * SharedPreferences updates do not produce Android package broadcasts.
 */
object PluginStateChangeRegistry {

    private val listeners = CopyOnWriteArraySet<(String) -> Unit>()

    fun addListener(listener: (String) -> Unit) {
        listeners += listener
    }

    fun removeListener(listener: (String) -> Unit) {
        listeners -= listener
    }

    internal fun notifyChanged(packageName: String) {
        if (packageName.isBlank()) return
        listeners.forEach { listener -> listener(packageName) }
    }
}
