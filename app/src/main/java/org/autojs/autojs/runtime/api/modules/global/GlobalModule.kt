package org.autojs.autojs.runtime.api.modules.global

import android.os.Build
import io.github.g00fy2.versioncompare.Version
import org.autojs.autojs.core.automator.UiObject
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.ScreenMetrics
import org.autojs.autojs.runtime.api.modules.ScriptFunction
import org.autojs.autojs.runtime.api.modules.ScriptGetter
import org.autojs.autojs.runtime.api.modules.ScriptMessages
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.autojs.autojs.runtime.api.modules.ScriptNameLookup
import org.autojs.autojs.runtime.api.modules.ScriptProperty
import org.autojs.autojs.runtime.api.modules.ScriptPrototype
import org.autojs.autojs.runtime.api.modules.ScriptToString
import org.autojs.autojs.runtime.api.modules.automator.Auto
import org.autojs.autojs.runtime.api.modules.console.Console
import org.autojs.autojs.runtime.api.modules.global.GlobalValues.Axis
import org.autojs.autojs.runtime.api.modules.global.GlobalValues.ComponentFetchMode
import org.autojs.autojs.runtime.api.modules.global.GlobalValues.isNullish
import org.autojs.autojs.runtime.api.modules.s13n.S13nModule
import org.autojs.autojs.runtime.api.modules.selector.Selector
import org.autojs.autojs.runtime.api.modules.shell.Shell
import org.autojs.autojs.runtime.api.modules.shizuku.Shizuku
import org.autojs.autojs.runtime.api.modules.toast.Toast
import org.autojs.autojs.runtime.api.modules.util.UtilModule
import org.autojs.autojs.runtime.exception.NotImplementedError
import org.autojs.autojs.util.RhinoUtils
import org.autojs.autojs6.BuildConfig
import org.autojs.autojs6.R
import org.mozilla.javascript.Callable
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.VarScope
import org.mozilla.javascript.Wrapper
import kotlin.math.floor
import org.mozilla.javascript.ScriptRuntime as RhinoScriptRuntime

/**
 * The global functions on the new script module layer (roadmap P3.0, appendix C Q17): an assign module, so every member
 * is a name of the global object in the order of the current `augment/global/Global` (`isAutoJs6`, `toString`, the
 * functions, then the getters `WIDTH` ... `pangu`), with no module object and no `global.global`; the Java classes
 * readable as global names (`Intent`, `File`, ...) come from the name lookup prototype [GlobalJavaClasses] on the
 * global object, as the current `GlobalClasses` proxy prototype.
 *
 * The type predicates delegate to the new `util` ([UtilModule], P2.3), as the current members delegate to the current
 * util. Members that need the host still call the current implementations of the modules that are not migrated yet
 * (toast and console printing, `auto` / `shizuku` / `shell` for the current component, `pickup` for selector
 * conditions); they move to the new modules when those are migrated (P3.2 - P3.4). Arguments follow the real
 * signatures (T4); a value of the wrong type is a TypeError, an unusable value a RangeError (D10, appendix C Q12), host
 * exceptions keep their type (T5).
 *
 * zh-CN: 新脚本模块层上的全局函数 (P3.0, 附录 C Q17): assign 模块, 每个成员都是全局对象上的名称, 顺序同现有 augment/global/Global (isAutoJs6, toString,
 * 各函数, 再是 getter WIDTH ... pangu), 没有模块对象, 也没有 global.global; 可作为全局名称读取的 Java 类 (Intent, File 等) 来自全局对象上的名称查找原型
 * GlobalJavaClasses, 同现有 GlobalClasses 代理原型. 类型判断委托新层 util (P2.3), 与现有成员委托现有 util 相同. 依赖宿主的成员仍调用尚未迁移的模块的现有实现
 * (toast 与控制台打印, 取当前组件的 auto / shizuku / shell, 选择器条件的 pickup), 待这些模块迁移 (P3.2 - P3.4) 后改用新模块. 参数按真实签名 (T4); 类型
 * 不对为 TypeError, 取值不可用为 RangeError (D10, 附录 C Q12), 宿主异常保留类型 (T5).
 */
@ScriptModule(name = "global", assign = true)
class GlobalModule(private val runtime: ScriptRuntime) {

    private val util = UtilModule(runtime)

    @ScriptPrototype
    val javaClasses: ScriptNameLookup = GlobalJavaClasses

    @ScriptProperty
    val isAutoJs6 = true

