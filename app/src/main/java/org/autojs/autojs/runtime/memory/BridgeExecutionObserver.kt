package org.autojs.autojs.runtime.memory

/**
 * Reports the host-side session objects a plugin engine creates for one execution (docs/memory-phase2/ROADMAP.md T1 (a)), so that
 * the bridge leak gate can keep weak references to them. Nothing listens in production: [listener] stays null, the call sites only
 * read it, and this object never keeps a reference to what it reports.
 * zh-CN: 报告插件引擎为一次执行创建的宿主侧会话对象 (docs/memory-phase2/ROADMAP.md T1 (a)), 供桥接泄漏门禁持有其弱引用.
 * 生产环境没有监听者: [listener] 保持为 null, 调用处只读取它, 本对象也从不持有所报告对象的引用.
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

    @Volatile
    var listener: Listener? = null

    @JvmStatic
    fun created(engine: String, kind: String, executionId: String?, instance: Any) {
        listener?.created(engine, kind, executionId, instance)
    }
}
