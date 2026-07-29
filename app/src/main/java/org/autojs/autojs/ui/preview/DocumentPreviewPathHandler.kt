package org.autojs.autojs.ui.preview

import android.content.res.AssetManager
import android.webkit.WebResourceResponse
import androidx.webkit.WebViewAssetLoader
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.util.Locale

class DocumentPreviewFilePathHandler(
    rootDirectory: File,
) : WebViewAssetLoader.PathHandler {

    private val canonicalRoot = rootDirectory.canonicalFile

    init {
        require(canonicalRoot.isDirectory) { "Preview resource root must be a directory" }
    }

    override fun handle(path: String): WebResourceResponse {
        val resource = DocumentPreviewPathPolicy.resolve(canonicalRoot, path) ?: return notFound()
        val input = try {
            resource.file.inputStream()
        } catch (_: IOException) {
            return notFound()
        } catch (_: SecurityException) {
            return notFound()
        }
        return WebResourceResponse(
            resource.mimeType,
            null,
            200,
            "OK",
            RESPONSE_HEADERS,
            input,
        )
    }
}

class DocumentPreviewAssetPathHandler(
    private val assetManager: AssetManager,
) : WebViewAssetLoader.PathHandler {

    override fun handle(path: String): WebResourceResponse {
        val assetPath = path.takeIf(DocumentPreviewAssetPolicy::isAllowed) ?: return notFound()
        val input = try {
            assetManager.open("$ASSET_ROOT/$assetPath", AssetManager.ACCESS_STREAMING)
        } catch (_: IOException) {
            return notFound()
        } catch (_: SecurityException) {
            return notFound()
        }
        return WebResourceResponse(
            "text/css",
            Charsets.UTF_8.name(),
            200,
            "OK",
            RESPONSE_HEADERS,
            input,
        )
    }

    companion object {
        private const val ASSET_ROOT = "document-preview"
    }
}

internal object DocumentPreviewAssetPolicy {
    private val allowedAssets = setOf(
        "base.css",
        "github-dark.css",
        "github-light.css",
        "html-base.css",
        "paper.css",
        "sepia.css",
    )

    fun isAllowed(path: String): Boolean = path in allowedAssets
}

internal object DocumentPreviewPathPolicy {

    data class Resource(
        val file: File,
        val mimeType: String,
    )

    private const val MAX_PATH_LENGTH = 2048

    fun resolve(rootDirectory: File, path: String): Resource? {
        if (!isSafeRelativePath(path)) return null
        val mimeType = mimeType(path) ?: return null
        val canonicalRoot = runCatching { rootDirectory.canonicalFile }.getOrNull() ?: return null
        val candidate = runCatching { File(canonicalRoot, path).canonicalFile }.getOrNull() ?: return null
        val rootPrefix = canonicalRoot.path.trimEnd(File.separatorChar) + File.separator
        if (!candidate.path.startsWith(rootPrefix) || !candidate.isFile) return null
        return Resource(candidate, mimeType)
    }

    fun isSafeRelativePath(path: String): Boolean {
        if (path.isEmpty() || path.length > MAX_PATH_LENGTH || path.startsWith('/')) return false
        if (path.any { it == '\\' || it == '\u0000' || it == '%' || it == '?' || it == '#' || it == ':' }) {
            return false
        }
        if (path.any { it.code < 0x20 || it.code == 0x7f }) return false
        return path.split('/').none { it.isEmpty() || it == "." || it == ".." }
    }

    fun mimeType(path: String): String? = when (path.substringAfterLast('.').lowercase(Locale.ROOT)) {
        "css" -> "text/css"
        "png" -> "image/png"
        "jpg", "jpeg" -> "image/jpeg"
        "gif" -> "image/gif"
        "webp" -> "image/webp"
        "avif" -> "image/avif"
        "bmp" -> "image/bmp"
        "ico" -> "image/x-icon"
        "svg" -> "image/svg+xml"
        "heic" -> "image/heic"
        "heif" -> "image/heif"
        "woff" -> "font/woff"
        "woff2" -> "font/woff2"
        "ttf" -> "font/ttf"
        "otf" -> "font/otf"
        "mp4", "m4v" -> "video/mp4"
        "webm" -> "video/webm"
        "ogv" -> "video/ogg"
        "mp3" -> "audio/mpeg"
        "m4a" -> "audio/mp4"
        "ogg", "oga" -> "audio/ogg"
        "wav" -> "audio/wav"
        else -> null
    }
}

private val RESPONSE_HEADERS = mapOf(
    "Cache-Control" to "no-store, max-age=0",
    "X-Content-Type-Options" to "nosniff",
)

private fun notFound(): WebResourceResponse = WebResourceResponse(
    "text/plain",
    Charsets.UTF_8.name(),
    404,
    "Not Found",
    RESPONSE_HEADERS,
    ByteArrayInputStream("Not Found".toByteArray(Charsets.UTF_8)),
)
