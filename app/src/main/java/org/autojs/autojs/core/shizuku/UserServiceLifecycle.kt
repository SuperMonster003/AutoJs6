package org.autojs.autojs.core.shizuku

/**
 * The single exit path of the Shizuku user service process (docs/shizuku-oom/ROADMAP.md T3 / T7). The service registers
 * what it started for the host ([Child]: remote shell processes and in-flight execCommand processes), and [shutdown] ends
 * them before [exit] ends the process, so nothing the service started is left to init when it goes away. Hosts attach a
 * token ([attachHost]); once every attached host died, or no host attached within [attachTimeoutMillis] after [start], the
 * service shuts itself down. Shizuku only destroys a non-daemon service when its record goes away, which a killed host may
 * never let it see.
 * zh-CN: Shizuku 用户服务进程的唯一退出出口 (docs/shizuku-oom/ROADMAP.md T3 / T7). 服务登记它为宿主启动的内容 ([Child]: 远程 shell 进程与执行中的
 * execCommand 进程), [shutdown] 先结束它们再由 [exit] 结束进程, 服务退出时不会把它启动的进程留给 init. 宿主附着一个令牌 ([attachHost]); 全部已附着的
 * 宿主都死亡后, 或 [start] 之后 [attachTimeoutMillis] 内没有宿主附着, 服务自行退出. Shizuku 只在记录被移除时销毁非 daemon 服务, 而被杀的宿主可能
 * 让它永远等不到这一刻.
 */
internal class UserServiceLifecycle(
    private val scheduler: Scheduler,
    private val exit: (reason: String) -> Unit,
    private val attachTimeoutMillis: Long = ATTACH_TIMEOUT_MILLIS,
) {

    /** Something the service started for a host, ended by [shutdown]. zh-CN: 服务为宿主启动的内容, 由 [shutdown] 结束. */
    fun interface Child {
        fun terminate()
    }

    /** Runs an action once after a delay. zh-CN: 延迟后执行一次动作. */
    fun interface Scheduler {
        fun schedule(delayMillis: Long, action: Runnable): Cancellable
    }

    fun interface Cancellable {
        fun cancel()
    }

    /**
     * Runs [action] once. A caller racing with the first run waits until it finished: when the host dies, its client
     * token's death and [shutdown] both end a remote shell process, and the service exits as soon as [shutdown] returns,
     * which would cut a process tree being ended on the other thread short and leave its commands to init.
     * zh-CN: 只执行一次 [action]. 与首次执行竞争的调用方会等它执行完: 宿主死亡时, 它的 client 令牌死亡与 [shutdown] 都会结束同一个远程 shell 进程, 而
     * [shutdown] 一返回服务就退出, 另一线程正在结束的进程树会因此中断, 其中的命令被留给 init.
     */
    class Once(private val action: () -> Unit) {

        private var done = false

        /** True when this call ran [action]. zh-CN: 本次调用执行了 [action] 时返回 true. */
        @Synchronized
        fun run(): Boolean {
            if (done) return false
            done = true
            action()
            return true
        }

    }

    private val lock = Any()
    private val children = LinkedHashSet<Child>()
    private val hosts = HashSet<Any>()
    private var watchdog: Cancellable? = null
    private var attachedOnce = false
    private var shutdownReason: String? = null

    val isShuttingDown: Boolean
        get() = synchronized(lock) { shutdownReason != null }

    /** Starts the attach watchdog. zh-CN: 启动附着看门狗. */
    fun start() {
        val handle = scheduler.schedule(attachTimeoutMillis) { onAttachTimeout() }
        synchronized(lock) {
            if (shutdownReason == null && !attachedOnce) {
                watchdog = handle
                return
            }
        }
        handle.cancel()
    }

    /**
     * Adds [host]; false when it was attached already or the service is shutting down. The caller links the host's death
     * to [onHostDied].
     * zh-CN: 添加 [host]; 已附着过或服务正在退出时返回 false. 调用方把宿主的死亡关联到 [onHostDied].
     */
    fun attachHost(host: Any): Boolean {
        val handle: Cancellable?
        synchronized(lock) {
            if (shutdownReason != null || !hosts.add(host)) return false
            attachedOnce = true
            handle = watchdog
            watchdog = null
        }
        handle?.cancel()
        return true
    }

    /** [host] died; the service shuts down once no attached host is left. zh-CN: [host] 已死亡; 没有已附着的宿主时服务退出. */
    fun onHostDied(host: Any) {
        synchronized(lock) {
            if (!hosts.remove(host) || hosts.isNotEmpty()) return
        }
        shutdown(REASON_HOST_DIED)
    }

    /**
     * Registers [child]; when the service is already shutting down, [child] is terminated at once and false is returned.
     * zh-CN: 登记 [child]; 服务已在退出时立即结束 [child] 并返回 false.
     */
    fun register(child: Child): Boolean {
        synchronized(lock) {
            if (shutdownReason == null) {
                children.add(child)
                return true
            }
        }
        runCatching { child.terminate() }
        return false
    }

    fun unregister(child: Child) {
        synchronized(lock) { children.remove(child) }
    }

    /**
     * Terminates the registered children in registration order, then calls [exit] with [reason]. Only the first call
     * does anything; later ones (another trigger racing with it) return at once.
     * zh-CN: 按登记顺序结束已登记的内容, 然后以 [reason] 调用 [exit]. 只有第一次调用生效; 之后的调用 (与之竞争的其他触发) 立即返回.
     */
    fun shutdown(reason: String) {
        val pending: List<Child>
        val handle: Cancellable?
        synchronized(lock) {
            if (shutdownReason != null) return
            shutdownReason = reason
            pending = children.toList()
            children.clear()
            handle = watchdog
            watchdog = null
        }
        handle?.cancel()
        pending.forEach { runCatching { it.terminate() } }
        exit(reason)
    }

    private fun onAttachTimeout() {
        synchronized(lock) {
            if (attachedOnce) return
        }
        shutdown(REASON_NO_HOST_ATTACHED)
    }

    companion object {

        const val ATTACH_TIMEOUT_MILLIS = 30_000L

        const val REASON_DESTROY = "destroy"
        const val REASON_EXIT = "exit"
        const val REASON_HOST_DIED = "host-died"
        const val REASON_NO_HOST_ATTACHED = "no-host-attached"

    }

}