    @ScriptFunction(name = "toString", toStringRole = ScriptToString.LITERAL)
    fun describe(): String = "[object global]"

    /** Always throws NotImplementedError, with the host's message when [reason] is nullish or empty. */
    @ScriptFunction(name = "TODO")
    fun todo(reason: Any? = null) {
        val message = if (isNullish(reason) || reason is CharSequence && reason.isEmpty()) ScriptMessages.notImplemented() else RhinoScriptRuntime.toString(reason)
        throw NotImplementedError(message)
    }

    @ScriptFunction
    fun isUiThread(): Boolean = RhinoUtils.isUiThread()

    @ScriptFunction
    fun isJavaObject(scope: VarScope, o: Any?): Boolean = util.isJavaObject(scope, o)

    @ScriptFunction
    fun isInteger(o: Any?): Boolean = util.isInteger(o)

    @ScriptFunction
    fun isBigInt(o: Any?): Boolean = util.isBigInt(o)

    @ScriptFunction
    fun isPrimitive(o: Any?): Boolean = util.isPrimitive(o)

    @ScriptFunction
    fun isReference(o: Any?): Boolean = util.isReference(o)

    @ScriptFunction
    fun isEmptyObject(o: Any?): Boolean = util.isEmptyObject(o)

    @ScriptFunction
    fun unwrapJavaObject(o: Any?): Any? = util.unwrapJavaObject(o)

    @ScriptFunction(aliases = ["toastverbose"])
    fun toastVerbose(text: Any?, isLong: Any? = ABSENT, isForcible: Any? = ABSENT) =
        toastAndPrint(text, isLong, isForcible) { args -> Console.verbose(runtime, args) }

    @ScriptFunction(aliases = ["toastlog"])
    fun toastLog(text: Any? = ABSENT, isLong: Any? = ABSENT, isForcible: Any? = ABSENT) =
        toastAndPrint(text, isLong, isForcible) { args -> Console.log(runtime, args) }

    @ScriptFunction(aliases = ["toastinfo"])
    fun toastInfo(text: Any?, isLong: Any? = ABSENT, isForcible: Any? = ABSENT) =
        toastAndPrint(text, isLong, isForcible) { args -> Console.info(runtime, args) }

    @ScriptFunction(aliases = ["toastwarn"])
    fun toastWarn(text: Any?, isLong: Any? = ABSENT, isForcible: Any? = ABSENT) =
        toastAndPrint(text, isLong, isForcible) { args -> Console.warn(runtime, args) }

    @ScriptFunction(aliases = ["toasterror"])
    fun toastError(text: Any?, isLong: Any? = ABSENT, isForcible: Any? = ABSENT) =
        toastAndPrint(text, isLong, isForcible) { args -> Console.error(runtime, args) }

    /** Shows the toast with the arguments as given and prints the text (only the first argument) to the console. */
    private fun toastAndPrint(text: Any?, isLong: Any?, isForcible: Any?, print: (Array<Any?>) -> Unit) {
        val args = arrayOf(text, isLong, isForcible).filter { it !== ABSENT }.toTypedArray()
        Toast.call(runtime, args)
        print(if (args.isEmpty()) emptyArray() else arrayOf(args[0]))
    }

    @ScriptFunction
    fun sleep(millisMin: Any?, millisMax: Any? = null) {
        require(!RhinoUtils.isUiThread()) { ScriptMessages.resource(R.string.error_function_called_in_ui_thread, "UI thread cannot be blocked. Try running %s() in sub-thread or sub-script", "sleep") }
        runtime.sleep(GlobalValues.sleepMillis(GlobalValues.sleepRange(millisMin, millisMax), Math.random()))
    }

    @ScriptFunction
    fun isStopped(): Boolean = runtime.isStopped

    @ScriptFunction
    fun isShuttingDown(): Boolean = runtime.isStopped

    @ScriptFunction
    fun notStopped(): Boolean = !runtime.isStopped

    @ScriptFunction
    fun isRunning(): Boolean = !runtime.isStopped

    /** Stops the script, reporting [e] (anything `s13n.throwable` accepts) as its error when given. */
    @ScriptFunction
    fun exit(e: Any? = null) {
        if (isNullish(e)) runtime.exit() else runtime.exit(S13nModule.throwable(e))
    }

    @ScriptFunction
    fun stop() = runtime.exit()

    @ScriptFunction
    fun setClip(text: Any?) {
        runtime.clip = if (isNullish(text)) "" else RhinoScriptRuntime.toString(text)
    }

