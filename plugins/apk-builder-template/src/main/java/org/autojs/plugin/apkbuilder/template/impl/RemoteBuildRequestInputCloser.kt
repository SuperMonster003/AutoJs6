package org.autojs.plugin.apkbuilder.template.impl

import java.io.Closeable
import java.util.Collections
import java.util.IdentityHashMap
import java.util.concurrent.atomic.AtomicBoolean

internal class RemoteBuildRequestInputCloser(
    closeables: List<Closeable?>,
    private val onCloseFailure: (Throwable) -> Unit = {},
) : Closeable {

    private val closed = AtomicBoolean(false)
    private val closeables = Collections.newSetFromMap(IdentityHashMap<Closeable, Boolean>()).apply {
        closeables.filterNotNull().forEach(::add)
    }.toList()

    override fun close() {
        if (!closed.compareAndSet(false, true)) {
            return
        }
        closeables.forEach { closeable ->
            runCatching(closeable::close).onFailure(onCloseFailure)
        }
    }
}
