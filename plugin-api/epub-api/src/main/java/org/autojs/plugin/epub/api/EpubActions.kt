package org.autojs.plugin.epub.api

import org.autojs.plugin.common.api.PluginActions

/**
 * Discovery and launch contract of the EPUB plugin family. The literals are frozen: the plugin
 * manifest, the host `<queries>` block, the plugin center registration and the reader launcher
 * all repeat them.
 */
object EpubActions {

    /** Generic plugin-discovery action answered by the plugin's `IPluginInfoProvider` service. */
    const val INFO = PluginActions.INFO

    /** Action of the [IEpubPlugin] service; always paired with [SERVICE_CATEGORY]. */
    const val SERVICE_ACTION = "org.autojs.plugin.EPUB"

    /** Intent category of the EPUB service, so unrelated `org.autojs.plugin.*` services never match. */
    const val SERVICE_CATEGORY = "epub"

    /** Signature permission every Binder entry point and the reader entry of the plugin require. */
    const val PLUGIN_PERMISSION = "org.autojs.permission.PLUGIN"

    /**
     * Action of the plugin reader Activity that claims a session opened by [IEpubPlugin.openReader].
     * The host starts it with an explicit component and [EXTRA_SESSION_TOKEN] as the only extra;
     * the plugin never starts the Activity from its service (two-step launch, roadmap D12).
     */
    const val READER_ACTIVITY_ACTION = "org.autojs.plugin.EPUB_READER_OPEN"

    /** String extra of [READER_ACTIVITY_ACTION]: the session token from `IEpubReaderSession.getState`. */
    const val EXTRA_SESSION_TOKEN = "org.autojs.plugin.epub.extra.SESSION_TOKEN"
}
