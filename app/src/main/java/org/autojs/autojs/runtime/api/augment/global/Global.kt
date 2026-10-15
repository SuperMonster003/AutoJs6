package org.autojs.autojs.runtime.api.augment.global

import android.os.Build
import io.github.g00fy2.versioncompare.Version
import org.autojs.autojs.annotation.RhinoFunctionBody
import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.core.automator.UiObject
import org.autojs.autojs.rhino.ArgumentGuards
import org.autojs.autojs.rhino.ArgumentGuards.Companion.component1
import org.autojs.autojs.rhino.ArgumentGuards.Companion.component2
import org.autojs.autojs.rhino.ArgumentGuards.Companion.component3
import org.autojs.autojs.rhino.ArgumentGuards.Companion.component4
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.rhino.extension.AnyExtensions.jsBrief
import org.autojs.autojs.rhino.extension.NumberExtensions.jsString
import org.autojs.autojs.rhino.extension.ScriptableExtensions.defineProp
import org.autojs.autojs.rhino.extension.ScriptableExtensions.prop
import org.autojs.autojs.rhino.extension.ScriptableObjectExtensions.inquire
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.ScreenMetrics
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.api.augment.automator.Auto
import org.autojs.autojs.runtime.api.augment.console.Console
import org.autojs.autojs.runtime.api.augment.s13n.S13n
import org.autojs.autojs.runtime.api.augment.selector.Selector
import org.autojs.autojs.runtime.api.augment.shell.Shell
import org.autojs.autojs.runtime.api.augment.shizuku.Shizuku
import org.autojs.autojs.runtime.api.augment.toast.Toast
import org.autojs.autojs.runtime.api.augment.util.Util
import org.autojs.autojs.runtime.exception.NotImplementedError
import org.autojs.autojs.runtime.exception.ShouldNeverHappenException
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.autojs.util.RhinoUtils
import org.autojs.autojs.util.RhinoUtils.NOT_CONSTRUCTABLE
import org.autojs.autojs.util.RhinoUtils.UNDEFINED
import org.autojs.autojs.util.RhinoUtils.callFunction
import org.autojs.autojs.util.RhinoUtils.coerceBoolean
import org.autojs.autojs.util.RhinoUtils.coerceIntNumber
import org.autojs.autojs.util.RhinoUtils.coerceLongNumber
import org.autojs.autojs.util.RhinoUtils.coerceNumber
import org.autojs.autojs.util.RhinoUtils.coerceString
import org.autojs.autojs.util.RhinoUtils.newBaseFunction
import org.autojs.autojs.util.RhinoUtils.newNativeObject
import org.autojs.autojs.util.RhinoUtils.undefined
import org.autojs.autojs.util.StringUtils.str
import org.autojs.autojs6.BuildConfig
import org.autojs.autojs6.R
import org.mozilla.javascript.Callable
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined
import java.util.function.Supplier
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToLong

@Suppress("unused", "UNUSED_PARAMETER")
class Global(private val scriptRuntime: ScriptRuntime) : Augmentable(scriptRuntime) {

    internal data class SleepRange(val min: Double, val max: Double)

    /**
     * Polling plan resolved from the `limit` and `interval` arguments of `wait`.
     * zh-CN: 由 `wait` 的 `limit` 与 `interval` 参数解析出的轮询计划.
     */
    internal data class WaitPolicy(val times: Long, val timeout: Long, val interval: Long) {
        val isNoop get() = times <= 0
    }

    override val selfAssignmentProperties = listOf(
        "isAutoJs6" to true,
    )

