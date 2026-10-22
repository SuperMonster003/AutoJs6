package org.autojs.autojs.engine

import android.util.Log
import org.autojs.autojs6.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

enum class NodeBridgeResourceType(val idPrefix: String) {
    IMAGE_HANDLE("image"),
    OCR_OPERATION("ocr"),
    BARCODE_OPERATION("barcode"),
    DATABASE_HANDLE("database"),
    SCREEN_CAPTURER("screen-capturer"),
    WEBSOCKET_CONNECTION("websocket"),
    SHELL_EXECUTION("shell"),
    DIALOG("dialog"),
    WORK_MANAGER_TASK("work"),
    JAVA_OBJECT("java"),
    WORKER("worker"),
    NETWORK_CALL("network"),
    NOTIFICATION("notification"),
    SENSOR_SUBSCRIPTION("sensor"),
    UI_SESSION("ui"),
    OVERLAY_SESSION("overlay"),
    INPUT_OBSERVER_SUBSCRIPTION("input-observer"),
}

class NodeBridgeResourceHandle internal constructor(
    private val registry: NodeBridgeResourceRegistry,
    val id: String,
    val type: NodeBridgeResourceType,
) {
    fun release(reason: String = "released"): Boolean = registry.release(id, reason)

    fun close(reason: String = "closed"): Boolean = registry.close(id, reason)
}

data class NodeBridgeResourceCleanupSummary(
    val ownerExecutionId: String,
    val reason: String,
    val closedCount: Int,
    val leakedCount: Int,
    val closeErrorCount: Int,
    val activeTypeCounts: Map<String, Int>,
) {
    fun toJson(): String = JSONObject()
        .put("ownerExecutionId", ownerExecutionId)
        .put("reason", reason)
        .put("closedCount", closedCount)
        .put("leakedCount", leakedCount)
        .put("closeErrorCount", closeErrorCount)
        .put("activeTypeCounts", JSONObject(activeTypeCounts))
        .toString()
}

/**
 * [tracker] receives the objects behind the resources registered with one, for the weak check after the script ends
 * (`RuntimeResourceScope.track`, docs/memory-phase2/ROADMAP.md T6).
 * zh-CN: [tracker] 接收登记时附带的资源对象, 供脚本结束后的弱引用复查 (`RuntimeResourceScope.track`, docs/memory-phase2/ROADMAP.md T6).
 */
