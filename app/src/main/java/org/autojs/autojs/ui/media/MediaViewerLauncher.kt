package org.autojs.autojs.ui.media

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.CancellationSignal
import android.provider.OpenableColumns
import androidx.core.content.FileProvider
import org.autojs.autojs.model.explorer.ExplorerItem
import org.autojs.autojs.runtime.api.Mime
import org.autojs.autojs.util.FileUtils
import java.io.File
import java.util.Locale

internal enum class MediaViewerKind {
    IMAGE,
    AUDIO,
    VIDEO,
}

internal data class MediaViewerRequest(
    val uri: Uri,
    val mimeType: String,
    val displayName: String,
    val kind: MediaViewerKind,
)

/**
 * Pure routing policy shared by the exported entry point and unit tests.
 */
internal object MediaViewerPolicy {

    private const val ACTION_VIEW = "android.intent.action.VIEW"
    private const val SUPPORTED_EXTERNAL_SCHEME = "content"

    fun normalizeMimeType(mimeType: String?): String? {
        val normalized = mimeType
            ?.substringBefore(';')
            ?.trim()
            ?.lowercase(Locale.ROOT)
            ?: return null
        val separator = normalized.indexOf('/')
        return normalized.takeIf {
            it.length in 3..255 &&
                separator in 1..<it.lastIndex &&
                separator == it.lastIndexOf('/') &&
                it.none { char -> char.isISOControl() || char.isWhitespace() }
        }
    }

    fun kindForMimeType(mimeType: String?): MediaViewerKind? {
        val normalized = normalizeMimeType(mimeType) ?: return null
        val subtype = normalized.substringAfter('/', "")
        if (subtype.isBlank()) return null
        return when (normalized.substringBefore('/')) {
            "image" -> MediaViewerKind.IMAGE
            "audio" -> MediaViewerKind.AUDIO
            "video" -> MediaViewerKind.VIDEO
            else -> null
        }
    }

    fun resolveExternal(action: String?, scheme: String?, mimeType: String?): MediaViewerKind? {
        if (action != ACTION_VIEW || !scheme.equals(SUPPORTED_EXTERNAL_SCHEME, ignoreCase = true)) {
            return null
        }
        return kindForMimeType(mimeType)
    }

    fun wildcardMimeType(kind: MediaViewerKind): String = when (kind) {
        MediaViewerKind.IMAGE -> Mime.IMAGE_WILDCARD
        MediaViewerKind.AUDIO -> Mime.AUDIO_WILDCARD
        MediaViewerKind.VIDEO -> Mime.VIDEO_WILDCARD
    }
}

/**
 * Central launcher for local explorer files and externally granted content URIs.
 */
object MediaViewerLauncher {

    @JvmStatic
    fun open(context: Context, item: ExplorerItem): Boolean {
        val kind = when {
            item.isImageViewable -> MediaViewerKind.IMAGE
            item.isAudioPlayable -> MediaViewerKind.AUDIO
            item.isVideoPlayable -> MediaViewerKind.VIDEO
            else -> return false
        }
        return openLocalFile(context, File(item.path), kind)
    }

    @JvmStatic
    fun open(context: Context, file: File): Boolean {
        val canonicalFile = runCatching { file.canonicalFile }.getOrNull() ?: return false
        val type = FileUtils.TYPE.determineBy(canonicalFile)
        val kind = when {
            type.isImageViewable() -> MediaViewerKind.IMAGE
            type.isAudioPlayable() -> MediaViewerKind.AUDIO
            type.isVideoPlayable() -> MediaViewerKind.VIDEO
            else -> return false
        }
        return openLocalFile(context, canonicalFile, kind)
    }

    internal fun createExternalRequest(
        context: Context,
        uri: Uri,
        mimeType: String,
        kind: MediaViewerKind,
        cancellationSignal: CancellationSignal,
    ): MediaViewerRequest? {
        val normalizedMimeType = MediaViewerPolicy.normalizeMimeType(mimeType) ?: return null
        if (
            MediaViewerPolicy.resolveExternal(
                Intent.ACTION_VIEW,
                uri.scheme,
                normalizedMimeType,
            ) != kind
        ) {
            return null
        }
        return MediaViewerRequest(
            uri = uri,
            mimeType = normalizedMimeType,
            displayName = resolveDisplayName(context, uri, cancellationSignal),
            kind = kind,
        )
    }

