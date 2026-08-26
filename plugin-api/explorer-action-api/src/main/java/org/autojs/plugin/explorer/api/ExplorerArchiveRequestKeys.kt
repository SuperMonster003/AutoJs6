package org.autojs.plugin.explorer.api

/** Host-supplied metadata accompanying the read-only source descriptor. */
object ExplorerArchiveRequestKeys {
    const val DISPLAY_NAME = "displayName"
    const val SIZE = "size"
    const val LAST_MODIFIED = "lastModified"
    /** v11 transient password. Both peers must erase and remove it after the synchronous call. */
    const val PASSWORD = "password"
}