    @ScriptFunction
    fun getClip(): String {
        val legacyResult = runtime.clip
        return when {
            legacyResult.isNotEmpty() -> legacyResult
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && runtime.floaty.hasPermission() -> runtime.floaty.getClip()
            else -> legacyResult
        }
    }

    @ScriptFunction
    fun currentPackage(mode: Any? = null): String = when (GlobalValues.componentFetchMode("currentPackage", mode)) {
        ComponentFetchMode.ACCESSIBILITY -> Auto.currentPackage(runtime, emptyArray())
        ComponentFetchMode.SHIZUKU -> Shizuku.currentPackage(runtime, emptyArray())
        ComponentFetchMode.ROOT -> Shell.currentPackage(runtime, emptyArray())
        ComponentFetchMode.AUTOMATISM -> firstNotEmpty(
            { Shizuku.currentPackage(runtime, emptyArray()) },
            { Shell.currentPackage(runtime, emptyArray()) },
            { Auto.currentPackage(runtime, emptyArray()) },
        )
    }

    @ScriptFunction
    fun currentActivity(mode: Any? = null): String = when (GlobalValues.componentFetchMode("currentActivity", mode)) {
        ComponentFetchMode.ACCESSIBILITY -> Auto.currentActivity(runtime, emptyArray())
        ComponentFetchMode.SHIZUKU -> Shizuku.currentActivity(runtime, emptyArray())
        ComponentFetchMode.ROOT -> Shell.currentActivity(runtime, emptyArray())
        ComponentFetchMode.AUTOMATISM -> firstNotEmpty(
            { Shizuku.currentActivity(runtime, emptyArray()) },
            { Shell.currentActivity(runtime, emptyArray()) },
            { Auto.currentActivity(runtime, emptyArray()) },
        )
    }

    @ScriptFunction
    fun currentComponent(mode: Any? = null): String = when (GlobalValues.componentFetchMode("currentComponent", mode)) {
        ComponentFetchMode.ACCESSIBILITY -> Auto.currentComponent(runtime, emptyArray())
        ComponentFetchMode.SHIZUKU -> Shizuku.currentComponent(runtime, emptyArray())
        ComponentFetchMode.ROOT -> Shell.currentComponent(runtime, emptyArray())
        ComponentFetchMode.AUTOMATISM -> firstNotEmpty(
            { Shizuku.currentComponent(runtime, emptyArray()) },
            { Shell.currentComponent(runtime, emptyArray()) },
            { Auto.currentComponent(runtime, emptyArray()) },
        )
    }

    private fun firstNotEmpty(vararg sources: () -> String): String = sources.firstNotNullOfOrNull { f -> f().takeUnless { it.isEmpty() } }.orEmpty()

    /**
     * Blocks until [condition] (a function or a pickup selector) is satisfied or the limit is reached; see
     * [GlobalValues.normalizeWaitArguments] for the positions of the callback object.
     */
    @ScriptFunction
    fun wait(cx: Context, scope: VarScope, condition: Any?, limit: Any? = null, interval: Any? = null, callback: Any? = null): Any? {
        val (conditionArg, limitArg, intervalArg, callbackArg) = GlobalValues.normalizeWaitArguments(condition, limit, interval, callback)
        return waitFor(cx, scope, "wait", limitArg, intervalArg, callbackArg) {
            if ((conditionArg as? Wrapper)?.unwrap() is UiObject) throw GlobalValues.cannotBe("wait", "condition", "a UiObject")
            when (conditionArg) {
                is Callable -> conditionArg.call(cx, scope, Undefined.SCRIPTABLE_UNDEFINED, emptyArray())
                else -> Selector.pickup(runtime, arrayOf(conditionArg))
            }
        }
    }

    @ScriptFunction
    fun waitForActivity(cx: Context, scope: VarScope, activityName: Any?, limit: Any? = null, interval: Any? = null, callback: Any? = null): Any? {
        require(!RhinoUtils.isUiThread()) {
            ScriptMessages.resource(
                R.string.blocking_operations_cannot_be_performed_on_the_ui_thread_for_waitforactivity_with_solution,
                "Blocking operations cannot be performed on the UI thread. Try executing waitForActivity in a sub-thread or sub-script, or use setInterval to loop and check the activity",
            )
        }
        if (isNullish(activityName)) throw GlobalValues.typeError("waitForActivity", "activityName", "string", activityName)
        val name = RhinoScriptRuntime.toString(activityName)
        val (_, limitArg, intervalArg, callbackArg) = GlobalValues.normalizeWaitArguments(null, limit, interval, callback)
        return waitFor(cx, scope, "waitForActivity", limitArg, intervalArg, callbackArg) { currentActivity() == name }
    }

