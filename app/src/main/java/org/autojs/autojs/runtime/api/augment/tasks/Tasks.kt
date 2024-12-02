package org.autojs.autojs.runtime.api.augment.tasks

import org.autojs.autojs.annotation.RhinoFunctionBody
import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.extension.AnyExtensions.jsBrief
import org.autojs.autojs.extension.AnyExtensions.toRuntimePath
import org.autojs.autojs.extension.ArrayExtensions.toNativeArray
import org.autojs.autojs.extension.FlexibleArray
import org.autojs.autojs.extension.ScriptableExtensions.prop
import org.autojs.autojs.extension.ScriptableObjectExtensions.acquire
import org.autojs.autojs.extension.ScriptableObjectExtensions.inquire
import org.autojs.autojs.external.receiver.DynamicBroadcastReceivers
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.api.augment.threads.Threads
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.autojs.timing.IntentTask
import org.autojs.autojs.timing.TimedTask
import org.autojs.autojs.timing.TimedTaskManager
import org.autojs.autojs.util.RhinoUtils.callFunction
import org.autojs.autojs.util.RhinoUtils.coerceArray
import org.autojs.autojs.util.RhinoUtils.coerceBoolean
import org.autojs.autojs.util.RhinoUtils.coerceIntNumber
import org.autojs.autojs.util.RhinoUtils.coerceLongNumber
import org.autojs.autojs.util.RhinoUtils.coerceObject
import org.autojs.autojs.util.RhinoUtils.coerceString
import org.autojs.autojs.util.RhinoUtils.isUiThread
import org.autojs.autojs.util.RhinoUtils.newNativeArray
import org.autojs.autojs.util.RhinoUtils.newNativeObject
import org.autojs.autojs.util.RhinoUtils.now
import org.joda.time.LocalDateTime
import org.joda.time.LocalTime
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeDate
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.ScriptableObject
import java.time.LocalDate
import kotlin.math.pow

@Suppress("unused", "UNUSED_PARAMETER")
class Tasks(scriptRuntime: ScriptRuntime) : Augmentable(scriptRuntime) {

    override val selfAssignmentFunctions = listOf(
        ::addTask.name,
        ::addDailyTask.name,
        ::addWeeklyTask.name,
        ::addDisposableTask.name,
        ::addIntentTask.name,
        ::getTimedTask.name,
        ::getIntentTask.name,
        ::removeTask.name,
        ::removeTimedTask.name,
        ::removeIntentTask.name,
        ::updateTask.name,
        ::queryTimedTasks.name,
        ::queryIntentTasks.name,
        ::timeFlagToDays.name,
        ::daysToTimeFlag.name,
    )

