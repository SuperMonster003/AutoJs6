@file:Suppress("FunctionName")

package org.autojs.autojs.util

import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.Parcelable
import org.autojs.autojs.AutoJs
import org.autojs.autojs.annotation.RhinoStandardFunctionInterface
import org.autojs.autojs.core.automator.UiObjectCollection
import org.autojs.autojs.engine.RhinoJavaScriptEngine
import org.autojs.autojs.rhino.TopLevelScope
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.rhino.extension.AnyExtensions.jsBrief
import org.autojs.autojs.rhino.extension.AnyExtensions.jsSpecies
import org.autojs.autojs.rhino.extension.AnyExtensions.jsUnwrapped
import org.autojs.autojs.rhino.extension.ScriptableExtensions.defineProp
import org.autojs.autojs.rhino.extension.ScriptableExtensions.prop
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.ScriptPromiseAdapter
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.autojs.autojs.runtime.exception.WrappedRuntimeException
import org.autojs.autojs6.BuildConfig
import org.mozilla.javascript.AbstractEcmaObjectOperations
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.BoundFunction
import org.mozilla.javascript.Callable
import org.mozilla.javascript.ConsString
import org.mozilla.javascript.Context
import org.mozilla.javascript.ContinuationPending
import org.mozilla.javascript.ImporterTopLevel
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeDate
import org.mozilla.javascript.NativeJSON
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.ScriptRuntime.emptyArgs
import org.mozilla.javascript.ScriptRuntime.setBuiltinProtoAndParent
import org.mozilla.javascript.ScriptRuntime.toObject
import org.mozilla.javascript.ScriptRuntime.toString
import org.mozilla.javascript.ScriptRuntime.typeErrorById
import org.mozilla.javascript.ScriptRuntime.`typeof`
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.ScriptableObject.DONTENUM
import org.mozilla.javascript.ScriptableObject.NOT_FOUND
import org.mozilla.javascript.ScriptableObject.ensureScriptable
import org.mozilla.javascript.ScriptableObject.ensureScriptableObject
import org.mozilla.javascript.Symbol
import org.mozilla.javascript.TopLevel
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.Wrapper
import org.mozilla.javascript.json.JsonParser
import java.io.Serializable
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.math.BigInteger
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import org.mozilla.javascript.ScriptRuntime as RhinoScriptRuntime

@Suppress("unused")
object RhinoUtils {

    const val NOT_CALLABLE = 0x01
    const val NOT_CONSTRUCTABLE = 0x02
    const val DEFAULT_CALLER = 0x03
    const val DEFAULT_CONSTRUCTOR = 0x04

    /** [Long]: 2^53 - 1. */
    const val MAX_SAFE_INT_IEEE754_L = 9_007_199_254_740_991L

    /** [Long]: -(2^53 - 1). */
    const val MIN_SAFE_INT_IEEE754_L = -9_007_199_254_740_991L

    /** [Double]: 2^53 - 1. */
    const val MAX_SAFE_INT_IEEE754_D = 9_007_199_254_740_991.0

    /** [Double]: -(2^53 - 1). */
    const val MIN_SAFE_INT_IEEE754_D = -9_007_199_254_740_991.0

    /** [java.math.BigDecimal]: 2^53 - 1. */
    val MAX_SAFE_INT_IEEE754_BD = MAX_SAFE_INT_IEEE754_D.toBigDecimal()

    /** [java.math.BigDecimal]: -(2^53 - 1). */
    val MIN_SAFE_INT_IEEE754_BD = MIN_SAFE_INT_IEEE754_D.toBigDecimal()

    /** [java.math.BigInteger]: 2^53 - 1. */
    val MAX_SAFE_INT_IEEE754_BI = MAX_SAFE_INT_IEEE754_L.toBigInteger()

    /** [java.math.BigInteger]: -(2^53 - 1). */
    val MIN_SAFE_INT_IEEE754_BI = MIN_SAFE_INT_IEEE754_L.toBigInteger()

    private val TAG = RhinoUtils::class.java.simpleName

    private val STANDARD_OBJECTS_KEY = Any()

    @JvmStatic
    val UNDEFINED = Undefined.instance as Undefined

    @JvmStatic
    fun Context.getOrCreateStandardObjects(): ScriptableObject {
        // Cache standard objects per Context to avoid repeated initStandardObjects().
        // zh-CN: 按 Context 缓存标准对象, 避免重复调用 initStandardObjects().
        val cached = this.getThreadLocal(STANDARD_OBJECTS_KEY) as? ScriptableObject
        if (cached != null) return cached
        return this.initStandardObjects().also { created ->
            this.putThreadLocal(STANDARD_OBJECTS_KEY, created)
        }
    }

    @JvmStatic
    val standardObjects: ScriptableObject by lazy {
        // Create a global singleton standard objects as a low-fidelity fallback.
        // zh-CN: 创建全局单例标准对象, 用作低保真兜底.
        try {
            Context.enter().getOrCreateStandardObjects()
        } finally {
            Context.exit()
        }
    }

    @JvmStatic
    val objectPrototype: Scriptable by lazy {
        ScriptableObject.getObjectPrototype(standardObjects)
    }

    @JvmStatic
    val arrayPrototype: Scriptable by lazy {
        ScriptableObject.getArrayPrototype(standardObjects)
    }

    @JvmStatic
    val functionPrototype: Scriptable by lazy {
        standardObjects["Function"].let {
            if (it is Scriptable) it["prototype"] as? Scriptable else null
        } ?: throw IllegalStateException("Failed to get object: Function.prototype")
    }

