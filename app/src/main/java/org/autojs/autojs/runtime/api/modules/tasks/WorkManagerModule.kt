package org.autojs.autojs.runtime.api.modules.tasks

import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.tasks.WorkManager
import org.autojs.autojs.runtime.api.modules.ScriptFunction
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.mozilla.javascript.NativeObject

/**
 * The `workManager` module (alias `work_manager`, both with `$` forms) on the new script module layer (roadmap P3.4): the
 * Auto.js Pro compatible task facade of the current module, in its order, served by its implementation
 * (`augment/tasks/WorkManager` over `Tasks`; to be moved out before P5.2). `timers` shares these entries. Arguments as for
 * `tasks` ([TaskArgs]): options that are not an object and ids without an integer are TypeError / RangeError (D10; the
 * current module passed them on and failed with IllegalArgumentException), surplus arguments a TypeError (Q7; the current
 * module ignored them).
 *
 * zh-CN: 新脚本模块层上的 workManager 模块 (别名 work_manager, 均有 $ 形式, P3.4): 现有模块的 Auto.js Pro 兼容任务接口, 顺序相同, 由其实现完成
 * (augment/tasks/WorkManager, 基于 Tasks; P5.2 前迁出). timers 共用这些入口. 参数同 tasks (TaskArgs): 选项不是对象, ID 不是整数为 TypeError / RangeError
 * (D10; 现有模块交给下层后抛 IllegalArgumentException), 多余参数为 TypeError (Q7; 现有模块忽略).
 */
@ScriptModule(name = "workManager", aliases = ["work_manager"])
class WorkManagerModule(private val runtime: ScriptRuntime) {

    @ScriptFunction
    fun addDailyTask(options: NativeObject? = null): Any? = WorkManager.addDailyTask(runtime, args(options))

    @ScriptFunction
    fun addWeeklyTask(options: NativeObject? = null): Any? = WorkManager.addWeeklyTask(runtime, args(options))

    @ScriptFunction
    fun addDisposableTask(options: NativeObject? = null): Any? = WorkManager.addDisposableTask(runtime, args(options))

    @ScriptFunction
    fun addIntentTask(options: NativeObject? = null): Any? = WorkManager.addIntentTask(runtime, args(options))

    @ScriptFunction
    fun addBroadcastIntentTask(options: NativeObject? = null): Any? = WorkManager.addBroadcastIntentTask(runtime, args(options))

    @ScriptFunction
    fun getTimedTask(id: Any?): Any? = WorkManager.getTimedTask(runtime, arrayOf(TaskArgs.id("workManager.getTimedTask", id)))

    @ScriptFunction
    fun getIntentTask(id: Any?): Any? = WorkManager.getIntentTask(runtime, arrayOf(TaskArgs.id("workManager.getIntentTask", id)))

    @ScriptFunction
    fun removeTimedTask(id: Any?): Boolean? = WorkManager.removeTimedTask(runtime, arrayOf(TaskArgs.id("workManager.removeTimedTask", id)))

    @ScriptFunction
    fun removeIntentTask(id: Any?): Boolean? = WorkManager.removeIntentTask(runtime, arrayOf(TaskArgs.id("workManager.removeIntentTask", id)))

    @ScriptFunction
    fun queryTimedTasks(options: NativeObject? = null): Any = WorkManager.queryTimedTasks(runtime, args(options))

    @ScriptFunction
    fun queryIntentTasks(options: NativeObject? = null): Any = WorkManager.queryIntentTasks(runtime, args(options))

    private fun args(options: NativeObject?): Array<Any?> = if (options == null) emptyArray() else arrayOf(options)

}
