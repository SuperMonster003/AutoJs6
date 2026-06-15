package org.autojs.autojs.ui.edit.editor.service

import android.content.Context
import android.net.Uri
import io.reactivex.Observable
import org.autojs.autojs.ui.edit.editor.loading.EditorFileReadMetadata
import org.autojs.autojs.ui.edit.editor.loading.EditorFileReadPipeline
import org.autojs.autojs.ui.edit.editor.loading.EditorFileReadResult
import java.io.InputStream
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference

class EditorFileService(
    context: Context,
) {

    private val fileReadPipeline = EditorFileReadPipeline(context)

    fun queryContentLengthOrNull(uri: Uri): Long? {
        return fileReadPipeline.queryContentLengthOrNull(uri)
    }

    fun readText(uri: Uri): EditorFileReadResult {
        return fileReadPipeline.readText(uri)
    }

    fun streamTextChunks(
        uri: Uri,
        cancel: AtomicBoolean,
        streamRef: AtomicReference<InputStream?>,
        onMetadata: (EditorFileReadMetadata) -> Unit,
    ): Observable<String> {
        return fileReadPipeline.streamTextChunks(
            uri = uri,
            cancel = cancel,
            streamRef = streamRef,
            onMetadata = onMetadata,
        )
    }
}
