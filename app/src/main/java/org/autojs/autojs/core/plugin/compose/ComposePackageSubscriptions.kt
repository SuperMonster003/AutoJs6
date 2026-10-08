package org.autojs.autojs.core.plugin.compose

import java.io.Closeable
import org.autojs.plugin.compose.api.ComposeUiErrorCodes

/** Identity epochs, not broadcast counts: a late replacement broadcast cannot kill a new session. */
internal class ComposePackageSubscriptions : Closeable {
    internal class Watch(val identity: ComposeUiPluginLoader.Package, val invalidated: (Throwable) -> Unit)
    class Snapshot internal constructor(internal val watches: Set<Watch>)
    private val watches = LinkedHashSet<Watch>()
    private var closed = false

    @Synchronized fun add(identity: ComposeUiPluginLoader.Package, invalidated: (Throwable) -> Unit): Closeable {
        check(!closed)
        val watch = Watch(identity, invalidated)
        watches += watch
        return Closeable { synchronized(this) { watches.remove(watch) } }
    }

    @Synchronized fun snapshot() = Snapshot(watches.toSet())

    fun changed(installed: ComposeUiPluginLoader.Package?, gateFailure: ComposeUiError? = null,
        observed: Snapshot = snapshot()) {
        val invalidated = synchronized(this) {
            if (closed) return
            watches.filter { it in observed.watches }.mapNotNull { watch ->
                val failure = if (watch.identity != installed) ComposeUiError(ComposeUiErrorCodes.PLUGIN_UNAVAILABLE) else gateFailure
                failure?.let { watch to it }
            }.also { changes -> watches.removeAll(changes.map { it.first }.toSet()) }
        }
        // Callers deliver into reserved core control channels, never synchronous script functions.
        invalidated.forEach { (watch, failure) -> watch.invalidated(failure) }
    }

    @Synchronized override fun close() { closed = true; watches.clear() }
}
