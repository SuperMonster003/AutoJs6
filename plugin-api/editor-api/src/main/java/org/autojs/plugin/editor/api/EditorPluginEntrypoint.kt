package org.autojs.plugin.editor.api

import android.content.Context

/**
 * Implemented by the plugin entrypoint named by [EditorPluginContract.META_DATA_ENTRYPOINT].
 *
 * The plugin must package this API as `compileOnly`. The host supplies the API classloader as
 * the parent of the plugin [ClassLoader], so implementations and callers share these types.
 */
interface EditorPluginEntrypoint {

    val contractVersion: Int

    fun createSession(
        hostContext: Context,
        pluginContext: Context,
        config: EditorPluginSessionConfig,
        callback: EditorPluginCallback,
    ): EditorPluginSession
}
