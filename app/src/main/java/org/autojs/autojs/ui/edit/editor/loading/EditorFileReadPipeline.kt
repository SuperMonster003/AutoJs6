package org.autojs.autojs.ui.edit.editor.loading

import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import io.reactivex.Observable
import io.reactivex.ObservableEmitter
import org.autojs.autojs.ui.edit.editor.encoding.EditorEncodingCodec
import org.autojs.autojs.ui.edit.editor.encoding.EditorEncodingStreamDecoder
import org.autojs.autojs.ui.edit.editor.service.EditorFileIoCoordinator
import org.autojs.autojs.util.StringUtils
import java.io.BufferedInputStream
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.nio.charset.Charset
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class EditorFileReadPipeline(
    context: Context,
) {

    private val resolver = context.contentResolver

    fun queryContentLengthOrNull(uri: Uri): Long? {
        return EditorFileIoCoordinator.withReadAccess(uri) {
            when (uri.scheme?.lowercase()) {
                "file" -> uri.path?.let { File(it).length().coerceAtLeast(0L) }
                "content" -> queryContentSizeOrNull(uri)
                else -> null
            }
        }
    }

    fun queryDisplayNameOrNull(uri: Uri): String? {
        return EditorFileIoCoordinator.withReadAccess(uri) {
            when (uri.scheme?.lowercase()) {
                "file" -> uri.path?.let(::File)?.name
                "content" -> queryContentDisplayNameOrNull(uri)
                else -> uri.lastPathSegment
            }?.takeIf(String::isNotBlank)
        }
    }

    fun readText(uri: Uri, charsetOverride: Charset? = null): EditorFileReadResult {
        return EditorFileIoCoordinator.withReadAccess(uri) {
            val rawBytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                ?: throw IOException("Cannot open input stream for $uri")
            val metadata = detectMetadata(rawBytes, charsetOverride)
            val offset = if (metadata.hadBom) {
                StringUtils.bomBytes(metadata.charset).size.coerceAtMost(rawBytes.size)
            } else 0
            val len = (rawBytes.size - offset).coerceAtLeast(0)
            val payload = rawBytes.copyOfRange(offset, offset + len)
            val strictText = EditorEncodingCodec.decodeStrictOrNull(payload, metadata.charset)
            EditorFileReadResult(
                text = strictText ?: EditorEncodingCodec.decodeReplacing(payload, metadata.charset),
                metadata = metadata.copy(
                    hadDecodingErrors = strictText == null,
                    decodingErrorsWereAccepted = strictText == null && charsetOverride != null,
                ),
            )
        }
    }

    fun streamTextChunks(
        uri: Uri,
        cancel: AtomicBoolean,
        streamRef: AtomicReference<InputStream?>,
        onMetadata: (EditorFileReadMetadata) -> Unit,
        charsetOverride: Charset? = null,
    ): Observable<String> {
        return Observable.create { emitter ->
            EditorFileIoCoordinator.withReadAccess(uri) {
                // Emit an early placeholder so the caller can continue its editor-load flow.
                emitter.onNext("")

                val raw = resolver.openInputStream(uri) ?: run {
                    emitter.onError(IllegalStateException("Cannot open input stream for $uri"))
                    return@withReadAccess
                }
                streamRef.set(raw)

                try {
                    BufferedInputStream(raw).use { input ->
                        if (cancel.get() || emitter.isDisposed) {
                            return@use
                        }

                        val metadata = detectStreamMetadata(input, uri, cancel, emitter, charsetOverride) ?: return@use
                        onMetadata(metadata)
                        streamDecodeAndEmit(
                            input = input,
                            metadata = metadata,
                            cancel = cancel,
                            emitter = emitter,
                            decodingErrorsWereAccepted = charsetOverride != null,
                            onMetadata = onMetadata,
                        )
                    }
                } finally {
                    streamRef.set(null)
                }
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

    private fun queryContentDisplayNameOrNull(uri: Uri): String? {
        var cursor: Cursor? = null
        return try {
            cursor = resolver.query(
                uri,
                arrayOf(OpenableColumns.DISPLAY_NAME),
                null,
                null,
                null,
            )
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0 && !cursor.isNull(index)) {
                    cursor.getString(index)
                } else null
            } else null
        } finally {
            runCatching { cursor?.close() }
        }
    }

    private fun detectMetadata(
        bytes: ByteArray,
        charsetOverride: Charset? = null,
        endOfInput: Boolean = true,
    ): EditorFileReadMetadata {
        if (charsetOverride != null) {
            return EditorFileReadMetadata(
                charset = charsetOverride,
                charsetConfidence = 100,
                hadBom = StringUtils.hasBom(bytes, charsetOverride),
            )
        }
        val detected = StringUtils.detectEncoding(bytes, endOfInput)
        val charset = detected.charset
        return EditorFileReadMetadata(
            charset = charset,
            charsetConfidence = detected.confidence,
            hadBom = StringUtils.hasBom(bytes, charset),
        )
    }

    private fun detectStreamMetadata(
        input: BufferedInputStream,
        uri: Uri,
        cancel: AtomicBoolean,
        emitter: ObservableEmitter<String>,
        charsetOverride: Charset?,
    ): EditorFileReadMetadata? {
        val sampleLimitBytes = 256 * 1024
        input.mark(sampleLimitBytes + 16)

        val sample = ByteArray(sampleLimitBytes + 1)
        var sampleRead = 0
        while (sampleRead < sample.size && !cancel.get() && !emitter.isDisposed) {
            val read = input.read(sample, sampleRead, sample.size - sampleRead)
            if (read < 0) break
            if (read == 0) {
                val oneByte = input.read()
                if (oneByte < 0) break
                sample[sampleRead++] = oneByte.toByte()
                continue
            }
            sampleRead += read
        }
        val truncated = sampleRead > sampleLimitBytes
        val sampleBytes = sample.copyOf(sampleRead.coerceAtMost(sampleLimitBytes))
        val metadata = detectMetadata(sampleBytes, charsetOverride, endOfInput = !truncated)

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
        decodingErrorsWereAccepted: Boolean,
        onMetadata: (EditorFileReadMetadata) -> Unit,
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

        val result = EditorEncodingStreamDecoder.decode(
            input = input,
            charset = metadata.charset,
            isCancelled = { emitter.isDisposed || cancel.get() },
            onDecodedText = emitter::onNext,
        )
        if (!result.completed) return
        if (result.hadDecodingErrors) {
            onMetadata(
                metadata.copy(
                    hadDecodingErrors = true,
                    decodingErrorsWereAccepted = decodingErrorsWereAccepted,
                ),
            )
        }
        emitter.onComplete()
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
    val hadDecodingErrors: Boolean = false,
    val decodingErrorsWereAccepted: Boolean = false,
)