    companion object : FlexibleArray() {

        private val daysOfWeekFlatList: List<String> = listOf(
            "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday",
            "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun",
            "一", "二", "三", "四", "五", "六", "日",
            1, 2, 3, 4, 5, 6, 0,
            1, 2, 3, 4, 5, 6, 7,
        ).map { "$it".lowercase() }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun addTask(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any = ensureArgumentsOnlyOne(args) {
            addTaskRhino(it)
        }

        @JvmStatic
        @RhinoFunctionBody
        fun addTaskRhino(task: Any?): Any {
            when (task) {
                is TimedTask -> TimedTaskManager.addTaskSync(task)
                is IntentTask -> TimedTaskManager.addTaskSync(task)
                else -> listOf(
                    "Argument task ${task.jsBrief()} for tasks.addTask",
                    "must be either TimedTask or IntentTask",
                ).joinToString(" ").let { throw WrappedIllegalArgumentException(it) }
            }
            return task
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun addDailyTask(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? = ensureArgumentsAtMost(args, 1) { argList ->
            val (options) = argList
            val opt = coerceObject(options, newNativeObject())

            TimedTask.dailyTask(
                parseLocalTime(opt),
                opt.acquire("path") { it.toRuntimePath(scriptRuntime) },
                parseConfig(opt),
            ).let { task -> taskFulfilled(scriptRuntime, addTaskRhino(task), opt) }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun addWeeklyTask(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? = ensureArgumentsAtMost(args, 1) { argList ->
            val (options) = argList
            val opt = coerceObject(options, newNativeObject())

            TimedTask.weeklyTask(
                parseLocalTime(opt),
                parseDayOfWeekTimeFlag(opt).toLong(),
                opt.acquire("path") { it.toRuntimePath(scriptRuntime) },
                parseConfig(opt),
            ).let { task -> taskFulfilled(scriptRuntime, addTaskRhino(task), opt) }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun addDisposableTask(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? = ensureArgumentsAtMost(args, 1) { argList ->
            val (options) = argList
            val opt = coerceObject(options, newNativeObject())

            TimedTask.disposableTask(
                parseLocalDateTime(opt),
                opt.acquire("path") { it.toRuntimePath(scriptRuntime) },
                parseConfig(opt),
            ).let { task -> taskFulfilled(scriptRuntime, addTaskRhino(task), opt) }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun addIntentTask(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? = ensureArgumentsAtMost(args, 1) { argList ->
            val (options) = argList
            val opt = coerceObject(options, newNativeObject())

            IntentTask().apply {
                opt.acquire("path") { it.toRuntimePath(scriptRuntime) }.let {
                    scriptPath = it
                }
                opt.inquire("action") { coerceString(it) }?.let {
                    action = it
                    if (action == DynamicBroadcastReceivers.ACTION_STARTUP) {
                        // @Indecision opt.local
                        isLocal = true
                    }
                }
                opt.inquire("dataType") { coerceString(it) }?.let {
                    dataType = it
                }
                opt.inquire(listOf("isLocal", "local")) { coerceBoolean(it) }?.let {
                    // @FinalDecision opt.action
                    isLocal = it
                }
            }.let { task -> taskFulfilled(scriptRuntime, addTaskRhino(task), opt) }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun getTimedTask(scriptRuntime: ScriptRuntime, args: Array<out Any?>): TimedTask? = ensureArgumentsOnlyOne(args) {
            getTimedTaskRhino(it)
        }

        @JvmStatic
        @RhinoFunctionBody
        fun getTimedTaskRhino(it: Any?): TimedTask? = TimedTaskManager.getTimedTask(coerceLongNumber(it))

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun getIntentTask(scriptRuntime: ScriptRuntime, args: Array<out Any?>): IntentTask? = ensureArgumentsOnlyOne(args) {
            getIntentTaskRhino(it)
        }

        @JvmStatic
        @RhinoFunctionBody
        fun getIntentTaskRhino(it: Any?) = TimedTaskManager.getIntentTask(coerceLongNumber(it))

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun removeTask(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsOnlyOne(args) {
            removeTaskRhino(it)
        }

        @JvmStatic
        @RhinoFunctionBody
        fun removeTaskRhino(it: Any?): Boolean = when {
            it.isJsNullish() -> false
            it is TimedTask -> TimedTaskManager.removeTaskSync(it)
            it is IntentTask -> TimedTaskManager.removeTaskSync(it)
            else -> throw WrappedIllegalArgumentException("Argument task ${it.jsBrief()} is unacceptable for tasks.removeTask")
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun removeTimedTask(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsOnlyOne(args) {
            val id = coerceIntNumber(it)
            removeTaskRhino(getTimedTaskRhino(id))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun removeIntentTask(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsOnlyOne(args) {
            val id = coerceIntNumber(it)
            removeTaskRhino(getIntentTaskRhino(id))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun updateTask(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsOnlyOne(args) {
            updateTaskRhino(it)
        }

        @JvmStatic
        @RhinoFunctionBody
        fun updateTaskRhino(task: Any?): Boolean = when {
            task.isJsNullish() -> false
            task is TimedTask -> TimedTaskManager.updateTaskSync(task)
            task is IntentTask -> TimedTaskManager.updateTaskSync(task)
            else -> throw WrappedIllegalArgumentException("Argument task ${task.jsBrief()} is unacceptable for tasks.updateTask")
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun queryTimedTasks(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeArray = ensureArgumentsAtMost(args, 1) { argList ->
            val (options) = argList
            val opt = coerceObject(options, newNativeObject())

            val path = opt.inquire("path") { it.toRuntimePath(scriptRuntime) }
            val taskList = TimedTaskManager.allTasksAsList
            val result = path?.let { taskList.filter { it.scriptPath == path } } ?: taskList

            result.toNativeArray()
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun queryIntentTasks(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeArray = ensureArgumentsAtMost(args, 1) { argList ->
            val (options) = argList
            val opt = coerceObject(options, newNativeObject())

            val tasks = TimedTaskManager.allIntentTasksAsList

            val path = opt.inquire("path") { it.toRuntimePath(scriptRuntime) }
            val action = opt.inquire("action") { coerceString(it) }

            tasks.filter {
                when {
                    path != null && it.scriptPath != path -> false
                    action != null && it.action != action -> false
                    else -> true
                }
            }.toNativeArray()
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun timeFlagToDays(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeArray = ensureArgumentsOnlyOne(args) {
            val flag = coerceIntNumber(it)
            val days = mutableListOf<Int>()
            val binaryString = flag.toString(2)
            var currentDayNumber = binaryString.length - 1
            for (i in binaryString) {
                if (i != '0') {
                    days.add(0, currentDayNumber)
                }
                currentDayNumber--
            }
            days.toNativeArray()
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun daysToTimeFlag(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Int = ensureArgumentsOnlyOne(args) {
            val days = coerceArray(it, newNativeArray()).map { ele -> coerceIntNumber(ele) }
            Array(7) { 0 }.foldIndexed(0) { index, acc, _ ->
                acc + if (days.contains(index)) 2.0.pow(index.toDouble()).toInt() else 0
            }
        }

        private fun parseConfig(config: NativeObject) = ExecutionConfig().apply {
            delay = config.inquire("delay", ::coerceLongNumber, 0L)
            interval = config.inquire("interval", ::coerceLongNumber, 0L)
            loopTimes = config.inquire("loopTimes", ::coerceIntNumber, 1)
        }

        private fun parseLocalTime(options: ScriptableObject): LocalTime {
            val dateTime = options.prop("time").takeUnless { it.isJsNullish() } ?: options.prop("date")
            return when {
                dateTime.isJsNullish() -> LocalTime(now())
                dateTime is Number -> LocalTime(dateTime.toLong())
                dateTime is NativeDate -> LocalTime(dateTime.date)
                dateTime is String -> LocalTime.parse(dateTime)
                else -> throw WrappedIllegalArgumentException("Unknown dataTime ${dateTime.jsBrief()}")
            }
        }

        private fun parseLocalDateTime(options: ScriptableObject): LocalDateTime {
            val localDateTime = options.prop("time").takeUnless { it.isJsNullish() } ?: options.prop("date")
            return when {
                localDateTime.isJsNullish() -> LocalDateTime(now())
                localDateTime is Number -> LocalDateTime(localDateTime.toLong())
                localDateTime is NativeDate -> LocalDateTime(localDateTime.date)
                localDateTime is String -> LocalDateTime.parse(localDateTime)
                else -> throw WrappedIllegalArgumentException("Unknown localDateTime ${localDateTime.jsBrief()}")
            }
        }

        private fun parseDayOfWeekTimeFlag(opt: NativeObject): Int {
            var timeFlag = 0

            val daysOfWeek: List<Any?> = opt.inquire("daysOfWeek", ::coerceArray, listOf(LocalDate.now().dayOfWeek.value % 7).toNativeArray())
            daysOfWeek.forEach { element ->
                val eleString = coerceString(element)
                val dayIndex = daysOfWeekFlatList.indexOf(eleString.lowercase()) % 7
                require(dayIndex >= 0) { "Unknown day: $eleString" }
                timeFlag = timeFlag or TimedTask.getDayOfWeekTimeFlag(dayIndex + 1).toInt()
            }
            return timeFlag
        }

        private fun taskFulfilled(scriptRuntime: ScriptRuntime, task: Any, options: NativeObject? = null): Any? {
            val opt = coerceObject(options, newNativeObject())

            var runnableResult: Any? = null

            val r = Runnable {
                val callback = opt.prop("callback")
                runnableResult = when {
                    callback.isJsNullish() -> task
                    else -> {
                        require(callback is BaseFunction) {
                            "Property \"callback\" for options must be a JavaScript Function instead of ${callback.jsBrief()}"
                        }
                        callFunction(callback, opt, opt, arrayOf(task))
                    }
                }
            }

            return when {
                opt.inquire(listOf("isAsync", "async"), ::coerceBoolean, false) || isUiThread() -> {
                    Threads.start(scriptRuntime, arrayOf(r))
                }
                else -> {
                    r.run()
                    runnableResult
                }
            }
        }

    }

}
