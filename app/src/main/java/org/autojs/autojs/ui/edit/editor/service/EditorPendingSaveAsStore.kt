package org.autojs.autojs.ui.edit.editor.service

import android.net.Uri
import android.os.Handler
import android.os.Looper
import java.io.Closeable
import java.io.File
import java.util.LinkedHashMap
import java.util.UUID

data class EditorPendingSaveAsRecord(
    val sessionId: String,
    val operationId: String,
    val sourceUri: Uri,
    val destination: File,
    val encoding: EditorFileSaveEncoding,
    val savedByteCount: Long,
    val savedSha256: String,
    val completedAtMillis: Long = System.currentTimeMillis(),
)

/** The text is transient and is never retained by [EditorPendingSaveAsStore]. */
data class EditorPendingSaveAsResult(
    val record: EditorPendingSaveAsRecord,
    val savedText: String,
)

class EditorSaveAsOperationToken internal constructor(
    val sessionId: String,
    val operationId: String,
)

class EditorPendingSaveAsRegistration internal constructor(
    private val closeAction: () -> Unit,
) : Closeable {

    private var closed = false

    override fun close() {
        if (closed) return
        closed = true
        closeAction()
    }
}

sealed class EditorPendingSaveAsObservation {
    object None : EditorPendingSaveAsObservation()

    data class Completed(
        val record: EditorPendingSaveAsRecord,
    ) : EditorPendingSaveAsObservation()

    data class Waiting(
        val registration: EditorPendingSaveAsRegistration,
    ) : EditorPendingSaveAsObservation()
}

/**
 * Bridges an in-flight Save As across Activity recreation.
 *
 * Entries are keyed by a stable editor-session id, rather than by source URI, so two editor
 * windows for the same file cannot consume each other's result. Completed entries contain only
 * small metadata; the potentially large editor text is delivered directly to the live view.
 */
object EditorPendingSaveAsStore {

    private const val COMPLETED_TTL_MILLIS = 15L * 60L * 1000L
    private const val MAX_COMPLETED_ENTRIES = 32

    private data class PendingEntry(
        val token: EditorSaveAsOperationToken,
        val sourceUri: Uri,
        val startedAtMillis: Long,
        val listeners: LinkedHashMap<Long, (EditorPendingSaveAsRecord?) -> Unit> = LinkedHashMap(),
    )

    private sealed class Entry {
        data class Pending(val value: PendingEntry) : Entry()
        data class Completed(val record: EditorPendingSaveAsRecord) : Entry()
    }

    private val lock = Any()
    private val entriesBySession = LinkedHashMap<String, Entry>()
    private val mainHandler = Handler(Looper.getMainLooper())
    private var nextListenerId = 1L

    fun begin(sessionId: String, sourceUri: Uri): EditorSaveAsOperationToken {
        require(sessionId.isNotBlank())
        val token = EditorSaveAsOperationToken(sessionId, UUID.randomUUID().toString())
        val replacedListeners = synchronized(lock) {
            purgeCompletedLocked()
            val replaced = entriesBySession.put(
                sessionId,
                Entry.Pending(
                    PendingEntry(
                        token = token,
                        sourceUri = sourceUri,
                        startedAtMillis = System.currentTimeMillis(),
                    ),
                ),
            )
            (replaced as? Entry.Pending)?.value?.listeners?.values?.toList().orEmpty()
        }
        dispatch(replacedListeners, null)
        return token
    }

    fun complete(
        token: EditorSaveAsOperationToken,
        destination: File,
        encoding: EditorFileSaveEncoding,
        savedByteCount: Long,
        savedSha256: String,
    ): EditorPendingSaveAsRecord? {
        var listeners: List<(EditorPendingSaveAsRecord?) -> Unit> = emptyList()
        val record = synchronized(lock) {
            val pending = (entriesBySession[token.sessionId] as? Entry.Pending)?.value
                ?: return@synchronized null
            if (pending.token != token) return@synchronized null
            EditorPendingSaveAsRecord(
                sessionId = token.sessionId,
                operationId = token.operationId,
                sourceUri = pending.sourceUri,
                destination = destination,
                encoding = encoding,
                savedByteCount = savedByteCount,
                savedSha256 = savedSha256,
            ).also {
                listeners = pending.listeners.values.toList()
                entriesBySession[token.sessionId] = Entry.Completed(it)
                trimCompletedLocked()
            }
        }
        if (record != null) {
            dispatch(listeners, record)
        }
        return record
    }

    fun cancel(token: EditorSaveAsOperationToken) {
        val listeners = synchronized(lock) {
            val pending = (entriesBySession[token.sessionId] as? Entry.Pending)?.value
                ?: return@synchronized emptyList()
            if (pending.token != token) return@synchronized emptyList()
            entriesBySession.remove(token.sessionId)
            pending.listeners.values.toList()
        }
        dispatch(listeners, null)
    }

    fun peekCompleted(sessionId: String): EditorPendingSaveAsRecord? = synchronized(lock) {
        purgeCompletedLocked()
        val completed = entriesBySession[sessionId] as? Entry.Completed ?: return@synchronized null
        completed.record
    }

    fun consumeOrObserve(
        sessionId: String,
        listener: (EditorPendingSaveAsRecord?) -> Unit,
    ): EditorPendingSaveAsObservation = synchronized(lock) {
        purgeCompletedLocked()
        when (val entry = entriesBySession[sessionId]) {
            null -> EditorPendingSaveAsObservation.None
            is Entry.Completed -> EditorPendingSaveAsObservation.Completed(entry.record)
            is Entry.Pending -> {
                val listenerId = nextListenerId++
                val operationId = entry.value.token.operationId
                entry.value.listeners[listenerId] = listener
                EditorPendingSaveAsObservation.Waiting(
                    EditorPendingSaveAsRegistration {
                        synchronized(lock) {
                            val current = entriesBySession[sessionId] as? Entry.Pending
                            if (current?.value?.token?.operationId == operationId) {
                                current.value.listeners.remove(listenerId)
                            }
                        }
                    },
                )
            }
        }
    }

    fun removeIfSame(record: EditorPendingSaveAsRecord) {
        synchronized(lock) {
            val current = entriesBySession[record.sessionId] as? Entry.Completed ?: return
            if (current.record.operationId == record.operationId) {
                entriesBySession.remove(record.sessionId)
            }
        }
    }

    private fun dispatch(
        listeners: List<(EditorPendingSaveAsRecord?) -> Unit>,
        record: EditorPendingSaveAsRecord?,
    ) {
        listeners.forEach { listener ->
            if (Looper.myLooper() == Looper.getMainLooper()) {
                listener(record)
            } else {
                mainHandler.post { listener(record) }
            }
        }
    }

    private fun purgeCompletedLocked() {
        val cutoff = System.currentTimeMillis() - COMPLETED_TTL_MILLIS
        entriesBySession.entries.removeAll { (_, entry) ->
            entry is Entry.Completed && entry.record.completedAtMillis < cutoff
        }
    }

    private fun trimCompletedLocked() {
        val completedKeys = entriesBySession.entries
            .asSequence()
            .filter { it.value is Entry.Completed }
            .map { it.key }
            .toList()
        val removeCount = (completedKeys.size - MAX_COMPLETED_ENTRIES).coerceAtLeast(0)
        completedKeys.take(removeCount).forEach(entriesBySession::remove)
    }
}
