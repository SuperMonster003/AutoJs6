package org.autojs.autojs.ui.edit.editor.loading

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import io.reactivex.Observable
import io.reactivex.ObservableEmitter
import org.autojs.autojs.util.StringUtils
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.Charset
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class EditorFileReadPipeline(
    context: Context,
) {

    private val resolver = context.contentResolver

    fun queryContentLengthOrNull(uri: Uri): Long? {
        return when (uri.scheme?.lowercase()) {
            "file" -> uri.path?.let { File(it).length().coerceAtLeast(0L) }
            "content" -> queryContentSizeOrNull(uri)
            else -> null
        }
    }

    fun readText(uri: Uri): EditorFileReadResult {
        val rawBytes = resolver.openInputStream(uri)?.use { it.readBytes() } ?: ByteArray(0)
        val metadata = detectMetadata(rawBytes)
        val offset = if (metadata.hadBom) {
            StringUtils.bomBytes(metadata.charset).size.coerceAtMost(rawBytes.size)
        } else 0
        val len = (rawBytes.size - offset).coerceAtLeast(0)
        return EditorFileReadResult(
            text = String(rawBytes, offset, len, metadata.charset),
            metadata = metadata,
        )
    }

    fun streamTextChunks(
        uri: Uri,
        cancel: AtomicBoolean,
        streamRef: AtomicReference<InputStream?>,
        onMetadata: (EditorFileReadMetadata) -> Unit,
    ): Observable<String> {
        return Observable.create { emitter ->
            // Emit an early placeholder so the caller can continue its editor-load flow.
            emitter.onNext("")

            val raw = resolver.openInputStream(uri) ?: run {
                emitter.onError(IllegalStateException("Cannot open input stream for $uri"))
                return@create
            }
            streamRef.set(raw)

            try {
                BufferedInputStream(raw).use { input ->
                    if (cancel.get() || emitter.isDisposed) {
                        return@use
                    }

                    val metadata = detectStreamMetadata(input, uri, cancel, emitter) ?: return@use
                    onMetadata(metadata)
                    streamDecodeAndEmit(input, metadata, cancel, emitter)
                }
            } finally {
                streamRef.set(null)
            }
        }
    }

    private fun queryContentSizeOrNull(uri: Uri): Long? {
        var cursor: Cursor? = null
        return try {
            cursor = resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
            if (cursor != null && cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (idx >= 0 && !cursor.isNull(idx)) {
                    cursor.getLong(idx).coerceAtLeast(0L)
                } else null
            } else null
        } finally {
            runCatching { cursor?.close() }
        }
    }

    private fun detectMetadata(bytes: ByteArray): EditorFileReadMetadata {
        val detected = StringUtils.detectCharset(bytes)
        val charset = detected.charsetOrDefault()
        return EditorFileReadMetadata(
            charset = charset,
            charsetConfidence = detected.confidence ?: 0,
            hadBom = StringUtils.hasBom(bytes, charset),
        )
    }

    private fun detectStreamMetadata(
        input: BufferedInputStream,
        uri: Uri,
        cancel: AtomicBoolean,
        emitter: ObservableEmitter<String>,
    ): EditorFileReadMetadata? {
        val sampleLimitBytes = 64 * 1024
        input.mark(sampleLimitBytes + 8)

        val sample = ByteArray(sampleLimitBytes)
        val sampleRead = input.read(sample).coerceAtLeast(0)
        val sampleBytes = if (sampleRead == sample.size) sample else sample.copyOf(sampleRead)
        val metadata = detectMetadata(sampleBytes)

        runCatching { input.reset() }.getOrElse {
            if (!emitter.isDisposed && !cancel.get()) {
                emitter.onError(IllegalStateException("Stream reset failed for $uri"))
            }
            return null
        }

        return metadata
    }

    private fun streamDecodeAndEmit(
        input: BufferedInputStream,
        metadata: EditorFileReadMetadata,
        cancel: AtomicBoolean,
        emitter: ObservableEmitter<String>,
    ) {
        val bomBytes = StringUtils.bomBytes(metadata.charset)
        if (metadata.hadBom && bomBytes.isNotEmpty()) {
            var skipped = 0
            while (skipped < bomBytes.size) {
                if (cancel.get() || emitter.isDisposed) {
                    return
                }
                val r = input.read()
                if (r == -1) break
                skipped++
            }
        }

        val reader = InputStreamReader(input, metadata.charset)
        val buf = CharArray(32 * 1024)
        while (!emitter.isDisposed && !cancel.get()) {
            val n = reader.read(buf)
            if (n <= 0) break
            emitter.onNext(String(buf, 0, n))
        }

        if (!emitter.isDisposed && !cancel.get()) {
            emitter.onComplete()
        }
    }
}

data class EditorFileReadResult(
    val text: String,
    val metadata: EditorFileReadMetadata,
)

data class EditorFileReadMetadata(
    val charset: Charset,
    val charsetConfidence: Int,
    val hadBom: Boolean,
)