    @ScriptFunction
    fun waitForPackage(cx: Context, scope: VarScope, packageName: Any?, limit: Any? = null, interval: Any? = null, callback: Any? = null): Any? {
        require(!RhinoUtils.isUiThread()) {
            ScriptMessages.resource(
                R.string.blocking_operations_cannot_be_performed_on_the_ui_thread_for_waitforpackage_with_solution,
                "Blocking operations cannot be performed on the UI thread. Try executing waitForPackage in a sub-thread or sub-script, or use setInterval to loop and check the package",
            )
        }
        if (isNullish(packageName)) throw GlobalValues.typeError("waitForPackage", "packageName", "string", packageName)
        val name = RhinoScriptRuntime.toString(packageName)
        val (_, limitArg, intervalArg, callbackArg) = GlobalValues.normalizeWaitArguments(null, limit, interval, callback)
        return waitFor(cx, scope, "waitForPackage", limitArg, intervalArg, callbackArg) { currentPackage() == name }
    }

    /**
     * The polling loop of `wait`: checks [check] until it is satisfied or the policy ends, then runs `then` / `else`. A
     * limit of 0 checks nothing and ends as unsatisfied (false, `else` called with undefined), as the obsolete JS did
     * and the documented boolean result requires (the current Kotlin returned undefined without the callback).
     */
    private fun waitFor(cx: Context, scope: VarScope, function: String, limit: Any?, interval: Any?, callback: Any?, check: () -> Any?): Any? {
        val callbackObject = when {
            isNullish(callback) -> cx.newObject(scope)
            callback is ScriptableObject -> callback
            else -> throw GlobalValues.typeError(function, "callback", "Object", callback)
        }
        val policy = GlobalValues.waitPolicy(function, limit, interval)

        val start = System.currentTimeMillis()
        var times = policy.times
        var checked: Any? = Undefined.instance
        var result: Any? = false
        while (times > 0) {
            checked = check()
            result = GlobalValues.isWaitConditionSatisfied(checked)
            times -= 1
            if (result == true || times <= 0) break
            if (System.currentTimeMillis() - start > policy.timeout) break
            runtime.sleep(policy.interval)
        }

        val key = if (result == true) "then" else "else"
        val fn = ScriptableObject.getProperty(callbackObject, key)
        if (!isNullish(fn)) {
            if (fn !is Callable) throw GlobalValues.typeError(function, "callback.$key", "function", fn)
            fn.call(cx, scope, Undefined.SCRIPTABLE_UNDEFINED, arrayOf(checked)).takeUnless { Undefined.isUndefined(it) }?.let { result = it }
        }
        return result
    }

    /**
     * Math.random() without arguments, NaN with one, an integer in [min, max] with two (as documented). The bounds are
     * read with JS ToNumber, as the arithmetic of the obsolete JS did (null is 0, undefined NaN), but the result is
     * always a number (the obsolete JS concatenated a string bound).
     */
    @ScriptFunction
    fun random(min: Any? = ABSENT, max: Any? = ABSENT): Double = when {
        min === ABSENT -> Math.random()
        max === ABSENT -> Double.NaN
        else -> {
            val lower = RhinoScriptRuntime.toNumber(min)
            val upper = RhinoScriptRuntime.toNumber(max)
            floor(Math.random() * (upper - lower + 1)) + lower
        }
    }

    @ScriptFunction
    fun setScreenMetrics(width: Any?, height: Any?) {
        runtime.setScreenMetrics(GlobalValues.int("setScreenMetrics", "width", width), GlobalValues.int("setScreenMetrics", "height", height))
    }

    @ScriptFunction
    fun requiresApi(api: Any?) = ScriptRuntime.requiresApi(GlobalValues.int("requiresApi", "api", api))