    internal fun openExternal(context: Context, request: MediaViewerRequest): Boolean {
        if (
            MediaViewerPolicy.resolveExternal(
                Intent.ACTION_VIEW,
                request.uri.scheme,
                request.mimeType,
            ) != request.kind
        ) {
            return false
        }
        return launch(context, request, grantReadPermission = true)
    }

    internal fun requestFromIntent(intent: Intent, expectedKind: MediaViewerKind): MediaViewerRequest? {
        val uri = intent.data ?: return null
        val mimeType = MediaViewerPolicy.normalizeMimeType(intent.type) ?: return null
        if (MediaViewerPolicy.kindForMimeType(mimeType) != expectedKind) return null
        if (uri.scheme?.lowercase(Locale.ROOT) !in setOf("content", "file")) return null
        val displayName = intent.getStringExtra(EXTRA_DISPLAY_NAME)
            ?.let(::sanitizeDisplayName)
            ?.takeIf(String::isNotBlank)
            ?: uri.lastPathSegment
                ?.substringAfterLast('/')
                ?.let(::sanitizeDisplayName)
                ?.takeIf(String::isNotBlank)
            ?: ""
        return MediaViewerRequest(uri, mimeType, displayName, expectedKind)
    }

    private fun openLocalFile(
        context: Context,
        file: File,
        kind: MediaViewerKind,
    ): Boolean {
        val canonicalFile = runCatching { file.canonicalFile }.getOrNull()
            ?.takeIf { it.isFile && it.canRead() }
            ?: return false
        val detectedMimeType = Mime.fromFile(canonicalFile.path)
        val mimeType = detectedMimeType
            ?.let(MediaViewerPolicy::normalizeMimeType)
            ?.takeIf { MediaViewerPolicy.kindForMimeType(it) == kind }
            ?: MediaViewerPolicy.wildcardMimeType(kind)
        val uri = runCatching {
            FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                canonicalFile,
            )
        }.getOrNull() ?: return false
        return launch(
            context = context,
            request = MediaViewerRequest(
                uri = uri,
                mimeType = mimeType,
                displayName = canonicalFile.name,
                kind = kind,
            ),
            grantReadPermission = true,
        )
    }

    private fun launch(
        context: Context,
        request: MediaViewerRequest,
        grantReadPermission: Boolean,
    ): Boolean {
        val target = when (request.kind) {
            MediaViewerKind.IMAGE -> ImageViewerActivity::class.java
            MediaViewerKind.AUDIO -> AudioPlayerActivity::class.java
            MediaViewerKind.VIDEO -> VideoPlayerActivity::class.java
        }
        val intent = Intent(context, target).apply {
            action = Intent.ACTION_VIEW
            setDataAndType(request.uri, request.mimeType)
            putExtra(EXTRA_DISPLAY_NAME, sanitizeDisplayName(request.displayName))
            if (request.kind == MediaViewerKind.AUDIO) {
                putExtra(AudioPlayerActivity.EXTRA_START_PLAYBACK, true)
            }
            if (grantReadPermission) addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (context !is Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(intent)
            true
        }.getOrDefault(false)
    }

    private fun resolveDisplayName(
        context: Context,
        uri: Uri,
        cancellationSignal: CancellationSignal,
    ): String {
        val queriedName = if (uri.scheme.equals("content", ignoreCase = true)) {
            runCatching {
                context.contentResolver.query(
                    uri,
                    arrayOf(OpenableColumns.DISPLAY_NAME),
                    null,
                    null,
                    null,
                    cancellationSignal,
                )?.use { cursor ->
                    val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (index >= 0 && cursor.moveToFirst() && !cursor.isNull(index)) {
                        cursor.getString(index)
                    } else {
                        null
                    }
                }
            }.getOrNull()
        } else {
            uri.path?.let(::File)?.name
        }
        return sequenceOf(queriedName, uri.lastPathSegment?.substringAfterLast('/'))
            .filterNotNull()
            .map(::sanitizeDisplayName)
            .firstOrNull(String::isNotBlank)
            .orEmpty()
    }

    internal fun sanitizeDisplayName(name: String): String = name
        .filterNot { char ->
            char.isISOControl() ||
                char.code == 0x061C ||
                char.code in 0x200E..0x200F ||
                char.code in 0x202A..0x202E ||
                char.code in 0x2066..0x2069
        }
        .trim()
        .take(MAX_DISPLAY_NAME_LENGTH)

    internal const val EXTRA_DISPLAY_NAME = "org.autojs.autojs.extra.MEDIA_DISPLAY_NAME"
    private const val MAX_DISPLAY_NAME_LENGTH = 256
}
