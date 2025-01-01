package org.autojs.autojs.runtime.api.augment.global

import io.github.g00fy2.versioncompare.Version
import org.autojs.autojs.annotation.RhinoFunctionBody
import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.core.automator.UiObject
import org.autojs.autojs.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.extension.AnyExtensions.jsBrief
import org.autojs.autojs.extension.NumberExtensions.string
import org.autojs.autojs.extension.ScriptableExtensions.defineProp
import org.autojs.autojs.extension.ScriptableExtensions.prop
import org.autojs.autojs.extension.ScriptableObjectExtensions.inquire
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.ScreenMetrics
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.api.augment.automator.Auto
import org.autojs.autojs.runtime.api.augment.console.Console
import org.autojs.autojs.runtime.api.augment.jsox.Numberx
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
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined
import java.util.function.Supplier
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToLong

@Suppress("unused", "UNUSED_PARAMETER")
class Global(private val scriptRuntime: ScriptRuntime) : Augmentable(scriptRuntime) {

    override val selfAssignmentProperties = listOf(
        "isAutoJs6" to true,
    )

    override val selfAssignmentFunctions = listOf(
        "toString",
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
        "Promise" to Supplier { scriptRuntime.js_Promise },
        "ResultAdapter" to Supplier { scriptRuntime.js_ResultAdapter },
    )

