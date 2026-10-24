package org.autojs.autojs.runtime.memory

/**
 * One reading of the memory diagnostics (docs/memory/ROADMAP.md P2.1, the shape of T9): bytes and counts.
 * [pss] is -1 when it was not read, [contexts] is -1 when the Rhino context factory does not count contexts.
 * zh-CN: 内存诊断的一次读数 (docs/memory/ROADMAP.md P2.1, 形状同 T9): 字节数与计数. 未读取时 [pss] 为 -1, Rhino Context 工厂不计数时 [contexts] 为 -1.
 */
data class MemorySnapshot(
    /** Java heap in use, `Runtime.totalMemory() - Runtime.freeMemory()`. zh-CN: 已用 Java 堆. */
    val javaHeapUsed: Long,
    /** Java heap reserved by the VM, `Runtime.totalMemory()`. zh-CN: 虚拟机已分配的 Java 堆. */
    val javaHeapTotal: Long,
    /** Java heap limit, `Runtime.maxMemory()`. zh-CN: Java 堆上限. */
    val javaHeapMax: Long,
    /** `Debug.getNativeHeapAllocatedSize()`. */
    val nativeHeapAllocated: Long,
    /** `Debug.getNativeHeapSize()`. */
    val nativeHeapSize: Long,
    /** Proportional set size of the process, `Debug.getPss()` in bytes. zh-CN: 进程的 PSS (字节). */
    val pss: Long,
    /** Live script engines (`ScriptEngineManager.engines`). zh-CN: 存活的脚本引擎数. */
    val engines: Int,
    /** Finished script runtimes the GC has not reclaimed yet ([MemoryDiagnosticsCore.retained]). zh-CN: 已结束但尚未被回收的脚本 runtime 数. */
    val retainedRuntimes: Int,
    /**
     * Finished plugin engine executions with a session object the GC has not reclaimed yet, per engine (docs/memory-phase2/ROADMAP.md
     * P4.4, [MemoryDiagnosticsCore.retainedBridgeSessions]); every engine of [MemoryDiagnosticsCore.BRIDGE_ENGINES] is listed, 0 included.
     * zh-CN: 会话对象尚未被回收的已结束插件引擎执行数, 按引擎计 (docs/memory-phase2/ROADMAP.md P4.4); [MemoryDiagnosticsCore.BRIDGE_ENGINES] 的每个引擎都列出, 含 0.
     */
    val bridgeSessions: Map<String, Int>,
    /** Script images neither recycled nor collected (`ImageWrapper` registry). zh-CN: 既未回收也未被 GC 的脚本图像数. */
    val images: Int,
    /** Threads created for scripts, told by their names ([MemoryDiagnosticsCore.isScriptThread]). zh-CN: 为脚本创建的线程数, 按线程名判断. */
    val threads: Int,
    /** All live threads of the process. zh-CN: 进程内全部存活线程数. */
    val allThreads: Int,
    /** Rhino contexts entered and not yet released (`AndroidContextFactory.activeContextCount`). zh-CN: 已进入未释放的 Rhino Context 数. */
    val contexts: Int,
    /** Wall clock time of the reading, in milliseconds since the epoch. zh-CN: 读数时刻 (自纪元起的毫秒数). */
    val timestamp: Long,
)

/**
 * How a script ended (docs/memory/ROADMAP.md P2.1).
 * zh-CN: 脚本的结束方式 (docs/memory/ROADMAP.md P2.1).
 */
enum class ExitKind(val id: String) {

    /** The script ended on its own. zh-CN: 脚本自行结束. */
    NORMAL("normal"),

    /** The script ended with an uncaught error. zh-CN: 脚本因未捕获的错误结束. */
    ERROR("error"),

    /** The script was stopped: forceStop (the task list, stopAll) or its own exit(). zh-CN: 脚本被停止: forceStop (任务列表, stopAll) 或脚本自身的 exit(). */
    STOPPED("stopped"),
}

/**
 * A finished script runtime that is still reachable (docs/memory/ROADMAP.md P2.1, T9 `memory.retained()`).
 * [source] is the path shown in the console when the script started, [elapsedMs] the time since it ended.
 * zh-CN: 已结束但仍可达的脚本 runtime (docs/memory/ROADMAP.md P2.1, T9 `memory.retained()`). [source] 为脚本开始运行时控制台显示的路径, [elapsedMs] 为结束后经过的时间.
 */
data class RetainedRuntime(
    val id: Int,
    val name: String,
    val source: String,
    val endedAt: Long,
    val elapsedMs: Long,
    val exitKind: ExitKind,
    /** [MemoryDiagnosticsCore.ENGINE_RHINO], or [MemoryDiagnosticsCore.ENGINE_NODE] for a Node.js script (docs/memory-phase2/ROADMAP.md P4.4). */
    val engine: String,
)

/**
 * A finished plugin engine execution with a session object still reachable (docs/memory-phase2/ROADMAP.md P4.4, T1 (a)). [engine] is
 * the engine family of the execution (one of [MemoryDiagnosticsCore.BRIDGE_ENGINES]), [elapsedMs] the time since it ended, [objects]
 * the class names of its session objects not reclaimed yet (the classes of T1 (a), without the package).
 * zh-CN: 仍有会话对象可达的已结束插件引擎执行 (docs/memory-phase2/ROADMAP.md P4.4, T1 (a)). [engine] 为执行所属的引擎类别 ([MemoryDiagnosticsCore.BRIDGE_ENGINES]
 * 之一), [elapsedMs] 为结束后经过的时间, [objects] 为尚未被回收的会话对象的类名 (即 T1 (a) 的类, 不含包名).
 */
data class RetainedBridgeSession(
    val engine: String,
    val executionId: String,
    val endedAt: Long,
    val elapsedMs: Long,
    val objects: List<String>,
)

/**
 * A resource tracked in the resource scope of a finished runtime (docs/memory/ROADMAP.md P4.1) that the unreclaimed check found still
 * alive, for example a thread that did not end or a window still referenced; [resource] is the name it was tracked with.
 * zh-CN: 已结束 runtime 的资源作用域中跟踪的, 未回收检查发现仍存活的资源 (docs/memory/ROADMAP.md P4.1), 例如未结束的线程或仍被引用的窗口;
 * [resource] 为跟踪时的名称.
 */
data class UnreleasedResource(
    val id: Int,
    val name: String,
    val source: String,
    val resource: String,
    val elapsedMs: Long,
)

/**
 * What LeakCanary is doing in a debug build, for the unreclaimed runtime warning (docs/memory/ROADMAP.md P2.3).
 * [automaticHeapDump] tells whether LeakCanary dumps and analyzes the heap by itself once enough objects are retained.
 * zh-CN: debug 构建中 LeakCanary 的状态, 供未回收警告使用 (docs/memory/ROADMAP.md P2.3). [automaticHeapDump] 表示残留对象足够多时 LeakCanary 是否自行转储并分析.
 */
data class LeakCanaryStatus(
    val enabled: Boolean,
    val retainedObjects: Int,
    val automaticHeapDump: Boolean,
)
