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

    /**
     * Holds every requested document write lock in stable identity order.
     *
     * Multi-file editor transactions use this overload so an ordinary save in another editor
     * cannot interleave between per-file freshness checks and publication. Duplicate identities
     * are collapsed, and the global ordering prevents two overlapping transactions deadlocking.
     */
    fun <T> withWriteAccess(uris: Collection<Uri>, action: () -> T): T {
        val locks = uris.asSequence()
            .map(Uri::editorDocumentIdentity)
            .distinct()
            .sorted()
            .map { key -> uriLocks.computeIfAbsent(key) { ReentrantReadWriteLock(true) } }
            .toList()
        return withWriteLocks(locks, index = 0, action)
    }

    private fun <T> withWriteLocks(
        locks: List<ReentrantReadWriteLock>,
        index: Int,
        action: () -> T,
    ): T {
        if (index == locks.size) return action()
        return locks[index].write {
            withWriteLocks(locks, index + 1, action)
        }
    }
}
