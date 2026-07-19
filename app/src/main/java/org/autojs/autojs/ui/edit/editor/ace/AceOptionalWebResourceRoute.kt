package org.autojs.autojs.ui.edit.editor.ace

/** Browser-generated resources that are safe to answer without widening ACE's URL allowlist. */
internal object AceOptionalWebResourceRoute {

    const val FAVICON_URL = "https://appassets.androidplatform.net/favicon.ico"

    fun matches(url: String?): Boolean = url == FAVICON_URL
}
