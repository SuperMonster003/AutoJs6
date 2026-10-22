package org.autojs.autojs.runtime.api.modules.tasks

import org.autojs.autojs.runtime.api.modules.global.GlobalValues
import org.autojs.autojs.runtime.api.modules.global.GlobalValues.isNullish
import org.autojs.autojs.timing.IntentTask
import org.autojs.autojs.timing.TimedTask
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.Wrapper

/**
 * Argument rules shared by `tasks`, `workManager` and the task entries of `timers` (roadmap P3.4), checked before the
 * current implementation (`augment/tasks/Tasks`, `WorkManager`) runs: an id is an integer (the current `coerceLongNumber`,
 * truncating, now with TypeError / RangeError), a task is a `TimedTask`, an `IntentTask` or nullish, a day list is an
 * array of integers (D10; the current modules threw IllegalArgumentException).
 *
 * zh-CN: tasks, workManager 与 timers 的任务入口共用的参数规则 (P3.4), 在现有实现 (augment/tasks/Tasks, WorkManager) 运行前检查: ID 为整数 (同现有的
 * coerceLongNumber, 截断小数, 改为 TypeError / RangeError), 任务为 TimedTask, IntentTask 或 null / undefined, 星期列表为整数数组 (D10; 现有模块抛
 * IllegalArgumentException).
 */
internal object TaskArgs {

    fun id(function: String, id: Any?): Long = GlobalValues.long(function, "id", id)

    /** [task] as given when it is a task or nullish. */
    fun task(function: String, task: Any?): Any? = task.also {
        val value = if (it is Wrapper) it.unwrap() else it
        if (!isNullish(value) && value !is TimedTask && value !is IntentTask) {
            throw GlobalValues.typeError(function, "task", "TimedTask | IntentTask | null", it)
        }
    }

    /** [days] as given when it is nullish (no day) or an array of integers. */
    fun days(function: String, days: Any?): Any? = days.also {
        when (it) {
            is NativeArray -> it.forEachIndexed { i, e -> GlobalValues.int(function, "days[$i]", e) }
            else -> if (!isNullish(it)) throw GlobalValues.typeError(function, "days", "number[]", it)
        }
    }

}
