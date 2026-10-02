package org.autojs.autojs.core.plugin.compose

import android.content.Context
import android.content.ContextWrapper
import android.content.res.Configuration

/** P0 fixture: resources use plugin IDs; the Activity owns windows, theme and system services. */
class ComposeHostContext(
    base: Context,
    private val plugin: Context,
    private val loader: ClassLoader,
    configuration: Configuration = base.resources.configuration,
) : ContextWrapper(base) {
    private val resourceContext = plugin.createConfigurationContext(Configuration(configuration))
    override fun getResources() = resourceContext.resources
    override fun getAssets() = resourceContext.assets
    override fun getClassLoader() = loader
    override fun createConfigurationContext(overrideConfiguration: Configuration): Context =
        ComposeHostContext(baseContext.createConfigurationContext(overrideConfiguration), plugin, loader, overrideConfiguration)
}
