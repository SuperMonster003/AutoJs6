package org.autojs.autojs.engine

import android.content.Context
import android.os.Environment
import com.google.gson.JsonParser
import org.autojs.autojs.project.AgentScriptCatalog
import org.autojs.autojs.project.AgentManifestException
import org.autojs.autojs.execution.AgentScriptExecutionState
import org.json.JSONArray
import org.json.JSONObject
import java.io.Closeable
import java.io.File
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/** Registered scripts stay rooted in host-approved directories, independently of request roots. */
internal class NodeBridgeAgentBridge(
    context: Context,
    private val engineInfo: NodeBridgeEngineInfo,
    roots: List<File> = emptyList(),
    private val visibleRoots: List<File> = listOf(Environment.getExternalStorageDirectory()),
) : Closeable {
    private val appContext = context.applicationContext
    private val lock = Any()
    private var closed = false
    private val executions = LinkedHashSet<Invocation>()
    @Volatile private var catalog: AgentScriptCatalog? = null
    @Volatile private var configuredRoots = roots.toList()

    /** Validation precedes updating the plugin config; committing the prepared catalog is local. */
    fun prepareRoots(roots: List<File>): () -> Unit {
        require(roots.size <= 32) { "Too many Agent script roots." }
        val prepared = createCatalog(roots)
        return { synchronized(lock) { check(!closed); configuredRoots = roots.toList(); catalog = prepared } }
    }

    private fun createCatalog(roots: List<File>) = AgentScriptCatalog(File(engineInfo.cwd), visibleRoots, roots)
    private fun currentCatalog(): AgentScriptCatalog = catalog ?: synchronized(lock) {
        catalog ?: createCatalog(configuredRoots).also { catalog = it }
    }

    fun dispatch(request: NodeBridgeRequest, callback: (NodeBridgeResponse) -> Unit) {
        val replied = AtomicBoolean()
        fun reply(response: NodeBridgeResponse) {
            if (replied.compareAndSet(false, true)) runCatching { callback(response) }
        }
        try {
            synchronized(lock) { check(!closed) { "Agent bridge is closed." } }
            val args = JSONArray(request.argsJson)
            val scripts = currentCatalog()
            when (request.method) {
                "listScripts" -> {
                    require(args.length() <= 1)
                    val options = if (args.length() == 0) JSONObject() else args.getJSONObject(0)
                    val roots = options.opt("roots")?.takeUnless { it == JSONObject.NULL }?.let { raw ->
                        // One working-directory selector plus up to 32 configured extra roots.
                        require(raw is JSONArray && raw.length() <= 33)
                        (0 until raw.length()).map { index -> raw.get(index).also { require(it is String) } as String }
                    }
                    val query = optionalString(options, "query")
                    val limit = integer(options, "limit", 500L).also { require(it in 1..500) }.toInt()
                    val result = com.google.gson.JsonArray()
                    scripts.listScripts(roots, query, limit).forEach { result.add(it.toJsonObject()) }
                    reply(NodeBridgeResponse.success(request.id, result.toString()))
                }
                "readManifest" -> {
                    require(args.length() == 1 && args.get(0) is String)
                    reply(NodeBridgeResponse.success(request.id, scripts.readManifest(args.getString(0)).toJsonObject().toString()))
                }
                "execRegistered" -> {
                    require(args.length() in 2..3 && args.get(0) is String && args.get(1) is JSONObject)
                    val options = if (args.length() == 3) args.getJSONObject(2) else JSONObject()
                    val invocation = scripts.resolveForExecution(args.getString(0), JsonParser.parseString(args.getJSONObject(1).toString()).asJsonObject)
                    if (invocation.arguments.toString().toByteArray(Charsets.UTF_8).size > AgentScriptExecutionState.MAX_PARAMETERS_BYTES) {
                        throw AgentManifestException("LIMIT_EXCEEDED", "Registered-script arguments exceed the limit.")
                    }
                    val timeout = integer(options, "timeoutMs", invocation.entry.timeoutMs).also { require(it in 1..300_000) }
                        .coerceAtMost(invocation.entry.timeoutMs).coerceAtMost((request.timeoutMs - 750).coerceAtLeast(1))
                    require(timeout > 0)
                    val capture = options.opt("captureConsole")?.let { require(it is Boolean); it } ?: true
                    val runId = optionalString(options, "agentRunId") ?: "agent-${UUID.randomUUID()}"
                    require(runId.matches(Regex("[A-Za-z0-9][A-Za-z0-9_.:-]{0,127}")))
                    val preset = optionalString(options, "presetName").orEmpty().also {
                        require(it.toByteArray(Charsets.UTF_8).size <= 256 && it.none(Character::isISOControl))
                    }
                    val pending = Invocation()
                    synchronized(lock) { check(!closed); executions.add(pending) }
                    val handle = try {
                        NodeBridgeEngineDispatchService.dispatchRegistered(
                            appContext, request, invocation.launchPath,
                            (File(invocation.launchPath).takeIf { it.isDirectory } ?: invocation.scriptFile.parentFile).absolutePath,
                            invocation.arguments.toString(), runId, preset, timeout, capture,
                            expectedScriptPath = invocation.scriptFile.path,
                        ) { response ->
                            pending.completed.set(true)
                            synchronized(lock) { executions.remove(pending) }
                            reply(response)
                        }
                    } catch (error: Throwable) {
                        synchronized(lock) { executions.remove(pending) }
                        throw error
                    }
                    synchronized(lock) {
                        pending.handle = handle
                        if (closed && !pending.completed.get()) handle.close()
                    }
                }
                else -> reply(NodeBridgeResponse.failure(request.id, NodeBridgeError.bridgeUnavailable(request.module, request.method)))
            }
        } catch (error: Exception) {
            val failure = when (error) {
                is AgentManifestException -> when (error.code) {
                    "CAPABILITY_DENIED" -> NodeBridgeError.permissionDenied("CAPABILITY_DENIED", request.module, request.method, category = NodeBridgeErrorCategory.CAPABILITY_DENIED)
                    "LIMIT_EXCEEDED" -> NodeBridgeError.resourceLimit("LIMIT_EXCEEDED", request.module, request.method)
                    else -> NodeBridgeError.invalidRequest(error.code, request.module, request.method)
                }
                is IllegalArgumentException -> NodeBridgeError.invalidRequest("Invalid registered script, arguments or directory scope.", request.module, request.method)
                is SecurityException -> NodeBridgeError.permissionDenied("Script path is outside the permitted roots.", request.module, request.method)
                is IllegalStateException -> NodeBridgeError.processDead("Agent script bridge is unavailable.")
                else -> NodeBridgeError.providerFailed("Registered script operation failed.", request.module, request.method)
            }
            reply(NodeBridgeResponse.failure(request.id, failure))
        }
    }

    override fun close() {
        val pending = synchronized(lock) { closed = true; executions.toList().also { executions.clear() } }
        pending.forEach { if (!it.completed.get()) runCatching { it.handle?.close() } }
        catalog = null
    }

    private class Invocation {
        val completed = AtomicBoolean()
        var handle: Closeable? = null
    }

    private fun integer(options: JSONObject, name: String, fallback: Long): Long {
        if (!options.has(name)) return fallback
        val value = options.get(name)
        require(value is Number && value.toDouble().isFinite() && value.toDouble() == value.toLong().toDouble())
        return value.toLong()
    }

    private fun optionalString(options: JSONObject, name: String): String? {
        val value = options.opt(name) ?: return null
        require(value is String)
        return value
    }
}
