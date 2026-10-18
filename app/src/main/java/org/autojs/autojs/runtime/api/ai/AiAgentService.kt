package org.autojs.autojs.runtime.api.ai

import android.os.Environment
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import org.autojs.autojs.core.plugin.agent.ThreeStoveAgentLinkController
import org.autojs.autojs.core.plugin.agent.ThreeStoveAgentControlException
import org.autojs.autojs.project.AgentScriptCatalog
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.ScriptPromiseAdapter
import org.autojs.autojs.runtime.api.augment.ai.AgentRunNativeObject
import org.autojs.autojs.runtime.api.augment.ai.AiAsyncDispatcher
import org.autojs.autojs.util.WorkingDirectoryUtils
import org.autojs.autojs.util.RhinoUtils.js_json_parse
import org.autojs.autojs.app.GlobalAppContext
import java.io.Closeable
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

/** Per-ScriptRuntime facade; only the registry's immutable JVM run records can outlive it. */
internal class AiAgentService(private val runtime: ScriptRuntime) : Closeable {
    private val registry = ThreeStoveAgentLinkController.get(GlobalAppContext.get()).scriptRuns
    private val closed = AtomicBoolean()
    private val handles = ConcurrentHashMap<String, AgentRunNativeObject>()
    private val pending = ConcurrentHashMap.newKeySet<AiAsyncDispatcher>()
    init { runtime.closeableManager.add(this) }

    fun run(goal: String, options: JsonObject): AgentRunNativeObject {
        check(!closed.get()) { "Agent script runtime is closed" }
        check(handles.size < 256) { "Too many AgentRun handles in one script" }
        return handle(registry.start(AiAgentArguments.goal(goal), AiAgentArguments.options(options)), ownsRun = true)
    }
    fun get(id: String): AgentRunNativeObject? {
        check(!closed.get())
        AiAgentArguments.runId(id)
        handles[id]?.let { return it }
        return registry.get(id)?.let { handle(it, ownsRun = false) }
    }
    private fun handle(record: org.autojs.autojs.core.plugin.agent.ThreeStoveAgentRunRecord, ownsRun: Boolean): AgentRunNativeObject {
        if (handles.size >= 256) {
            if (ownsRun && !record.detached) runCatching { registry.cancel(record, "script-handle-limit") }
            throw IllegalStateException("Too many AgentRun handles in one script")
        }
        return handles[record.id] ?: AgentRunNativeObject(runtime, record, registry, ownsRun).also { handles[record.id] = it }
    }
    fun status(): JsonObject = registry.status()
    fun list(filter: JsonObject): ScriptPromiseAdapter {
        val checked = AiAgentArguments.filter(filter)
        return async {
            val result = JsonArray(); val limit = checked["limit"]?.asInt ?: 50
            var offset = 0
            while (result.size() < limit && offset <= 1000 && !closed.get()) {
                val page = registry.list(50, offset); val rows = page.getAsJsonArray("runs")
                rows.forEach { raw -> if (result.size() < limit && AiAgentArguments.matches(raw.asJsonObject, checked)) result.add(raw.deepCopy().apply { asJsonObject.add("id", asJsonObject.remove("runId")) }) }
                if (rows.size() < 50 || offset + rows.size() >= page["total"].asInt) break
                offset += rows.size()
            }
            result
        }
    }
    fun presets(): ScriptPromiseAdapter = async { JsonArray().apply { registry.presets().getAsJsonArray("presets").forEach { add(it.asJsonObject["id"].asString) } } }
    fun catalog(query: String?): ScriptPromiseAdapter {
        query?.let { AiAgentArguments.text(it, "catalog query", 4096, blank = true) }
        val work = File(WorkingDirectoryUtils.path)
        return async {
            val roots = registry.roots().map(::File)
            val catalog = AgentScriptCatalog(work, listOf(Environment.getExternalStorageDirectory()), roots)
            JsonArray().apply { catalog.listScripts(query = query).forEach { add(it.toJsonObject()) } }
        }
    }
    private fun async(work: () -> JsonElement): ScriptPromiseAdapter {
        check(!closed.get())
        val promise = ScriptPromiseAdapter()
        val dispatcher = AiAsyncDispatcher(runtime)
        pending.add(dispatcher)
        try { worker.execute {
            if (closed.get()) return@execute
            try {
                val json = work().toString()
                dispatcher.terminalValues(arrayOf(json)) { values ->
                    try { promise.resolve(js_json_parse(values[0] as String)) } finally { pending.remove(dispatcher) }
                }
            } catch (failure: Exception) {
                val error = JsonObject().apply {
                    addProperty("code", (failure as? ThreeStoveAgentControlException)?.code ?: "INVALID_REQUEST")
                    addProperty("message", "3-Stove Agent request could not be completed")
                    (failure as? ThreeStoveAgentControlException)?.hint?.let { addProperty("hint", it) }
                }.toString()
                dispatcher.terminalValues(arrayOf(error)) { values ->
                    try { promise.reject(js_json_parse(values[0] as String)) } finally { pending.remove(dispatcher) }
                }
            }
        } } catch (failure: Exception) { pending.remove(dispatcher); dispatcher.cancelSilently(); throw failure }
        return promise
    }
    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        pending.forEach { it.cancelSilently() }; pending.clear()
        handles.values.forEach { it.close() }; handles.clear()
    }
    companion object {
        private val worker = java.util.concurrent.ThreadPoolExecutor(2, 2, 10, java.util.concurrent.TimeUnit.SECONDS,
            java.util.concurrent.ArrayBlockingQueue(64), { task -> Thread(task, "agent-script-query").apply { isDaemon = true } },
            java.util.concurrent.ThreadPoolExecutor.AbortPolicy())
    }
}
