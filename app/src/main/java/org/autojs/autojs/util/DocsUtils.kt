package org.autojs.autojs.util

import org.autojs.autojs.pref.Pref
import org.autojs.autojs.util.StringUtils.key
import org.autojs.autojs6.R

object DocsUtils {

    private val docsUrlBody
        get() = Pref.getString(R.string.key_documentation_source, null).let {
            when (it == null || isLocal(it)) {
                true -> "file:///android_asset/docs/"
                else -> "https://docs.autojs6.com"
            }
        }

    @JvmStatic
    fun getUrl(suffix: String) = "${docsUrlBody.replace(Regex("/$"), "")}/${suffix.replace(Regex("^/"), "")}"

    private fun isLocal(source: String) = source == key(R.string.key_documentation_source_local)

}