package org.autojs.autojs.core.plugin.compose

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration

/** Resources use plugin IDs; the host owns windows, theme and system services (D11). */
class ComposeHostContext(
    base: Context,
    private val plugin: Context,
    private val loader: ClassLoader,
    private val configuration: Configuration? = null,
) : ContextWrapper(base) {
    private var snapshot = Configuration(configuration ?: base.resources.configuration)
    private var resourceContext = plugin.createConfigurationContext(snapshot)
    private fun aligned(): Context {
        val current = configuration ?: baseContext.resources.configuration
        if (snapshot != current) {
            snapshot = Configuration(current)
            resourceContext = plugin.createConfigurationContext(snapshot)
        }
        return resourceContext
    }
    override fun getResources() = aligned().resources
    override fun getAssets() = aligned().assets
    override fun getClassLoader() = loader
    override fun createConfigurationContext(overrideConfiguration: Configuration): Context =
        ComposeHostContext(baseContext.createConfigurationContext(overrideConfiguration), plugin, loader, overrideConfiguration)
}
