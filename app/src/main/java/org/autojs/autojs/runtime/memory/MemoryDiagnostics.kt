package org.autojs.autojs.runtime.memory

import android.content.Context
import android.os.Debug
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.Job
import org.autojs.autojs.core.image.ImageWrapper
import org.autojs.autojs.core.pref.Pref
import org.autojs.autojs.engine.JavaScriptEngine
import org.autojs.autojs.engine.ScriptEngine
import org.autojs.autojs.engine.ScriptEngineManager
import org.autojs.autojs.engine.ScriptEngineService
import org.autojs.autojs.execution.ScriptExecution
import org.autojs.autojs.execution.SimpleScriptExecutionListener
import org.autojs.autojs.leakcanary.LeakCanarySetup
import org.autojs.autojs.rhino.AndroidContextFactory
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.autojs.autojs.script.ScriptSource
import org.autojs.autojs6.R
import org.mozilla.javascript.ContextFactory
import java.io.File
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.ScheduledThreadPoolExecutor
import java.util.concurrent.TimeUnit

/**
 * The process-wide memory diagnostics (docs/memory/ROADMAP.md P2.1 / P2.3): [snapshot], [collect], [retained] and [dumpHeap] for the
 * memory gate, the developer options page and the script API. [install] hooks it into the engine service, after which every finished
 * Rhino runtime is tracked by a weak reference (with its id, source, end time and [ExitKind]), and 10 s after a script ends its runtime
 * is collected once and reported to the global console if it is still reachable (the developer options switch
 * "Warn in the console when a finished script is not reclaimed").
 *
 * zh-CN: 进程级内存诊断 (docs/memory/ROADMAP.md P2.1 / P2.3): 为内存门禁, 开发者选项页面与脚本 API 提供 [snapshot], [collect], [retained]
 * 与 [dumpHeap]. [install] 把它挂到引擎服务上, 之后每个已结束的 Rhino runtime 以弱引用登记 (含 id, 来源, 结束时间与 [ExitKind]); 脚本结束
 * 10 s 后对其 runtime 回收一次, 仍可达时向全局控制台报告 (开发者选项开关 "脚本结束后未回收时在控制台警告").
 */
object MemoryDiagnostics : MemoryDiagnosticsCore(AndroidMemoryProbes, AndroidMemoryClock) {

    private const val TAG = "MemoryDiagnostics"

    // The exit kind seen by the execution listener before the engine is destroyed.
    // zh-CN: 引擎销毁前由执行监听器得到的结束方式.
    private const val ENGINE_TAG_EXIT_KIND = "org.autojs.autojs.runtime.memory.exitKind"

    private const val CHECK_THREAD_NAME = "MemoryDiagnostics"
    private const val CHECK_THREAD_KEEP_ALIVE_S = 30L

    @Volatile
    private var installed = false

    private val scheduleLock = Any()
    private var scheduledCheck: ScheduledFuture<*>? = null
    private var scheduledCheckAt = Long.MAX_VALUE

    private val checkExecutor by lazy {
        ScheduledThreadPoolExecutor(1) { runnable -> Thread(runnable, CHECK_THREAD_NAME).apply { isDaemon = true } }.apply {
            setKeepAliveTime(CHECK_THREAD_KEEP_ALIVE_S, TimeUnit.SECONDS)
            allowCoreThreadTimeOut(true)
        }
    }

    /**
     * Hooks the diagnostics into [service] (called once by its constructor): the engine lifecycle callback tracks finished runtimes,
     * the execution listener remembers errors and interruptions for [ExitKind]. LeakCanary keeps its own hook in the engine service.
     * zh-CN: 把诊断挂到 [service] 上 (由其构造函数调用一次): 引擎生命周期回调登记已结束的 runtime, 执行监听器记下错误与中断以得到 [ExitKind].
     * LeakCanary 在引擎服务中保留自己的挂点.
     */
    @JvmStatic
    fun install(service: ScriptEngineService) {
        if (installed) return
        installed = true
        service.registerEngineLifecycleCallback(object : ScriptEngineManager.EngineLifecycleCallback {
            override fun onEngineCreate(engine: ScriptEngine<out ScriptSource>) = Unit
            override fun onEngineRemove(engine: ScriptEngine<out ScriptSource>) = onEngineRemoved(engine)
        })
        service.registerGlobalScriptExecutionListener(object : SimpleScriptExecutionListener() {
            override fun onException(execution: ScriptExecution, e: Throwable) {
                val kind = if (ScriptInterruptedException.causedByInterrupt(e)) ExitKind.STOPPED else ExitKind.ERROR
                execution.engine?.setTag(ENGINE_TAG_EXIT_KIND, kind)
            }
        })
    }

    /**
     * Writes an hprof heap dump to [file] (it holds everything in the heap, script variables included).
     * zh-CN: 把 hprof 堆转储写入 [file] (其中包含堆中全部数据, 含脚本变量).
     */
    @JvmStatic
    fun dumpHeap(file: File): File {
        file.parentFile?.mkdirs()
        Debug.dumpHprofData(file.absolutePath)
        return file
    }

