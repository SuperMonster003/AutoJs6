package org.autojs.autojs.core.automator.flow

import org.autojs.autojs.core.automator.FakeUiNode
import java.util.concurrent.LinkedBlockingQueue

/**
 * Fakes for the Flow core tests: a manual clock, lanes that queue tasks until the test drains
 * them, bindings over Kotlin lambdas and [FakeUiNode], and a real single-thread lane for the
 * few tests that need actual blocking.
 * zh-CN: Flow 核心测试的替身: 手动时钟, 把任务排队直到测试排空的 lane, 基于 Kotlin lambda 与 [FakeUiNode] 的 bindings, 以及少数
 * 需要真实阻塞的测试所用的真实单线程 lane.
 */
class FakeClock(var now: Long = 0L) {
    fun advance(millis: Long) {
        now += millis
    }
}

class FakeLane(val name: String) : FlowLane {

    private val queue = ArrayDeque<Runnable>()

    var executed = 0
        private set

    var interrupts = 0
        private set

    val size: Int get() = queue.size

    override fun post(task: Runnable) {
        queue.addLast(task)
    }

    override fun interruptCurrent() {
        interrupts += 1
    }

    fun runOne(): Boolean {
        val task = queue.removeFirstOrNull() ?: return false
        task.run()
        executed += 1
        return true
    }

    fun runAll() {
        @Suppress("ControlFlowWithEmptyBody")
        while (runOne()) {
        }
    }

}

/** A callback the fake bindings can invoke. zh-CN: 假 bindings 能调用的回调. */
typealias Fn = (List<Any?>) -> Any?

class FakeBindings : FlowBindings {

    /** What a `String` condition currently finds, if anything. zh-CN: `String` 条件此刻找到的东西 (若有). */
    val screen = HashMap<String, Any?>()

    var probes = 0
        private set

    val probeLog = ArrayList<Pair<Any?, FlowScope>>()

    val invoked = ArrayList<Pair<Any?, List<Any?>>>()

    val globalActions = ArrayList<String>()

    var globalResult = true

    /** What `awaitService` answers; the timeouts asked for are recorded. zh-CN: `awaitService` 的回答; 请求的超时被记录. */
    var onAwaitService: (Long) -> Boolean = { true }

    val serviceWaits = ArrayList<Long>()

    /** The result of a node action, or `null` for a target the action does not apply to. zh-CN: 节点动作的结果, 或对不适用目标为 `null`. */
    var perform: (action: String, target: Any?, args: List<Any?>) -> Boolean? = { _, target, _ ->
        (target as? FakeUiNode)?.performAction(0)
    }

    override fun probe(cond: Any?, scope: FlowScope): Any? {
        probes += 1
        probeLog += cond to scope
        return when (cond) {
            is Function0<*> -> cond()
            is String -> screen[cond]
            else -> cond
        }
    }

    override fun describe(cond: Any?): String? = when (cond) {
        is String -> "text(\"$cond\")"
        is Function0<*> -> "fn"
        else -> cond?.toString()
    }

    @Suppress("UNCHECKED_CAST")
    override fun invoke(fn: Any?, args: List<Any?>): Any? {
        invoked += fn to args
        return (fn as Fn)(args)
    }

    override fun perform(action: String, target: Any?, args: List<Any?>): Boolean? = perform.invoke(action, target, args)

    override fun performGlobal(action: String, args: List<Any?>): Boolean {
        globalActions += action
        return globalResult
    }

    override fun awaitService(timeoutMillis: Long): Boolean {
        serviceWaits += timeoutMillis
        return onAwaitService(timeoutMillis)
    }

}

/** The scheduler with every dependency faked; [drain] runs both lanes until they are empty. zh-CN: 全部依赖为替身的调度器; [drain] 运行两个 lane 直到清空. */
class FlowRig(random: Double = 0.0) {

    val clock = FakeClock()
    val worker = FakeLane("worker")
    val script = FakeLane("script")
    val bindings = FakeBindings()
    val sleeps = ArrayList<Long>()
    val scheduler = FlowScheduler(
        worker, script, bindings,
        clock = { clock.now },
        sleep = { millis ->
            sleeps += millis
            clock.advance(millis)
        },
        random = { random },
    )

    fun drain() {
        @Suppress("ControlFlowWithEmptyBody")
        while (worker.runOne() || script.runOne()) {
        }
    }

    /** A condition that yields [value] from the [from]-th probe on (1-based). zh-CN: 从第 [from] 次探测起 (从 1 计) 给出 [value] 的条件. */
    fun appearing(from: Int, value: Any = FakeUiNode.root("target")): () -> Any? {
        var calls = 0
        return { if (++calls >= from) value else null }
    }

    val never: () -> Any? = { null }

}

/** A real single-thread lane, for the tests that block. zh-CN: 真实的单线程 lane, 供需要阻塞的测试使用. */
class ThreadLane(name: String) : FlowLane {

    private val queue = LinkedBlockingQueue<Runnable>()

    @Volatile
    private var running = false

    private val thread = Thread({
        while (true) {
            val task = try {
                queue.take()
            } catch (_: InterruptedException) {
                break
            }
            running = true
            try {
                task.run()
            } catch (_: Throwable) {
                // Kept out of the way of the test; the scheduler reports failures through the nodes.
            } finally {
                running = false
                Thread.interrupted()
            }
        }
    }, name).apply {
        isDaemon = true
        start()
    }

    override fun post(task: Runnable) {
        queue.put(task)
    }

    override fun interruptCurrent() {
        if (running) thread.interrupt()
    }

}
