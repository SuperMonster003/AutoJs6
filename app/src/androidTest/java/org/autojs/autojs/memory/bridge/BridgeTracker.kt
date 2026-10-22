package org.autojs.autojs.memory.bridge

import android.app.Instrumentation
import android.app.UiAutomation
import android.content.ComponentName
import android.content.Context
import android.os.ParcelFileDescriptor
import androidx.test.platform.app.InstrumentationRegistry
import org.autojs.autojs.engine.JavaScriptEngine
import org.autojs.autojs.engine.ScriptEngine
import org.autojs.autojs.engine.ScriptEngineManager
import org.autojs.autojs.memory.GateMetrics
import org.autojs.autojs.memory.scenarios.GateNativeProbe
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.memory.BridgeExecutionObserver
import org.autojs.autojs.runtime.memory.StaticRegistries
import org.autojs.autojs.script.ScriptSource
import org.json.JSONArray
import org.json.JSONObject
import java.lang.ref.WeakReference
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Receives the engine lifecycle events and the session objects that [BridgeExecutionObserver] reports for the run in progress
 * (docs/memory-phase2/ROADMAP.md T1 (a)); holds weak references only. The companion reads the binding counts of T1 (h), the bridge
 * threads of T1 (d) and the plugin process PSS of T1 (i).
 * zh-CN: 接收当前运行的引擎生命周期事件与 [BridgeExecutionObserver] 报告的会话对象 (docs/memory-phase2/ROADMAP.md T1 (a)); 只持有弱引用.
 * companion 读取 T1 (h) 的绑定计数, T1 (d) 的桥接线程与 T1 (i) 的插件进程 PSS.
 */
internal class BridgeTracker : ScriptEngineManager.EngineLifecycleCallback, BridgeExecutionObserver.Listener {

    @Volatile
    var window: BridgeWindow? = null

    override fun onEngineCreate(engine: ScriptEngine<out ScriptSource>) {
        window?.engineCreated(engine)
    }

    override fun onEngineRemove(engine: ScriptEngine<out ScriptSource>) {
        window?.engineRemoved(engine)
    }

    override fun created(engine: String, kind: String, executionId: String?, instance: Any) {
        window?.sessionCreated("$engine.$kind", executionId, instance)
    }

