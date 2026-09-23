package org.autojs.autojs.engine

import java.io.Closeable

/** Link-owned invocation IDs let stop win even before a script has an execution ID. */
internal class AgentInvocationRegistry(private val now: () -> Long) : Closeable {
    private class Entry(val at: Long, var claimed: Boolean = false, var cancelled: Boolean = false, var finished: Boolean = false, var handle: Closeable? = null)
    private val entries = linkedMapOf<String, Entry>()
    private var closed = false
    private fun reserve(id: String): Entry {
        require(id.matches(Regex("[a-f0-9]{8}(-[a-f0-9]{4}){3}-[a-f0-9]{12}")))
        check(!closed)
        entries.entries.removeAll { (_, value) -> value.handle == null && now() - value.at > 310_000 }
        entries[id]?.let { return it }
        require(entries.size < 1024) { "Too many registered invocations" }
        return Entry(now()).also { entries[id] = it }
    }
    @Synchronized fun claim(id: String): Boolean {
        val entry = reserve(id)
        if (entry.claimed || entry.cancelled) return false
        entry.claimed = true
        return true
    }
    fun attach(id: String, handle: Closeable) {
        val cancel = synchronized(this) {
            val entry = entries[id]
            if (closed || entry == null || entry.cancelled || entry.finished) true else { entry.handle = handle; false }
        }
        if (cancel) handle.close()
    }
    @Synchronized fun finish(id: String) { entries[id]?.apply { finished = true; handle = null } }
    fun stop(id: String) {
        val handle = synchronized(this) { reserve(id).let { it.cancelled = true; it.handle.also { _ -> it.handle = null } } }
        handle?.close()
    }
    override fun close() {
        val handles = synchronized(this) { closed = true; entries.values.mapNotNull { it.handle }.also { entries.clear() } }
        handles.forEach { runCatching { it.close() } }
    }
}