    companion object {

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

            var min = coerceLongNumber(millisMin, 0L).coerceAtLeast(0L)

            val max = when {
                millisMax.isJsNullish() -> min
                millisMax is Number -> coerceLongNumber(millisMax).coerceAtMost(Long.MAX_VALUE)
                millisMax is String -> Regex("[+-]?(\\d+(\\.\\d+)?(e\\d+)?)").find(millisMax)?.let { matched ->
                    val delta = matched.value.toLong()
                    val newMax = min + delta
                    min -= delta
                    newMax
                } ?: throw WrappedIllegalArgumentException("String argument millisMax must have a number contained for sleep")
                else -> throw WrappedIllegalArgumentException("Argument millisMax ${millisMax.jsBrief()} must be a number or a string for sleep")
            }

            val randBound = coerceLongNumber(Math.random() * (max - min))
            scriptRuntime.sleep(min + randBound)
            UNDEFINED
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
            scriptRuntime.clip
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
            val (conditionArg, limitArg, intervalArg, callbackArg) = argList

            if (limitArg is NativeObject) {
                // @Overload wait(condition, callback): any
                return@ensureArgumentsLengthInRange arrayOf(
                    /* condition = */ conditionArg,
                    /* limit = */ null,
                    /* interval = */ null,
                    /* callback = */ limitArg,
                ).let { newArgList -> wait(scriptRuntime, newArgList) }
            }

            if (intervalArg is NativeObject) {
                // @Overload wait(condition, limit, callback): any
                return@ensureArgumentsLengthInRange arrayOf(
                    /* condition = */ conditionArg,
                    /* limit = */ limitArg,
                    /* interval = */ null,
                    /* callback = */ intervalArg,
                ).let { newArgList -> wait(scriptRuntime, newArgList) }
            }

            var result: Any?
            val start = System.currentTimeMillis()
            val callback = callbackArg.takeUnless { it.isJsNullish() } ?: newNativeObject()

            require(callback is ScriptableObject) {
                "Argument callback ${callbackArg.jsBrief()} must be a ScriptableObject for wait"
            }

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
            if (times <= 0) return@ensureArgumentsLengthInRange UNDEFINED

            require(conditionArg !is UiObject) { "UiObject cannot be used as the condition for wait" }

            var checked: Any?

            while (true) {
                checked = when (conditionArg) {
                    is BaseFunction -> callFunction(scriptRuntime, conditionArg, scriptRuntime.topLevelScope, emptyArray<Any?>())
                    else -> Selector.pickup(scriptRuntime, arrayOf(conditionArg))
                }

                // Some falsy values [ 0, 0n, -0, "" (empty string) ] should pass the check.
                result = !(checked.isJsNullish() || checked == false || checked is Double && checked.isNaN())

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
                require(fn is BaseFunction) { "Property \"$fnPropertyName\" of callback ${fn.jsBrief()} must be a Function for wait" }
                callFunction(scriptRuntime, fn, scriptRuntime.topLevelScope, arrayOf(checked)).takeUnless { Undefined.isUndefined(it) }?.let { result = it }
            }

            result
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun waitForActivity(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? = ensureArgumentsLengthInRange(args, 1..4) { argList ->
            require(!RhinoUtils.isUiThread()) {
                globalContext.getString(R.string.blocking_operations_cannot_be_performed_on_the_ui_thread_for_waitforactivity_with_solution)
            }
            val (activityNameArg, limitArg, intervalArg, callbackArg) = argList
            val condition = newBaseFunction("condition", {
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
            val condition = newBaseFunction("condition", {
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
                        num.toInt() == 6 -> {
                            requiresAutojsVersion(scriptRuntime, arrayOf(num.toString()))
                        }
                        else -> {
                            require(num.toInt() >= 461) {
                                "指定的 AutoJs6 应用版本号需大于 461"
                            }
                            require(BuildConfig.VERSION_CODE >= num.toInt()) {
                                "AutoJs6 应用版本号需不低于 ${num.string}"
                            }
                        }
                    }
                }
                else -> coerceString(version).let { ver ->
                    require(Version(BuildConfig.VERSION_NAME).isAtLeast(Version(ver))) {
                        "AutoJs6 应用版本需不低于 $ver"
                    }
                }
            }
            UNDEFINED
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun getScaleBases(scriptRuntime: ScriptRuntime, args: Array<out Any?>): NativeObject = ensureArgumentsIsEmpty(args) {
            newNativeObject().also { o ->
                o.defineProp("x", scriptRuntime.scale.baseX)
                o.defineProp("y", scriptRuntime.scale.baseY)
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
            undefined {
                setScaleBaseX(scriptRuntime, arrayOf(coerceIntNumber(baseX)))
                setScaleBaseY(scriptRuntime, arrayOf(coerceIntNumber(baseY)))
            }
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
            var (num, base, isRatio) = argList
            val deviceWidth = ScreenMetrics.deviceScreenWidth
            if (argList.isEmpty()) {
                return@ensureArgumentsAtMost deviceWidth.toDouble()
            }
            if (base is Boolean) {
                isRatio = base
                base = null
            }
            num = coerceNumber(num)
            if (abs(num) < 1 && isRatio != false || coerceBoolean(isRatio, false)) {
                return@ensureArgumentsAtMost (deviceWidth * num).roundToLong().toDouble()
            }
            when {
                base.isJsNullish() -> base = scriptRuntime.scale.baseX
                base is Number -> require(RhinoUtils.isInteger(base)) { "Scale base ${base.jsBrief()} must be a positive integer for cX" }
                else -> throw WrappedIllegalArgumentException("Argument base ${base.jsBrief()} for cX must be a number if provided")
            }
            return@ensureArgumentsAtMost (deviceWidth * num / coerceIntNumber(base)).roundToLong().toDouble()
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun cY(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Double = ensureArgumentsAtMost(args, 3) { argList ->
            var (num, base, isRatio) = argList
            val deviceHeight = ScreenMetrics.deviceScreenHeight
            if (argList.isEmpty()) {
                return@ensureArgumentsAtMost deviceHeight.toDouble()
            }
            if (base is Boolean) {
                isRatio = base
                base = null
            }
            num = coerceNumber(num)
            if (abs(num) < 1 && isRatio != false || coerceBoolean(isRatio, false)) {
                return@ensureArgumentsAtMost (deviceHeight * num).roundToLong().toDouble()
            }
            when {
                base.isJsNullish() -> base = scriptRuntime.scale.baseY
                base is Number -> require(RhinoUtils.isInteger(base)) { "Scale base ${base.jsBrief()} must be a positive integer for cY" }
                else -> throw WrappedIllegalArgumentException("Argument base ${base.jsBrief()} for cY must be a number if provided")
            }
            return@ensureArgumentsAtMost (deviceHeight * num / coerceIntNumber(base)).roundToLong().toDouble()
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun cYx(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Double = ensureArgumentsAtMost(args, 3) { argList ->
            var (num, base, isRatio) = argList
            scriptRuntime.scale.ensureBasesConsistent()
            if (base is Boolean) {
                isRatio = base
                base = null
            }
            if (base is String) {
                base = Numberx.parseRatio(scriptRuntime, arrayOf(base))
                isRatio = true
            }
            val deviceWidth = ScreenMetrics.deviceScreenWidth
            num = coerceNumber(num)
            when {
                abs(num) < 1 || coerceBoolean(isRatio, false) -> {
                    base = coerceNumber(base, scriptRuntime.scale.baseY.toDouble() / scriptRuntime.scale.baseX.toDouble())
                    when {
                        base.isNaN() -> Double.NaN
                        0 < base && base <= 1 -> (num * deviceWidth / base).roundToLong().toDouble()
                        else -> (num * deviceWidth * base).roundToLong().toDouble()
                    }
                }
                else -> {
                    base = coerceNumber(base, scriptRuntime.scale.baseX)
                    when {
                        base.isNaN() -> Double.NaN
                        else -> (num * deviceWidth / base).roundToLong().toDouble()
                    }
                }
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun cXy(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Double = ensureArgumentsAtMost(args, 3) { argList ->
            var (num, base, isRatio) = argList
            scriptRuntime.scale.ensureBasesConsistent()
            if (base is Boolean) {
                isRatio = base
                base = null
            }
            if (base is String) {
                base = Numberx.parseRatio(scriptRuntime, arrayOf(base))
                isRatio = true
            }
            val deviceHeight = ScreenMetrics.deviceScreenHeight
            num = coerceNumber(num)
            when {
                abs(num) < 1 || coerceBoolean(isRatio, false) -> {
                    base = coerceNumber(base, scriptRuntime.scale.baseY.toDouble() / scriptRuntime.scale.baseX.toDouble())
                    when {
                        base.isNaN() -> Double.NaN
                        0 < base && base <= 1 -> (num * deviceHeight * base).roundToLong().toDouble()
                        else -> (num * deviceHeight / base).roundToLong().toDouble()
                    }
                }
                else -> {
                    base = coerceNumber(base, scriptRuntime.scale.baseY)
                    when {
                        base.isNaN() -> Double.NaN
                        else -> (num * deviceHeight / base).roundToLong().toDouble()
                    }
                }
            }
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