    /**
     * What LeakCanary is doing, in words, or null when the build has no LeakCanary (release builds).
     * zh-CN: 以文字说明 LeakCanary 的状态; 构建中没有 LeakCanary (release 构建) 时为 null.
     */
    @JvmStatic
    fun describeLeakCanary(context: Context): String? {
        val status = runCatching { LeakCanarySetup.status(context) }.getOrNull() ?: return null
        return when {
            !status.enabled -> context.getString(R.string.text_memory_leakcanary_off)
            status.automaticHeapDump -> context.getString(R.string.text_memory_leakcanary_analyzing, status.retainedObjects)
            else -> context.getString(R.string.text_memory_leakcanary_not_analyzing, status.retainedObjects)
        }
    }

    @JvmStatic
    fun isUnreclaimedWarningEnabled() = runCatching { Pref.getBoolean(R.string.key_memory_unreclaimed_warning, true) }.getOrDefault(true)

    private fun onEngineRemoved(engine: ScriptEngine<out ScriptSource>) {
        val runtime: ScriptRuntime = (engine as? JavaScriptEngine)?.runtime ?: return
        val source = engine.getTag(ScriptEngine.TAG_SOURCE) as? ScriptSource
        val name = runCatching { source?.name }.getOrNull() ?: "#${engine.id}"
        val path = runCatching { source?.elegantPath }.getOrNull() ?: name
        val warn = isUnreclaimedWarningEnabled()
        val resources = runCatching { runtime.resourceScope.trackedReferences() }.getOrDefault(emptyList())
        track(runtime, engine.id, name, path, exitKindOf(engine, runtime), check = warn, resources = resources)
        if (warn) scheduleCheck()
    }

    override fun onUnreleased(resource: UnreleasedResource) {
        Log.w(TAG, "Resource of a finished script still alive: ${resource.resource} [id=${resource.id}, script=${resource.name}, elapsed=${resource.elapsedMs} ms]")
    }

    private fun exitKindOf(engine: ScriptEngine<*>, runtime: ScriptRuntime): ExitKind {
        (engine.getTag(ENGINE_TAG_EXIT_KIND) as? ExitKind)?.let { return it }
        if (engine.uncaughtException != null) return ExitKind.ERROR
        // forceStop of a Rhino engine (also behind exit()) cancels the coroutine job of its runtime, nothing else does.
        // zh-CN: Rhino 引擎的 forceStop (exit() 也经由它) 会取消其 runtime 的协程 Job, 其他路径不会.
        val job = runCatching { runtime.coroutineScope.coroutineContext[Job] }.getOrNull()
        return if (job?.isCancelled == true) ExitKind.STOPPED else ExitKind.NORMAL
    }

    private fun scheduleCheck() {
        val at = nextCheckAt() ?: return
        synchronized(scheduleLock) {
            if (scheduledCheck != null && scheduledCheckAt <= at) return
            scheduledCheck?.cancel(false)
            scheduledCheckAt = at
            val delay = (at - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
            scheduledCheck = checkExecutor.schedule(Runnable { runCheck() }, delay, TimeUnit.MILLISECONDS)
        }
    }

    private fun runCheck() {
        synchronized(scheduleLock) {
            scheduledCheck = null
            scheduledCheckAt = Long.MAX_VALUE
        }
        runCatching { checkEnded().forEach(::warnUnreclaimed) }.onFailure { Log.w(TAG, "Unreclaimed runtime check failed", it) }
        scheduleCheck()
    }

    private fun warnUnreclaimed(runtime: RetainedRuntime) {
        val service = ScriptEngineService.getInstance() ?: return
        val context = service.languageContext
        val message = buildString {
            append(context.getString(R.string.text_memory_runtime_not_reclaimed, runtime.source))
            describeLeakCanary(context)?.let { append(" (").append(it).append(")") }
        }
        Log.w(TAG, "$message [id=${runtime.id}, exit=${runtime.exitKind.id}, elapsed=${runtime.elapsedMs} ms]")
        service.globalConsole.warn(message)
    }
}

private object AndroidMemoryProbes : JvmMemoryProbes() {

    override fun nativeHeapAllocated() = Debug.getNativeHeapAllocatedSize()

    override fun nativeHeapSize() = Debug.getNativeHeapSize()

    override fun pss() = Debug.getPss() * 1024

    override fun engines() = ScriptEngineService.getInstance()?.engines?.size ?: 0

    override fun images() = ImageWrapper.liveCount()

    override fun contexts() = (ContextFactory.getGlobal() as? AndroidContextFactory)?.activeContextCount ?: -1
}

private object AndroidMemoryClock : MemoryClock {

    override fun currentTimeMillis() = System.currentTimeMillis()

    override fun elapsedRealtime() = SystemClock.elapsedRealtime()

    override fun sleep(ms: Long) = SystemClock.sleep(ms)
}