    companion object {

        /**
         * Name parts of the threads the bridges start in the host (docs/memory-phase2/ROADMAP.md T1 (d), appendix F), next to the
         * script threads of the first gate.
         * zh-CN: 桥接在宿主中启动的线程名片段 (docs/memory-phase2/ROADMAP.md T1 (d), 附录 F), 与第一期的脚本线程并列.
         */
        val BRIDGE_THREAD_MARKERS = listOf(
            "AutoJs6Node", "AutoJs6-Node", "node-console-input", "runtime-web", "web-display-capture", "host-capability",
            "AutoJs6BridgeScreenshot", "AutoJs6ScreenText", "autojs-java-bridge", "BunHostCall", "python-runtime",
            "autojs-bun-", "autojs-quickjs-",
        )

        /**
         * Connection pools of the AidlPluginHost clients (P2.1), read through reflection until StaticRegistries reports them (P2.2).
         * zh-CN: AidlPluginHost 客户端的连接池 (P2.1), 在 StaticRegistries 报告它们之前 (P2.2) 经反射读取.
         */
        private val AIDL_POOLS = linkedMapOf(
            "BunRuntimePluginClient.connectionPool" to "org.autojs.autojs.core.plugin.bun.BunRuntimePluginClient",
            "QuickJsRuntimePluginClient.connectionPool" to "org.autojs.autojs.core.plugin.quickjs.QuickJsRuntimePluginClient",
        )

        fun bridgeThreads(): List<String> =
            GateMetrics.liveThreads().map { it.name }.filter { name -> BRIDGE_THREAD_MARKERS.any { name.contains(it) } }

        /** Host-side registries of T1 (h). zh-CN: T1 (h) 的宿主侧注册表. */
        fun hostRegistries(): Map<String, Int> {
            val counts = LinkedHashMap(StaticRegistries.bridgeCounts())
            AIDL_POOLS.forEach { (name, client) -> if (name !in counts) aidlPoolSize(client)?.let { counts[name] = it } }
            return counts
        }

        /** Size of `connectionPool` of the AidlPluginHost held by the client object [client], or null. */
        private fun aidlPoolSize(client: String): Int? = runCatching {
            val type = Class.forName(client)
            val host = type.getDeclaredField("host").apply { isAccessible = true }.get(null)!!
            val lock = field(host, host.javaClass, "poolLock")!!
            val pool = field(host, host.javaClass, "connectionPool") as Map<*, *>
            synchronized(lock) { pool.size }
        }.getOrNull()

        /**
         * Reachable instances of the session classes (kind -> class name) through VMDebug, which collects garbage first;
         * -1 for a kind that cannot be counted.
         * zh-CN: 经 VMDebug 计数的会话类 (类别 -> 类名) 可达实例数, VMDebug 会先回收垃圾; 无法计数的类别为 -1.
         */
        fun sessionInstances(classes: Map<String, String>): Map<String, Long> {
            if (classes.isEmpty()) return emptyMap()
            val counts = GateNativeProbe.instanceCounts(classes)
            return classes.keys.associateWith { counts.optLong(it, -1L) }
        }

        /**
         * System-side binding count of T1 (h): the connections of this process in `LoadedApk.mServices` whose active component
         * belongs to one of [packages], read through reflection (allowed by `hidden_api_policy 1`, ROADMAP D9). Returns
         * `unavailable` with the reason when a field cannot be read.
         * zh-CN: T1 (h) 的系统侧绑定计数: 本进程 `LoadedApk.mServices` 中活动组件属于 [packages] 的连接数, 经反射读取 (由 `hidden_api_policy 1` 放行,
         * ROADMAP D9). 字段读不到时返回 `unavailable` 与原因.
         */
        fun systemBindings(context: Context, packages: Set<String>): JSONObject = try {
            val threadClass = Class.forName("android.app.ActivityThread")
            val thread = threadClass.getMethod("currentActivityThread").invoke(null)
            val loadedPackages = field(thread, threadClass, "mPackages") as Map<*, *>
            val loadedApk = (loadedPackages[context.packageName] as? WeakReference<*>)?.get()
                ?: error("no LoadedApk for ${context.packageName}")
            val services = field(loadedApk, loadedApk.javaClass, "mServices") as Map<*, *>
            var connections = 0
            var plugin = 0
            synchronized(services) {
                services.values.forEach { perContext ->
                    (perContext as Map<*, *>).values.forEach { dispatcher ->
                        connections++
                        val active = field(dispatcher!!, dispatcher.javaClass, "mActiveConnections") as Map<*, *>
                        if (active.keys.any { (it as? ComponentName)?.packageName in packages }) plugin++
                    }
                }
            }
            JSONObject().put("serviceConnections", connections).put("pluginConnections", plugin)
        } catch (error: Throwable) {
            JSONObject().put("unavailable", (error.javaClass.simpleName + ": " + error.message).take(300))
        }

        private fun field(owner: Any, type: Class<*>, name: String): Any? =
            type.getDeclaredField(name).apply { isAccessible = true }.get(owner)

        /**
         * PSS of every process of [packageName] (T1 (i), recorded only), read through the UiAutomation shell; null when no process runs.
         * With [uid], the processes of that uid count too (the bun child processes of the Bun plugin).
         * zh-CN: [packageName] 全部进程的 PSS (T1 (i), 只记录), 经 UiAutomation shell 读取; 没有进程时为 null.
         * 给出 [uid] 时, 该 uid 的进程也计入 (Bun 插件的 bun 子进程).
         */
        fun pluginPss(instrumentation: Instrumentation, packageName: String, uid: Int? = null): JSONObject? {
            val processes = shell(instrumentation, "ps -A -o PID,UID,NAME").orEmpty().lines().mapNotNull { line ->
                val parts = line.trim().split(Regex("\\s+"))
                val pid = parts.getOrNull(0)?.toIntOrNull() ?: return@mapNotNull null
                val processUid = parts.getOrNull(1)?.toIntOrNull()
                val name = parts.getOrNull(2) ?: return@mapNotNull null
                if (name == packageName || name.startsWith("$packageName:") || (uid != null && processUid == uid)) pid to name else null
            }
            if (processes.isEmpty()) return null
            val detail = JSONArray()
            var total = 0L
            processes.forEach { (pid, name) ->
                val pss = shell(instrumentation, "dumpsys meminfo $pid")?.let(GateNativeProbe::parseMeminfo)?.optLong("totalPssKb", -1L) ?: -1L
                if (pss > 0) total += pss
                detail.put(JSONObject().put("process", name).put("pid", pid).put("totalPssKb", pss))
            }
            return JSONObject().put("totalPssKb", total).put("processes", detail)
        }

        /** Output of a shell command run through UiAutomation, or null. zh-CN: 经 UiAutomation 执行的 shell 命令的输出, 或 null. */
        fun shell(instrumentation: Instrumentation, command: String): String? = runCatching {
            val automation = instrumentation.getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES)
            ParcelFileDescriptor.AutoCloseInputStream(automation.executeShellCommand(command)).bufferedReader().use { it.readText() }
        }.getOrNull()
    }
}

/**
 * The engines and session objects of one run, as weak references.
 * zh-CN: 一次运行的引擎与会话对象, 均为弱引用.
 */
internal class BridgeWindow {

    private class TrackedEngine(engine: ScriptEngine<*>) {
        val engine = WeakReference<ScriptEngine<*>>(engine)

        @Volatile
        var removed = false

        @Volatile
        var runtime: WeakReference<ScriptRuntime>? = null
    }

    private class TrackedSession(val kind: String, val executionId: String?, instance: Any) {
        val reference = WeakReference(instance)
    }

    private val engines = CopyOnWriteArrayList<TrackedEngine>()
    private val sessions = CopyOnWriteArrayList<TrackedSession>()

    fun engineCreated(engine: ScriptEngine<*>) {
        engines += TrackedEngine(engine)
    }

    fun engineRemoved(engine: ScriptEngine<*>) {
        val tracked = engines.firstOrNull { it.engine.get() === engine } ?: return
        tracked.runtime = (engine as? JavaScriptEngine)?.runtime?.let { WeakReference(it) }
        tracked.removed = true
    }

    fun sessionCreated(kind: String, executionId: String?, instance: Any) {
        sessions += TrackedSession(kind, executionId, instance)
    }

    fun created() = engines.size
    fun allRemoved() = engines.all { it.removed }
    fun notRemoved() = engines.count { !it.removed }
    fun runtimeReferences(): List<WeakReference<*>> = engines.mapNotNull { it.runtime }
    fun engineReferences(): List<WeakReference<*>> = engines.map { it.engine }
    fun sessionReferences(): Map<String, List<WeakReference<*>>> = sessions.groupBy({ it.kind }, { it.reference })
    fun executionIds(): List<String> = sessions.mapNotNull { it.executionId }.distinct()

    /** Stops the engines still alive on the main thread, as the task list's stop button does. */
    fun forceStopAlive() {
        val alive = engines.filter { !it.removed }.map { it.engine }
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            alive.forEach { reference -> reference.get()?.let { runCatching { it.forceStop() } } }
        }
    }
}
