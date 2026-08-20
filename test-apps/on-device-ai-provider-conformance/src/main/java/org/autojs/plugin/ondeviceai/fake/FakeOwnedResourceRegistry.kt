package org.autojs.plugin.ondeviceai.fake

import java.io.Closeable

class FakeOwnedResourceRegistry<T : Closeable> : Closeable {
    private val resources = linkedSetOf<T>()
    private var closed = false

    @Synchronized
    fun register(resource: T) {
        check(!closed) { "Owned-resource registry is closed" }
        check(resources.add(resource)) { "Owned resource is already registered" }
    }

    fun release(resource: T) {
        val shouldClose = synchronized(this) { resources.remove(resource) }
        if (shouldClose) closeQuietly(resource)
    }

    override fun close() {
        val pending = synchronized(this) {
            if (closed) return
            closed = true
            resources.toList().also { resources.clear() }
        }
        pending.forEach(::closeQuietly)
    }

    private fun closeQuietly(resource: T) {
        runCatching { resource.close() }
    }
}
