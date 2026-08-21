package org.autojs.autojs.core.accessibility.monitor

import java.io.Closeable

class CloseableManager {

    private val mCloseables = linkedSetOf<Closeable>()

    @Synchronized
    fun add(closeable: Closeable) {
        mCloseables += closeable
    }

    fun recycleAll() {
        val closeables = synchronized(this) {
            mCloseables.toList().also { mCloseables.clear() }
        }
        var firstFailure: Throwable? = null
        closeables.forEach { closeable ->
            try {
                closeable.close()
            } catch (failure: Throwable) {
                val priorFailure = firstFailure
                if (priorFailure == null) {
                    firstFailure = failure
                } else if (priorFailure !== failure) {
                    priorFailure.addSuppressed(failure)
                }
            }
        }
        firstFailure?.let { throw it }
    }

    @Synchronized
    fun remove(closeable: Closeable) {
        mCloseables -= closeable
    }

}
