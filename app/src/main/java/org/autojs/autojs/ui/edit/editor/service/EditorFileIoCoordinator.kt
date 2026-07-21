package org.autojs.autojs.ui.edit.editor.service

import android.net.Uri
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * Serializes reads and writes of the same document inside this app process.
 *
 * ContentResolver's `rwt` mode may expose a partially truncated document to a concurrent reader.
 * Encoding Reload, Convert, ordinary Save, and a second editor instance therefore share one URI
 * lock. Stream reads hold the lock until their synchronous source loop exits.
 */
object EditorFileIoCoordinator {

    private val uriLocks = ConcurrentHashMap<String, ReentrantReadWriteLock>()

    fun <T> withReadAccess(uri: Uri, action: () -> T): T {
        val key = uri.editorDocumentIdentity()
        val lock = uriLocks.computeIfAbsent(key) { ReentrantReadWriteLock(true) }
        return lock.read(action)
    }

    fun <T> withWriteAccess(uri: Uri, action: () -> T): T {
        val key = uri.editorDocumentIdentity()
        val lock = uriLocks.computeIfAbsent(key) { ReentrantReadWriteLock(true) }
        return lock.write(action)
    }
}