class NodeBridgeResourceRegistry(
    private val ownerExecutionId: String,
    private val tracker: ((resource: Any, name: String) -> Unit)? = null,
    private val clock: () -> Long = { System.currentTimeMillis() },
) {
    private val active = ConcurrentHashMap<String, ResourceRecord>()
    private val createdCount = AtomicInteger(0)
    private val closedCount = AtomicInteger(0)
    private val destroyClosedCount = AtomicInteger(0)
    private val leakCount = AtomicInteger(0)
    private val closeErrorCount = AtomicInteger(0)
    private val maxActiveCount = AtomicInteger(0)
    @Volatile
    private var lastCleanupSummary: NodeBridgeResourceCleanupSummary? = null
    @Volatile
    private var closedReason: String? = null

    fun register(
        type: NodeBridgeResourceType,
        id: String = "${type.idPrefix}-${UUID.randomUUID()}",
        metadata: Map<String, String> = emptyMap(),
        resource: Any? = null,
        close: (String) -> Unit = {},
    ): NodeBridgeResourceHandle {
        resource?.let { tracker?.invoke(it, "node.${type.idPrefix}") }
        val record = ResourceRecord(
            id = id,
            type = type,
            createdAtEpochMs = clock(),
            ownerExecutionId = ownerExecutionId,
            metadata = metadata.toSortedMap(),
            close = close,
        )
        require(active.putIfAbsent(id, record) == null) {
            "AutoJs6 bridge resource already exists for execution $ownerExecutionId: $id"
        }
        createdCount.incrementAndGet()
        recordMaxActive(active.size)
        // A resource registered after closeAll() (e.g. an overlay posted before destroy) would stay here with its
        // close callback and the provider it captures, so close it at once (C6).
        // zh-CN: closeAll() 之后登记的资源 (例如 destroy 前已 post 的浮窗) 会连同关闭回调及其捕获的 provider 一直留在这里, 因此立即关闭 (C6).
        closedReason?.let { reason ->
            if (active.remove(id, record)) {
                leakCount.incrementAndGet()
                closeRecord(record, reason, fromDestroy = true)
                Log.w(TAG, "Closed AutoJs6 Node bridge resource registered after destroy: ${type.name} $id")
            }
        }
        return NodeBridgeResourceHandle(this, id, type)
    }

    fun release(id: String, reason: String = "released"): Boolean {
        val record = active.remove(id) ?: return false
        if (record.closed.compareAndSet(false, true)) {
            closedCount.incrementAndGet()
        }
        return true
    }

    fun close(id: String, reason: String = "closed"): Boolean {
        val record = active.remove(id) ?: return false
        closeRecord(record, reason, fromDestroy = false)
        return true
    }

    fun closeAll(reason: String = "destroy"): NodeBridgeResourceCleanupSummary {
        // Set before the snapshot: register() either lands in it or sees the flag.
        // zh-CN: 在取快照之前设置: register() 要么进入快照, 要么看到该标志.
        if (closedReason == null) closedReason = reason
        val records = active.values.sortedWith(compareBy<ResourceRecord> { it.type.name }.thenBy { it.createdAtEpochMs })
        val activeTypeCounts = typeCounts(records)
        var closedByDestroy = 0
        records.forEach { record ->
            if (active.remove(record.id, record)) {
                if (closeRecord(record, reason, fromDestroy = true)) {
                    closedByDestroy += 1
                }
            }
        }
        val summary = NodeBridgeResourceCleanupSummary(
            ownerExecutionId = ownerExecutionId,
            reason = reason,
            closedCount = closedByDestroy,
            leakedCount = records.size,
            closeErrorCount = closeErrorCount.get(),
            activeTypeCounts = activeTypeCounts,
        )
        lastCleanupSummary = summary
        leakCount.addAndGet(records.size)
        if (records.isNotEmpty()) {
            Log.w(TAG, "Closed leaked AutoJs6 Node bridge resources: ${summary.toJson()}")
        }
        return summary
    }

    fun activeCount(): Int = active.size

    fun nativeDiagnostics(): Map<String, String> = linkedMapOf<String, String>().apply {
        put("embedded_script.bridge_resource.owner_execution_id", ownerExecutionId)
        put("embedded_script.bridge_resource.active_count", active.size.toString())
        put("embedded_script.bridge_resource.max_active_count", maxActiveCount.get().toString())
        put("embedded_script.bridge_resource.created_count", createdCount.get().toString())
        put("embedded_script.bridge_resource.closed_count", closedCount.get().toString())
        put("embedded_script.bridge_resource.destroy_closed_count", destroyClosedCount.get().toString())
        put("embedded_script.bridge_resource.leak_count", leakCount.get().toString())
        put("embedded_script.bridge_resource.close_error_count", closeErrorCount.get().toString())
        put("embedded_script.bridge_resource.active_types", JSONObject(typeCounts(active.values)).toString())
        put("embedded_script.bridge_resource.last_cleanup_summary", lastCleanupSummary?.toJson().orEmpty())
        if (BuildConfig.DEBUG) {
            put("embedded_script.bridge_resource.dump_json", dumpActiveResources().toString())
        }
    }

    private fun closeRecord(record: ResourceRecord, reason: String, fromDestroy: Boolean): Boolean {
        if (!record.closed.compareAndSet(false, true)) {
            return false
        }
        runCatching {
            record.close(reason)
        }.onFailure { error ->
            closeErrorCount.incrementAndGet()
            Log.w(TAG, "Failed to close AutoJs6 Node bridge resource ${record.id}: ${error.message}", error)
        }
        closedCount.incrementAndGet()
        if (fromDestroy) {
            destroyClosedCount.incrementAndGet()
        }
        return true
    }

    private fun dumpActiveResources(): JSONArray {
        val dump = JSONArray()
        active.values
            .sortedWith(compareBy<ResourceRecord> { it.type.name }.thenBy { it.createdAtEpochMs })
            .forEach { record ->
                dump.put(
                    JSONObject()
                        .put("id", record.id)
                        .put("type", record.type.name)
                        .put("createdAtEpochMs", record.createdAtEpochMs)
                        .put("ownerExecutionId", record.ownerExecutionId)
                        .put("metadata", JSONObject(record.metadata)),
                )
            }
        return dump
    }

    private fun typeCounts(records: Collection<ResourceRecord>): Map<String, Int> {
        return records
            .groupingBy { it.type.name }
            .eachCount()
            .toSortedMap()
    }

    private fun recordMaxActive(count: Int) {
        while (true) {
            val current = maxActiveCount.get()
            if (count <= current) {
                return
            }
            if (maxActiveCount.compareAndSet(current, count)) {
                return
            }
        }
    }

    private data class ResourceRecord(
        val id: String,
        val type: NodeBridgeResourceType,
        val createdAtEpochMs: Long,
        val ownerExecutionId: String,
        val metadata: Map<String, String>,
        val close: (String) -> Unit,
        val closed: AtomicBoolean = AtomicBoolean(false),
    )

    companion object {
        private const val TAG = "NodeBridgeResource"
    }
}
