package org.autojs.plugin.nodejs.api

object NodeJsPluginActions {
    /** Frozen generic plugin-discovery action; Android manifests use the same literal. */
    const val INFO = "org.autojs.plugin.INFO"
    const val RUNTIME = "org.autojs.plugin.nodejs.RUNTIME"
    /** External runtime access for independently signed packaged applications. */
    const val PACKAGED_RUNTIME = "org.autojs.plugin.nodejs.PACKAGED_RUNTIME"
    const val PACKAGED_WAKE = "org.autojs.plugin.nodejs.PACKAGED_WAKE"
    const val PACKAGED_RUNTIME_PERMISSION = "org.autojs.plugin.nodejs.permission.RUNTIME"
}
