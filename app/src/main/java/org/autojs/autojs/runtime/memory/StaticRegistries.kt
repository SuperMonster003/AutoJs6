package org.autojs.autojs.runtime.memory

import org.autojs.autojs.core.accessibility.AccessibilityService
import org.autojs.autojs.core.broadcast.Broadcast
import org.autojs.autojs.core.console.ConsoleImpl
import org.autojs.autojs.core.looper.LooperHelper
import org.autojs.autojs.core.web.WebSocket
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
}