    @JvmStatic
    fun callToStringFunction(o: Scriptable): String =
        Context.toString(callFunction(o, "toString", arrayOf()))

    @JvmStatic
    fun callToStringFunction(scriptRuntime: ScriptRuntime, o: Scriptable): String =
        Context.toString(callFunction(scriptRuntime, o, "toString", arrayOf()))

    @JvmStatic
    @Throws(NoSuchMethodException::class, InvocationTargetException::class, IllegalAccessException::class)
    fun callFunction(obj: Scriptable, name: String, paramsToFunction: Array<Any?>): Any? {
        val property = obj.prop(name)
        if (property == NOT_FOUND) throw IllegalArgumentException("Property $name not found on $obj")
        if (property !is BaseFunction) throw IllegalArgumentException("Property $name on $obj must be a BaseFunction instead of ${property.jsBrief()}")
        return callFunction(property, obj, obj, paramsToFunction)
    }

    @JvmStatic
    @Throws(NoSuchMethodException::class, InvocationTargetException::class, IllegalAccessException::class)
    fun callFunction(scriptRuntime: ScriptRuntime, obj: Scriptable, name: String, paramsToFunction: Array<Any?>): Any? {
        val property = obj.prop(name)
        if (property == NOT_FOUND) throw IllegalArgumentException("Property $name not found on $obj")
        if (property !is BaseFunction) throw IllegalArgumentException("Property $name on $obj must be a BaseFunction instead of ${property.jsBrief()}")
        return callFunction(scriptRuntime, property, obj, obj, paramsToFunction)
    }

    @JvmStatic
    @Throws(NoSuchMethodException::class, InvocationTargetException::class, IllegalAccessException::class)
    fun callFunction(func: BaseFunction, paramsToFunction: Array<Any?>): Any? =
        callFunction(func, null, paramsToFunction)

    @JvmStatic
    @Throws(NoSuchMethodException::class, InvocationTargetException::class, IllegalAccessException::class)
    fun callFunction(scriptRuntime: ScriptRuntime, func: BaseFunction, paramsToFunction: Array<Any?>): Any? =
        callFunction(scriptRuntime, func, null, paramsToFunction)

    @JvmStatic
    @Throws(NoSuchMethodException::class, InvocationTargetException::class, IllegalAccessException::class)
    fun callFunction(func: BaseFunction, scope: Scriptable?, args: Array<Any?>): Any? =
        callFunction(func, scope, scope, args)

    @JvmStatic
    @Throws(NoSuchMethodException::class, InvocationTargetException::class, IllegalAccessException::class)
    fun callFunction(scriptRuntime: ScriptRuntime, func: BaseFunction, scope: Scriptable?, args: Array<Any?>): Any? =
        callFunction(scriptRuntime, func, scope, scope, args)

    @JvmStatic
    @Throws(NoSuchMethodException::class, InvocationTargetException::class, IllegalAccessException::class)
    fun callFunction(func: BaseFunction, scope: Scriptable?, thisObj: Scriptable?, args: Array<Any?>): Any? =
        callFunction(null, func, scope, thisObj, args)

    @JvmStatic
    fun callFunction(scriptRuntime: ScriptRuntime?, func: BaseFunction, scope: Scriptable?, thisObj: Scriptable?, args: Array<Any?>): Any? = withRhinoContext(scriptRuntime) { cx ->
        try {
            // Prefer an existing scope to avoid repeatedly creating standard objects.
            // zh-CN: 优先使用已有的作用域, 避免反复创建标准对象.
            val niceScope = when {
                scope != null -> scope
                func.parentScope != null -> func.parentScope
                scriptRuntime != null -> scriptRuntime.topLevelScope
                else -> ImporterTopLevel(cx)
            }
            when {
                RhinoScriptRuntime.hasTopCall(cx) -> func.call(cx, niceScope, thisObj, args)
                else -> RhinoScriptRuntime.doTopCall(func, cx, niceScope, thisObj, args, false)
            }
        } catch (e: InterruptedException) {
            throw ScriptInterruptedException(e)
        } catch (e: ContinuationPending) {
            throw e
        } catch (e: Throwable) {
            e.printStackTrace()
            when {
                isMainThread() -> {
                    scriptRuntime?.exit(e) ?: when (e) {
                        is ScriptInterruptedException -> throw e
                        else -> throw WrappedRuntimeException(e)
                    }
                }
                else -> when (e) {
                    is ScriptInterruptedException -> throw e
                    else -> throw WrappedRuntimeException(e)
                }
            }
        }
    }

    @JvmStatic
    fun constructFunction(ctor: BaseFunction, scope: TopLevelScope, args: Array<Any?>): Scriptable = withRhinoContext { cx ->
        ctor.construct(cx, scope, args)
    }

    @JvmStatic
    fun newBaseFunction(
        funcName: String?,
        callFunc: Int,
        constructFunc: Int,
    ) = object : InternalBaseFunction(funcName) {

        init {
            checkFlagsConflict(callFunc, constructFunc)
        }

        override fun call(cx: Context?, scope: Scriptable?, thisObj: Scriptable?, args: Array<out Any?>): Any {
            return super.callFlag(callFunc, cx, scope, thisObj, args)
        }

        override fun construct(cx: Context?, scope: Scriptable?, args: Array<out Any?>): Scriptable {
            return super.constructFlag(constructFunc, cx, scope, args)
        }

    }.apply { init() }

