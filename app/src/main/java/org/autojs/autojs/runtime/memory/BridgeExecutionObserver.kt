package org.autojs.autojs.runtime.memory

/**
 * Reports the host-side session objects a plugin engine creates for one execution (docs/memory-phase2/ROADMAP.md T1 (a)): [created]
 * for the bridge leak gate, which keeps weak references to them, and [ended] once the execution has released its bridge objects, for
 * the memory diagnostics (P4.4), which tell the sessions still reachable after that. Nothing listens to [created] in production:
 * [listener] stays null. [endListener] is [MemoryDiagnostics] once it is installed. Neither this object nor its listeners keep a
 * strong reference to what is reported.
 * zh-CN: 报告插件引擎为一次执行创建的宿主侧会话对象 (docs/memory-phase2/ROADMAP.md T1 (a)): [created] 供桥接泄漏门禁持有其弱引用,
 * [ended] 在执行释放其桥接对象之后报告, 供内存诊断 (P4.4) 找出之后仍可达的会话. 生产环境中 [created] 没有监听者: [listener] 保持为 null.
 * [endListener] 在 [MemoryDiagnostics] 安装后即为它. 本对象及其监听者都不强引用所报告的对象.
 */
object BridgeExecutionObserver {

    fun interface Listener {
        /**
         * Called on the thread that created [instance]; [engine] is the engine family (node, bun, quickjs, lua, python), [kind] names
         * the object (broker, provider, ...), [executionId] is the execution it belongs to when known.
         * zh-CN: 在创建 [instance] 的线程上调用; [engine] 为引擎类别 (node, bun, quickjs, lua, python), [kind] 为对象名称 (broker, provider 等),
         * [executionId] 为其所属执行 (已知时).
         */
        fun created(engine: String, kind: String, executionId: String?, instance: Any)
    }

    fun interface EndListener {
        /**
         * Called once per execution, on the thread that ended it, after its bridge objects were released; [objects] are the session
         * objects of the execution still reachable at that point. The listener holds them weakly.
         * zh-CN: 每次执行调用一次, 在结束该执行的线程上, 于其桥接对象释放之后; [objects] 为此时仍可达的该执行的会话对象. 监听者只弱引用它们.
         */
        fun ended(engine: String, executionId: String, objects: List<Any>)
    }

    @Volatile
    var listener: Listener? = null

    @Volatile
    var endListener: EndListener? = null

    @JvmStatic
    fun created(engine: String, kind: String, executionId: String?, instance: Any) {
        listener?.created(engine, kind, executionId, instance)
    }

    @JvmStatic
    fun ended(engine: String, executionId: String, objects: List<Any>) {
        endListener?.ended(engine, executionId, objects)
    }
}
