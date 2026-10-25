package org.autojs.autojs.runtime.api.modules.tasks

import org.autojs.autojs.annotation.RhinoFunctionBody
import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.execution.ExecutionConfig
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.rhino.extension.AnyExtensions.jsBrief
import org.autojs.autojs.rhino.extension.AnyExtensions.toRuntimePath
import org.autojs.autojs.rhino.ArgumentGuards
import org.autojs.autojs.rhino.extension.ScriptableExtensions.prop
import org.autojs.autojs.rhino.extension.ScriptableObjectExtensions.acquire
import org.autojs.autojs.rhino.extension.ScriptableObjectExtensions.inquire
import org.autojs.autojs.external.receiver.DynamicBroadcastReceivers
import org.autojs.autojs.rhino.extension.IterableExtensions.toNativeArray
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.modules.threads.Threads
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
import org.autojs.autojs.util.RhinoUtils.getDeclarationScope
import org.autojs.autojs.util.RhinoUtils.newNativeArray
import org.autojs.autojs.util.RhinoUtils.newNativeObject
import org.autojs.autojs.util.RhinoUtils.now
import org.joda.time.LocalDate
import org.joda.time.LocalDateTime
import org.joda.time.LocalTime
import org.mozilla.javascript.Callable
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeDate
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.ScriptableObject
import kotlin.math.pow

@Suppress("unused", "UNUSED_PARAMETER")
class Tasks(scriptRuntime: ScriptRuntime) : ArgumentGuards() {