    @JvmStatic
    fun newBaseFunction(
        funcName: String?,
        callFunc: Int,
        constructFunc: ((args: Array<out Any?>) -> Any?)?,
    ) = object : InternalBaseFunction(funcName) {

        override fun call(cx: Context?, scope: Scriptable?, thisObj: Scriptable?, args: Array<out Any?>): Any {
            return super.callFlag(callFunc, cx, scope, thisObj, args)
        }

        override fun construct(cx: Context?, scope: Scriptable?, args: Array<out Any?>): Scriptable {
            return super.constructFunc(constructFunc, cx, scope, args)
        }

    }.apply { init() }

    @JvmStatic
    fun newBaseFunction(
        funcName: String?,
        callFunc: ((args: Array<out Any?>) -> Any?)?,
        constructFunc: Int,
    ) = object : InternalBaseFunction(funcName) {

        override fun call(cx: Context?, scope: Scriptable?, thisObj: Scriptable?, args: Array<out Any?>): Any? {
            return super.callFunc(callFunc, cx, scope, thisObj, args)
        }

        override fun construct(cx: Context?, scope: Scriptable?, args: Array<out Any?>): Scriptable {
            return super.constructFlag(constructFunc, cx, scope, args)
        }

    }.apply { init() }

    @JvmStatic
    fun newBaseFunction(
        funcName: String?,
        callFunc: ((args: Array<out Any?>) -> Any?)?,
        constructFunc: ((args: Array<out Any?>) -> Any?)?,
    ) = object : InternalBaseFunction(funcName) {

        override fun call(cx: Context?, scope: Scriptable?, thisObj: Scriptable?, args: Array<out Any?>): Any? {
            return super.callFunc(callFunc, cx, scope, thisObj, args)
        }

        override fun construct(cx: Context?, scope: Scriptable?, args: Array<out Any?>): Scriptable {
            return super.constructFunc(constructFunc, cx, scope, args)
        }

    }.apply { init() }

    @JvmStatic
    fun newObject(scope: Scriptable) = newNativeObject().also {
        setBuiltinProtoAndParent(it, scope, TopLevel.Builtins.Object)
    }

    @JvmStatic
    fun wrap(o: Any?): Any = withRhinoContext { cx ->
        cx.wrapFactory.wrap(cx, ImporterTopLevel(cx), o, o?.let { it::class.java })
    }

    @JvmStatic
    fun unwrap(o: Any?): Any? = when (o) {
        is String, is ConsString -> Context.toString(o)
        is BigInteger -> o
        is Number -> Context.toNumber(o)
        is Boolean -> Context.toBoolean(o)
        is Wrapper -> unwrap(o.unwrap())
        is Unit -> UNDEFINED
        else -> o
    }

    @JvmStatic
    fun putExtraForIntent(intent: Intent, key: Any, value: Any?) {
        val k = Context.toString(key)
        when (value) {
            is Parcelable -> intent.putExtra(k, value)
            is Serializable -> intent.putExtra(k, value)
            is CharSequence -> intent.putExtra(k, value)
            null -> intent.putExtra(k, "null")

            // @Comment by SuperMonster003 on Jul 29, 2024.
            //  ! No longer needed as all cases have been covered by Parcelable and Serializable.
            //  ! zh-CN: 由于 Parcelable 和 Serializable 已经涵盖了所有情况, 因此不再需要.
            //  # is String -> intent.putExtra(k, value)
            //  # is Char -> intent.putExtra(k, value)
            //  # is Boolean -> intent.putExtra(k, value)
            //  # is Double -> intent.putExtra(k, value)
            //  # is Float -> intent.putExtra(k, value)
            //  # is Int -> intent.putExtra(k, value)
            //  # is Long -> intent.putExtra(k, value)
            //  # is Short -> intent.putExtra(k, value)
            //  # is Byte -> intent.putExtra(k, value)
            //  # is CharArray -> intent.putExtra(k, value)
            //  # is BooleanArray -> intent.putExtra(k, value)
            //  # is DoubleArray -> intent.putExtra(k, value)
            //  # is FloatArray -> intent.putExtra(k, value)
            //  # is IntArray -> intent.putExtra(k, value)
            //  # is LongArray -> intent.putExtra(k, value)
            //  # is ShortArray -> intent.putExtra(k, value)
            //  # is ByteArray -> intent.putExtra(k, value)
            //  # is Array<*> -> intent.putExtra(k, value)
            //  # is Bundle -> intent.putExtra(k, value)

            else -> throw IllegalArgumentException("Unsupported value type \"${value.javaClass.name}\" for intent.putExtra()")
        }
    }

    @JvmStatic
    fun isUiThread() = isMainThread()

    @JvmStatic
    fun isMainThread() = Looper.myLooper() == Looper.getMainLooper()

    @JvmStatic
    fun isBackgroundThread() = !isMainThread()

    @JvmStatic
    fun dispatchToMainThread(r: Runnable) = dispatchToMainThread(Handler(Looper.getMainLooper()), r)

    @JvmStatic
    fun dispatchToMainThread(handler: Handler, r: Runnable) {
        if (isMainThread()) r.run() else handler.post(r)
    }

    @JvmStatic
    fun encodeURI(scriptRuntime: ScriptRuntime, str: String): String =
        Context.toString(callGlobalFunction(scriptRuntime, "encodeURI", arrayOf(str)))

    @JvmStatic
    fun parseInt(scriptRuntime: ScriptRuntime, str: String?): Double =
        Context.toNumber(callGlobalFunction(scriptRuntime, "parseInt", arrayOf(str)))

    @JvmStatic
    fun parseInt(scriptRuntime: ScriptRuntime, str: String?, radix: Number): Double =
        Context.toNumber(callGlobalFunction(scriptRuntime, "parseInt", arrayOf(str, radix.toDouble())))

