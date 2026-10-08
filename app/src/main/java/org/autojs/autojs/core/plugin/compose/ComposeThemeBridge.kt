package org.autojs.autojs.core.plugin.compose

import android.content.ComponentCallbacks
import android.content.Context
import android.content.res.Configuration
import org.autojs.autojs.theme.ThemeColor
import org.autojs.autojs.theme.ThemeColorManager
import org.autojs.autojs.theme.ThemeColorMutable
import org.autojs.plugin.compose.api.model.ThemeSpec

/** One main-thread bridge per mounted view. ThemeColorManager stores a weak reference. */
class ComposeThemeBridge(context: Context, private val changed: (ThemeSpec) -> Unit) : ThemeColorMutable, ComponentCallbacks, AutoCloseable {
    private val context = context.applicationContext
    private var closed = false
    private var seed = ThemeColorManager.colorPrimary
    private var configuration = Configuration(context.resources.configuration)
    private var previous: ThemeSpec? = null

    init { this.context.registerComponentCallbacks(this); ThemeColorManager.add(this) }
    fun snapshot() = ThemeSpec(seedArgb = seed, dark = configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES)
    override fun setThemeColor(color: ThemeColor) { seed = color.colorPrimary; publish() }
    override fun onConfigurationChanged(newConfig: Configuration) { configuration = Configuration(newConfig); publish() }
    override fun onLowMemory() = Unit
    private fun publish() { if (!closed) snapshot().takeIf { it != previous }?.let { previous = it; changed(it) } }
    override fun close() { if (!closed) { closed = true; context.unregisterComponentCallbacks(this) } }
}
