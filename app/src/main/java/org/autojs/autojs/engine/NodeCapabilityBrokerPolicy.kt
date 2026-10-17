package org.autojs.autojs.engine

import org.autojs.autojs6.BuildConfig
import org.autojs.plugin.pinyin.api.PinyinCustomDictionaryContract
import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

object NodeCapabilityBrokerPolicy {

    const val SCHEMA = "autojs6-node-capability-broker-v2"
    const val VERSION = 2
    const val DEFAULT_MAX_AUDIT_ROWS = 64
    const val DEFAULT_MAX_REQUEST_BYTES = 96 * 1024
    const val DEFAULT_MAX_ARGS_BYTES = 32 * 1024
    const val PINYIN_CONVERT_MAX_ARGS_BYTES = PinyinCustomDictionaryContract.MAX_JSON_BYTES + 16 * 1024
    /** `files.write` carries up to 1 MiB of content; base64 inflates it by a third. */
    const val FILES_WRITE_MAX_ARGS_BYTES = 2 * 1024 * 1024
    /** `plugin_tools.invoke` carries up to 64 KiB of tool arguments plus the tool name and options. */
    const val PLUGIN_TOOLS_INVOKE_MAX_ARGS_BYTES = 64 * 1024 + 8 * 1024
    /** Envelope allowance on top of an operation's args ceiling for the whole request. */
    const val REQUEST_ENVELOPE_BYTES = 16 * 1024

    val operationDeclarations: List<OperationDeclaration> by lazy {
        val supportedMethods = NodeBridgeModules.supportedMethodsByModule.toMutableMap().apply {
            put(
                NodeLifecycleCheckpointStore.BRIDGE_MODULE_NAME,
                setOf(
                    NodeLifecycleCheckpointStore.METHOD_CHECKPOINT,
                    NodeLifecycleCheckpointStore.METHOD_READ_CHECKPOINT,
                    NodeLifecycleCheckpointStore.METHOD_CLEAR_CHECKPOINT,
                ),
            )
        }
        supportedMethods
            .toSortedMap()
            .flatMap { (module, methods) ->
                methods.sorted().map { method ->
                    OperationDeclaration(
                        module = module,
                        method = method,
                        requiredCapabilities = NodeBridgePermissionManifest.requiredCapabilitiesFor(module, method),
                        maxArgsBytes = when {
                            module == NodeBridgeModules.PINYIN && method == "convert" -> PINYIN_CONVERT_MAX_ARGS_BYTES
                            module == NodeBridgeModules.OPENCC -> 64 * 1024
                            module in NodeBridgeModules.imageModules && method == "fromBase64" -> 64 * 1024
                            module == NodeBridgeModules.FILES && method == "write" -> FILES_WRITE_MAX_ARGS_BYTES
                            module == NodeBridgeModules.PLUGIN_TOOLS && method == "invoke" -> PLUGIN_TOOLS_INVOKE_MAX_ARGS_BYTES
                            else -> DEFAULT_MAX_ARGS_BYTES
                        },
                    )
                }
            }
    }

    private val declarationsByKey: Map<String, OperationDeclaration> by lazy {
        operationDeclarations.associateBy { operationKey(it.module, it.method) }
    }

    fun declarationFor(module: String, method: String): OperationDeclaration? {
        return declarationsByKey[operationKey(module, method)]
    }

    fun declarationFor(request: NodeBridgeRequest): OperationDeclaration? {
        return declarationFor(request.module, request.method)
    }

    fun preflight(
        request: NodeBridgeRequest,
        maxRequestBytes: Int = DEFAULT_MAX_REQUEST_BYTES,
    ): NodeBridgeError? {
        val declaration = declarationFor(request)
            ?: return NodeBridgeError.bridgeUnavailable(request.module, request.method)
        if (request.timeoutMs <= 0L) {
            return NodeBridgeError.invalidRequest(
                message = "AutoJs6 capability broker requires a positive bridge timeout.",
                module = request.module,
                method = request.method,
            )
        }
        if (!isJsonArray(request.argsJson)) {
            return NodeBridgeError.invalidRequest(
                message = "AutoJs6 capability broker requires JSON-array arguments.",
                module = request.module,
                method = request.method,
            )
        }
        val argsBytes = byteSize(request.argsJson)
        if (argsBytes > declaration.maxArgsBytes) {
            return NodeBridgeError.resourceLimit(
                message = "AutoJs6 capability broker rejected ${request.module}.${request.method}: args payload is $argsBytes bytes, limit ${declaration.maxArgsBytes}.",
                module = request.module,
                method = request.method,
            )
        }
        val requestBytes = safeRequestBytes(request)
        val requestLimit = maxOf(maxRequestBytes, declaration.maxArgsBytes + REQUEST_ENVELOPE_BYTES)
        if (requestBytes > requestLimit) {
            return NodeBridgeError.resourceLimit(
                message = "AutoJs6 capability broker rejected ${request.module}.${request.method}: request payload is $requestBytes bytes, limit $requestLimit.",
                module = request.module,
                method = request.method,
            )
        }
        return null
    }