    @JvmStatic
    fun parseFloat(scriptRuntime: ScriptRuntime, str: String?): Double =
        Context.toNumber(callGlobalFunction(scriptRuntime, "parseFloat", arrayOf(str)))

    private fun callGlobalFunction(scriptRuntime: ScriptRuntime, name: String, paramsToFunction: Array<Any?>) =
        callFunction(scriptRuntime, scriptRuntime.topLevelScope, name, paramsToFunction)

    @JvmStatic
    fun ensureNativeArrayLength(o: NativeArray, length: Int, desc: String) {
        if (o.size != length) throw RuntimeException("Array argument for $desc must be length of $length")
    }

    @JvmStatic
    fun ensureNativeArrayLengthInRage(o: NativeArray, intRange: IntRange, desc: String) {
        if (o.size !in intRange) throw RuntimeException("Array argument for $desc must be length of $intRange")
    }

    @JvmStatic
    fun flatten(input: Any?): List<Any?> = when (input) {
        is Collection<*> -> input.flatMap { flatten(it ?: return@flatMap emptyList()) }
        is Array<*> -> input.flatMap { flatten(it ?: return@flatMap emptyList()) }
        else -> listOf(input)
    }

    @JvmStatic
    fun toFunctionName(cls: KClass<*>, func: KFunction<*>): String = "${cls.simpleName}.${func.name}"

    @JvmStatic
    fun toFunctionName(cls: KClass<*>, func: KFunction<*>, paramName: String): String = "${cls.simpleName}.${func.name}($paramName)"

    @JvmStatic
    fun runJavaScript(code: String): Any? = withRhinoContext { cx ->
        val scope = cx.getOrCreateStandardObjects()
        cx.evaluateString(scope, code, null, 1, null)
    }

    @JvmStatic
    fun isInteger(o: Any?): Boolean {
        return !o.isJsNullish() && o is Number && o.toDouble().let { d -> !d.isInfinite() && !d.isNaN() && (floor(d) == d) }
    }

    @JvmStatic
    @JvmOverloads
    fun coerceObject(o: Any?, def: NativeObject? = null): NativeObject = when {
        !o.isJsNullish() -> {
            require(o is NativeObject) { "Failed to coerce ${o.jsBrief()} into a JavaScript object" }
            o
        }
        else -> {
            require(!def.isJsNullish()) { "Failed to coerce nullish into a JavaScript object" }
            def!!
        }
    }

    @JvmStatic
    @JvmOverloads
    fun coerceArray(o: Any?, def: NativeArray? = null): NativeArray = when {
        !o.isJsNullish() -> {
            require(o is NativeArray) { "Failed to coerce ${o.jsBrief()} into a JavaScript array" }
            o
        }
        else -> {
            require(!def.isJsNullish()) { "Failed to coerce nullish into a JavaScript array" }
            def!!
        }
    }

    @JvmStatic
    @JvmOverloads
    fun coerceFunction(o: Any?, def: BaseFunction? = null): BaseFunction = when {
        !o.isJsNullish() -> {
            require(o is BaseFunction) { "Failed to coerce ${o.jsBrief()} into a JavaScript function" }
            o
        }
        else -> {
            require(!def.isJsNullish()) { "Failed to coerce nullish into a JavaScript function" }
            def!!
        }
    }

    @JvmStatic
    @JvmOverloads
    fun coerceRunnable(scriptRuntime: ScriptRuntime, o: Any?, def: Runnable? = null): Runnable = when {
        !o.isJsNullish() -> when (o) {
            is BaseFunction -> Runnable { callFunction(scriptRuntime, o, arrayOf()) }
            is Runnable -> o
            else -> throw IllegalArgumentException("Failed to coerce ${o.jsBrief()} into a Runnable")
        }
        else -> {
            require(!def.isJsNullish()) { "Failed to coerce nullish into a Runnable" }
            def!!
        }
    }

    @JvmStatic
    @JvmOverloads
    fun coerceString(o: Any?, def: String? = null): String = when {
        !o.isJsNullish() -> Context.toString(o)
        !def.isJsNullish() -> def!!
        else -> throw IllegalArgumentException("Failed to coerce nullish (${o.jsSpecies()}) into a string")
    }

    @JvmStatic
    @JvmOverloads
    fun coerceStringLowercase(o: Any?, def: String? = null): String = coerceString(o, def).lowercase()

    @JvmStatic
    @JvmOverloads
    fun coerceStringUppercase(o: Any?, def: String? = null): String = coerceString(o, def).uppercase()

    @JvmStatic
    @JvmOverloads
    fun coerceBoolean(o: Any?, def: Boolean? = null): Boolean = when {
        !o.isJsNullish() -> Context.toBoolean(o)
        !def.isJsNullish() -> def!!
        else -> throw IllegalArgumentException("Failed to coerce nullish (${o.jsSpecies()}) into a boolean")
    }

    @JvmStatic
    @JvmOverloads
    fun coerceNumber(o: Any?, def: Number? = null): Double = when {
        !o.isJsNullish() -> Context.toNumber(o).also {
            require(!it.isNaN() || def == RhinoScriptRuntime.NaN) {
                "Failed to coerce ${o.jsBrief()} into a valid number"
            }
        }
        !def.isJsNullish() -> def!!.toDouble()
        else -> throw IllegalArgumentException("Failed to coerce nullish (${o.jsSpecies()}) into a valid number")
    }

    @JvmStatic
    @JvmOverloads
    fun coerceIntNumber(o: Any?, def: Number? = null) = coerceNumber(o, def).roundToInt()

    @JvmStatic
    @JvmOverloads
    fun coerceLongNumber(o: Any?, def: Number? = null) = coerceNumber(o, def).roundToLong()

