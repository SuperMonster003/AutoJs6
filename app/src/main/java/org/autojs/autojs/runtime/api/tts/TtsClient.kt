package org.autojs.autojs.runtime.api.tts

import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.ScriptPromiseAdapter
import org.autojs.autojs.runtime.api.modules.ScriptAsyncDispatcher
import org.autojs.autojs.runtime.api.modules.tts.TtsUtterance

/** Values and delivery owned by a scripting client, independent of the platform TTS actor. */
internal interface TtsTask {
    val id: String
    val operation: String
    val text: String?
    val result: Any
    val state: String
    val isDone: Boolean
    val publicValue: Any get() = this
    fun stop(): TtsTask
}

internal interface TtsTaskController {
    val task: TtsTask
    val id: String
    val isDone: Boolean
    fun queued(info: Any?): Boolean
    fun start(info: Any?): Boolean
    fun chunk(info: Any?): Boolean
    fun range(info: Any?): Boolean
    fun synthesis(info: Any?): Boolean
    fun audio(info: Any?): Boolean
    fun complete(value: Any?): Boolean
    fun fail(error: Throwable): Boolean
    fun stop(info: Any?, error: Throwable, cancelled: Boolean): Boolean
    fun cancelSilently(): Boolean
}

internal interface TtsResultDispatcher {
    val isTerminal: Boolean
    fun resolve(adapter: ScriptPromiseAdapter, value: Any?, terminal: () -> Unit)
    fun reject(adapter: ScriptPromiseAdapter, error: Throwable, terminal: () -> Unit)
    fun cancelSilently(terminal: () -> Unit)
}

internal interface TtsClient {
    fun createTask(id: String, operation: String, text: String?, requestStop: (String) -> Unit, onTerminal: () -> Unit): TtsTaskController
    fun createResultDispatcher(): TtsResultDispatcher
}

internal class RhinoTtsClient(private val runtime: ScriptRuntime) : TtsClient {
    override fun createTask(id: String, operation: String, text: String?, requestStop: (String) -> Unit, onTerminal: () -> Unit) =
        TtsUtterance.Controller(runtime, id, operation, text, requestStop, onTerminal)

    override fun createResultDispatcher() = object : TtsResultDispatcher {
        private val dispatcher = ScriptAsyncDispatcher(runtime)
        override val isTerminal get() = dispatcher.isTerminal
        override fun resolve(adapter: ScriptPromiseAdapter, value: Any?, terminal: () -> Unit) { dispatcher.resolve(adapter, value, terminal) }
        override fun reject(adapter: ScriptPromiseAdapter, error: Throwable, terminal: () -> Unit) { dispatcher.reject(adapter, error, terminal) }
        override fun cancelSilently(terminal: () -> Unit) { dispatcher.cancelSilently(terminal) }
    }
}
