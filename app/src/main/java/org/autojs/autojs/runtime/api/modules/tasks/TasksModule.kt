package org.autojs.autojs.runtime.api.modules.tasks

import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.tasks.Tasks
import org.autojs.autojs.runtime.api.modules.ScriptFunction
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.autojs.autojs.runtime.api.modules.global.GlobalValues
import org.autojs.autojs.timing.IntentTask
import org.autojs.autojs.timing.TimedTask
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject

/**
 * The `tasks` module on the new script module layer (roadmap P3.4): the functions of the current module in its order,
 * served by its implementation (`augment/tasks/Tasks`: the option parsing, the task database and the completion callbacks;
 * to be moved out before P5.2). This layer adds the signatures (T4) and checks the arguments of D10 first ([TaskArgs]):
 * options that are not an object, a task that is not a `TimedTask` / `IntentTask`, an id or a flag without an integer and
 * days that are not an array of integers are TypeError / RangeError (the current module threw IllegalArgumentException);
 * the fields inside the options keep the current checks.
 *
 * zh-CN: 新脚本模块层上的 tasks 模块 (P3.4): 函数及顺序同现有模块, 由其实现完成 (augment/tasks/Tasks: 选项解析, 任务数据库与完成回调; P5.2 前迁出). 本层增加
 * 签名 (T4) 并先按 D10 检查参数 (TaskArgs): 选项不是对象, 任务不是 TimedTask / IntentTask, ID 或标志不是整数, 星期不是整数数组, 均为 TypeError / RangeError
 * (现有模块抛 IllegalArgumentException); 选项内部的字段沿用现有检查.
 */
@ScriptModule(name = "tasks")
class TasksModule(private val runtime: ScriptRuntime) {

    @ScriptFunction
    fun addTask(task: Any?): Any? = Tasks.addTask(runtime, arrayOf(TaskArgs.task("tasks.addTask", task)))

    @ScriptFunction
    fun addTimedTask(options: NativeObject? = null): Any? = Tasks.addTimedTask(runtime, args(options))

    @ScriptFunction
    fun addDailyTask(options: NativeObject? = null): Any? = Tasks.addDailyTask(runtime, args(options))

    @ScriptFunction
    fun addWeeklyTask(options: NativeObject? = null): Any? = Tasks.addWeeklyTask(runtime, args(options))

    @ScriptFunction
    fun addMonthlyTask(options: NativeObject? = null): Any? = Tasks.addMonthlyTask(runtime, args(options))

    @ScriptFunction
    fun addYearlyTask(options: NativeObject? = null): Any? = Tasks.addYearlyTask(runtime, args(options))

    @ScriptFunction
    fun addDisposableTask(options: NativeObject? = null): Any? = Tasks.addDisposableTask(runtime, args(options))

    @ScriptFunction
    fun addCountdownTask(options: NativeObject? = null): Any? = Tasks.addCountdownTask(runtime, args(options))

    @ScriptFunction
    fun addIntentTask(options: NativeObject? = null): Any? = Tasks.addIntentTask(runtime, args(options))

    @ScriptFunction
    fun addBroadcastIntentTask(options: NativeObject? = null): Any? = Tasks.addBroadcastIntentTask(runtime, args(options))

    @ScriptFunction
    fun getTimedTask(id: Any?): TimedTask? = Tasks.getTimedTask(runtime, arrayOf(TaskArgs.id("tasks.getTimedTask", id)))

    @ScriptFunction
    fun getIntentTask(id: Any?): IntentTask? = Tasks.getIntentTask(runtime, arrayOf(TaskArgs.id("tasks.getIntentTask", id)))

    @ScriptFunction
    fun removeTask(task: Any?): Any? = Tasks.removeTask(runtime, arrayOf(TaskArgs.task("tasks.removeTask", task)))

    @ScriptFunction
    fun removeTimedTask(id: Any?, options: NativeObject? = null): Any? =
        Tasks.removeTimedTask(runtime, arrayOf(TaskArgs.id("tasks.removeTimedTask", id), *args(options)))

    @ScriptFunction
    fun removeIntentTask(id: Any?, options: NativeObject? = null): Any? =
        Tasks.removeIntentTask(runtime, arrayOf(TaskArgs.id("tasks.removeIntentTask", id), *args(options)))

    @ScriptFunction
    fun updateTask(task: Any?): Any? = Tasks.updateTask(runtime, arrayOf(TaskArgs.task("tasks.updateTask", task)))

    @ScriptFunction
    fun queryTimedTasks(options: NativeObject? = null): NativeArray = Tasks.queryTimedTasks(runtime, args(options))

    @ScriptFunction
    fun queryIntentTasks(options: NativeObject? = null): NativeArray = Tasks.queryIntentTasks(runtime, args(options))

    @ScriptFunction
    fun timeFlagToDays(flag: Any?): NativeArray = Tasks.timeFlagToDays(runtime, arrayOf(GlobalValues.int("tasks.timeFlagToDays", "flag", flag)))

    @ScriptFunction
    fun daysToTimeFlag(days: Any?): Int = Tasks.daysToTimeFlag(runtime, arrayOf(TaskArgs.days("tasks.daysToTimeFlag", days)))

    private fun args(options: NativeObject?): Array<Any?> = if (options == null) emptyArray() else arrayOf(options)

}