    @JvmStatic
    @JvmOverloads
    fun coerceFloatNumber(o: Any?, def: Number? = null) = coerceNumber(o, def).toFloat()

    @JvmStatic
    @JvmOverloads
    fun coerceDoubleNumber(o: Any?, def: Number? = null) = coerceNumber(o, def)

    @JvmStatic
    @JvmOverloads
    fun callPrototypeFunction(builtins: TopLevel.Builtins, funcName: String, thisObj: Scriptable, args: Array<Any?> = arrayOf()): Any? = withRhinoContext { cx ->
        callPrototypeFunction(builtins, funcName, thisObj, ImporterTopLevel(cx), args)
    }

    @JvmStatic
    @JvmOverloads
    fun callPrototypeFunction(className: String, funcName: String, thisObj: Scriptable, args: Array<Any?> = arrayOf()): Any? = withRhinoContext { cx ->
        callPrototypeFunction(className, funcName, thisObj, ImporterTopLevel(cx), args)
    }

    @JvmStatic
    @JvmOverloads
    fun callPrototypeFunction(builtins: TopLevel.Builtins, funcName: String, thisObj: Scriptable, scope: Scriptable, args: Array<Any?> = arrayOf()): Any? = withRhinoContext { cx ->
        val prototypeFunction = getPrototypeFunction(scope, builtins, funcName)
        prototypeFunction.call(cx, scope, thisObj, args)
    }

    @JvmStatic
    @JvmOverloads
    fun callPrototypeFunction(className: String, funcName: String, thisObj: Scriptable, scope: Scriptable, args: Array<Any?> = arrayOf()): Any? = withRhinoContext { cx ->
        val prototypeFunction = getPrototypeFunction(scope, className, funcName)
        prototypeFunction.call(cx, scope, thisObj, args)
    }

    @JvmStatic
    fun getPrototypeFunction(scope: Scriptable, builtins: TopLevel.Builtins, funcName: String) = TopLevel.getBuiltinPrototype(
        ScriptableObject.getTopLevelScope(scope), builtins
    ).prop(funcName) as BaseFunction

    @JvmStatic
    fun getPrototypeFunction(scope: Scriptable, className: String, funcName: String) = ScriptableObject.getClassPrototype(
        scope, className
    ).prop(funcName) as BaseFunction

    @JvmStatic
    fun javaArrayToString(o: Any?): String = when (o) {
        is CharArray -> "[ ${o.joinToString(", ")} ]"
        is BooleanArray -> "[ ${o.joinToString(", ")} ]"
        is DoubleArray -> "[ ${o.joinToString(", ")} ]"
        is FloatArray -> "[ ${o.joinToString(", ")} ]"
        is IntArray -> "[ ${o.joinToString(", ")} ]"
        is LongArray -> "[ ${o.joinToString(", ")} ]"
        is ShortArray -> "[ ${o.joinToString(", ")} ]"
        is ByteArray -> "[ ${o.joinToString(", ")} ]"
        is Array<*> -> "[ ${o.joinToString(", ")} ]"
        else -> throw IllegalArgumentException("Invalid java array: $o")
    }

    @JvmStatic
    fun javaObjectToString(o: Any?): String {
        o ?: return "null"
        return "${o.javaClass.name}@${Integer.toHexString(o.hashCode())}"
    }

    @JvmStatic
    fun js_array_isArray(arg: Scriptable) = arg.className == "Array"

    @Suppress("UnnecessaryVariable")
    @JvmStatic
    fun js_object_assign(tar: Scriptable?, src: Scriptable?): Scriptable = withRhinoContext { cx ->
        val topLevelScope = ImporterTopLevel(cx)
        val targetObj = when (tar != null) {
            true -> toObject(cx, topLevelScope, tar)
            else -> toObject(cx, topLevelScope, UNDEFINED)
        }
        if (src.isJsNullish()) {
            return@withRhinoContext targetObj
        }
        val sourceObj = toObject(cx, topLevelScope, src)
        for (key in sourceObj.ids) {
            when (key) {
                is Int -> {
                    val intId = key
                    if (sourceObj.has(intId, sourceObj)) {
                        AbstractEcmaObjectOperations.put(cx, targetObj, intId, sourceObj[intId, sourceObj], true)
                    }
                }
                else -> {
                    val stringId = toString(key)
                    if (sourceObj.has(stringId, sourceObj)) {
                        AbstractEcmaObjectOperations.put(cx, targetObj, stringId, sourceObj.prop(stringId), true)
                    }
                }
            }
        }
        return@withRhinoContext targetObj
    }

    @JvmStatic
    fun js_object_keys(arg: ScriptableObject): NativeArray = withRhinoContext { cx ->
        val topLevel = ImporterTopLevel(cx)
        val obj = toObject(cx, topLevel, arg)
        val ids = obj.ids
        ids.indices.forEach { i -> ids[i] = toString(ids[i]) }
        cx.newArray(topLevel, ids) as NativeArray
    }

    @JvmStatic
    fun js_object_values(arg: ScriptableObject): NativeArray = withRhinoContext { cx ->
        val topLevel = ImporterTopLevel(cx)
        val obj = toObject(cx, topLevel, arg)
        var ids = obj.ids
        var j = 0
        for (i in ids.indices) {
            if (ids[i] is Int) {
                val intId = ids[i] as Int
                if (obj.has(intId, obj) && isEnumerable(intId, obj)) {
                    ids[j++] = obj[intId, obj]
                }
            } else {
                val stringId = toString(ids[i])
                // getter may remove keys
                if (obj.has(stringId, obj) && isEnumerable(stringId, obj)) {
                    ids[j++] = obj[stringId, obj]
                }
            }
        }
        if (j != ids.size) {
            ids = ids.copyOf(j)
        }
        cx.newArray(topLevel, ids) as NativeArray
    }

