package org.autojs.autojs.engine

import android.os.Bundle
import android.util.Log
import org.autojs.autojs.core.console.ConsoleImpl
import org.autojs.autojs.core.console.ConsoleInputRequest
import org.autojs.plugin.nodejs.api.NodeJsRuntimeContract
import java.util.concurrent.Executors

/** Registers stdin demand with the same console queue used by console.rawInput(). */
internal class NodeRuntimeConsoleInput(
    private val console: ConsoleImpl,
    private val send: (Bundle) -> Boolean,
) : AutoCloseable {
    private val lock = Any()
    private var closed = false
    private var waiting = false
    private var request: ConsoleInputRequest? = null

    fun onState(ready: Boolean): Unit = synchronized(lock) {
        if (closed) return@synchronized
        waiting = ready
        if (!ready) {
            request?.cancel()
            request = null
        } else if (request == null || request?.isDone == true) {
            request = console.requestInput { text ->
                // A console submission may be on the UI thread. Binder stays off that thread.
                sender.execute {
                    synchronized(lock) {
                        if (closed) return@execute
                        val message = Bundle().apply {
                            putString(NodeJsRuntimeContract.KEY_MESSAGE_KIND, NodeJsRuntimeContract.MESSAGE_STDIN)
                            putString(NodeJsRuntimeContract.KEY_MESSAGE_DATA, "$text\n")
                        }
                        val accepted = runCatching { send(message) }
                            .onFailure { Log.w("NodeRuntimeConsoleInput", "stdin delivery failed", it) }
                            .getOrDefault(false)
                        if (!accepted && waiting) {
                            request = null
                            onState(true)
                        }
                    }
                }
            }
        }
    }

    override fun close() = synchronized(lock) {
        closed = true
        request?.cancel()
        request = null
    }

    companion object {
        private val sender = Executors.newSingleThreadExecutor { task ->
            Thread(task, "node-console-input").apply { isDaemon = true }
        }
    }
}
