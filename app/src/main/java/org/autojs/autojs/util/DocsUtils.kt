package org.autojs.autojs.util

import org.autojs.autojs.core.pref.keys.GeneralKeys
import org.autojs.autojs.util.StringUtils.key
import org.autojs.autojs6.R

/** URL and route conversion shared by online documentation and the Offline Docs plugin. */
object DocsUtils {

    const val INDEX_URL_SUFFIX = "index.html"
    const val OFFLINE_DOCS_ORIGIN = "https://offline-docs.autojs6.local"
    const val ONLINE_DOCS_ORIGIN = "https://docs.autojs6.com"

    private const val LEGACY_LOCAL_DOCS_URL_BODY = "file:///android_asset/docs/"

    enum class Source {
        OFFLINE,
        ONLINE,
    }

    @JvmStatic
    val currentSource: Source
        get() = GeneralKeys.DOCUMENTATION_SOURCE.storedKey().let { preference ->
            if (preference == null || preference == key(R.string.key_documentation_source_local)) {
                Source.OFFLINE
            } else {
                Source.ONLINE
            }
        }

    @JvmStatic
    fun isOfflineSourceSelected() = currentSource == Source.OFFLINE

    @JvmStatic
    fun getUrl(suffix: String) = getUrl(currentSource, suffix)

    @JvmStatic
    fun getUrl(source: Source, suffix: String): String {
        val origin = when (source) {
            Source.OFFLINE -> OFFLINE_DOCS_ORIGIN
            Source.ONLINE -> ONLINE_DOCS_ORIGIN
        }
        return "${origin.trimEnd('/')}/${suffix.trimStart('/').ifBlank { INDEX_URL_SUFFIX }}"
    }

    @JvmStatic
    fun getOfflineUrl(suffix: String) = getUrl(Source.OFFLINE, suffix)

    @JvmStatic
    fun getOnlineUrl(suffix: String) = getUrl(Source.ONLINE, suffix)

    @JvmStatic
    fun getUrlByCurrentSource(url: String?) = getUrl(extractUrlSuffix(url) ?: INDEX_URL_SUFFIX)

    @JvmStatic
    fun isDocumentationSourcePreferenceKey(changedKey: String?) =
        changedKey == GeneralKeys.DOCUMENTATION_SOURCE.name

    @JvmStatic
    fun extractUrlSuffix(url: String?): String? {
        val normalizedUrl = url?.trim()?.takeIf(String::isNotEmpty) ?: return null
        return extractUrlSuffixByBase(normalizedUrl, OFFLINE_DOCS_ORIGIN)
            ?: extractUrlSuffixByBase(normalizedUrl, LEGACY_LOCAL_DOCS_URL_BODY)
            ?: extractUrlSuffixByBase(normalizedUrl, ONLINE_DOCS_ORIGIN)
    }

    @JvmStatic
    fun isOfflineDocsUrl(url: String?): Boolean {
        val normalizedUrl = url?.trim() ?: return false
        return normalizedUrl == OFFLINE_DOCS_ORIGIN || normalizedUrl.startsWith("$OFFLINE_DOCS_ORIGIN/")
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

        val pageEnd = normalizedRoute.indexOfAny(charArrayOf('?', '#')).takeIf { it >= 0 }
            ?: normalizedRoute.length
        val page = normalizedRoute.take(pageEnd).takeIf { it.isNotBlank() && it != "index" }
            ?: INDEX_URL_SUFFIX
        val tail = normalizedRoute.substring(pageEnd)
        val pageWithExtension = when {
            page.endsWith(".html", ignoreCase = true) -> page
            else -> "$page.html"
        }
        return pageWithExtension + tail
    }
}