    fun declarationSummaryJson(): String {
        return JSONObject().apply {
            put("schema", SCHEMA)
            put("version", VERSION)
            put("operationCount", operationDeclarations.size)
            put("operations", JSONArray().apply {
                operationDeclarations.forEach { declaration -> put(declaration.toJsonObject()) }
            })
        }.toString()
    }

    private fun operationKey(module: String, method: String): String = "$module.$method"

    private fun isJsonArray(value: String): Boolean {
        return runCatching { JSONArray(value.ifBlank { "[]" }) }.isSuccess
    }

    private fun safeRequestBytes(request: NodeBridgeRequest): Int {
        return runCatching { byteSize(request.toJson()) }.getOrElse {
            byteSize(request.id) + byteSize(request.module) + byteSize(request.method) + byteSize(request.argsJson)
        }
    }

    private fun byteSize(value: String): Int = value.toByteArray(StandardCharsets.UTF_8).size

    data class OperationDeclaration(
        val module: String,
        val method: String,
        val requiredCapabilities: List<String>,
        val maxArgsBytes: Int = DEFAULT_MAX_ARGS_BYTES,
        val parameterValidated: Boolean = true,
        val sizeLimited: Boolean = true,
        val cancellable: Boolean = true,
        val audited: Boolean = true,
        val executionOwned: Boolean = true,
    ) {
        fun toJsonObject(): JSONObject = JSONObject().apply {
            put("module", module)
            put("method", method)
            put("requiredCapabilities", JSONArray(requiredCapabilities))
            put("maxArgsBytes", maxArgsBytes)
            put("parameterValidated", parameterValidated)
            put("sizeLimited", sizeLimited)
            put("cancellable", cancellable)
            put("audited", audited)
            put("executionOwned", executionOwned)
        }
    }
}

