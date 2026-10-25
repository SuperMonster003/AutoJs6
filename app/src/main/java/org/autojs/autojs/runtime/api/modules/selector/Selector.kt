package org.autojs.autojs.runtime.api.modules.selector

import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.core.accessibility.UiSelector
import org.autojs.autojs.core.automator.UiObject
import org.autojs.autojs.core.automator.filter.BooleanFilter
import org.autojs.autojs.core.automator.selector.SelectorRegistry
import org.autojs.autojs.rhino.JsArgumentCoercion
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.rhino.extension.AnyExtensions.jsUnwrapped
import org.autojs.autojs.rhino.ArgumentGuards
import org.autojs.autojs.rhino.extension.ScriptableExtensions.defineProp
import org.autojs.autojs.rhino.extension.ScriptableExtensions.hasProp
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.autojs.autojs.runtime.exception.ShouldNeverHappenException
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.autojs.runtime.exception.WrappedRuntimeException
import org.autojs.autojs.util.RhinoUtils
import org.autojs.autojs.util.RhinoUtils.NOT_CONSTRUCTABLE
import org.autojs.autojs.util.RhinoUtils.coerceBoolean
import org.autojs.autojs.util.RhinoUtils.coerceString
import org.autojs.autojs.util.RhinoUtils.newBaseFunction
import org.autojs.autojs.util.RhinoUtils.withRhinoContext
import org.mozilla.javascript.Callable
import org.mozilla.javascript.Context
import org.mozilla.javascript.RhinoException
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.VarScope
import java.lang.reflect.Array as JavaArray
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import kotlin.reflect.full.declaredMembers

class Selector(private val scriptRuntime: ScriptRuntime) : ArgumentGuards() {

    private val global by lazy {
        scriptRuntime.topLevelScope
    }

    init {
        // Registry properties (text, clickable, ...) go straight to the registry; the whitelisted
        // query and action methods (find, findOne, click, ...) are resolved reflectively as before.
        // zh-CN: 注册表属性 (text, clickable, ...) 直接进入注册表; 白名单中的查询与动作方法 (find, findOne, click, ...) 仍按反射解析.
        val queryMethods = UiSelector::class.java.methods.groupBy { it.name }
        for (methodName in SelectorGlobals.names) {
            if (!isGlobalAugmentTarget(methodName)) {
                // @Caution by SuperMonster003 as of Oct 23, 2022.
                //  ! The following methods have been assigned by 'automator' module,
                //  ! which not belonging to UiSelector.
                //  ! zh-CN:
                //  ! 以下方法已被 "automator" 模块分配, 因此不再属于 UiSelector.
                //  !
                //  # [ click / longClick / scrollDown / scrollUp / setText ].
                continue
            }
            // @Hint by SuperMonster003 on Jul 25, 2024.
            //  ! For scope binding.
            //  ! zh-CN: 用于绑定作用域.
            withRhinoContext(scriptRuntime) { cx ->
                global.defineProp(methodName, newBaseFunction(global, null, { argList ->
                    val methodKey = coerceString(argList[0])
                    newBaseFunction(global, methodKey, { arguments ->
                        runCatching {
                            // @Caution by SuperMonster003 on Oct 28, 2024.
                            //  ! Do NOT reuse one selector object as a new global selector
                            //  ! will APPEND on the old one (e.g., text('a') -> text('a').desc('b')).
                            //  ! zh-CN:
                            //  ! 不要复用同一个选择器对象, 因为新的全局选择器将附加到旧选择器之上
                            //  ! (例如 text('a') -> text('a').desc('b')).
                            when {
                                SelectorGlobals.isRegistryProperty(methodKey) -> applyRegistryProperty(methodKey, arguments)
                                else -> invokeQueryMethod(queryMethods[methodKey].orEmpty(), methodKey, arguments)
                            }
                        }.getOrElse { e ->
                            if (ScriptInterruptedException.causedByInterrupt(e)) throw e
                            scriptRuntime.console.warn("${e.message}\n${e.stackTraceToString()}")
                            scriptRuntime.console.warn("method: ${methodKey}, arguments: [${arguments.joinToString(",") { coerceString(it) }}]")
                            // A Java exception thrown from a BaseFunction is not a JS error and would bypass
                            // the script's try / catch; a Rhino exception (already carrying the script
                            // position) is rethrown as is.
                            // zh-CN: BaseFunction 抛出的 Java 异常不是 JS 错误, 会绕过脚本的 try / catch; Rhino 异常 (已带脚本
                            // 位置) 原样重抛.
                            throw if (e is RhinoException) e else WrappedRuntimeException(e)
                        }
                    }, NOT_CONSTRUCTABLE)
                }, NOT_CONSTRUCTABLE).call(cx, global, global.globalThis, arrayOf(methodName)))
            }
        }
    }