    override val selfAssignmentFunctions = listOf(
        "toString" to AS_LITERAL_TO_STRING,
        ::TODO.name,
        ::isUiThread.name,
        ::isJavaObject.name,
        ::isInteger.name,
        ::isBigInt.name,
        ::isPrimitive.name,
        ::isReference.name,
        ::isEmptyObject.name,
        ::unwrapJavaObject.name,
        ::toastVerbose.name to listOf(::toastVerbose.name, ::toastVerbose.name.lowercase()),
        ::toastLog.name to listOf(::toastLog.name, ::toastLog.name.lowercase()),
        ::toastInfo.name to listOf(::toastInfo.name, ::toastInfo.name.lowercase()),
        ::toastWarn.name to listOf(::toastWarn.name, ::toastWarn.name.lowercase()),
        ::toastError.name to listOf(::toastError.name, ::toastError.name.lowercase()),
        ::sleep.name,
        ::isStopped.name,
        ::isShuttingDown.name,
        ::notStopped.name,
        ::isRunning.name,
        ::exit.name,
        ::stop.name,
        ::setClip.name,
        ::getClip.name,
        ::currentPackage.name,
        ::currentActivity.name,
        ::currentComponent.name,
        ::wait.name,
        ::waitForActivity.name,
        ::waitForPackage.name,
        ::random.name,
        ::setScreenMetrics.name,
        ::requiresApi.name,
        ::requiresAutojsVersion.name,
        ::getScaleBases.name,
        ::getScaleBaseX.name,
        ::getScaleBaseY.name,
        ::setScaleBases.name,
        ::setScaleBaseX.name,
        ::setScaleBaseY.name,
        ::cX.name,
        ::cY.name,
        ::cYx.name,
        ::cXy.name,
    )

    override val selfAssignmentGetters = listOf<Pair<String, Supplier<Any?>>>(
        "WIDTH" to Supplier { ScreenMetrics.deviceScreenWidth },
        "HEIGHT" to Supplier { ScreenMetrics.deviceScreenHeight },
        "axios" to Supplier { scriptRuntime.js_mod_axios },
        "cheerio" to Supplier { scriptRuntime.js_mod_cheerio },
        "dayjs" to Supplier { scriptRuntime.js_mod_dayjs },
        "i18n" to Supplier { scriptRuntime.js_mod_i18n },
        "pangu" to Supplier { scriptRuntime.js_mod_pangu },
    )

