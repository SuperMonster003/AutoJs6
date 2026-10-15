package org.autojs.plugin.gallery.api

import org.autojs.plugin.common.api.PluginActions

/**
 * Discovery and launch contract of the gallery plugin family (the script object `gallery`). The
 * literals are frozen: the plugin manifest, the host `<queries>` block, the plugin center
 * registration and the session launcher all repeat them.
 */
object GalleryActions {

    /** Generic plugin-discovery action answered by the plugin's `IPluginInfoProvider` service. */
    const val INFO = PluginActions.INFO

    /** Action of the [IGalleryPlugin] service; always paired with [SERVICE_CATEGORY]. */
    const val SERVICE_ACTION = "org.autojs.plugin.GALLERY"

    /** Intent category of the gallery service, so unrelated `org.autojs.plugin.*` services never match. */
    const val SERVICE_CATEGORY = "gallery"

    /** Signature permission every Binder entry point and the session Activity of the plugin require. */
    const val PLUGIN_PERMISSION = "org.autojs.permission.PLUGIN"

    /**
     * Action of the plugin Activity that claims a picker, viewer or editor session. The host starts it
     * with an explicit component and [EXTRA_SESSION_TOKEN] as the only extra; the plugin never starts
     * the Activity from its service (two-step launch, plugin roadmap T17).
     */
    const val SESSION_ACTIVITY_ACTION = "org.autojs.plugin.GALLERY_SESSION_OPEN"

    /** String extra of [SESSION_ACTIVITY_ACTION]: the session token from `IGallerySession.getState`. */
    const val EXTRA_SESSION_TOKEN = "org.autojs.plugin.gallery.extra.SESSION_TOKEN"
}