    companion object : ArgumentGuards() {

        private const val MILLIS_PER_SECOND = 1_000L
        private const val MILLIS_PER_MINUTE = 60_000L
        private const val MILLIS_PER_HOUR = 3_600_000L
        private const val MILLIS_PER_DAY = 86_400_000L

        private val daysOfWeekFlatList: List<String> = listOf(
            "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday",
            "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun",
            "一", "二", "三", "四", "五", "六", "日",
            1, 2, 3, 4, 5, 6, 0,
            1, 2, 3, 4, 5, 6, 7,
        ).map { "$it".lowercase() }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun addTask(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? = ensureArgumentsOnlyOne(args) {
            addTaskRhino(it)
        }

        @JvmStatic
        @RhinoFunctionBody
        fun addTaskRhino(task: Any?): Any? {
            when {
                task.isJsNullish() -> return null
                task is TimedTask -> TimedTaskManager.addTaskSync(task)
                task is IntentTask -> TimedTaskManager.addTaskSync(task)
                else -> listOf(
                    "Argument \"task\" ${task.jsBrief()} for tasks.addTask",
                    "must be either TimedTask or IntentTask",
                ).joinToString(" ").let { throw WrappedIllegalArgumentException(it) }
            }
            return task
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun addTimedTask(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? = ensureArgumentsAtMost(args, 1) { argList ->
            val (options) = argList
            val opt = coerceObject(options, newNativeObject(scriptRuntime.topLevelScope))

            createTimedTask(scriptRuntime, opt, parseRepeatMode(opt))
                .let { task -> taskFulfilled(scriptRuntime, addTaskRhino(task), opt) }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun addDailyTask(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? = ensureArgumentsAtMost(args, 1) { argList ->
            val (options) = argList
            val opt = coerceObject(options, newNativeObject(scriptRuntime.topLevelScope))

            createTimedTask(scriptRuntime, opt, TimedTask.REPEAT_MODE_DAILY)
                .let { task -> taskFulfilled(scriptRuntime, addTaskRhino(task), opt) }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun addWeeklyTask(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? = ensureArgumentsAtMost(args, 1) { argList ->
            val (options) = argList
            val opt = coerceObject(options, newNativeObject(scriptRuntime.topLevelScope))

            createTimedTask(scriptRuntime, opt, TimedTask.REPEAT_MODE_WEEKLY)
                .let { task -> taskFulfilled(scriptRuntime, addTaskRhino(task), opt) }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun addMonthlyTask(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? = ensureArgumentsAtMost(args, 1) { argList ->
            val (options) = argList
            val opt = coerceObject(options, newNativeObject(scriptRuntime.topLevelScope))

            createTimedTask(scriptRuntime, opt, TimedTask.REPEAT_MODE_MONTHLY)
                .let { task -> taskFulfilled(scriptRuntime, addTaskRhino(task), opt) }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun addYearlyTask(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? = ensureArgumentsAtMost(args, 1) { argList ->
            val (options) = argList
            val opt = coerceObject(options, newNativeObject(scriptRuntime.topLevelScope))

            createTimedTask(scriptRuntime, opt, TimedTask.REPEAT_MODE_YEARLY)
                .let { task -> taskFulfilled(scriptRuntime, addTaskRhino(task), opt) }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun addDisposableTask(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? = ensureArgumentsAtMost(args, 1) { argList ->
            val (options) = argList
            val opt = coerceObject(options, newNativeObject(scriptRuntime.topLevelScope))

            createTimedTask(scriptRuntime, opt, TimedTask.REPEAT_MODE_DISPOSABLE)
                .let { task -> taskFulfilled(scriptRuntime, addTaskRhino(task), opt) }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun addCountdownTask(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? = ensureArgumentsAtMost(args, 1) { argList ->
            val (options) = argList
            val opt = coerceObject(options, newNativeObject(scriptRuntime.topLevelScope))

            createTimedTask(scriptRuntime, opt, TimedTask.REPEAT_MODE_COUNTDOWN)
                .let { task -> taskFulfilled(scriptRuntime, addTaskRhino(task), opt) }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun addIntentTask(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? = ensureArgumentsAtMost(args, 1) { argList ->
            val (options) = argList
            val opt = coerceObject(options, newNativeObject(scriptRuntime.topLevelScope))

            IntentTask().apply {
                opt.acquire("path") { it.toRuntimePath(scriptRuntime) }.let {
                    setScriptPathAndCaptureContext(it)
                }
                opt.inquire("action") { coerceString(it) }?.let {
                    action = it
                    if (action == DynamicBroadcastReceivers.ACTION_STARTUP) {
                        // @Indecision opt.local
                        isLocal = true
                    }
                }
                opt.inquire("category") { coerceString(it) }?.let {
                    category = it
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
        fun addBroadcastIntentTask(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? {
            return addIntentTask(scriptRuntime, args)
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
        fun removeTask(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? = ensureArgumentsOnlyOne(args) {
            removeTaskRhino(it)
        }

        @JvmStatic
        @RhinoFunctionBody
        fun removeTaskRhino(it: Any?): Any? {
            removeTaskSyncResult(it)
            return it
        }

        @JvmStatic
        @RhinoFunctionBody
        internal fun removeTaskSyncResult(it: Any?): Boolean = when {
            it.isJsNullish() -> false
            it is TimedTask -> TimedTaskManager.removeTaskSync(it)
            it is IntentTask -> TimedTaskManager.removeTaskSync(it)
            else -> throw WrappedIllegalArgumentException("Argument task ${it.jsBrief()} is unacceptable for tasks.removeTask")
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun removeTimedTask(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? = ensureArgumentsLengthInRange(args, 1..2) {
            val (idArg, options) = it
            val task = getTimedTaskRhino(idArg)
            removeTaskRhino(task)
            taskFulfilled(scriptRuntime, task, coerceObject(options, newNativeObject(scriptRuntime.topLevelScope)))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun removeIntentTask(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? = ensureArgumentsLengthInRange(args, 1..2) {
            val (idArg, options) = it
            val task = getIntentTaskRhino(idArg)
            removeTaskRhino(task)
            taskFulfilled(scriptRuntime, task, coerceObject(options, newNativeObject(scriptRuntime.topLevelScope)))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun updateTask(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? = ensureArgumentsOnlyOne(args) {
            updateTaskRhino(it)
        }

        @JvmStatic
        @RhinoFunctionBody
        fun updateTaskRhino(task: Any?): Any? {
            when {
                task.isJsNullish() -> return null
                task is TimedTask -> {
                    task.isScheduled = false
                    TimedTaskManager.updateTaskSync(task)
                }
                task is IntentTask -> TimedTaskManager.updateTaskSync(task)
                else -> throw WrappedIllegalArgumentException("Argument task ${task.jsBrief()} is unacceptable for tasks.updateTask")
            }
            return task
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun queryTimedTasks(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeArray = ensureArgumentsAtMost(args, 1) { argList ->
            val (options) = argList
            val opt = coerceObject(options, newNativeObject(scriptRuntime.topLevelScope))

            val path = opt.inquire("path") { it.toRuntimePath(scriptRuntime) }
            val taskList = TimedTaskManager.allTasksAsList
            val result = path?.let { taskList.filter { it.scriptPath == path } } ?: taskList

            result.toNativeArray()
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun queryIntentTasks(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeArray = ensureArgumentsAtMost(args, 1) { argList ->
            val (options) = argList
            val opt = coerceObject(options, newNativeObject(scriptRuntime.topLevelScope))

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
            val days = coerceArray(it, newNativeArray(scriptRuntime.topLevelScope)).map { ele -> coerceIntNumber(ele) }
            Array(7) { 0 }.foldIndexed(0) { index, acc, _ ->
                acc + if (days.contains(index)) 2.0.pow(index.toDouble()).toInt() else 0
            }
        }

        private fun parseConfig(config: NativeObject) = ExecutionConfig().apply {
            delay = config.inquire("delay", ::coerceLongNumber, 0L)
            interval = config.inquire("interval", ::coerceLongNumber, 0L)
            loopTimes = config.inquire("loopTimes", ::coerceIntNumber, 1)
        }

        private fun createTimedTask(scriptRuntime: ScriptRuntime, opt: NativeObject, repeatMode: Int): TimedTask {
            val path = opt.acquire("path") { it.toRuntimePath(scriptRuntime) }
            val config = parseConfig(opt)
            return when (repeatMode) {
                TimedTask.REPEAT_MODE_DISPOSABLE -> TimedTask.disposableTask(parseLocalDateTime(opt), path, config)
                TimedTask.REPEAT_MODE_COUNTDOWN -> TimedTask.countdownTask(parseCountdownDurationMillis(opt), path, config)
                TimedTask.REPEAT_MODE_DAILY -> TimedTask.dailyTask(parseLocalTime(opt), path, config)
                TimedTask.REPEAT_MODE_WEEKLY -> TimedTask.weeklyTask(parseLocalTime(opt), parseDayOfWeekTimeFlag(opt).toLong(), path, config)
                TimedTask.REPEAT_MODE_MONTHLY -> TimedTask.monthlyTask(parseLocalTime(opt), parseDayOfMonth(opt), path, config)
                TimedTask.REPEAT_MODE_YEARLY -> parseYearlyMonthDay(opt).let { (monthOfYear, dayOfMonth) ->
                    TimedTask.yearlyTask(parseLocalTime(opt), monthOfYear, dayOfMonth, path, config)
                }
                else -> throw WrappedIllegalArgumentException("Unknown repeat mode: $repeatMode")
            }.let { applyRepeatEnd(it, opt) }
        }

        private fun applyRepeatEnd(task: TimedTask, opt: NativeObject): TimedTask {
            if (task.isDisposable) {
                return task
            }
            when (parseEndMode(opt)) {
                TimedTask.END_MODE_UNTIL_DATE -> {
                    task.endMode = TimedTask.END_MODE_UNTIL_DATE
                    task.endValue = parseRequiredEndDate(opt).toDateTimeAtStartOfDay().millis
                }
                TimedTask.END_MODE_COUNT -> {
                    val times: Int = opt.inquire(listOf("times", "endCount", "count")) { coerceIntNumber(it) }
                        ?: throw WrappedIllegalArgumentException("Property \"times\" or \"endCount\" is required when end mode is count")
                    require(times > 0) { "Property \"times\" must be greater than 0" }
                    task.endMode = TimedTask.END_MODE_COUNT
                    task.endValue = times.toLong()
                }
                else -> {
                    task.endMode = TimedTask.END_MODE_FOREVER
                    task.endValue = 0L
                }
            }
            return task
        }

        private fun parseRepeatMode(opt: NativeObject): Int {
            val mode = opt.inquire(listOf("repeatMode", "repeat", "type", "mode"))
                ?: throw WrappedIllegalArgumentException("Property \"repeatMode\" is required for tasks.addTimedTask")
            return parseRepeatMode(mode)
        }

        private fun parseRepeatMode(mode: Any): Int = when (mode) {
            is Number -> coerceIntNumber(mode)
            else -> when (normalizeModeName(mode)) {
                "disposable", "once", "onetime", "oneoff" -> TimedTask.REPEAT_MODE_DISPOSABLE
                "countdown", "after", "runafter", "later" -> TimedTask.REPEAT_MODE_COUNTDOWN
                "daily", "day" -> TimedTask.REPEAT_MODE_DAILY
                "weekly", "week" -> TimedTask.REPEAT_MODE_WEEKLY
                "monthly", "month" -> TimedTask.REPEAT_MODE_MONTHLY
                "yearly", "year", "annually", "annual" -> TimedTask.REPEAT_MODE_YEARLY
                else -> throw WrappedIllegalArgumentException("Unknown repeat mode: ${mode.jsBrief()}")
            }
        }

        private fun parseEndMode(opt: NativeObject): Int {
            val mode = opt.inquire(listOf("endMode", "ends", "end", "repeatEnd"))
            return when {
                !mode.isJsNullish() -> parseEndMode(mode!!)
                opt.inquire(listOf("untilDate", "until", "endDate")) != null -> TimedTask.END_MODE_UNTIL_DATE
                opt.inquire(listOf("times", "endCount")) != null -> TimedTask.END_MODE_COUNT
                else -> TimedTask.END_MODE_FOREVER
            }
        }

        private fun parseEndMode(mode: Any): Int = when (mode) {
            is Number -> coerceIntNumber(mode)
            else -> when (normalizeModeName(mode)) {
                "forever", "always", "never" -> TimedTask.END_MODE_FOREVER
                "untildate", "until", "date" -> TimedTask.END_MODE_UNTIL_DATE
                "count", "times", "number", "numberofevents", "fornumberofevents" -> TimedTask.END_MODE_COUNT
                else -> throw WrappedIllegalArgumentException("Unknown end mode: ${mode.jsBrief()}")
            }
        }

        private fun normalizeModeName(value: Any): String {
            return coerceString(value).lowercase().replace(Regex("[\\s_-]"), "")
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
            parseOptionalCountdownDateTime(options)?.let { return it }
            val localDateTime = options.prop("time").takeUnless { it.isJsNullish() } ?: options.prop("date")
            return when {
                localDateTime.isJsNullish() -> LocalDateTime(now())
                localDateTime is Number -> LocalDateTime(localDateTime.toLong())
                localDateTime is NativeDate -> LocalDateTime(localDateTime.date)
                localDateTime is String -> LocalDateTime.parse(localDateTime)
                else -> throw WrappedIllegalArgumentException("Unknown localDateTime ${localDateTime.jsBrief()}")
            }
        }

        private fun parseOptionalCountdownDateTime(options: ScriptableObject): LocalDateTime? {
            if (options !is NativeObject) {
                return null
            }
            val duration = options.inquire(listOf("after", "countdown", "timeOffset", "duration", "runAfter"))
            if (duration.isJsNullish()) {
                return null
            }
            return createCountdownDateTime(parseDurationMillis(duration, options))
        }

        private fun parseCountdownDurationMillis(opt: NativeObject): Long {
            val duration = opt.inquire(listOf("after", "countdown", "timeOffset", "duration", "runAfter"))
            val durationMillis = when {
                duration.isJsNullish() -> parseDurationFieldsMillis(opt)
                else -> parseDurationMillis(duration, opt)
            }
            return requirePositiveCountdownDuration(durationMillis)
        }

        private fun createCountdownDateTime(durationMillis: Long): LocalDateTime {
            val positiveDurationMillis = requirePositiveCountdownDuration(durationMillis)
            val targetMillis = runCatching { Math.addExact(now(), positiveDurationMillis) }.getOrElse {
                throw WrappedIllegalArgumentException("Countdown target time is too large")
            }
            return LocalDateTime(targetMillis)
        }

        private fun requirePositiveCountdownDuration(durationMillis: Long): Long {
            require(durationMillis > 0) { "Countdown duration must be greater than 0" }
            return durationMillis
        }

        private fun parseDurationMillis(duration: Any?, owner: ScriptableObject): Long = when {
            duration.isJsNullish() -> 0L
            duration is Number -> coerceLongNumber(duration)
            duration is NativeObject -> parseDurationFieldsMillis(duration)
            else -> parseDurationFieldsMillis(coerceObject(duration, newNativeObject(getDeclarationScope(owner))))
        }

        private fun parseDurationFieldsMillis(opt: NativeObject): Long {
            val days = opt.inquire(listOf("days", "day", "d", "delayDays"), ::coerceLongNumber, 0L)
            val hours = opt.inquire(listOf("hours", "hour", "hr", "h", "delayHours"), ::coerceLongNumber, 0L)
            val minutes = opt.inquire(listOf("minutes", "minute", "min", "m", "delayMinutes"), ::coerceLongNumber, 0L)
            val seconds = opt.inquire(listOf("seconds", "second", "sec", "s", "delaySeconds"), ::coerceLongNumber, 0L)
            val millis = opt.inquire(listOf("milliseconds", "millisecond", "millis", "ms", "delayMillis"), ::coerceLongNumber, 0L)

            require(listOf(days, hours, minutes, seconds, millis).all { it >= 0 }) {
                "Countdown duration values must not be negative"
            }

            return runCatching {
                listOf(
                    Math.multiplyExact(days, MILLIS_PER_DAY),
                    Math.multiplyExact(hours, MILLIS_PER_HOUR),
                    Math.multiplyExact(minutes, MILLIS_PER_MINUTE),
                    Math.multiplyExact(seconds, MILLIS_PER_SECOND),
                    millis,
                ).fold(0L) { acc, value -> Math.addExact(acc, value) }
            }.getOrElse {
                throw WrappedIllegalArgumentException("Countdown duration is too large")
            }
        }

        private fun parseDayOfMonth(opt: NativeObject): Int {
            val dayOfMonth: Int = opt.inquire(listOf("dayOfMonth", "day")) { coerceIntNumber(it) }
                ?: opt.inquire("date") { parseLocalDate(it).dayOfMonth }
                ?: LocalDate.now().dayOfMonth
            require(dayOfMonth in 1..31) { "Property \"dayOfMonth\" must be between 1 and 31" }
            return dayOfMonth
        }

        private fun parseYearlyMonthDay(opt: NativeObject): Pair<Int, Int> {
            val date = opt.inquire("date") { parseLocalDate(it) } ?: LocalDate.now()
            val monthOfYear: Int = opt.inquire(listOf("monthOfYear", "month")) { coerceIntNumber(it) } ?: date.monthOfYear
            val dayOfMonth: Int = opt.inquire(listOf("dayOfMonth", "day")) { coerceIntNumber(it) } ?: date.dayOfMonth
            require(monthOfYear in 1..12) { "Property \"monthOfYear\" must be between 1 and 12" }
            require(dayOfMonth in 1..31) { "Property \"dayOfMonth\" must be between 1 and 31" }
            return monthOfYear to dayOfMonth
        }

        private fun parseRequiredEndDate(opt: NativeObject): LocalDate {
            val value = opt.inquire(listOf("untilDate", "until", "endDate"))
                ?: throw WrappedIllegalArgumentException("Property \"untilDate\" or \"endDate\" is required when end mode is until date")
            return parseLocalDate(value)
        }

        private fun parseLocalDate(date: Any?): LocalDate {
            return when {
                date.isJsNullish() -> LocalDate.now()
                date is Number -> LocalDate(date.toLong())
                date is NativeDate -> LocalDate(date.date)
                date is String -> parseLocalDateString(date)
                else -> throw WrappedIllegalArgumentException("Unknown localDate ${date.jsBrief()}")
            }
        }

        private fun parseLocalDateString(date: String): LocalDate {
            return runCatching { LocalDate.parse(date) }.getOrElse {
                runCatching { LocalDateTime.parse(date).toLocalDate() }.getOrElse {
                    throw WrappedIllegalArgumentException("Unknown localDate ${date.jsBrief()}")
                }
            }
        }

        private fun parseDayOfWeekTimeFlag(opt: NativeObject): Int {
            var timeFlag = 0

            val daysOfWeek: List<Any?> = opt.inquire("daysOfWeek", ::coerceArray, listOf(LocalDate.now().dayOfWeek % 7).toNativeArray())
            daysOfWeek.forEach { element ->
                val eleString = coerceString(element)
                val dayIndex = daysOfWeekFlatList.indexOf(eleString.lowercase()) % 7
                require(dayIndex >= 0) { "Unknown day: $eleString" }
                timeFlag = timeFlag or TimedTask.getDayOfWeekTimeFlag(dayIndex + 1).toInt()
            }
            return timeFlag
        }

        private fun taskFulfilled(scriptRuntime: ScriptRuntime, task: Any?, options: NativeObject? = null): Any? {
            val opt = coerceObject(options, newNativeObject(scriptRuntime.topLevelScope))

            var runnableResult: Any? = null

            val r = Runnable {
                val callback = opt.prop("callback")
                runnableResult = when {
                    callback.isJsNullish() -> task
                    else -> {
                        require(callback is Callable) {
                            "Property \"callback\" for options must be a JavaScript Function instead of ${callback.jsBrief()}"
                        }
                        callFunction(scriptRuntime, callback, scriptRuntime.topLevelScope, opt, arrayOf(task))
                    }
                }
            }

            return when {
                shouldRunTaskCallbackAsync(opt) -> {
                    Threads.start(scriptRuntime, arrayOf(r))
                }
                else -> {
                    r.run()
                    runnableResult
                }
            }
        }

        internal fun shouldRunTaskCallbackAsync(options: NativeObject): Boolean {
            return options.inquire(listOf("isAsync", "async"), ::coerceBoolean, false)
        }

    }

}
