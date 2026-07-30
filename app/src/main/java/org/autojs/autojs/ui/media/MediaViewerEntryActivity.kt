package org.autojs.autojs.ui.media

import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.CancellationSignal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Exported, data-only entry point for media opened by another app.
 *
 * Arbitrary extras and write/persistable/prefix grants are deliberately not forwarded.
 */
class MediaViewerEntryActivity : Activity() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val cancellationSignal = CancellationSignal()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (savedInstanceState != null) {
            finish()
            return
        }

        val incoming = intent
        if (incoming.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION == 0) {
            finish()
            return
        }
        val uri = incoming.data
        val mimeType = MediaViewerPolicy.normalizeMimeType(incoming.type)
        val kind = if (uri != null) {
            MediaViewerPolicy.resolveExternal(incoming.action, uri.scheme, mimeType)
        } else {
            null
        }
        if (uri == null || mimeType == null || kind == null) {
            finish()
            return
        }

        val appContext = applicationContext
        val contentResolver = appContext.contentResolver
        val signal = cancellationSignal
        val resolution = scope.async(Dispatchers.IO) {
            resolveRequest(
                context = appContext,
                contentResolver = contentResolver,
                uri = uri,
                mimeType = mimeType,
                kind = kind,
                cancellationSignal = signal,
            )
        }
        scope.launch {
            val request = withTimeoutOrNull(URI_RESOLUTION_TIMEOUT_MILLIS) {
                resolution.await()
            }
            if (request != null) {
                MediaViewerLauncher.openExternal(this@MediaViewerEntryActivity, request)
            } else {
                cancellationSignal.cancel()
                resolution.cancel()
            }
            finish()
        }
    }

    override fun onDestroy() {
        cancellationSignal.cancel()
        scope.cancel()
        super.onDestroy()
    }

    private companion object {
        const val URI_RESOLUTION_TIMEOUT_MILLIS = 15_000L
    }
}

private fun resolveRequest(
    context: Context,
    contentResolver: ContentResolver,
    uri: Uri,
    mimeType: String,
    kind: MediaViewerKind,
    cancellationSignal: CancellationSignal,
): MediaViewerRequest? {
    val readable = runCatching {
        contentResolver.openFileDescriptor(uri, "r", cancellationSignal)?.use { true } ?: false
    }.getOrDefault(false)
    if (!readable) return null

    val resolverMimeType = runCatching { contentResolver.getType(uri) }.getOrNull()
    if (resolverMimeType != null && MediaViewerPolicy.kindForMimeType(resolverMimeType) != kind) {
        return null
    }
    return MediaViewerLauncher.createExternalRequest(
        context = context,
        uri = uri,
        mimeType = mimeType,
        kind = kind,
        cancellationSignal = cancellationSignal,
    )
}
