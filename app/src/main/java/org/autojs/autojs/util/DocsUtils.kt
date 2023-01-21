package org.autojs.autojs.util

import org.autojs.autojs.pref.Pref
import org.autojs.autojs.util.StringUtils.key
import org.autojs.autojs6.R

object DocsUtils {

    fun getUrl() = Pref.getString(R.string.key_documentation_source, null).let {
        when (it == null || isLocal(it)) {
            true -> "file:///android_asset/docs/"
            else -> "https://SuperMonster003.github.io/AutoJs6-Documentation/"
        }
    }

    @JvmStatic
    fun getUrl(suffix: String) = getUrl() + suffix.replace(Regex("^/"), "")

    private fun isLocal(source: String) = source == key(R.string.key_documentation_source_local)

}