    @JvmStatic
    @JvmOverloads
    fun js_object_create(o: Scriptable? = null, properties: ScriptableObject? = null): NativeObject = withRhinoContext { cx ->
        val topLevel = ImporterTopLevel(cx)
        newNativeObject().also {
            it.parentScope = topLevel
            it.prototype = when (o) {
                null -> null
                else -> ensureScriptable(o)
            }
            if (!properties.isJsNullish()) {
                it.defineOwnProperties(cx, ensureScriptableObject(Context.toObject(properties, topLevel)))
            }
        }
    }

    @JvmStatic
    fun js_object_getPrototypeOf(o: Scriptable?): Scriptable? = withRhinoContext { cx ->
        val topLevel = ImporterTopLevel(cx)
        NativeObject.getCompatibleObject(cx, topLevel, o).prototype
    }

    @JvmStatic
    fun js_object_setPrototypeOf(o: Scriptable, proto: Scriptable?): Scriptable {
        val protoObj = when (proto) {
            null -> null
            else -> ensureScriptable(proto)
        }
        return when {
            protoObj is Symbol -> throw typeErrorById("msg.arg.not.object", js_typeof(protoObj))
            o !is ScriptableObject -> o
            !o.isExtensible -> throw typeErrorById("msg.not.extensible")
            // cycle detection
            else -> {
                var prototypeProto = protoObj
                while (prototypeProto != null) {
                    if (prototypeProto == o) {
                        throw typeErrorById("msg.object.cyclic.prototype", o.javaClass.simpleName)
                    }
                    prototypeProto = prototypeProto.prototype
                }
                o.apply { prototype = protoObj }
            }
        }
    }

    @JvmStatic
    fun js_object_hasOwnProperty(o: Scriptable, property: String): Boolean = withRhinoContext { cx ->
        // Context.toBoolean(callPrototypeFunction(TopLevel.Builtins.Object, "hasOwnProperty", o, arrayOf(property)))
        AbstractEcmaObjectOperations.hasOwnProperty(cx, o, property)
    }

    @JvmStatic
    fun js_object_getOwnPropertyNames(o: Scriptable): NativeArray = withRhinoContext { cx ->
        val topLevel = ImporterTopLevel(cx)
        val obj = ensureScriptableObject(toObject(cx, topLevel, o))
        // val ids = obj.getIds(true, false)
        // ids.indices.forEach { i -> ids[i] = toString(ids[i]) }
        // cx.newArray(topLevel, ids) as NativeArray
        try {
            val map = obj.startCompoundOp(false)
            val ids = obj.getIds(map, true, false)
            ids.indices.forEach { i -> ids[i] = toString(ids[i]) }
            cx.newArray(topLevel, ids) as NativeArray
        } catch (_: Exception) {
            cx.newArray(topLevel, emptyArray()) as NativeArray
        }
    }

    @JvmStatic
    fun js_object_getOwnPropertyDescriptor(value: ScriptableObject, key: Any): ScriptableObject = withRhinoContext { cx ->
        val topLevel = ImporterTopLevel(cx)
        val obj = ensureScriptableObject(toObject(cx, topLevel, value))
        obj.getOwnPropertyDescriptor(cx, key).toScriptableObject(topLevel)
    }

    @JvmStatic
    fun js_function_bind(scope: Scriptable? = null, targetFunction: Callable, vararg args: Scriptable): BoundFunction = withRhinoContext { cx ->
        val topLevel = scope ?: ImporterTopLevel(cx)
        val argc: Int = args.size
        val boundThis: Scriptable?
        val boundArgs: Array<Any?>
        when {
            argc > 0 -> {
                boundThis = RhinoScriptRuntime.toObjectOrNull(cx, args[0], topLevel)
                boundArgs = arrayOfNulls(argc - 1)
                System.arraycopy(args, 1, boundArgs, 0, argc - 1)
            }
            else -> {
                boundThis = null
                boundArgs = emptyArgs
            }
        }
        BoundFunction(cx, topLevel, targetFunction, boundThis, boundArgs)
    }

    @JvmStatic
    fun js_json_parse(text: String): Any? = withRhinoContext { cx ->
        val topLevel = ImporterTopLevel(cx)
        JsonParser(cx, topLevel).parseValue(text)
    }

    @JvmStatic
    @JvmOverloads
    fun js_json_stringify(value: Any?, replacer: Any? = null, space: Any? = null): Any = withRhinoContext { cx ->
        val topLevel = ImporterTopLevel(cx)
        NativeJSON.stringify(cx, topLevel, value, replacer, space)
    }

    @JvmStatic
    fun js_typeof(value: Any?): String = `typeof`(value)

    @JvmStatic
    fun js_require(scriptRuntime: ScriptRuntime, id: String): Any? {
        return callFunction(scriptRuntime, scriptRuntime.topLevelScope, "require", arrayOf(id))
    }

    @JvmStatic
    fun js_date_parseString(s: String): Double = withRhinoContext { cx ->
        NativeDate.date_parseString(cx, s)
    }

    @JvmStatic
    fun js_eval(scope: Scriptable, s: String): Any? = withRhinoContext { cx ->
        val global = ScriptableObject.getTopLevelScope(scope)
        RhinoScriptRuntime.evalSpecial(cx, global, global, arrayOf(s), "eval code", 1)
    }

