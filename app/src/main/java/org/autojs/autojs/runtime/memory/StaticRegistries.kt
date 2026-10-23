package org.autojs.autojs.runtime.memory

import org.autojs.autojs.core.accessibility.AccessibilityService
import org.autojs.autojs.core.broadcast.Broadcast
import org.autojs.autojs.core.console.ConsoleImpl
import org.autojs.autojs.core.looper.LooperHelper
import org.autojs.autojs.core.plugin.bun.BunRuntimePluginClient
import org.autojs.autojs.core.plugin.lua.LuaRuntimeBinderCallLane
import org.autojs.autojs.core.plugin.python.PythonLongRunningExecutionRegistry
import org.autojs.autojs.core.plugin.python.PythonRuntimeExecutionCoordinator
import org.autojs.autojs.core.plugin.quickjs.QuickJsRuntimePluginClient
import org.autojs.autojs.core.web.WebSocket
import org.autojs.autojs.engine.NodeAudioRecordingService
import org.autojs.autojs.engine.NodeBridgeEngineDispatchService
import org.autojs.autojs.engine.NodeBridgeUiSessionStore
import org.autojs.autojs.engine.NodeDialogActivity
import org.autojs.autojs.engine.NodeJsRuntimePluginBridge
import org.autojs.autojs.engine.NodeJsRuntimePluginHost
import org.autojs.autojs.engine.RuntimeWebActionActivity
import org.autojs.autojs.engine.RuntimeWebFileAccess
import org.autojs.autojs.engine.RuntimeWebLocalDownloads
import org.autojs.autojs.engine.RuntimeWebMediaService
import org.autojs.autojs.engine.ScriptEngineService
import org.autojs.autojs.ui.enhancedfloaty.FloatyService

/**
 * Sizes of the process-wide registries that script runtimes add to (docs/memory/ROADMAP.md T7 / P4.2, appendix B.1), for the memory
 * gate: after a scenario every count must be back where it was at the end of its warm-ups, so an entry a finished script leaves
 * behind shows up even when it does not keep the runtime itself reachable.
 * zh-CN: 脚本运行时会写入的进程级注册表的大小 (docs/memory/ROADMAP.md T7 / P4.2, 附录 B.1), 供内存门禁使用: 场景结束后每项计数都必须回到预热结束时的值,
 * 因此已结束的脚本留下的条目即使没有让运行时本身保持可达也会被发现.
 */
object StaticRegistries {

    fun counts(): Map<String, Int> {
        val counts = linkedMapOf(
            "LooperHelper.loopers" to LooperHelper.size(),
            "FloatyService.windows" to FloatyService.windowCount(),
            "ConsoleImpl.showingConsoles" to ConsoleImpl.showingConsoleCount(),
            "WebSocket.instances" to WebSocket.instanceCount(),
            "Broadcast.emitters" to Broadcast.listenerCount(),
        )
        ScriptEngineService.getInstance()?.let { service ->
            counts["ScriptEngineService.executionListeners"] = service.globalScriptExecutionListenerCount
            counts["ScriptEngineService.lifecycleCallbacks"] = service.engineLifecycleCallbackCount
        }
        AccessibilityService.busCounts().forEach { (bus, count) -> counts["AccessibilityService.$bus"] = count }
        return counts
    }

    /**
     * Sizes of the process-wide tables of the plugin engine bridges (docs/memory-phase2/ROADMAP.md T1 (h)), kept apart from [counts]
     * so that the first gate judges what it always did. Node, the Bun / QuickJS connection pools (P2.2), the Lua Binder call lane
     * and the Python admission queue and long-running registry (P3).
     * zh-CN: 插件引擎桥接的进程级表的大小 (docs/memory-phase2/ROADMAP.md T1 (h)), 与 [counts] 分开, 使第一期门禁的判定保持不变.
     * 包含 Node, Bun / QuickJS 的连接池 (P2.2), Lua 的 Binder 调用通道, 以及 Python 的准入队列与长时运行登记表 (P3).
     */
    fun bridgeCounts(): Map<String, Int> {
        val counts = linkedMapOf(
            "NodeJsRuntimePluginHost.connectionPool" to NodeJsRuntimePluginHost.pooledConnectionCount(),
            "NodeJsRuntimePluginHost.executionRegistry" to NodeJsRuntimePluginHost.registeredExecutionCount(),
        )
        NodeJsRuntimePluginBridge.registryCounts().forEach { (name, count) -> counts["NodeJsRuntimePluginBridge.$name"] = count }
        counts["NodeBridgeUiSessionStore.sessions"] = NodeBridgeUiSessionStore.sessionCount()
        counts["NodeDialogActivity.sessions"] = NodeDialogActivity.sessions.size
        counts["RuntimeWebActionActivity.actions"] = RuntimeWebActionActivity.actionCount()
        counts["RuntimeWebFileAccess.grants"] = RuntimeWebFileAccess.grantCount()
        counts["RuntimeWebFileAccess.captures"] = RuntimeWebFileAccess.captureCount()
        counts["RuntimeWebLocalDownloads.active"] = RuntimeWebLocalDownloads.activeCount()
        counts["RuntimeWebMediaService.entries"] = RuntimeWebMediaService.entryCount()
        counts["NodeAudioRecordingService.entries"] = NodeAudioRecordingService.entryCount()
        counts["NodeBridgeEngineDispatchService.registeredExecutions"] = NodeBridgeEngineDispatchService.registeredExecutionCount()
        counts["BunRuntimePluginClient.connectionPool"] = BunRuntimePluginClient.pooledConnectionCount()
        counts["QuickJsRuntimePluginClient.connectionPool"] = QuickJsRuntimePluginClient.pooledConnectionCount()
        counts["LuaRuntimeBinderCallLane.inFlight"] = LuaRuntimeBinderCallLane.inFlightCalls()
        PythonRuntimeExecutionCoordinator.snapshot().let { queue ->
            counts["PythonRuntimeExecutionCoordinator.active"] = if (queue.active) 1 else 0
            counts["PythonRuntimeExecutionCoordinator.pending"] = queue.pendingExecutions
        }
        counts["PythonLongRunningExecutionRegistry.records"] = PythonLongRunningExecutionRegistry.recordCount()
        return counts
    }
}
