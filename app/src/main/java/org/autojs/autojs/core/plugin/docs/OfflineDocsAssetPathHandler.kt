package org.autojs.autojs.core.plugin.docs

import android.content.res.AssetManager
import android.webkit.WebResourceResponse
import androidx.webkit.WebViewAssetLoader
import org.autojs.plugin.offlinedocs.api.OfflineDocsPluginContract
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.Locale

/** Serves a validated subset of the trusted plugin's static documentation assets. */
class OfflineDocsAssetPathHandler private constructor(
    private val assetManager: AssetManager,
    private val assetRoot: String,
    private val entryPoint: String,
    private val verifiedAssetPaths: Set<String>,
) : WebViewAssetLoader.PathHandler {

    constructor(plugin: OfflineDocsPluginHost.LoadedPlugin) : this(
        assetManager = plugin.assetManager,
        assetRoot = plugin.candidate.assetRoot,
        entryPoint = plugin.candidate.entryPoint,
        verifiedAssetPaths = plugin.verifiedAssetPaths,
    )

    init {
        require(assetRoot == OfflineDocsPluginContract.ASSET_ROOT)
        require(entryPoint == OfflineDocsPluginContract.ENTRY_POINT)
    }

    override fun handle(path: String): WebResourceResponse {
        val assetPath = OfflineDocsAssetPathPolicy.resolve(path, entryPoint) ?: return notFound()
        if (assetPath !in verifiedAssetPaths) return notFound()
        val stream = try {
            assetManager.open("$assetRoot/$assetPath", AssetManager.ACCESS_STREAMING)
        } catch (_: IOException) {
            return notFound()
        } catch (_: SecurityException) {
            return notFound()
        }
        val mime = OfflineDocsAssetPathPolicy.mimeType(assetPath) ?: run {
            stream.close()
            return notFound()
        }
        val encoding = if (mime.startsWith("text/") || mime == "application/javascript") {
            Charsets.UTF_8.name()
        } else {
            null
        }
        return WebResourceResponse(
            mime,
            encoding,
            200,
            "OK",
            RESPONSE_HEADERS,
            stream,
        )
    }

    private fun notFound(): WebResourceResponse = WebResourceResponse(
        "text/plain",
        Charsets.UTF_8.name(),
        404,
        "Not Found",
        RESPONSE_HEADERS,
        ByteArrayInputStream("Not Found".toByteArray(Charsets.UTF_8)),
    )

    companion object {
        const val DOMAIN = "offline-docs.autojs6.local"
        const val PATH_PREFIX = "/"
        const val BASE_URL = "https://$DOMAIN/"
        const val ENTRY_POINT_URL = "$BASE_URL${OfflineDocsPluginContract.ENTRY_POINT}"

        private val RESPONSE_HEADERS = mapOf(
            "Cache-Control" to "no-store, max-age=0",
            "X-Content-Type-Options" to "nosniff",
        )
    }
}

internal object OfflineDocsAssetPathPolicy {

    private const val MAX_PATH_LENGTH = 1024
    private val allowedExtensions = setOf("html", "css", "js", "png", "woff2")

    fun resolve(path: String, entryPoint: String = OfflineDocsPluginContract.ENTRY_POINT): String? {
        if (path.isEmpty()) return entryPoint
        if (path.length > MAX_PATH_LENGTH || path.startsWith('/')) return null
        if (path.any { it == '\\' || it == '\u0000' || it == '%' || it == '?' || it == '#' || it == ':' }) {
            return null
        }
        if (path.any { it.code < 0x20 || it.code == 0x7f }) return null
        val segments = path.split('/')
        if (segments.any { it.isEmpty() || it == "." || it == ".." }) return null
        val extension = path.substringAfterLast('.', missingDelimiterValue = "").lowercase(Locale.ROOT)
        return path.takeIf { extension in allowedExtensions }
    }

    fun mimeType(path: String): String? = when (path.substringAfterLast('.').lowercase(Locale.ROOT)) {
        "html" -> "text/html"
        "css" -> "text/css"
        "js" -> "application/javascript"
        "png" -> "image/png"
        "woff2" -> "font/woff2"
        else -> null
    }
}