    @JvmStatic
    @JvmOverloads
    fun <R> withRhinoContext(scriptRuntime: ScriptRuntime? = null, function: (context: Context) -> R): R {
        var cxRhino: Context? = null
        return try {
            val cx = Context.getCurrentContext()
                ?: when (scriptRuntime) {
                    null -> {
                        // This fallback Context.enter() does NOT guarantee WrapFactory from RhinoJavaScriptEngine.
                        // zh-CN: 这个兜底的 Context.enter() 无法保证使用 RhinoJavaScriptEngine 的 WrapFactory.
                        Context.enter()
                    }
                    else -> {
                        // Enter Rhino Context via engine's ContextFactory to keep WrapFactory and AutoJsContext bindings effective.
                        // zh-CN: 通过引擎的 ContextFactory 进入 Rhino Context, 以保持 WrapFactory 和 AutoJsContext 绑定生效.
                        val engine = scriptRuntime.engines.myEngine()
                        require(engine is RhinoJavaScriptEngine) {
                            "Current engine ${engine.javaClass.name} is not a RhinoJavaScriptEngine"
                        }
                        engine.enterContext()
                    }
                }.apply {
                    languageVersion = Context.VERSION_ES6
                    isInterpretedMode = true
                }.also { cxRhino = it }

            function.invoke(cx)
        } finally {
            cxRhino?.let { Context.exit() }
        }
    }

    fun <T, R> T.withTimeConsuming(name: String, function: (thisArg: T) -> R?): R? {

        // @Hint by SuperMonster003 on Apr 5, 2025.
        //  ! Kotlin's measureTimeMillis also could be used to calculate the execution time of a function.
        //  ! zh-CN: 也可以使用 kotlin 的 measureTimeMillis 计算函数执行耗时.

        val start = System.currentTimeMillis()
        val result = function.invoke(this)
        if (BuildConfig.DEBUG) {
            val duration = System.currentTimeMillis() - start
            val msg = "$name: $duration"
            AutoJs.instance.globalConsole.warn(msg)
        }
        return result
    }

    fun undefined(function: () -> Any?): Undefined = UNDEFINED.also {
        function.invoke()
    }

    fun now() = System.currentTimeMillis()

    private fun parseConstructFunc(constructFunc: Int, funcName: String?): ((Array<out Any?>) -> Nothing)? = when (constructFunc) {
        NOT_CONSTRUCTABLE -> { _: Array<out Any?> -> throw RuntimeException("Function ${funcName ?: "anonymous"} is unable to be invoked with \"new\" operator") }
        DEFAULT_CONSTRUCTOR -> null
        else -> throw IllegalArgumentException("Unknown flag $constructFunc for argument constructFunc of RhinoUtils.newBaseFunction")
    }

    private fun parseCallFunc(callFunc: Int, funcName: String?): ((Array<out Any?>) -> Nothing)? = when (callFunc) {
        NOT_CALLABLE -> { _: Array<out Any?> -> throw RuntimeException("Function ${funcName ?: "anonymous"} is unable to be invoked as a function") }
        DEFAULT_CALLER -> null
        else -> throw IllegalArgumentException("Unknown flag $callFunc for argument callFunc of RhinoUtils.newBaseFunction")
    }

    private fun isEnumerable(key: String, obj: Any): Boolean {
        return obj !is ScriptableObject || obj.getAttributes(key) and DONTENUM == 0
    }

    private fun isEnumerable(index: Int, obj: Any): Boolean {
        return obj !is ScriptableObject || obj.getAttributes(index) and DONTENUM == 0
    }

    internal fun initNewBaseFunction(baseFunction: BaseFunction) {
        // @Caution by SuperMonster003 on Nov 9, 2024.
        //  ! This will cause severely memory leak.
        //  ! zh-CN: 这将导致严重的内存泄漏.
        //  # baseFunction.exportAsJSClass(BaseFunction.MAX_PROTOTYPE_ID, ImporterTopLevel(Context.getCurrentContext()), false)

        baseFunction.prototype = functionPrototype

        val prototypeObject = baseFunction["prototype"]
        when {
            prototypeObject.isJsNullish() -> {
                baseFunction.defineProp("prototype", newNativeObject().also { o ->
                    o.defineProp("constructor", baseFunction, DONTENUM)
                }, DONTENUM)
            }
            prototypeObject is Scriptable -> {
                prototypeObject.defineProp("constructor", baseFunction, DONTENUM)
            }
        }
    }

    @JvmStatic
    fun initNativeObjectPrototype(o: NativeObject): NativeObject {
        return o.apply { prototype = objectPrototype }
    }

    @JvmStatic
    fun initNativeArrayPrototype(a: NativeArray): NativeArray {
        return a.apply { prototype = arrayPrototype }
    }

    @JvmStatic
    fun newNativeObject() = initNativeObjectPrototype(NativeObject())

    @JvmStatic
    @JvmOverloads
    fun newNativeArray(lengthArg: Long = 0) = initNativeArrayPrototype(NativeArray(lengthArg))

    @JvmStatic
    fun newNativeArray(array: Array<Any?>) = initNativeArrayPrototype(NativeArray(array))

    @JvmStatic
    fun hashCodeOfScriptable(other: Any?): Int? {
        if (other !is Scriptable) return null
        val getClassFunc = other["getClass"]
        if (getClassFunc is BaseFunction) {
            val clazz = callFunction(getClassFunc, emptyArray()).jsUnwrapped()
            if (clazz != UiObjectCollection::class.java) return null
        }
        val hashCodeFunc = other["hashCode"]
        if (hashCodeFunc is BaseFunction) {
            val hashCode = callFunction(hashCodeFunc, emptyArray()).jsUnwrapped()
            if (hashCode is Number) return hashCode.toInt()
        }
        return null
    }

