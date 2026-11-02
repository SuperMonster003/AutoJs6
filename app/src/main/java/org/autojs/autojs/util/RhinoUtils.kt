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
import org.mozilla.javascript.ContinuationTopCall
import org.mozilla.javascript.LambdaConstructor
import org.mozilla.javascript.LambdaFunction
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeDate
import org.mozilla.javascript.NativeJSON
import org.mozilla.javascript.NativeJavaMethod
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
import org.mozilla.javascript.SerializableCallable
import org.mozilla.javascript.SerializableConstructable
import org.mozilla.javascript.Symbol
import org.mozilla.javascript.TopLevel
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.VarScope
import org.mozilla.javascript.WithScope
import org.mozilla.javascript.Wrapper
import org.mozilla.javascript.json.JsonParser
import java.io.Serializable
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.math.BigInteger
import java.util.IdentityHashMap
import java.util.concurrent.ExecutionException
import java.util.concurrent.FutureTask
import kotlin.math.floor
import kotlin.reflect.KClass
import kotlin.reflect.KFunction
import org.mozilla.javascript.Function as RhinoFunction
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
    fun Context.getOrCreateStandardObjects(): TopLevel {
        // Cache standard objects per Context to avoid repeated initStandardObjects().
        // zh-CN: 按 Context 缓存标准对象, 避免重复调用 initStandardObjects().
        val cached = getRegisteredStandardObjects()
        if (cached != null) return cached
        return TopLevelScope().also { created ->
            created.initStandardObjects(this, false)
            registerStandardObjects(created)
        }
    }

    @JvmStatic
    fun Context.registerStandardObjects(scope: TopLevel) {
        putThreadLocal(STANDARD_OBJECTS_KEY, scope)
    }

    /** Drops the standard objects [registerStandardObjects] put in this Context (the engine that registered them has ended). */
    @JvmStatic
    fun Context.unregisterStandardObjects() {
        removeThreadLocal(STANDARD_OBJECTS_KEY)
    }

    private fun Context.getRegisteredStandardObjects(): TopLevel? =
        getThreadLocal(STANDARD_OBJECTS_KEY) as? TopLevel

    @JvmStatic
    fun Context.getCurrentTopCallScopeOrDefault(): VarScope = when {
        RhinoScriptRuntime.hasTopCall(this) -> RhinoScriptRuntime.getTopCallScope(this)
        else -> getRegisteredStandardObjects() ?: standardObjects
    }

    @JvmStatic
    fun Context.getCurrentTopCallScopeOrFallback(): VarScope = when {
        RhinoScriptRuntime.hasTopCall(this) -> RhinoScriptRuntime.getTopCallScope(this)
        else -> getRegisteredStandardObjects() ?: standardObjects
    }

    @JvmStatic
    val standardObjects: TopLevel by lazy {
        // Create a global singleton standard objects as a low-fidelity fallback.
        // zh-CN: 创建全局单例标准对象, 用作低保真兜底.
        // Always in a Context of its own: never inside the Context of the script that asks first, so that nothing of that
        // script (its engine binding, WrapFactory, interrupt state) takes part in it (docs/memory/ROADMAP.md appendix C Q11).
        // zh-CN: 总在单独的 Context 中创建, 不在首次请求它的脚本的 Context 内创建, 使该脚本的任何状态 (引擎绑定, WrapFactory, 中断状态)
        // 都不参与其中 (docs/memory/ROADMAP.md 附录 C Q11).
        when (Context.getCurrentContext()) {
            null -> createStandardObjects()
            else -> FutureTask(::createStandardObjects).let { task ->
                Thread(task, "RhinoStandardObjects").start()
                try {
                    task.get()
                } catch (e: ExecutionException) {
                    throw e.cause ?: e
                }
            }
        }
    }

    private fun createStandardObjects(): TopLevel {
        val cx = Context.enter()
        try {
            return TopLevelScope().apply { initStandardObjects(cx, false) }
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
        standardObjects.prop("Function").let {
            if (it is Scriptable) it["prototype"] as? Scriptable else null
        } ?: throw IllegalStateException("Failed to get object: Function.prototype")
    }

    @JvmStatic
    fun getTopLevelScope(scope: Scriptable): TopLevel =
        ScriptableObject.getTopLevelScope(getDeclarationScope(scope))

    @JvmStatic
    fun getObjectPrototype(scope: Scriptable): Scriptable =
        ScriptableObject.getObjectPrototype(getDeclarationScope(scope))

    @JvmStatic
    fun getArrayPrototype(scope: Scriptable): Scriptable =
        ScriptableObject.getArrayPrototype(getDeclarationScope(scope))

    @JvmStatic
    fun getFunctionPrototype(scope: Scriptable): Scriptable =
        ScriptableObject.getFunctionPrototype(getDeclarationScope(scope))

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
        if (property !is Callable) throw IllegalArgumentException("Property $name on $obj must be callable instead of ${property.jsBrief()}")
        return callFunction(null, property, getDeclarationScope(obj), obj, paramsToFunction)
    }

    @JvmStatic
    @Throws(NoSuchMethodException::class, InvocationTargetException::class, IllegalAccessException::class)
    fun callFunction(scriptRuntime: ScriptRuntime, obj: Scriptable, name: String, paramsToFunction: Array<Any?>): Any? {
        val property = obj.prop(name)
        if (property == NOT_FOUND) throw IllegalArgumentException("Property $name not found on $obj")
        if (property !is Callable) throw IllegalArgumentException("Property $name on $obj must be callable instead of ${property.jsBrief()}")
        return callFunction(scriptRuntime, property, getDeclarationScope(obj), obj, paramsToFunction)
    }

    @JvmStatic
    @Throws(NoSuchMethodException::class, InvocationTargetException::class, IllegalAccessException::class)
    fun callFunction(scriptRuntime: ScriptRuntime, scope: VarScope, name: String, paramsToFunction: Array<Any?>): Any? {
        val obj = ScriptableObject.getTopLevelScope(scope).globalThis
        return callFunction(scriptRuntime, obj, name, paramsToFunction)
    }

    @JvmStatic
    @Throws(NoSuchMethodException::class, InvocationTargetException::class, IllegalAccessException::class)
    fun callFunction(func: Callable, paramsToFunction: Array<Any?>): Any? =
        callFunction(func, null as VarScope?, paramsToFunction)

    @JvmStatic
    @Throws(NoSuchMethodException::class, InvocationTargetException::class, IllegalAccessException::class)
    fun callFunction(scriptRuntime: ScriptRuntime, func: Callable, paramsToFunction: Array<Any?>): Any? =
        callFunction(scriptRuntime, func, null as VarScope?, paramsToFunction)

    @JvmStatic
    @Throws(NoSuchMethodException::class, InvocationTargetException::class, IllegalAccessException::class)
    fun callFunction(func: Callable, scope: Scriptable?, args: Array<Any?>): Any? =
        callFunction(func, scope?.let { getDeclarationScope(it) }, scope, args)

    @JvmStatic
    @Throws(NoSuchMethodException::class, InvocationTargetException::class, IllegalAccessException::class)
    fun callFunction(scriptRuntime: ScriptRuntime, func: Callable, scope: Scriptable?, args: Array<Any?>): Any? =
        callFunction(scriptRuntime, func, scope?.let { getDeclarationScope(it) }, scope, args)

    @JvmStatic
    @Throws(NoSuchMethodException::class, InvocationTargetException::class, IllegalAccessException::class)
    fun callFunction(func: Callable, scope: VarScope?, args: Array<Any?>): Any? =
        callFunction(func, scope, Undefined.SCRIPTABLE_UNDEFINED, args)

    @JvmStatic
    @Throws(NoSuchMethodException::class, InvocationTargetException::class, IllegalAccessException::class)
    fun callFunction(scriptRuntime: ScriptRuntime, func: Callable, scope: VarScope?, args: Array<Any?>): Any? =
        callFunction(scriptRuntime, func, scope, Undefined.SCRIPTABLE_UNDEFINED, args)

    @JvmStatic
    @Throws(NoSuchMethodException::class, InvocationTargetException::class, IllegalAccessException::class)
    fun callFunction(func: Callable, scope: Scriptable?, thisObj: Scriptable?, args: Array<Any?>): Any? =
        callFunction(null, func, scope?.let { getDeclarationScope(it) }, thisObj, args)

    @JvmStatic
    @Throws(NoSuchMethodException::class, InvocationTargetException::class, IllegalAccessException::class)
    fun callFunction(func: Callable, scope: VarScope?, thisObj: Scriptable?, args: Array<Any?>): Any? =
        callFunction(null, func, scope, thisObj, args)

    @JvmStatic
    fun callFunction(scriptRuntime: ScriptRuntime?, func: Callable, scope: Scriptable?, thisObj: Scriptable?, args: Array<Any?>): Any? =
        callFunction(scriptRuntime, func, scope?.let { getDeclarationScope(it) }, thisObj, args)

    @JvmStatic
    fun callFunction(scriptRuntime: ScriptRuntime?, func: Callable, scope: VarScope?, thisObj: Scriptable?, args: Array<Any?>): Any? =
        callFunction(scriptRuntime, func, scope, thisObj, args, asCoroutine = false)

    /**
     * Like [callFunction], but when no script code runs on this thread and [func] is an interpreted script function, it is
     * called as the top of a coroutine ([ContinuationTopCall]): it may suspend (`promise.await()`, `continuation.delay` and so
     * on), and the call then returns undefined while the coroutine waits to be resumed (docs/augment/ROADMAP.md Q23).
     *
     * zh-CN: 与 [callFunction] 相同, 但当前线程上没有脚本代码运行且 [func] 是解释执行的脚本函数时, 把它作为协程的顶层调用 ([ContinuationTopCall]):
     * 它可以挂起 (`promise.await()`, `continuation.delay` 等), 此时本调用返回 undefined, 协程等待之后恢复 (docs/augment/ROADMAP.md Q23).
     */
    @JvmStatic
    fun callFunctionAsCoroutine(scriptRuntime: ScriptRuntime?, func: Callable, scope: VarScope?, thisObj: Scriptable?, args: Array<Any?>): Any? =
        callFunction(scriptRuntime, func, scope, thisObj, args, asCoroutine = true)

    private fun callFunction(
        scriptRuntime: ScriptRuntime?,
        func: Callable,
        scope: VarScope?,
        thisObj: Scriptable?,
        args: Array<Any?>,
        asCoroutine: Boolean,
    ): Any? = withRhinoContext(scriptRuntime) { cx ->
        try {
            // Prefer an existing scope to avoid repeatedly creating standard objects.
            // zh-CN: 优先使用已有的作用域, 避免反复创建标准对象.
            val declarationScope = (func as? RhinoFunction)?.declarationScope
            val niceScope = when {
                scope != null -> scope
                declarationScope != null -> declarationScope
                scriptRuntime != null -> scriptRuntime.topLevelScope
                else -> cx.getOrCreateStandardObjects()
            }
            val niceThisObj = thisObj ?: Undefined.SCRIPTABLE_UNDEFINED
            when {
                RhinoScriptRuntime.hasTopCall(cx) -> func.call(cx, niceScope, niceThisObj, args)
                asCoroutine && ContinuationTopCall.canCall(cx, func) -> try {
                    ContinuationTopCall.call(cx, func, niceScope, niceThisObj, args)
                } catch (_: ContinuationPending) {
                    // Suspended: the coroutine is captured and is resumed later.
                    // zh-CN: 已挂起: 协程已被捕获, 稍后恢复.
                    Undefined.instance
                }
                else -> RhinoScriptRuntime.doTopCall(func, cx, niceScope, niceThisObj, args, false)
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
    fun constructFunction(ctor: RhinoFunction, scope: TopLevelScope, args: Array<Any?>): Scriptable = withRhinoContext { cx ->
        ctor.construct(cx, scope, args)
    }

    @JvmStatic
    fun newBaseFunction(
        funcName: String?,
        callFunc: Int,
        constructFunc: Int,
    ): BaseFunction = withFunctionCreationScope {
        newBaseFunction(it, funcName, callFunc, constructFunc)
    }

    @JvmStatic
    fun newBaseFunction(
        scope: VarScope,
        funcName: String?,
        callFunc: Int,
        constructFunc: Int,
    ): BaseFunction {
        validateCallFlag(callFunc)
        validateConstructFlag(constructFunc)
        require(callFunc != NOT_CALLABLE || constructFunc != DEFAULT_CONSTRUCTOR) {
            "BaseFunction ${funcName ?: "anonymous"} cannot have the flag \"NOT_CALLABLE\" simultaneously with the flag \"DEFAULT_CONSTRUCTOR\""
        }
        val target = callableFromFlag(callFunc, funcName)
        return when (constructFunc) {
            NOT_CONSTRUCTABLE -> newLambdaFunction(scope, funcName, target)
            DEFAULT_CONSTRUCTOR -> newLambdaConstructor(scope, funcName, target, defaultConstructorTarget(target))
            else -> error("Unreachable construct flag: $constructFunc")
        }
    }

    @JvmStatic
    fun newBaseFunction(
        funcName: String?,
        callFunc: Int,
        constructFunc: ((args: Array<out Any?>) -> Any?)?,
    ): BaseFunction = withFunctionCreationScope {
        newBaseFunction(it, funcName, callFunc, constructFunc)
    }

    @JvmStatic
    fun newBaseFunction(
        scope: VarScope,
        funcName: String?,
        callFunc: Int,
        constructFunc: ((args: Array<out Any?>) -> Any?)?,
    ): BaseFunction {
        validateCallFlag(callFunc)
        val target = callableFromFlag(callFunc, funcName)
        val targetConstructor = constructorTarget(funcName, constructFunc, target)
        return when (callFunc) {
            NOT_CALLABLE -> newLambdaConstructor(scope, funcName, targetConstructor)
            DEFAULT_CALLER -> newLambdaConstructor(scope, funcName, target, targetConstructor)
            else -> error("Unreachable call flag: $callFunc")
        }
    }

    @JvmStatic
    fun newBaseFunction(
        funcName: String?,
        callFunc: ((args: Array<out Any?>) -> Any?)?,
        constructFunc: Int,
    ): BaseFunction = withFunctionCreationScope {
        newBaseFunction(it, funcName, callFunc, constructFunc)
    }

    @JvmStatic
    fun newBaseFunction(
        scope: VarScope,
        funcName: String?,
        callFunc: ((args: Array<out Any?>) -> Any?)?,
        constructFunc: Int,
    ): BaseFunction {
        validateConstructFlag(constructFunc)
        val target = callable(callFunc)
        return when (constructFunc) {
            NOT_CONSTRUCTABLE -> newLambdaFunction(scope, funcName, target)
            DEFAULT_CONSTRUCTOR -> newLambdaConstructor(scope, funcName, target, defaultConstructorTarget(target))
            else -> error("Unreachable construct flag: $constructFunc")
        }
    }

    @JvmStatic
    fun newBaseFunction(
        funcName: String?,
        callFunc: ((args: Array<out Any?>) -> Any?)?,
        constructFunc: ((args: Array<out Any?>) -> Any?)?,
    ): BaseFunction = withFunctionCreationScope {
        newBaseFunction(it, funcName, callFunc, constructFunc)
    }

    @JvmStatic
    fun newBaseFunction(
        scope: VarScope,
        funcName: String?,
        callFunc: ((args: Array<out Any?>) -> Any?)?,
        constructFunc: ((args: Array<out Any?>) -> Any?)?,
    ): BaseFunction {
        val target = callable(callFunc)
        return newLambdaConstructor(scope, funcName, target, constructorTarget(funcName, constructFunc, target))
    }

    private fun <R> withFunctionCreationScope(block: (VarScope) -> R): R =
        withRhinoContext { cx -> block(cx.getCurrentTopCallScopeOrDefault()) }

    private fun newLambdaFunction(scope: VarScope, funcName: String?, target: SerializableCallable): BaseFunction =
        withRhinoContext {
            LambdaFunction(scope, funcName ?: "anonymous", 0, target, false)
        }

    private fun newLambdaConstructor(
        scope: VarScope,
        funcName: String?,
        target: SerializableCallable,
        targetConstructor: SerializableConstructable,
    ): BaseFunction = withRhinoContext {
        LambdaConstructor(scope, funcName ?: "anonymous", 0, target, targetConstructor)
    }

    private fun newLambdaConstructor(
        scope: VarScope,
        funcName: String?,
        targetConstructor: SerializableConstructable,
    ): BaseFunction = withRhinoContext {
        LambdaConstructor(scope, funcName ?: "anonymous", 0, LambdaConstructor.CONSTRUCTOR_NEW, targetConstructor)
    }

    private fun callable(callFunc: ((args: Array<out Any?>) -> Any?)?): SerializableCallable {
        if (callFunc == null) return undefinedCallable()
        return SerializableCallable { _, _, _, args -> callFunc.invoke(args) }
    }

    private fun callableFromFlag(callFunc: Int, funcName: String?): SerializableCallable = when (callFunc) {
        NOT_CALLABLE -> SerializableCallable { _, _, _, _ ->
            throw RuntimeException("Function ${funcName ?: "anonymous"} is unable to be invoked as a function")
        }
        DEFAULT_CALLER -> undefinedCallable()
        else -> throw IllegalArgumentException("Unknown flag $callFunc for argument callFunc of RhinoUtils.newBaseFunction")
    }

    private fun undefinedCallable(): SerializableCallable =
        SerializableCallable { _, _, _, _ -> UNDEFINED }

    private fun defaultConstructorTarget(target: SerializableCallable): SerializableConstructable =
        SerializableConstructable { cx, nt, scope, args ->
            when (val result = target.call(cx, scope, Undefined.SCRIPTABLE_UNDEFINED, args)) {
                is Scriptable -> result
                else -> NativeObject()
            }
        }

    private fun constructorTarget(
        funcName: String?,
        constructFunc: ((args: Array<out Any?>) -> Any?)?,
        fallbackTarget: SerializableCallable,
    ): SerializableConstructable = SerializableConstructable { cx, nt, scope, args ->
        val result = if (constructFunc == null) DEFAULT_CONSTRUCTOR else constructFunc.invoke(args)
        when (result) {
            DEFAULT_CONSTRUCTOR -> defaultConstructorTarget(fallbackTarget).construct(cx, nt, scope, args)
            is Int -> throw IllegalArgumentException("Unknown flag $result for constructor function ${funcName ?: "anonymous"}")
            is Scriptable -> result
            else -> throw IllegalArgumentException(
                "Result of constructor for ${funcName ?: "anonymous"} must be a Rhino Scriptable instead of ${result.jsBrief()}",
            )
        }
    }

    private fun validateCallFlag(callFunc: Int) {
        require(callFunc == NOT_CALLABLE || callFunc == DEFAULT_CALLER) {
            "Unknown flag $callFunc for argument callFunc of RhinoUtils.newBaseFunction"
        }
    }

    private fun validateConstructFlag(constructFunc: Int) {
        require(constructFunc == NOT_CONSTRUCTABLE || constructFunc == DEFAULT_CONSTRUCTOR) {
            "Unknown flag $constructFunc for argument constructFunc of RhinoUtils.newBaseFunction"
        }
    }

    @JvmStatic
    fun newObject(scope: VarScope) = newNativeObject(scope)

    @JvmStatic
    fun newObject(scope: Scriptable): NativeObject {
        return newObject(getDeclarationScope(scope))
    }

    @JvmStatic
    fun getDeclarationScope(scope: Scriptable): VarScope {
        var candidate: Scriptable? = scope
        while (candidate != null) {
            if (candidate is VarScope) {
                return candidate
            }
            candidate.parentScope?.let { return it }
            candidate = candidate.prototype
        }
        return standardObjects
    }

    /**
     * Creates a scope equivalent to `with (obj) { ... }`:
     * names resolve on [obj] (including its prototype chain) before falling back to [parentScope].
     *
     * Since Rhino separated [VarScope] from [Scriptable], a plain object can no longer serve
     * as an evaluation scope by itself, and [getDeclarationScope] only yields its parent scope,
     * which drops the object itself.
     *
     * zh-CN: 创建等价于 `with (obj) { ... }` 的作用域: 名称先在 [obj] (含原型链) 上解析, 再回退到 [parentScope].
     *
     * Rhino 将 [VarScope] 与 [Scriptable] 分离后, 普通对象无法再直接作为求值作用域,
     * 而 [getDeclarationScope] 只会得到其父作用域, 对象本身会被丢弃.
     */
    @JvmStatic
    @JvmOverloads
    fun newWithScope(obj: Scriptable, parentScope: VarScope = getDeclarationScope(obj)): VarScope = WithScope(parentScope, obj)

    @JvmStatic
    fun getDeclarationScopeOrDefault(vararg scopes: Scriptable?): VarScope {
        scopes.forEach { scope ->
            if (scope != null) return getDeclarationScope(scope)
        }
        return standardObjects
    }

    @JvmStatic
    fun Context.getDeclarationScopeOrDefault(vararg scopes: Scriptable?): VarScope {
        scopes.forEach { scope ->
            if (scope != null) return getDeclarationScope(scope)
        }
        return getOrCreateStandardObjects()
    }

    @JvmStatic
    fun defineFunctionProperties(
        target: ScriptableObject,
        names: Array<String>,
        clazz: Class<*>,
        attributes: Int,
    ) {
        defineFunctionProperties(getDeclarationScope(target), target, names, clazz, attributes)
    }

    @JvmStatic
    fun defineFunctionProperties(
        scope: VarScope,
        target: ScriptableObject,
        names: Array<String>,
        clazz: Class<*>,
        attributes: Int,
    ) {
        target.defineFunctionProperties(scope, names, clazz, attributes)
    }

    @JvmStatic
    @Suppress("DEPRECATION")
    fun newNativeJavaMethod(scope: VarScope, method: Method, name: String): NativeJavaMethod =
        NativeJavaMethod(scope, method, name)

    /**
     * Invokes [method] for a script call and throws what the method throws as Rhino's own Java members do
     * ([Context.throwAsScriptRuntimeEx]): a Rhino exception as is, any other exception wrapped so that script `catch`
     * blocks catch it (`JavaException`, with `javaException`). An [InvocationTargetException] reaching the interpreter
     * makes it fail with a ClassCastException, past the script's try / catch (api-coverage Q116).
     *
     * zh-CN: 为脚本调用反射执行 [method], 其抛出的异常按 Rhino 自身 Java 成员的方式抛出 ([Context.throwAsScriptRuntimeEx]):
     * Rhino 异常原样抛出, 其他异常经包装后可被脚本的 `catch` 捕获 (`JavaException`, 带 `javaException`).
     * [InvocationTargetException] 直接到达解释器时, 解释器会以 ClassCastException 失败, 越过脚本的 try / catch (api-coverage Q116).
     */
    @JvmStatic
    fun invokeForScript(method: Method, receiver: Any?, vararg args: Any?): Any? = try {
        method.invoke(receiver, *args)
    } catch (e: InvocationTargetException) {
        throw Context.throwAsScriptRuntimeEx(e)
    }

    @JvmStatic
    fun wrap(o: Any?): Any = withRhinoContext { cx ->
        val scope = cx.getOrCreateStandardObjects()
        cx.wrapFactory.wrap(cx, scope, o, o?.let { it::class.java })
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
    fun toJsValue(scope: VarScope, value: Any?): Any? = withRhinoContext { cx ->
        toJsValue(cx, scope, value, IdentityHashMap())
    }

    private fun toJsValue(
        cx: Context,
        scope: VarScope,
        value: Any?,
        seen: IdentityHashMap<Any, Scriptable>,
    ): Any? = when (value) {
        null -> null
        is Scriptable, is String, is Number, is Boolean, is BigInteger, is Symbol, is Undefined -> value
        is ConsString -> Context.toString(value)
        is Unit -> UNDEFINED
        NOT_FOUND -> UNDEFINED
        is Array<*> -> seen[value] ?: (cx.newArray(scope, value.size) as NativeArray).also { result ->
            seen[value] = result
            value.forEachIndexed { index, item ->
                result.put(index, result, toJsValue(cx, scope, item, seen))
            }
        }
        is List<*> -> seen[value] ?: (cx.newArray(scope, value.size) as NativeArray).also { result ->
            seen[value] = result
            value.forEachIndexed { index, item ->
                result.put(index, result, toJsValue(cx, scope, item, seen))
            }
        }
        is Map<*, *> -> when {
            value.keys.any { it !is String } -> Context.javaToJS(value, scope)
            else -> seen[value] ?: newObject(scope).also { result ->
                seen[value] = result
                value.forEach { (key, item) ->
                    result.put(key as String, result, toJsValue(cx, scope, item, seen))
                }
            }
        }
        else -> Context.javaToJS(value, scope)
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
        is Collection<*> -> input.flatMap { flatten(it) }
        is Array<*> -> input.flatMap { flatten(it) }
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
    fun coerceCallable(o: Any?, def: Callable? = null): Callable = when {
        !o.isJsNullish() -> {
            require(o is Callable) { "Failed to coerce ${o.jsBrief()} into a JavaScript function" }
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
            is Callable -> Runnable {
                callFunction(scriptRuntime, o, scriptRuntime.topLevelScope, Undefined.SCRIPTABLE_UNDEFINED, emptyArray())
            }
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
    fun coerceIntNumber(o: Any?, def: Number? = null): Int {
        numberInput(o, def).let { if (it is Int) return it }
        return coerceIntegralNumber(o, def, Int.MIN_VALUE.toDouble(), Int.MAX_VALUE.toDouble(), "Int").toInt()
    }

    @JvmStatic
    @JvmOverloads
    fun coerceLongNumber(o: Any?, def: Number? = null): Long {
        numberInput(o, def).let { if (it is Long) return it }
        return coerceIntegralNumber(o, def, MIN_LONG_VALUE_AS_DOUBLE, MAX_LONG_VALUE_AS_DOUBLE, "Long").toLong()
    }

    private fun numberInput(o: Any?, def: Number?): Any? = when {
        !o.isJsNullish() -> o
        !def.isJsNullish() -> def
        else -> null
    }

    private fun coerceIntegralNumber(o: Any?, def: Number?, min: Double, max: Double, target: String): Double {
        val number = coerceNumber(o, def)
        require(!number.isNaN() && !number.isInfinite()) {
            "Failed to coerce ${numberInput(o, def).jsBrief()} into a finite $target number"
        }
        val truncated = when {
            number > 0.0 -> floor(number)
            else -> kotlin.math.ceil(number)
        }
        require(truncated in min..max) {
            "Failed to coerce ${numberInput(o, def).jsBrief()} into a $target number without overflow"
        }
        return truncated
    }

    private val MAX_LONG_VALUE_AS_DOUBLE = Double.fromBits(0x43dfffffffffffffL)
    private val MIN_LONG_VALUE_AS_DOUBLE = Double.fromBits(-4332462841530417152L)

    @JvmStatic
    @JvmOverloads
    fun coerceFloatNumber(o: Any?, def: Number? = null) = coerceNumber(o, def).toFloat()

    @JvmStatic
    @JvmOverloads
    fun coerceDoubleNumber(o: Any?, def: Number? = null) = coerceNumber(o, def)

    @JvmStatic
    @JvmOverloads
    fun callPrototypeFunction(builtins: TopLevel.Builtins, funcName: String, thisObj: Scriptable, args: Array<Any?> = arrayOf()): Any? = withRhinoContext { cx ->
        callPrototypeFunction(builtins, funcName, thisObj, cx.getDeclarationScopeOrDefault(thisObj), args)
    }

    @JvmStatic
    @JvmOverloads
    fun callPrototypeFunction(className: String, funcName: String, thisObj: Scriptable, args: Array<Any?> = arrayOf()): Any? = withRhinoContext { cx ->
        callPrototypeFunction(className, funcName, thisObj, cx.getDeclarationScopeOrDefault(thisObj), args)
    }

    @JvmStatic
    @JvmOverloads
    fun callPrototypeFunction(builtins: TopLevel.Builtins, funcName: String, thisObj: Scriptable, scope: VarScope, args: Array<Any?> = arrayOf()): Any? = withRhinoContext { cx ->
        val prototypeFunction = getPrototypeCallable(scope, builtins, funcName)
        prototypeFunction.call(cx, scope, thisObj, args)
    }

    @JvmStatic
    @JvmOverloads
    fun callPrototypeFunction(className: String, funcName: String, thisObj: Scriptable, scope: VarScope, args: Array<Any?> = arrayOf()): Any? = withRhinoContext { cx ->
        val prototypeFunction = getPrototypeCallable(scope, className, funcName)
        prototypeFunction.call(cx, scope, thisObj, args)
    }

    @JvmStatic
    fun getPrototypeCallable(scope: VarScope, builtins: TopLevel.Builtins, funcName: String) = TopLevel.getBuiltinPrototype(
        ScriptableObject.getTopLevelScope(scope), builtins
    ).prop(funcName) as Callable

    @JvmStatic
    fun getPrototypeCallable(scope: VarScope, className: String, funcName: String) = ScriptableObject.getClassPrototype(
        scope, className
    ).prop(funcName) as Callable

    @JvmStatic
    fun getPrototypeFunction(scope: VarScope, builtins: TopLevel.Builtins, funcName: String) = TopLevel.getBuiltinPrototype(
        ScriptableObject.getTopLevelScope(scope), builtins
    ).prop(funcName) as BaseFunction

    @JvmStatic
    fun getPrototypeFunction(scope: VarScope, className: String, funcName: String) = ScriptableObject.getClassPrototype(
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
        val topLevelScope = cx.getDeclarationScopeOrDefault(tar, src)
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
        val topLevel = getDeclarationScope(arg)
        val obj = toObject(cx, topLevel, arg)
        val ids = obj.ids
        ids.indices.forEach { i -> ids[i] = toString(ids[i]) }
        cx.newArray(topLevel, ids) as NativeArray
    }

    @JvmStatic
    fun js_object_values(arg: ScriptableObject): NativeArray = withRhinoContext { cx ->
        val topLevel = getDeclarationScope(arg)
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
        val topLevel = cx.getDeclarationScopeOrDefault(o, properties)
        newNativeObject(topLevel).also {
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
        val topLevel = cx.getDeclarationScopeOrDefault(o)
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
            o.prototype === protoObj -> o
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
        val topLevel = getDeclarationScope(o)
        val obj = ensureScriptableObject(toObject(cx, topLevel, o))
        val map = obj.startCompoundOp(false)
        val ids = try {
            obj.getIds(map, true, false)
        } finally {
            map.close()
        }
        ids.indices.forEach { i -> ids[i] = toString(ids[i]) }
        cx.newArray(topLevel, ids) as NativeArray
    }

    @JvmStatic
    fun js_object_getOwnPropertyDescriptor(value: ScriptableObject, key: Any): Any = withRhinoContext { cx ->
        val scope = getDeclarationScope(value)
        val obj = ensureScriptableObject(toObject(cx, scope, value))
        obj.getOwnPropertyDescriptor(cx, key)?.toScriptableObject(scope) ?: UNDEFINED
    }

    @JvmStatic
    fun js_function_bind(scope: Scriptable? = null, targetFunction: Callable, vararg args: Any?): BoundFunction = withRhinoContext { cx ->
        val topLevel = cx.getDeclarationScopeOrDefault(scope)
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
        when {
            RhinoScriptRuntime.hasTopCall(cx) -> BoundFunction(cx, topLevel, targetFunction, boundThis, boundArgs)
            else -> {
                val global = ScriptableObject.getTopLevelScope(topLevel)
                RhinoScriptRuntime.doTopCall(
                    Callable { _, _, _, _ -> BoundFunction(cx, topLevel, targetFunction, boundThis, boundArgs) },
                    cx,
                    global,
                    global.globalThis,
                    emptyArgs,
                    false,
                ) as BoundFunction
            }
        }
    }

    @JvmStatic
    fun js_json_parse(text: String): Any? = withRhinoContext { cx ->
        val topLevel = cx.getOrCreateStandardObjects()
        JsonParser(cx, topLevel).parseValue(text)
    }

    @JvmStatic
    @JvmOverloads
    fun js_json_stringify(value: Any?, replacer: Any? = null, space: Any? = null): Any = withRhinoContext { cx ->
        val topLevel = cx.getOrCreateStandardObjects()
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
        val global = getTopLevelScope(scope)
        RhinoScriptRuntime.evalSpecial(cx, global, global.globalThis, arrayOf(s), "eval code", 1)
    }

    @JvmStatic
    fun js_eval(scope: VarScope, s: String): Any? = withRhinoContext { cx ->
        val global = ScriptableObject.getTopLevelScope(scope)
        RhinoScriptRuntime.evalSpecial(cx, global, global.globalThis, arrayOf(s), "eval code", 1)
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

    private fun isEnumerable(key: String, obj: Any): Boolean {
        return obj !is ScriptableObject || obj.getAttributes(key) and DONTENUM == 0
    }

    private fun isEnumerable(index: Int, obj: Any): Boolean {
        return obj !is ScriptableObject || obj.getAttributes(index) and DONTENUM == 0
    }

    internal fun initNewBaseFunction(baseFunction: BaseFunction) = withFunctionCreationScope { scope ->
        initNewBaseFunction(scope, baseFunction)
    }

    internal fun initNewBaseFunction(scope: VarScope, baseFunction: BaseFunction) = withRhinoContext { cx ->
        // @Caution by SuperMonster003 on Nov 9, 2024.
        //  ! This will cause severely memory leak.
        //  ! zh-CN: 这将导致严重的内存泄漏.
        //  # baseFunction.exportAsJSClass(BaseFunction.MAX_PROTOTYPE_ID, ImporterTopLevel(Context.getCurrentContext()), false)

        RhinoScriptRuntime.setFunctionProtoAndParent(baseFunction, cx, scope)

        val prototypeObject = baseFunction["prototype"]
        when {
            prototypeObject.isJsNullish() -> {
                baseFunction.defineProp("prototype", newObject(scope).also { o ->
                    o.defineProp("constructor", baseFunction, DONTENUM)
                }, DONTENUM)
            }
            prototypeObject is Scriptable -> {
                prototypeObject.defineProp("constructor", baseFunction, DONTENUM)
            }
        }
    }

    @JvmStatic
    fun initNativeObjectPrototype(o: NativeObject): NativeObject = withRhinoContext { cx ->
        initNativeObjectPrototype(cx.getCurrentTopCallScopeOrFallback(), o)
    }

    @JvmStatic
    fun initNativeObjectPrototype(scope: VarScope, o: NativeObject): NativeObject =
        o.apply { setBuiltinProtoAndParent(this, scope, TopLevel.Builtins.Object) }

    @JvmStatic
    fun initNativeArrayPrototype(a: NativeArray): NativeArray = withRhinoContext { cx ->
        initNativeArrayPrototype(cx.getCurrentTopCallScopeOrFallback(), a)
    }

    @JvmStatic
    fun initNativeArrayPrototype(scope: VarScope, a: NativeArray): NativeArray =
        a.apply { setBuiltinProtoAndParent(this, scope, TopLevel.Builtins.Array) }

    @JvmStatic
    fun newNativeObject() = initNativeObjectPrototype(NativeObject())

    @JvmStatic
    fun newNativeObject(scope: VarScope) = initNativeObjectPrototype(scope, NativeObject())

    @JvmStatic
    @JvmOverloads
    fun newNativeArray(lengthArg: Long = 0) = initNativeArrayPrototype(NativeArray(lengthArg))

    @JvmStatic
    @JvmOverloads
    fun newNativeArray(scope: VarScope, lengthArg: Long = 0) =
        initNativeArrayPrototype(scope, NativeArray(lengthArg))

    @JvmStatic
    fun newNativeArray(array: Array<Any?>) = initNativeArrayPrototype(NativeArray(array))

    @JvmStatic
    fun newNativeArray(scope: VarScope, array: Array<Any?>) =
        initNativeArrayPrototype(scope, NativeArray(array))

    @JvmStatic
    fun hashCodeOfScriptable(other: Any?): Int? {
        if (other !is Scriptable) return null
        val getClassFunc = other["getClass"]
        if (getClassFunc is Callable) {
            val clazz = callFunction(getClassFunc, other, other, emptyArray()).jsUnwrapped()
            if (clazz != UiObjectCollection::class.java) return null
        }
        val hashCodeFunc = other["hashCode"]
        if (hashCodeFunc is Callable) {
            val hashCode = callFunction(hashCodeFunc, other, other, emptyArray()).jsUnwrapped()
            if (hashCode is Number) return hashCode.toInt()
        }
        return null
    }

    fun handleAsyncOperation(scriptRuntime: ScriptRuntime, func: () -> Any?): Scriptable {
        return handleAsyncOperation(scriptRuntime, func) { it }
    }

    fun <T> handleAsyncOperation(
        scriptRuntime: ScriptRuntime,
        operation: () -> T,
        uiMapper: (T) -> Any?,
    ): Scriptable {
        val promiseAdapter = ScriptPromiseAdapter()
        val uiHandler = Handler(Looper.getMainLooper())

        // The operation keeps the script running, and a Promise of its own would not (Rhino-For-AutoJs6 appendix C Q7). The token
        // goes one message after the settlement, which is when its reactions have reached the thread of an awaiting function.
        // zh-CN: 操作期间脚本保持运行, Promise 本身不保活 (Rhino-For-AutoJs6 附录 C Q7). 令牌在落定之后的下一条消息释放, 此时反应已到达 await 所在线程.
        val waitToken = Looper.myLooper()?.let { scriptRuntime.loopers.waitWhenIdleToken() }
        val settling = { settle: () -> Unit ->
            Runnable {
                try {
                    settle()
                } finally {
                    uiHandler.post { waitToken?.release() }
                }
            }
        }

        // Use Android Thread directly to avoid coupling with JS threads/loopers here.
        // zh-CN: 直接使用 Android Thread, 避免在此处与 JS 线程/Looper 机制强耦合.
        Thread {
            try {
                // Do the blocking work (e.g. network request) in background thread.
                // zh-CN: 在后台线程执行阻塞式操作 (如网络请求).
                val result = operation.invoke()

                // Resolve on UI thread so that UI scripts can safely touch `ui.*` in then().
                // zh-CN: 在 UI 线程 resolve, 让 UI 脚本在 then() 中可安全操作 `ui.*`.
                uiHandler.post(settling {
                    try {
                        // Map result on UI thread (e.g. wrap Java objects into Rhino objects).
                        // zh-CN: 在 UI 线程映射结果 (例如将 Java 对象包装为 Rhino 对象).
                        val mapped = uiMapper.invoke(result)
                        promiseAdapter.resolve(mapped)
                    } catch (t: Throwable) {
                        promiseAdapter.reject(t)
                    }
                })
            } catch (t: Throwable) {
                // Reject on UI thread for consistent callback threading.
                // zh-CN: 在 UI 线程 reject, 保持回调线程一致性.
                uiHandler.post(settling { promiseAdapter.reject(t) })
            }
        }.start()

        // Convert ScriptPromiseAdapter into a JavaScript Promise.
        // zh-CN: 将 ScriptPromiseAdapter 转换为 JavaScript Promise.
        return callFunction(scriptRuntime, scriptRuntime.js_ResultAdapter, "promise", arrayOf(promiseAdapter)) as Scriptable
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

        internal fun callFunc(callFunc: ((args: Array<out Any?>) -> Any?)?, cx: Context?, scope: VarScope?, thisObj: Scriptable?, args: Array<out Any?>): Any? {
            return when (callFunc) {
                null -> super.call(cx, Undefined.instance, scope ?: cx?.getOrCreateStandardObjects() ?: standardObjects, thisObj, args)
                else -> callFunc.invoke(args)
            }
        }

        internal fun constructFunc(constructFunc: ((args: Array<out Any?>) -> Any?)?, cx: Context?, scope: VarScope?, args: Array<out Any?>): Scriptable {
            val niceScope = scope ?: parentScope ?: cx?.getOrCreateStandardObjects() ?: standardObjects
            return when (constructFunc) {
                null -> super.construct(cx, this, niceScope, args)
                else -> when (val result = constructFunc.invoke(args)) {
                    is Int -> constructFlag(result, cx, scope, args)
                    else -> result.also {
                        require(it is Scriptable) { "Result of constructor for ${getFunctionName()} must be a Rhino Scriptable instead of ${it.jsBrief()}" }
                    } as Scriptable
                }
            }.also {
                it.prototype = getClassPrototype()
                it.parentScope = niceScope
            }
        }

        internal fun callFlag(flag: Int, cx: Context?, scope: VarScope?, thisObj: Scriptable?, args: Array<out Any?>): Any? = when (flag) {
            DEFAULT_CALLER -> super.call(cx, Undefined.instance, scope ?: cx?.getOrCreateStandardObjects() ?: standardObjects, thisObj, args)
            NOT_CALLABLE -> throw RuntimeException("Function ${funcName ?: "anonymous"} is unable to be invoked as a function")
            else -> throw IllegalArgumentException("Unknown flag $flag for argument callFunc of RhinoUtils.newBaseFunction")
        }

        internal fun constructFlag(flag: Int, cx: Context?, scope: VarScope?, args: Array<out Any?>): Scriptable = when (flag) {
            DEFAULT_CONSTRUCTOR -> super.construct(cx, this, scope ?: cx?.getOrCreateStandardObjects() ?: standardObjects, args)
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

    }

}
