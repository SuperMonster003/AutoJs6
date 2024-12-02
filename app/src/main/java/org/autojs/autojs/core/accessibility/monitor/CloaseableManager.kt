package org.autojs.autojs.core.accessibility.monitor

import java.io.Closeable

class CloseableManager {

    private val mCloseables = hashSetOf<Closeable>()

    fun add(closeable: Closeable) {
        mCloseables.add(closeable)
    }

    fun recycleAll() {
        val iterator = mCloseables.iterator()
        while (iterator.hasNext()) {
            iterator.next().close()
        }
        mCloseables.clear()
    }

    fun remove(closeable: Closeable) {
        mCloseables.remove(closeable)
    }

}