    fun handleAsyncOperation(scriptRuntime: ScriptRuntime, func: () -> Any?): NativeObject {
        return handleAsyncOperation(scriptRuntime, func) { it }
    }

    fun <T> handleAsyncOperation(
        scriptRuntime: ScriptRuntime,
        operation: () -> T,
        uiMapper: (T) -> Any?,
    ): NativeObject {
        val promiseAdapter = ScriptPromiseAdapter()

        // Use Android Thread directly to avoid coupling with JS threads/loopers here.
        // zh-CN: 直接使用 Android Thread, 避免在此处与 JS 线程/Looper 机制强耦合.
        Thread {
            try {
                // Do the blocking work (e.g. network request) in background thread.
                // zh-CN: 在后台线程执行阻塞式操作 (如网络请求).
                val result = operation.invoke()

                // Resolve on UI thread so that UI scripts can safely touch `ui.*` in then().
                // zh-CN: 在 UI 线程 resolve, 让 UI 脚本在 then() 中可安全操作 `ui.*`.
                Handler(Looper.getMainLooper()).post {
                    try {
                        // Map result on UI thread (e.g. wrap Java objects into Rhino objects).
                        // zh-CN: 在 UI 线程映射结果 (例如将 Java 对象包装为 Rhino 对象).
                        val mapped = uiMapper.invoke(result)
                        promiseAdapter.resolve(mapped)
                    } catch (t: Throwable) {
                        promiseAdapter.reject(t)
                    }
                }
            } catch (t: Throwable) {
                // Reject on UI thread for consistent callback threading.
                // zh-CN: 在 UI 线程 reject, 保持回调线程一致性.
                Handler(Looper.getMainLooper()).post {
                    promiseAdapter.reject(t)
                }
            }
        }.start()

        // Convert ScriptPromiseAdapter into a JavaScript Promise.
        // zh-CN: 将 ScriptPromiseAdapter 转换为 JavaScript Promise.
        return callFunction(scriptRuntime, scriptRuntime.js_ResultAdapter, "promise", arrayOf(promiseAdapter)) as NativeObject
    }

    @JvmStatic
    fun Class<*>.getRhinoStandardFunctionMethods(): List<Method> {
        return this.declaredMethods.filter {
            it.isAnnotationPresent(RhinoStandardFunctionInterface::class.java)
        }
    }

    class ObsoletedRhinoFunctionException(funcName: String) : Exception(
        "Function \"$funcName\" can no longer be used as it has been obsoleted",
    )

    open class InternalBaseFunction(private val funcName: String?) : BaseFunction() {

        internal fun init() = initNewBaseFunction(this)

        internal fun callFunc(callFunc: ((args: Array<out Any?>) -> Any?)?, cx: Context?, scope: Scriptable?, thisObj: Scriptable?, args: Array<out Any?>): Any? {
            return when (callFunc) {
                null -> super.call(cx, scope ?: ImporterTopLevel(cx), thisObj, args)
                else -> callFunc.invoke(args)
            }
        }

        internal fun constructFunc(constructFunc: ((args: Array<out Any?>) -> Any?)?, cx: Context?, scope: Scriptable?, args: Array<out Any?>): Scriptable {
            return when (constructFunc) {
                null -> super.construct(cx, scope ?: ImporterTopLevel(cx), args)
                else -> when (val result = constructFunc.invoke(args)) {
                    is Int -> constructFlag(result, cx, scope, args)
                    else -> result.also {
                        require(it is Scriptable) { "Result of constructor for ${getFunctionName()} must be a Rhino Scriptable instead of ${it.jsBrief()}" }
                    } as Scriptable
                }
            }.also { it.prototype = prototype }
        }

        internal fun callFlag(flag: Int, cx: Context?, scope: Scriptable?, thisObj: Scriptable?, args: Array<out Any?>) = when (flag) {
            DEFAULT_CALLER -> super.call(cx, scope ?: ImporterTopLevel(cx), thisObj, args)
            NOT_CALLABLE -> throw RuntimeException("Function ${funcName ?: "anonymous"} is unable to be invoked as a function")
            else -> throw IllegalArgumentException("Unknown flag $flag for argument callFunc of RhinoUtils.newBaseFunction")
        }

        internal fun constructFlag(flag: Int, cx: Context?, scope: Scriptable?, args: Array<out Any?>) = when (flag) {
            DEFAULT_CONSTRUCTOR -> super.construct(cx, scope ?: ImporterTopLevel(cx), args)
            NOT_CONSTRUCTABLE -> throw RuntimeException("Function ${funcName ?: "anonymous"} is unable to be invoked with \"new\" operator")
            else -> throw IllegalArgumentException("Unknown flag $flag for argument constructFunc of RhinoUtils.newBaseFunction")
        }

        internal fun checkFlagsConflict(callFlag: Int, constructFlag: Int) {
            when (constructFlag) {
                DEFAULT_CONSTRUCTOR -> require(callFlag != NOT_CALLABLE) {
                    "BaseFunction ${toString()} cannot have the flag \"NOT_CALLABLE\" simultaneously with the flag \"DEFAULT_CONSTRUCTOR\""
                }
            }
        }

        override fun getFunctionName() = funcName ?: "anonymous"

        override fun toString(): String = "function ${funcName ?: ""}() { ... }"

        override fun getDefaultValue(typeHint: Class<*>?) = when (typeHint) {
            RhinoScriptRuntime.StringClass -> toString()
            else -> this
        }

    }

}