class NodeCapabilityBrokerAuditLog(
    private val ownerExecutionId: String,
    private val maxRows: Int = NodeCapabilityBrokerPolicy.DEFAULT_MAX_AUDIT_ROWS,
    private val maxRequestBytes: Int = NodeCapabilityBrokerPolicy.DEFAULT_MAX_REQUEST_BYTES,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val rows = ConcurrentLinkedQueue<AuditRow>()
    private val totalCount = AtomicInteger(0)
    private val rejectedCount = AtomicInteger(0)
    private val failedCount = AtomicInteger(0)
    private val droppedCount = AtomicInteger(0)

    fun begin(request: NodeBridgeRequest): Attempt {
        val startedAt = clock()
        val declaration = NodeCapabilityBrokerPolicy.declarationFor(request)
        val preflightError = NodeCapabilityBrokerPolicy.preflight(request, maxRequestBytes)
        return Attempt(
            request = request,
            declaration = declaration,
            startedAtEpochMs = startedAt,
            preflightError = preflightError,
        )
    }

    fun complete(attempt: Attempt, response: NodeBridgeResponse) {
        val completedAt = clock()
        val error = attempt.preflightError ?: response.error
        val outcome = when {
            attempt.preflightError != null -> "rejected"
            response.ok -> "succeeded"
            else -> "failed"
        }
        if (outcome == "rejected") {
            rejectedCount.incrementAndGet()
        }
        if (outcome == "failed") {
            failedCount.incrementAndGet()
        }
        totalCount.incrementAndGet()
        rows.add(
            AuditRow(
                ownerExecutionId = ownerExecutionId,
                requestId = attempt.request.id,
                module = attempt.request.module,
                method = attempt.request.method,
                declared = attempt.declaration != null,
                requiredCapabilities = attempt.declaration?.requiredCapabilities.orEmpty(),
                argsBytes = byteSize(attempt.request.argsJson),
                requestBytes = safeRequestBytes(attempt.request),
                startedAtEpochMs = attempt.startedAtEpochMs,
                completedAtEpochMs = completedAt,
                durationMs = (completedAt - attempt.startedAtEpochMs).coerceAtLeast(0L),
                outcome = outcome,
                code = if (response.ok) "OK" else error?.code.orEmpty().ifBlank { NodeRuntimeErrorCodes.BRIDGE_PROCESS_DEAD },
                category = if (response.ok) "ok" else error?.category.orEmpty().ifBlank { NodeBridgeErrorCategory.PROCESS_DEAD },
            ),
        )
        trimRows()
    }

    fun record(request: NodeBridgeRequest, response: NodeBridgeResponse) {
        complete(begin(request), response)
    }

    fun snapshot(): List<AuditRow> = rows.toList()

    fun nativeDiagnostics(): Map<String, String> {
        val retained = rows.toList()
        val last = retained.lastOrNull()
        return linkedMapOf<String, String>().apply {
            put("embedded_script.capability_broker.schema", NodeCapabilityBrokerPolicy.SCHEMA)
            put("embedded_script.capability_broker.version", NodeCapabilityBrokerPolicy.VERSION.toString())
            put("embedded_script.capability_broker.owner_execution_id", ownerExecutionId)
            put("embedded_script.capability_broker.declared_operation_count", NodeCapabilityBrokerPolicy.operationDeclarations.size.toString())
            put("embedded_script.capability_broker.max_audit_rows", maxRows.toString())
            put("embedded_script.capability_broker.max_request_bytes", maxRequestBytes.toString())
            put("embedded_script.capability_broker.audit_total_count", totalCount.get().toString())
            put("embedded_script.capability_broker.audit_retained_count", retained.size.toString())
            put("embedded_script.capability_broker.audit_dropped_count", droppedCount.get().toString())
            put("embedded_script.capability_broker.audit_rejected_count", rejectedCount.get().toString())
            put("embedded_script.capability_broker.audit_failed_count", failedCount.get().toString())
            put("embedded_script.capability_broker.audit_last_json", last?.toJsonObject()?.toString().orEmpty())
            if (BuildConfig.DEBUG) {
                put("embedded_script.capability_broker.audit_log_json", JSONArray().apply {
                    retained.forEach { row -> put(row.toJsonObject()) }
                }.toString())
            }
        }
    }

    private fun trimRows() {
        val limit = maxRows.coerceAtLeast(1)
        while (rows.size > limit) {
            if (rows.poll() != null) {
                droppedCount.incrementAndGet()
            } else {
                return
            }
        }
    }

    private fun safeRequestBytes(request: NodeBridgeRequest): Int {
        return runCatching { byteSize(request.toJson()) }.getOrElse {
            byteSize(request.id) + byteSize(request.module) + byteSize(request.method) + byteSize(request.argsJson)
        }
    }

    private fun byteSize(value: String): Int = value.toByteArray(StandardCharsets.UTF_8).size

    data class Attempt(
        val request: NodeBridgeRequest,
        val declaration: NodeCapabilityBrokerPolicy.OperationDeclaration?,
        val startedAtEpochMs: Long,
        val preflightError: NodeBridgeError?,
    )

    data class AuditRow(
        val ownerExecutionId: String,
        val requestId: String,
        val module: String,
        val method: String,
        val declared: Boolean,
        val requiredCapabilities: List<String>,
        val argsBytes: Int,
        val requestBytes: Int,
        val startedAtEpochMs: Long,
        val completedAtEpochMs: Long,
        val durationMs: Long,
        val outcome: String,
        val code: String,
        val category: String,
    ) {
        fun toJsonObject(): JSONObject = JSONObject().apply {
            put("schema", NodeCapabilityBrokerPolicy.SCHEMA)
            put("ownerExecutionId", ownerExecutionId)
            put("requestId", requestId)
            put("module", module)
            put("method", method)
            put("declared", declared)
            put("requiredCapabilities", JSONArray(requiredCapabilities))
            put("argsBytes", argsBytes)
            put("requestBytes", requestBytes)
            put("startedAtEpochMs", startedAtEpochMs)
            put("completedAtEpochMs", completedAtEpochMs)
            put("durationMs", durationMs)
            put("outcome", outcome)
            put("code", code)
            put("category", category)
        }
    }
}
