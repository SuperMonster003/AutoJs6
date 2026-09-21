package org.autojs.autojs.core.plugin.mail

import org.autojs.plugin.mail.api.MailContract

/**
 * Decides which "mail arrived" tasks a background-watch event launches (mail roadmap P8):
 * an arrival already seen (same watch, folder and uid, [DEDUP_CAPACITY] most recent) launches
 * nothing; each task listens to one watch or to every watch and may filter the message; a task
 * launched less than `MIN_TRIGGER_INTERVAL_MS` ago is not launched again at once, the latest
 * event is kept and launched once the interval has passed (a burst of arrivals collapses into
 * one launch per interval per task, the last message wins). Single-threaded by contract: the
 * receiver calls it on the main thread and [schedule] posts back to the same thread. Pure Kotlin.
 * zh-CN: 决定一个后台守望事件启动哪些 "邮件到达" 任务 (邮件路线图 P8): 已见过的到达 (同守望, 文件夹与 uid, 最近 [DEDUP_CAPACITY] 条) 不启动;
 * 每个任务监听一个或全部守望并可过滤消息; 距上次启动不足 `MIN_TRIGGER_INTERVAL_MS` 的任务不立即再启动, 保留最新事件并在间隔过后启动一次
 * (一阵到达合并为每任务每间隔一次启动, 最后一封胜出). 约定单线程: 接收器在主线程调用, [schedule] 回投同一线程. 纯 Kotlin.
 */
class MailTriggerDispatcher(
    private val clock: () -> Long = System::currentTimeMillis,
    private val schedule: (delayMs: Long, action: () -> Unit) -> Unit,
    private val minIntervalMs: Long = MailContract.MIN_TRIGGER_INTERVAL_MS,
) {
    /** One candidate task: its identity, its extras and how to launch it with an event. zh-CN: 一个候选任务: 标识, extras 与用事件启动它的方式. */
    class Target(val id: Long, val extras: MailTriggerTaskExtras, val launch: (MailTriggerEvent) -> Unit)

    private class TaskState(var lastLaunchAt: Long = Long.MIN_VALUE, var pending: MailTriggerEvent? = null, var pendingTarget: Target? = null)

    private val seen = object : LinkedHashMap<String, Boolean>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Boolean>?): Boolean = size > DEDUP_CAPACITY
    }
    private val states = HashMap<Long, TaskState>()

    /** Deliveries dropped as repeats. zh-CN: 作为重复丢弃的投递数. */
    var duplicates: Long = 0
        private set

    /** Launches delayed by the throttle (each may supersede an earlier pending one). zh-CN: 被节流推迟的启动数 (可能取代更早的待启动). */
    var throttled: Long = 0
        private set

    /**
     * Handles one event against the current [targets]; returns the ids of the tasks launched at
     * once (throttled ones follow later through [schedule]).
     * zh-CN: 用当前 [targets] 处理一个事件; 返回立即启动的任务 id (被节流的稍后经 [schedule] 启动).
     */
    fun dispatch(event: MailTriggerEvent, targets: List<Target>): List<Long> {
        if (seen.put(event.dedupKey, true) != null) {
            duplicates++
            return emptyList()
        }
        val now = clock()
        val launched = ArrayList<Long>()
        targets.forEach { target ->
            if (!target.extras.accepts(event.triggerId)) return@forEach
            if (!target.extras.filter.matches(event.message)) return@forEach
            val state = states.getOrPut(target.id) { TaskState() }
            val elapsed = now - state.lastLaunchAt
            if (state.lastLaunchAt != Long.MIN_VALUE && elapsed < minIntervalMs) {
                throttled++
                val first = state.pending == null
                state.pending = event
                state.pendingTarget = target
                if (first) schedule(minIntervalMs - elapsed) { flush(target.id) }
                return@forEach
            }
            state.lastLaunchAt = now
            target.launch(event)
            launched += target.id
        }
        return launched
    }

    private fun flush(taskId: Long) {
        val state = states[taskId] ?: return
        val event = state.pending ?: return
        val target = state.pendingTarget ?: return
        state.pending = null
        state.pendingTarget = null
        state.lastLaunchAt = clock()
        target.launch(event)
    }

    companion object {
        /** Arrivals remembered for the repeat check. zh-CN: 重复检查记住的到达数. */
        const val DEDUP_CAPACITY = 512
    }
}