    private fun applyRegistryProperty(name: String, arguments: Array<out Any?>): UiSelector {
        val selector = scriptRuntime.selector()
        val args = arguments.map { argument ->
            when (val unwrapped = argument.jsUnwrapped()) {
                is Callable -> BooleanFilter.BooleanSupplier { node ->
                    coerceBoolean(RhinoUtils.callFunction(scriptRuntime, unwrapped, null as VarScope?, Undefined.SCRIPTABLE_UNDEFINED, arrayOf(node)))
                }
                else -> unwrapped
            }
        }
        if (SelectorRegistry.apply(selector, name, args) != SelectorRegistry.Outcome.APPLIED) {
            throw WrappedIllegalArgumentException("Invalid arguments (${arguments.size}) for method UiSelector$$name")
        }
        return selector
    }

    private fun invokeQueryMethod(availableMethods: List<Method>, name: String, arguments: Array<out Any?>): Any? {
        require(availableMethods.isNotEmpty()) { "Method $name doesn't exist on global selector" }
        val resolved = SelectorMethodResolver.resolve(availableMethods, arguments)
            ?: throw WrappedIllegalArgumentException("Invalid arguments (${arguments.size}) for method UiSelector$$name")
        return try {
            resolved.method.invoke(scriptRuntime.selector(), *resolved.arguments)
        } catch (e: InvocationTargetException) {
            throw e.targetException
        }
    }

    fun invoke(vararg args: Any?): Scriptable = ensureArgumentsIsEmpty(args) {
        Context.javaToJS(scriptRuntime.selector(), scriptRuntime.topLevelScope) as Scriptable
    }

    private fun isGlobalAugmentTarget(key: String): Boolean {
        return !isInJavaObject(key) && !isInGlobal(key) && key !in SelectorGlobals.excluded
    }

    private fun isInGlobal(key: String): Boolean {
        return global.hasProp(key)
    }

    private fun isInJavaObject(key: String): Boolean {
        return Any::class.declaredMembers.any { it.name == key }
    }

