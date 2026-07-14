package org.autojs.autojs.util

import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.util.StringUtils.key
import org.autojs.autojs6.R

object DocsUtils {

    const val INDEX_URL_SUFFIX = "index.html"

    private const val LOCAL_DOCS_URL_BODY = "file:///android_asset/docs/"
    private const val ONLINE_DOCS_URL_BODY = "https://docs.autojs6.com"

    private val docsUrlBody
        get() = Pref.getStringOrNull(R.string.key_documentation_source).let {
            when (it == null || isLocal(it)) {
                true -> LOCAL_DOCS_URL_BODY
                else -> ONLINE_DOCS_URL_BODY
            }
        }

    @JvmStatic
    fun getUrl(suffix: String) = "${docsUrlBody.replace(Regex("/$"), "")}/${suffix.replace(Regex("^/"), "")}"

    @JvmStatic
    fun getUrlByCurrentSource(url: String?) = getUrl(extractUrlSuffix(url) ?: INDEX_URL_SUFFIX)

    @JvmStatic
    fun isDocumentationSourcePreferenceKey(changedKey: String?) = changedKey == key(R.string.key_documentation_source)

    private fun isLocal(source: String) = source == key(R.string.key_documentation_source_local)

    private fun extractUrlSuffix(url: String?): String? {
        val normalizedUrl = url?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return extractUrlSuffixByBase(normalizedUrl, LOCAL_DOCS_URL_BODY)
            ?: extractUrlSuffixByBase(normalizedUrl, ONLINE_DOCS_URL_BODY)
    }

    private fun extractUrlSuffixByBase(url: String, baseUrl: String): String? {
        val normalizedBaseUrl = baseUrl.trimEnd('/')
        if (url == normalizedBaseUrl || url == "$normalizedBaseUrl/") {
            return INDEX_URL_SUFFIX
        }

        val suffix = when {
            url.startsWith("$normalizedBaseUrl/") -> url.removePrefix("$normalizedBaseUrl/")
            url.startsWith("$normalizedBaseUrl#") -> url.removePrefix(normalizedBaseUrl)
            else -> return null
        }
        return when {
            suffix.isBlank() -> INDEX_URL_SUFFIX
            suffix.startsWith("#/") -> hashRouteToUrlSuffix(suffix.removePrefix("#/"))
            suffix.startsWith("$INDEX_URL_SUFFIX#/") -> hashRouteToUrlSuffix(suffix.removePrefix("$INDEX_URL_SUFFIX#/"))
            else -> suffix
        }
    }

    private fun hashRouteToUrlSuffix(route: String): String {
        val normalizedRoute = route.trimStart('/')
        if (normalizedRoute.isBlank()) {
            return INDEX_URL_SUFFIX
        }

        val pageEnd = normalizedRoute.indexOfAny(charArrayOf('?', '#')).takeIf { it >= 0 } ?: normalizedRoute.length
        val page = normalizedRoute.take(pageEnd).takeIf { it.isNotBlank() && it != "index" } ?: INDEX_URL_SUFFIX
        val tail = normalizedRoute.substring(pageEnd)
        val pageWithExtension = when {
            page.endsWith(".html", ignoreCase = true) -> page
            else -> "$page.html"
        }
        return pageWithExtension + tail
    }

}