    /** A version name ("6.2.0"), or a version code (at least 461; 2 to 9 stand for the version names "2.0" to "9.0"). */
    @ScriptFunction
    fun requiresAutojsVersion(version: Any?) {
        if (version is Number) {
            val number = version.toDouble()
            if (number.toInt() in 2..9) return requiresAutojsVersion(number.toString())
            if (number.toInt() < 461) throw RhinoScriptRuntime.rangeError(
                ScriptMessages.failedToCall(
                    "requiresAutojsVersion",
                    ScriptMessages.resource(R.string.error_specified_autojs6_version_number_must_be_greater_than_461, "Specified AutoJs6 version number must be greater than 461"),
                )
            )
            require(BuildConfig.VERSION_CODE >= number.toInt()) {
                ScriptMessages.resource(
                    R.string.error_autojs6_version_number_must_not_be_lower_than_num,
                    "AutoJs6 version number must not be lower than %1\$s",
                    RhinoScriptRuntime.numberToString(number, 10),
                )
            }
            return
        }
        if (isNullish(version)) throw GlobalValues.typeError("requiresAutojsVersion", "version", "string | number", version)
        val name = RhinoScriptRuntime.toString(version)
        require(Version(BuildConfig.VERSION_NAME).isAtLeast(Version(name))) {
            ScriptMessages.resource(R.string.error_autojs6_version_must_not_be_lower_than_ver, "AutoJs6 version must not be lower than %1\$s", name)
        }
    }

    @ScriptFunction
    fun getScaleBases(cx: Context, scope: VarScope): NativeObject {
        val (baseX, baseY) = runtime.scale.snapshotBases()
        return (cx.newObject(scope) as NativeObject).also { o ->
            ScriptableObject.defineProperty(o, "x", baseX, ScriptableObject.EMPTY)
            ScriptableObject.defineProperty(o, "y", baseY, ScriptableObject.EMPTY)
        }
    }

    @ScriptFunction
    fun getScaleBaseX(): Int = runtime.scale.baseX

    @ScriptFunction
    fun getScaleBaseY(): Int = runtime.scale.baseY

    @ScriptFunction
    fun setScaleBases(baseX: Any?, baseY: Any?) {
        runtime.scale.setBases(scaleBase("setScaleBases", "baseX", baseX), scaleBase("setScaleBases", "baseY", baseY))
    }

    @ScriptFunction
    fun setScaleBaseX(baseX: Any?) {
        runtime.scale.baseX = scaleBase("setScaleBaseX", "baseX", baseX)
    }

    @ScriptFunction
    fun setScaleBaseY(baseY: Any?) {
        runtime.scale.baseY = scaleBase("setScaleBaseY", "baseY", baseY)
    }

    /** A scale base: a positive integer (truncated as the current `coerceIntNumber`); setting a base twice stays an error of the host. */
    private fun scaleBase(function: String, param: String, value: Any?): Int =
        GlobalValues.int(function, param, value).also { if (it <= 0) throw RhinoScriptRuntime.rangeError(ScriptMessages.failedToCall(function, ScriptMessages.argumentPositive(param, it.toString()))) }

    @ScriptFunction
    fun cX(num: Any? = ABSENT, base: Any? = ABSENT, isRatio: Any? = ABSENT): Double = convert(Axis.X, num, base, isRatio)

    @ScriptFunction
    fun cY(num: Any? = ABSENT, base: Any? = ABSENT, isRatio: Any? = ABSENT): Double = convert(Axis.Y, num, base, isRatio)

    @ScriptFunction
    fun cYx(num: Any?, base: Any? = ABSENT, isRatio: Any? = ABSENT): Double = convert(Axis.Y_FROM_X, num, base, isRatio)

    @ScriptFunction
    fun cXy(num: Any?, base: Any? = ABSENT, isRatio: Any? = ABSENT): Double = convert(Axis.X_FROM_Y, num, base, isRatio)

    private fun convert(axis: Axis, num: Any?, base: Any?, isRatio: Any?): Double {
        val display = ScreenMetrics.dimensions()
        val args = arrayOf(num, base, isRatio).filter { it !== ABSENT }.toTypedArray()
        return GlobalValues.convert(axis, display.width, display.height, runtime.scale, args)
    }

    @ScriptGetter(name = "WIDTH")
    val width: Int
        get() = ScreenMetrics.deviceScreenWidth

    @ScriptGetter(name = "HEIGHT")
    val height: Int
        get() = ScreenMetrics.deviceScreenHeight

    @ScriptGetter
    val axios: Any
        get() = runtime.js_mod_axios

    @ScriptGetter
    val cheerio: Any
        get() = runtime.js_mod_cheerio

    @ScriptGetter
    val dayjs: Any
        get() = runtime.js_mod_dayjs

    @ScriptGetter
    val i18n: Any
        get() = runtime.js_mod_i18n

    @ScriptGetter
    val pangu: Any
        get() = runtime.js_mod_pangu

    private companion object {

        /** An optional argument the script did not pass (the arguments are passed on as given). */
        val ABSENT = Any()

    }

}