    @Suppress("SameParameterValue")
    companion object : ArgumentGuards() {

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun pickup(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? = ensureArgumentsAtMost(args, 5) {
            when (it.size) {
                5 -> {
                    // @Signature pickup<R>(root: UiObject, selector: Pickup.Selector, compass: Selector.Compass, resultType: Pickup.ResultType, callback: (o: any) => R): R;
                    UiSelector.pickup(
                        scriptRuntime,
                        asPickupRoot(it, 0),
                        asPickupSelector(it, 1),
                        asPickupCompass(it, 2),
                        asPickupResultType(it, 3),
                        asPickupCallback(it, 4),
                    )
                }
                4 -> {
                    if (it[0] is UiObject) {
                        if (it[3] is Callable) {
                            if (UiObject.isCompass(it[2])) {
                                // @Overload pickup<R>(root: UiObject, selector: Pickup.Selector, compass: Selector.Compass, callback: (o: any) => R): R;
                                return@ensureArgumentsAtMost UiSelector.pickup(
                                    scriptRuntime,
                                    asPickupRoot(it, 0),
                                    asPickupSelector(it, 1),
                                    asPickupCompass(it, 2),
                                    resultType = UiObject.RESULT_TYPE_WIDGET,
                                    asPickupCallback(it, 3),
                                )
                            }
                            // @Overload pickup<R>(root: UiObject, selector: Pickup.Selector, resultType: Pickup.ResultType, callback: (o: any) => R): R;
                            return@ensureArgumentsAtMost UiSelector.pickup(
                                scriptRuntime,
                                asPickupRoot(it, 0),
                                asPickupSelector(it, 1),
                                compass = UiObject.COMPASS_PASS_ON,
                                asPickupResultType(it, 2),
                                asPickupCallback(it, 3),
                            )
                        }
                        // @Overload pickup(root: UiObject, selector: Pickup.Selector, compass: Selector.Compass, resultType: Pickup.ResultType): any;
                        return@ensureArgumentsAtMost UiSelector.pickup(
                            scriptRuntime,
                            asPickupRoot(it, 0),
                            asPickupSelector(it, 1),
                            asPickupCompass(it, 2),
                            asPickupResultType(it, 3),
                            callback = null,
                        )
                    }
                    // @Overload pickup<R>(selector: Pickup.Selector, compass: Selector.Compass, resultType: Pickup.ResultType, callback: (o: any) => R): R;
                    return@ensureArgumentsAtMost UiSelector.pickup(
                        scriptRuntime,
                        root = null,
                        asPickupSelector(it, 0),
                        asPickupCompass(it, 1),
                        asPickupResultType(it, 2),
                        asPickupCallback(it, 3),
                    )
                }
                3 -> {
                    if (it[0] is UiObject) {
                        if (it[2] is Callable) {
                            // @Overload pickup<R>(root: UiObject, selector: Pickup.Selector, callback: (o: any) => R): R;
                            return@ensureArgumentsAtMost UiSelector.pickup(
                                scriptRuntime,
                                asPickupRoot(it, 0),
                                asPickupSelector(it, 1),
                                compass = UiObject.COMPASS_PASS_ON,
                                resultType = UiObject.RESULT_TYPE_WIDGET,
                                asPickupCallback(it, 2),
                            )
                        }
                        if (UiObject.isCompass(it[2])) {
                            // @Overload pickup(root: UiObject, selector: Pickup.Selector, compass: Selector.Compass): any;
                            return@ensureArgumentsAtMost UiSelector.pickup(
                                scriptRuntime,
                                asPickupRoot(it, 0),
                                asPickupSelector(it, 1),
                                asPickupCompass(it, 2),
                                resultType = UiObject.RESULT_TYPE_WIDGET,
                                callback = null,
                            )
                        }
                        // @Overload pickup(root: UiObject, selector: Pickup.Selector, resultType: Pickup.ResultType): any;
                        return@ensureArgumentsAtMost UiSelector.pickup(
                            scriptRuntime,
                            asPickupRoot(it, 0),
                            asPickupSelector(it, 1),
                            UiObject.COMPASS_PASS_ON,
                            asPickupResultType(it, 2),
                            callback = null,
                        )
                    }
                    if (it[2] is Callable) {
                        if (UiObject.isCompass(it[1])) {
                            // @Overload pickup<R>(selector: Pickup.Selector, compass: Selector.Compass, callback: (o: any) => R): R;
                            return@ensureArgumentsAtMost UiSelector.pickup(
                                scriptRuntime,
                                root = null,
                                asPickupSelector(it, 0),
                                asPickupCompass(it, 1),
                                UiObject.RESULT_TYPE_WIDGET,
                                asPickupCallback(it, 2),
                            )
                        }
                        // @Overload pickup<R>(selector: Pickup.Selector,
                        //  resultType: Pickup.ResultType, callback: (o: any) => R): R;
                        return@ensureArgumentsAtMost UiSelector.pickup(
                            scriptRuntime,
                            root = null,
                            asPickupSelector(it, 0),
                            UiObject.COMPASS_PASS_ON,
                            asPickupResultType(it, 1),
                            asPickupCallback(it, 2),
                        )
                    }
                    // @Overload pickup(selector: Pickup.Selector, compass: Selector.Compass, resultType: Pickup.ResultType): any;
                    return@ensureArgumentsAtMost UiSelector.pickup(
                        scriptRuntime,
                        root = null,
                        asPickupSelector(it, 0),
                        asPickupCompass(it, 1),
                        asPickupResultType(it, 2),
                        callback = null,
                    )
                }
                2 -> {
                    if (it[0] is UiObject) {
                        // @Overload pickup(root: UiObject, selector: Pickup.Selector): any;
                        return@ensureArgumentsAtMost UiSelector.pickup(
                            scriptRuntime,
                            asPickupRoot(it, 0),
                            asPickupSelector(it, 1),
                            UiObject.COMPASS_PASS_ON,
                            UiObject.RESULT_TYPE_WIDGET,
                            callback = null,
                        )
                    }
                    if (it[1] is Callable) {
                        // @Overload pickup<R>(selector: Pickup.Selector, callback: (o: any) => R): R;
                        return@ensureArgumentsAtMost UiSelector.pickup(
                            scriptRuntime,
                            root = null,
                            asPickupSelector(it, 0),
                            UiObject.COMPASS_PASS_ON,
                            UiObject.RESULT_TYPE_WIDGET,
                            asPickupCallback(it, 1),
                        )
                    }
                    if (UiObject.isCompass(it[1])) {
                        // @Overload pickup(selector: Pickup.Selector, compass: Selector.Compass): any;
                        return@ensureArgumentsAtMost UiSelector.pickup(
                            scriptRuntime,
                            root = null,
                            asPickupSelector(it, 0),
                            asPickupCompass(it, 1),
                            UiObject.RESULT_TYPE_WIDGET,
                            callback = null,
                        )
                    }
                    // @Overload pickup(selector: Pickup.Selector, resultType: Pickup.ResultType): any;
                    return@ensureArgumentsAtMost UiSelector.pickup(
                        scriptRuntime,
                        root = null,
                        asPickupSelector(it, 0),
                        UiObject.COMPASS_PASS_ON,
                        asPickupResultType(it, 1),
                        callback = null,
                    )
                }
                1 -> {
                    // @Overload pickup(selector: Pickup.Selector): any;
                    return@ensureArgumentsAtMost UiSelector.pickup(
                        scriptRuntime,
                        root = null,
                        asPickupSelector(it, 0),
                        compass = UiObject.COMPASS_PASS_ON,
                        resultType = UiObject.RESULT_TYPE_WIDGET,
                        callback = null,
                    )
                }
                0 -> {
                    // @Signature pickup(): UiObject;
                    return@ensureArgumentsAtMost UiSelector.pickup(
                        scriptRuntime,
                        root = null,
                        selector = null,
                        compass = null,
                        resultType = UiObject.RESULT_TYPE_WIDGET,
                        callback = null,
                    )
                }
                else -> throw ShouldNeverHappenException()
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun detect(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Any? = ensureArgumentsAtMost(args, 4) {
            when (it.size) {
                4 -> {
                    // @Signature detect<R>(w: UiObject, compass: Detect.Compass, resultType: Detect.ResultType, callback: ((o: any) => R)): R;
                    return@ensureArgumentsAtMost UiObject.detect(
                        scriptRuntime,
                        asDetectWidget(it, 0),
                        asDetectCompass(it, 1),
                        asDetectResultType(it, 2),
                        asDetectCallback(it, 3),
                    )
                }
                3 -> {
                    if (it[2] is Callable) {
                        if (UiObject.isCompass(it[1])) {
                            // @Overload detect<R>(w: UiObject, compass: Detect.Compass, callback: ((w: UiObject) => R)): R;
                            return@ensureArgumentsAtMost UiObject.detect(
                                scriptRuntime,
                                asDetectWidget(it, 0),
                                asDetectCompass(it, 1),
                                UiObject.RESULT_TYPE_WIDGET,
                                asDetectCallback(it, 2),
                            )
                        }
                        // @Overload detect<R>(w: UiObject, resultType: Detect.ResultType, callback: ((o: any) => R)): R;
                        return@ensureArgumentsAtMost UiObject.detect(
                            scriptRuntime,
                            asDetectWidget(it, 0),
                            UiObject.COMPASS_PASS_ON,
                            asDetectResultType(it, 1),
                            asDetectCallback(it, 2),
                        )
                    }
                    // @Overload detect(w: UiObject, compass: Detect.Compass, resultType: Detect.ResultType): any;
                    return@ensureArgumentsAtMost UiObject.detect(
                        scriptRuntime,
                        asDetectWidget(it, 0),
                        asDetectCompass(it, 1),
                        asDetectResultType(it, 2),
                        callback = null,
                    )
                }
                2 -> {
                    if (it[1] is Callable) {
                        // @Overload detect<T extends UiObject, R>(w: T, callback: ((w: T) => R)): R;
                        return@ensureArgumentsAtMost UiObject.detect(
                            scriptRuntime,
                            asDetectWidget(it, 0),
                            UiObject.COMPASS_PASS_ON,
                            UiObject.RESULT_TYPE_WIDGET,
                            asDetectCallback(it, 1),
                        )
                    }
                    if (UiObject.isCompass(it[1])) {
                        // @Overload detect(w: UiObject, compass: Detect.Compass): any;
                        return@ensureArgumentsAtMost UiObject.detect(
                            scriptRuntime,
                            asDetectWidget(it, 0),
                            asDetectCompass(it, 1),
                            UiObject.RESULT_TYPE_WIDGET,
                            callback = null,
                        )
                    }
                    // @Overload detect(w: UiObject, resultType: Detect.ResultType): any;
                    return@ensureArgumentsAtMost UiObject.detect(
                        scriptRuntime,
                        asDetectWidget(it, 0),
                        UiObject.COMPASS_PASS_ON,
                        asDetectResultType(it, 1),
                        callback = null,
                    )
                }
                else -> throw WrappedIllegalArgumentException(
                    "Arguments length (${it.size}) is unacceptable for detect",
                )
            }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun existsAll(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = unwrapArguments(args) {
            it.all { sel -> coerceBoolean(pickup(scriptRuntime, arrayOf(sel, "?"))) }
        }

        @JvmStatic
        @RhinoRuntimeFunctionInterface
        fun existsOne(scriptRuntime: ScriptRuntime, args: Array<out Any?>): Boolean = unwrapArguments(args) {
            it.any { sel -> coerceBoolean(pickup(scriptRuntime, arrayOf(sel, "?"))) }
        }

        private fun asPickupRoot(args: Array<Any?>, idx: Int): UiObject? {
            val root = args[idx]
            if (root.isJsNullish()) return null
            require(root is UiObject?) { "Argument[$idx] for pickup cannot be taken as a root" }
            return root
        }

        private fun asPickupSelector(args: Array<Any?>, idx: Int): Any? = args[idx]

        private fun asPickupCompass(args: Array<Any?>, idx: Int): CharSequence? {
            val compass = args[idx]
            if (compass.isJsNullish()) return null
            require(compass is CharSequence?) { "Argument[$idx] for pickup cannot be taken as a compass" }
            return compass
        }

        private fun asPickupResultType(args: Array<Any?>, idx: Int): Any? = args[idx]

        private fun asPickupCallback(args: Array<Any?>, idx: Int): Callable? {
            val callback = args[idx]
            if (callback.isJsNullish()) return null
            require(callback is Callable) { "Argument[$idx] for pickup cannot be taken as a callback" }
            return callback
        }

        private fun asDetectWidget(args: Array<Any?>, idx: Int): UiObject? {
            val widget = args[idx]
            if (widget.isJsNullish()) return null
            require(widget is UiObject?) { "Argument[$idx] for detect cannot be taken as a widget" }
            return widget
        }

        private fun asDetectCompass(args: Array<Any?>, idx: Int): CharSequence? {
            val compass = args[idx]
            if (compass.isJsNullish()) return null
            require(compass is CharSequence?) { "Argument[$idx] for detect cannot be taken as a compass" }
            return compass
        }

        private fun asDetectResultType(args: Array<Any?>, idx: Int): Any? = args[idx]

        private fun asDetectCallback(args: Array<Any?>, idx: Int): Callable? {
            val callback = args[idx]
            if (callback.isJsNullish()) return null
            require(callback is Callable) { "Argument[$idx] for detect cannot be taken as a callback" }
            return callback
        }

    }

}

internal object SelectorMethodResolver {

    data class Resolution(
        val method: Method,
        val arguments: Array<Any?>,
    )

    fun resolve(methods: List<Method>, args: Array<out Any?>): Resolution? {
        val unwrappedArgs = Array<Any?>(args.size) { index -> args[index].jsUnwrapped() }
        return methods.asSequence()
            .sortedBy { method -> if (method.isVarArgs) 1 else 0 }
            .mapNotNull { method ->
                convertArguments(method, unwrappedArgs)?.let { converted -> Resolution(method, converted) }
            }
            .firstOrNull()
    }

    private fun convertArguments(method: Method, args: Array<Any?>): Array<Any?>? {
        val parameterTypes = method.parameterTypes
        if (!method.isVarArgs) {
            if (parameterTypes.size != args.size) return null
            return Array(parameterTypes.size) { index ->
                val argument = convertArgument(parameterTypes[index], args[index]) ?: return null
                argument.value
            }
        }

        val fixedParameterCount = parameterTypes.size - 1
        if (args.size < fixedParameterCount) return null
        val converted = arrayOfNulls<Any?>(parameterTypes.size)
        repeat(fixedParameterCount) { index ->
            val argument = convertArgument(parameterTypes[index], args[index]) ?: return null
            converted[index] = argument.value
        }

        val componentType = requireNotNull(parameterTypes.last().componentType)
        val varargCount = args.size - fixedParameterCount
        val varargs = JavaArray.newInstance(componentType, varargCount)
        repeat(varargCount) { index ->
            val argument = convertArgument(componentType, args[fixedParameterCount + index]) ?: return null
            JavaArray.set(varargs, index, argument.value)
        }
        converted[parameterTypes.lastIndex] = varargs
        return converted
    }

    private fun convertArgument(parameterType: Class<*>, arg: Any?): ConvertedArgument? {
        val converted = JsArgumentCoercion.coerce(parameterType, arg)
        if (converted == null) {
            return if (parameterType.isPrimitive) null else ConvertedArgument(null)
        }
        return converted.takeIf(boxedType(parameterType)::isInstance)?.let(::ConvertedArgument)
    }

    private fun boxedType(type: Class<*>): Class<*> = when (type) {
        Boolean::class.java -> Boolean::class.javaObjectType
        Byte::class.java -> Byte::class.javaObjectType
        Char::class.java -> Char::class.javaObjectType
        Double::class.java -> Double::class.javaObjectType
        Float::class.java -> Float::class.javaObjectType
        Int::class.java -> Int::class.javaObjectType
        Long::class.java -> Long::class.javaObjectType
        Short::class.java -> Short::class.javaObjectType
        else -> type
    }

    private data class ConvertedArgument(val value: Any?)

}