    companion object : ArgumentGuards() {

        @Suppress("FunctionName")
        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun TODO(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Nothing = ensureArgumentsAtMost(args, 1) { argList ->
            val (reason) = argList
            when {
                reason.isJsNullish() || reason == "" -> {
                    globalContext.getString(R.string.error_an_operation_is_not_implemented)
                }
                else -> coerceString(reason)
            }.let { throw NotImplementedError(it) }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun isUiThread(scriptRuntime: ScriptRuntime, args: Array<out Any?>) = ensureArgumentsIsEmpty(args) {
            RhinoUtils.isUiThread()
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun isJavaObject(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsLength(args, 1) {
            Util.isJavaObject(it)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun isInteger(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsLength(args, 1) {
            Util.isInteger(it)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun isBigInt(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsLength(args, 1) {
            Util.isBigInt(it)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun isPrimitive(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsLength(args, 1) {
            Util.isPrimitive(it)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun isReference(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsLength(args, 1) {
            Util.isReference(it)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun isEmptyObject(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsLength(args, 1) {
            Util.isEmptyObject(it)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun unwrapJavaObject(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? = ensureArgumentsLength(args, 1) {
            Util.unwrapJavaObject(it)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun toastVerbose(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsLengthInRange(args, 1..3) {
            undefined {
                Toast.call(scriptRuntime, it)
                Console.verbose(scriptRuntime, getArgsForConsole(it))
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun toastLog(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsLengthInRange(args, 0..3) {
            undefined {
                Toast.call(scriptRuntime, it)
                Console.log(scriptRuntime, getArgsForConsole(it))
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun toastInfo(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsLengthInRange(args, 1..3) {
            undefined {
                Toast.call(scriptRuntime, it)
                Console.info(scriptRuntime, getArgsForConsole(it))
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun toastWarn(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsLengthInRange(args, 1..3) {
            undefined {
                Toast.call(scriptRuntime, it)
                Console.warn(scriptRuntime, getArgsForConsole(it))
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun toastError(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsLengthInRange(args, 1..3) {
            undefined {
                Toast.call(scriptRuntime, it)
                Console.error(scriptRuntime, getArgsForConsole(it))
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun sleep(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsLengthInRange(args, 1..2) { argList ->
            val (millisMin, millisMax) = argList

            require(!RhinoUtils.isUiThread()) { str(R.string.error_function_called_in_ui_thread, ::sleep.name) }

            val range = resolveSleepRange(coerceLongNumber(millisMin, 0L), millisMax)
            scriptRuntime.sleep(selectSleepMillis(range, Math.random()))
            UNDEFINED
        }

        internal fun resolveSleepRange(millisMin: Long, millisMax: Any?): SleepRange {
            val base = millisMin.coerceAtLeast(0L).toDouble()
            val rawBounds = when {
                millisMax.isJsNullish() -> base to base
                millisMax is Number -> base to coerceNumber(millisMax)
                millisMax is String -> {
                    val matched = Regex("[+-]?(\\d+(\\.\\d+)?(e\\d+)?)").find(millisMax)
                        ?: throw WrappedIllegalArgumentException("String argument millisMax must have a number contained for sleep")
                    val delta = matched.value.toDouble()
                    (base - delta) to (base + delta)
                }
                else -> throw WrappedIllegalArgumentException(
                    "Argument millisMax ${millisMax.jsBrief()} must be a number or a string for sleep"
                )
            }
            val min = minOf(rawBounds.first, rawBounds.second).coerceAtLeast(0.0)
            val max = maxOf(rawBounds.first, rawBounds.second)
                .coerceAtLeast(0.0)
                .coerceAtMost(Long.MAX_VALUE.toDouble())
            return SleepRange(min, max)
        }

        internal fun selectSleepMillis(range: SleepRange, random: Double): Long {
            require(random >= 0.0 && random < 1.0) { "Random value for sleep must be in [0, 1)" }
            val millis = range.min + ceil(random * (range.max - range.min))
            return when {
                millis <= 0.0 -> 0L
                millis >= Long.MAX_VALUE.toDouble() -> Long.MAX_VALUE
                else -> millis.toLong()
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun isStopped(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsIsEmpty(args) {
            scriptRuntime.isStopped
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun isShuttingDown(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsIsEmpty(args) {
            isStopped(scriptRuntime, args)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun notStopped(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsIsEmpty(args) {
            !scriptRuntime.isStopped
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun isRunning(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = ensureArgumentsIsEmpty(args) {
            !scriptRuntime.isStopped
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun exit(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsAtMost(args, 1) { argList ->
            val (e) = argList
            when {
                e.isJsNullish() -> scriptRuntime.exit()
                else -> scriptRuntime.exit(S13n.throwable(arrayOf(e)))
            }
            UNDEFINED
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun stop(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsIsEmpty(args) {
            exit(scriptRuntime, args)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun setClip(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsOnlyOne(args) { text ->
            scriptRuntime.clip = coerceString(text, "")
            UNDEFINED
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun getClip(scriptRuntime: ScriptRuntime, args: Array<out Any?>): String = ensureArgumentsIsEmpty(args) {
            val legacyResult = scriptRuntime.clip
            when {
                legacyResult.isNotEmpty() -> legacyResult
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && scriptRuntime.floaty.hasPermission() -> {
                    scriptRuntime.floaty.getClip()
                }
                else -> legacyResult
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun currentPackage(scriptRuntime: ScriptRuntime, args: Array<out Any?>): String = ensureArgumentsAtMost(args, 1) { argList ->
            val (o) = argList
            when (parseComponentFetchMode(o)) {
                ComponentFetchMode.ACCESSIBILITY -> Auto.currentPackage(scriptRuntime, emptyArray())
                ComponentFetchMode.SHIZUKU -> Shizuku.currentPackage(scriptRuntime, emptyArray())
                ComponentFetchMode.ROOT -> Shell.currentPackage(scriptRuntime, emptyArray())
                ComponentFetchMode.AUTOMATISM -> listOf(
                    { Shizuku.currentPackage(scriptRuntime, emptyArray()) },
                    { Shell.currentPackage(scriptRuntime, emptyArray()) },
                    { Auto.currentPackage(scriptRuntime, emptyArray()) }
                ).firstNotNullOfOrNull { f -> f().takeUnless { it.isEmpty() } }.orEmpty()
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun currentActivity(scriptRuntime: ScriptRuntime, args: Array<out Any?>): String = ensureArgumentsAtMost(args, 1) { argList ->
            val (o) = argList
            when (parseComponentFetchMode(o)) {
                ComponentFetchMode.ACCESSIBILITY -> Auto.currentActivity(scriptRuntime, emptyArray())
                ComponentFetchMode.SHIZUKU -> Shizuku.currentActivity(scriptRuntime, emptyArray())
                ComponentFetchMode.ROOT -> Shell.currentActivity(scriptRuntime, emptyArray())
                ComponentFetchMode.AUTOMATISM -> listOf(
                    { Shizuku.currentActivity(scriptRuntime, emptyArray()) },
                    { Shell.currentActivity(scriptRuntime, emptyArray()) },
                    { Auto.currentActivity(scriptRuntime, emptyArray()) }
                ).firstNotNullOfOrNull { f -> f().takeUnless { it.isEmpty() } }.orEmpty()
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun currentComponent(scriptRuntime: ScriptRuntime, args: Array<out Any?>): String = ensureArgumentsAtMost(args, 1) { argList ->
            val (o) = argList
            when (parseComponentFetchMode(o)) {
                ComponentFetchMode.ACCESSIBILITY -> Auto.currentComponent(scriptRuntime, emptyArray())
                ComponentFetchMode.SHIZUKU -> Shizuku.currentComponent(scriptRuntime, emptyArray())
                ComponentFetchMode.ROOT -> Shell.currentComponent(scriptRuntime, emptyArray())
                ComponentFetchMode.AUTOMATISM -> listOf(
                    { Shizuku.currentComponent(scriptRuntime, emptyArray()) },
                    { Shell.currentComponent(scriptRuntime, emptyArray()) },
                    { Auto.currentComponent(scriptRuntime, emptyArray()) }
                ).firstNotNullOfOrNull { f -> f().takeUnless { it.isEmpty() } }.orEmpty()
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun wait(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? = ensureArgumentsLengthInRange(args, 1..4) { argList ->
            val (conditionArg, limitArg, intervalArg, callbackArg) = normalizeWaitArguments(argList)

            var result: Any?
            val start = System.currentTimeMillis()
            val callback = callbackArg.takeUnless { it.isJsNullish() } ?: newNativeObject(scriptRuntime.topLevelScope)

            require(callback is ScriptableObject) {
                "Argument callback ${callbackArg.jsBrief()} must be a ScriptableObject for wait"
            }

            val policy = resolveWaitPolicy(limitArg, intervalArg)
            if (policy.isNoop) return@ensureArgumentsLengthInRange UNDEFINED

            var times = policy.times
            val timeout = policy.timeout
            val interval = policy.interval

            require(conditionArg !is UiObject) { "UiObject cannot be used as the condition for wait" }

            var checked: Any?

            while (true) {
                checked = when (conditionArg) {
                    is Callable -> callFunction(
                        scriptRuntime,
                        conditionArg,
                        scriptRuntime.topLevelScope,
                        Undefined.SCRIPTABLE_UNDEFINED,
                        emptyArray<Any?>(),
                    )
                    else -> Selector.pickup(scriptRuntime, arrayOf(conditionArg))
                }

                result = isWaitConditionSatisfied(checked)

                times -= 1

                if (result || times <= 0) break
                if (System.currentTimeMillis() - start > timeout) break
                scriptRuntime.sleep(interval)
            }

            val fnPropertyName: String

            val fn = when (coerceBoolean(result, false)) {
                true -> callback.prop("then".also { fnPropertyName = it })
                else -> callback.prop("else".also { fnPropertyName = it })
            }

            if (!fn.isJsNullish()) {
                require(fn is Callable) { "Property \"$fnPropertyName\" of callback ${fn.jsBrief()} must be a Function for wait" }
                callFunction(
                    scriptRuntime,
                    fn,
                    scriptRuntime.topLevelScope,
                    Undefined.SCRIPTABLE_UNDEFINED,
                    arrayOf(checked),
                ).takeUnless { Undefined.isUndefined(it) }?.let { result = it }
            }

            result
        }

        /**
         * Maps the positional arguments of `wait` onto (condition, limit, interval, callback).
         * A callback object may be placed at the `limit` or `interval` slot:
         * - wait(condition, callback)
         * - wait(condition, limit, callback)
         * In both cases, arguments after the callback object are discarded.
         *
         * zh-CN: 将 `wait` 的位置参数映射为 (condition, limit, interval, callback).
         * 回调对象可以出现在 `limit` 或 `interval` 的位置, 此时其后的参数将被丢弃.
         */
        internal fun normalizeWaitArguments(argList: Array<out Any?>): Array<Any?> {
            val (conditionArg, limitArg, intervalArg, callbackArg) = argList
            return when {
                limitArg is NativeObject -> arrayOf(conditionArg, null, null, limitArg)
                intervalArg is NativeObject -> arrayOf(conditionArg, limitArg, null, intervalArg)
                else -> arrayOf(conditionArg, limitArg, intervalArg, callbackArg)
            }
        }

        /**
         * Resolves the polling plan of `wait`.
         * A limit less than 100 is treated as the maximum number of attempts,
         * otherwise as a timeout in milliseconds (default 10000).
         * The interval defaults to 200 milliseconds.
         *
         * zh-CN: 解析 `wait` 的轮询计划.
         * 小于 100 的 limit 视为最大尝试次数, 否则视为毫秒超时 (默认 10000).
         * 间隔默认为 200 毫秒.
         */
        internal fun resolveWaitPolicy(limitArg: Any?, intervalArg: Any?): WaitPolicy {
            val limit = (coerceNumber(limitArg, Double.NaN).takeUnless { it.isNaN() } ?: 10000.0).also {
                require(it >= 0) { "Limitation ($it) cannot be negative for wait" }
            }.roundToLong()

            var (times, timeout) = when {
                limit < 100 -> limit to Long.MAX_VALUE
                else -> Long.MAX_VALUE to limit
            }

            val interval = (coerceNumber(intervalArg, Double.NaN).takeUnless { it.isNaN() } ?: 200.0).also {
                require(it.isFinite()) { "Interval cannot be Infinity for wait" }
                require(it >= 0) { "Interval ($it) cannot be negative for wait" }
            }.roundToLong()

            if (interval >= timeout) times = minOf(times, 1)

            return WaitPolicy(times, timeout, interval)
        }

        /**
         * Truthiness rule of `wait`: only nullish values, `false` and `NaN` fail the check,
         * so that falsy values like 0, -0 and "" (empty string) still pass.
         *
         * zh-CN: `wait` 的真值规则: 仅 nullish, `false` 与 `NaN` 视为不满足, 因此 0, -0 及空字符串等假值仍视为满足.
         */
        internal fun isWaitConditionSatisfied(checked: Any?): Boolean {
            return !(checked.isJsNullish() || checked == false || checked is Double && checked.isNaN())
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun waitForActivity(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? = ensureArgumentsLengthInRange(args, 1..4) { argList ->
            require(!RhinoUtils.isUiThread()) {
                globalContext.getString(R.string.blocking_operations_cannot_be_performed_on_the_ui_thread_for_waitforactivity_with_solution)
            }
            val (activityNameArg, limitArg, intervalArg, callbackArg) = argList
            val condition = newBaseFunction(scriptRuntime.topLevelScope, "condition", {
                currentActivity(scriptRuntime, emptyArray()) == coerceString(activityNameArg)
            }, NOT_CONSTRUCTABLE)
            wait(scriptRuntime, arrayOf(condition, limitArg, intervalArg, callbackArg))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun waitForPackage(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? = ensureArgumentsLengthInRange(args, 1..4) { argList ->
            require(!RhinoUtils.isUiThread()) {
                globalContext.getString(R.string.blocking_operations_cannot_be_performed_on_the_ui_thread_for_waitforpackage_with_solution)
            }
            val (packageNameArg, limitArg, intervalArg, callbackArg) = argList
            val condition = newBaseFunction(scriptRuntime.topLevelScope, "condition", {
                currentPackage(scriptRuntime, emptyArray()) == coerceString(packageNameArg)
            }, NOT_CONSTRUCTABLE)
            wait(scriptRuntime, arrayOf(condition, limitArg, intervalArg, callbackArg))
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun random(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Double = ensureArgumentsAtMost(args, 2) { argList ->
            val (minArg, maxArg) = argList
            when (argList.size) {
                0 -> Math.random()
                1 -> Double.NaN
                2 -> {
                    val min = coerceNumber(minArg, Double.NaN)
                    val max = coerceNumber(maxArg, Double.NaN)
                    floor(Math.random() * (max - min + 1)) + min
                }
                else -> throw ShouldNeverHappenException()
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun setScreenMetrics(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsLength(args, 2) {
            val (width, height) = it
            undefined { scriptRuntime.setScreenMetrics(coerceIntNumber(width), coerceIntNumber(height)) }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun requiresApi(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsOnlyOne(args) {
            undefined { ScriptRuntime.requiresApi(coerceIntNumber(it)) }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun requiresAutojsVersion(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsOnlyOne(args) { version ->
            when (version) {
                is Number -> coerceNumber(version, 0).let { num ->
                    when {
                        num.toInt() in 2..9 -> {
                            requiresAutojsVersion(scriptRuntime, arrayOf(num.toString()))
                        }
                        else -> {
                            require(num.toInt() >= 461) {
                                globalContext.getString(R.string.error_specified_autojs6_version_number_must_be_greater_than_461)
                            }
                            require(BuildConfig.VERSION_CODE >= num.toInt()) {
                                globalContext.getString(R.string.error_autojs6_version_number_must_not_be_lower_than_num, num.jsString)
                            }
                        }
                    }
                }
                else -> coerceString(version).let { ver ->
                    require(Version(BuildConfig.VERSION_NAME).isAtLeast(Version(ver))) {
                        globalContext.getString(R.string.error_autojs6_version_must_not_be_lower_than_ver, ver)
                    }
                }
            }
            UNDEFINED
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun getScaleBases(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeObject = ensureArgumentsIsEmpty(args) {
            val (baseX, baseY) = scriptRuntime.scale.snapshotBases()
            newNativeObject(scriptRuntime.topLevelScope).also { o ->
                o.defineProp("x", baseX)
                o.defineProp("y", baseY)
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun getScaleBaseX(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Int = ensureArgumentsIsEmpty(args) {
            scriptRuntime.scale.baseX
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun getScaleBaseY(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Int = ensureArgumentsIsEmpty(args) {
            scriptRuntime.scale.baseY
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun setScaleBases(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsLength(args, 2) {
            val (baseX, baseY) = it
            val convertedBaseX = coerceIntNumber(baseX)
            val convertedBaseY = coerceIntNumber(baseY)
            undefined { scriptRuntime.scale.setBases(convertedBaseX, convertedBaseY) }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun setScaleBaseX(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsOnlyOne(args) {
            scriptRuntime.scale.baseX = coerceIntNumber(it)
            UNDEFINED
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun setScaleBaseY(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Undefined = ensureArgumentsOnlyOne(args) {
            scriptRuntime.scale.baseY = coerceIntNumber(it)
            UNDEFINED
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun cX(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Double = ensureArgumentsAtMost(args, 3) { argList ->
            CoordinateScale.convert(CoordinateScale.Axis.X, ScreenMetrics.deviceScreenWidth, ScreenMetrics.deviceScreenHeight, scriptRuntime.scale, argList)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun cY(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Double = ensureArgumentsAtMost(args, 3) { argList ->
            CoordinateScale.convert(CoordinateScale.Axis.Y, ScreenMetrics.deviceScreenWidth, ScreenMetrics.deviceScreenHeight, scriptRuntime.scale, argList)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun cYx(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Double = ensureArgumentsLengthInRange(args, 1..3) { argList ->
            CoordinateScale.convert(CoordinateScale.Axis.Y_FROM_X, ScreenMetrics.deviceScreenWidth, ScreenMetrics.deviceScreenHeight, scriptRuntime.scale, argList)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun cXy(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Double = ensureArgumentsLengthInRange(args, 1..3) { argList ->
            CoordinateScale.convert(CoordinateScale.Axis.X_FROM_Y, ScreenMetrics.deviceScreenWidth, ScreenMetrics.deviceScreenHeight, scriptRuntime.scale, argList)
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun toString(scriptRuntime: ScriptRuntime, args: Array<out Any?>) = ensureArgumentsIsEmpty(args) { toStringRhino() }

        @JvmStatic
        @RhinoFunctionBody
        fun toStringRhino(): String = "[object global]"

        private fun getArgsForConsole(args: Array<Any?>): Array<Any?> = when {
            args.isEmpty() -> emptyArray()
            else -> arrayOf(args.first())
        }

        private fun parseComponentFetchMode(o: Any?): ComponentFetchMode = when {
            o.isJsNullish() -> ComponentFetchMode.AUTOMATISM
            o is NativeObject -> parseComponentFetchMode(o.inquire(listOf("by", "mode"), ::coerceString, ""))
            else -> when (coerceString(o, "").lowercase()) {
                "", "auto", "automatic", "automatism" -> ComponentFetchMode.AUTOMATISM
                "a11y", "accessibility" -> ComponentFetchMode.ACCESSIBILITY
                "shizuku" -> ComponentFetchMode.SHIZUKU
                "root" -> ComponentFetchMode.ROOT
                else -> throw WrappedIllegalArgumentException("Unknown component fetch mode: ${coerceString(o)}")
            }
        }

        private enum class ComponentFetchMode { AUTOMATISM, ACCESSIBILITY, SHIZUKU, ROOT }

    }

}
