package org.autojs.autojs.runtime.api.augment.selector

import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.core.accessibility.UiSelector
import org.autojs.autojs.core.automator.UiObject
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.rhino.extension.AnyExtensions.jsUnwrapped
import org.autojs.autojs.rhino.ArgumentGuards
import org.autojs.autojs.rhino.extension.ScriptableExtensions.defineProp
import org.autojs.autojs.rhino.extension.ScriptableExtensions.hasProp
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.api.augment.Invokable
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.autojs.autojs.runtime.exception.ShouldNeverHappenException
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.autojs.util.RhinoUtils.NOT_CONSTRUCTABLE
import org.autojs.autojs.util.RhinoUtils.coerceBoolean
import org.autojs.autojs.util.RhinoUtils.coerceString
import org.autojs.autojs.util.RhinoUtils.newBaseFunction
import org.autojs.autojs.util.RhinoUtils.withRhinoContext
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Context
import org.mozilla.javascript.Scriptable
import java.lang.reflect.Array as JavaArray
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.reflect.full.declaredMembers

class Selector(private val scriptRuntime: ScriptRuntime) : Augmentable(scriptRuntime), Invokable {

    private val global by lazy {
        scriptRuntime.topLevelScope
    }

    override val globalAssignmentFunctions = listOf(
        ::pickup.name,
        ::detect.name,
        ::existsAll.name,
        ::existsOne.name,
    )

    init {
        val selectorObj = scriptRuntime.selector()
        for (method in selectorObj.javaClass.methods) {
            val methodName = method.name
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
                global.defineProp(methodName, newBaseFunction(null, { argList ->
                    val methodKey = coerceString(argList[0])
                    newBaseFunction(methodKey, { arguments ->
                        runCatching {
                            val availableMethods = selectorObj.javaClass.methods.filter { method -> method.name == methodName }
                            require(availableMethods.isNotEmpty()) { "Method $methodKey doesn't exist on global selector" }
                            val resolved = SelectorMethodResolver.resolve(availableMethods, arguments)
                                ?: throw WrappedIllegalArgumentException(
                                    "Invalid arguments (${arguments.size}) for method UiSelector$$methodKey",
                                )

                            // @Caution by SuperMonster003 on Oct 28, 2024.
                            //  ! Do NOT use `selectorObj` as a new global selector
                            //  ! will APPEND on the old one (e.g., text('a') -> text('a').desc('b')).
                            //  ! zh-CN:
                            //  ! 不要使用 `selectorObj`, 因为新的全局选择器将附加到旧选择器之上
                            //  ! (例如 text('a') -> text('a').desc('b')).
                            try {
                                resolved.method.invoke(scriptRuntime.selector(), *resolved.arguments)
                            } catch (e: InvocationTargetException) {
                                throw e.targetException
                            }
                        }.getOrElse { e ->
                            if (ScriptInterruptedException.causedByInterrupt(e)) throw e
                            scriptRuntime.console.warn("${e.message}\n${e.stackTraceToString()}")
                            scriptRuntime.console.warn("method: ${methodKey}, arguments: [${arguments.joinToString(",") { coerceString(it) }}]")
                            throw e
                        }
                    }, NOT_CONSTRUCTABLE)
                }, NOT_CONSTRUCTABLE).call(cx, global, global.globalThis, arrayOf(methodName)))
            }
        }
    }

    override fun invoke(vararg args: Any?): Scriptable = ensureArgumentsIsEmpty(args) {
        Context.javaToJS(scriptRuntime.selector(), scriptRuntime.topLevelScope) as Scriptable
    }

    private fun isGlobalAugmentTarget(key: String): Boolean {
        return !isInJavaObject(key) && !isInGlobal(key) && !isInScopeAugmentMethodBlacklist(key)
    }

    private fun isInGlobal(key: String): Boolean {
        return global.hasProp(key)
    }

    private fun isInJavaObject(key: String): Boolean {
        return Object::class.declaredMembers.find { it.name == key } != null
    }

    private fun isInScopeAugmentMethodBlacklist(key: String): Boolean {
        return scopeAugmentMethodBlacklist.contains(key)
    }

    @Suppress("SameParameterValue")
    companion object : ArgumentGuards() {

        private val scopeAugmentMethodBlacklist = listOf(
            UiSelector::plus.name,
            UiSelector::append.name,
        )

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
                        if (it[3] is BaseFunction) {
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
                        if (it[2] is BaseFunction) {
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
                    if (it[2] is BaseFunction) {
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
                    if (it[1] is BaseFunction) {
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
                    if (it[2] is BaseFunction) {
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
                    if (it[1] is BaseFunction) {
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

        private fun asPickupCallback(args: Array<Any?>, idx: Int): BaseFunction? {
            val callback = args[idx]
            if (callback.isJsNullish()) return null
            require(callback is BaseFunction?) { "Argument[$idx] for pickup cannot be taken as a callback" }
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

        private fun asDetectCallback(args: Array<Any?>, idx: Int): BaseFunction? {
            val callback = args[idx]
            if (callback.isJsNullish()) return null
            require(callback is BaseFunction?) { "Argument[$idx] for detect cannot be taken as a callback" }
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
        val converted = when {
            arg !is Double -> arg
            parameterType == Double::class.java || parameterType == Double::class.javaObjectType -> arg
            parameterType == Float::class.java || parameterType == Float::class.javaObjectType -> arg.toFloat()
            parameterType == Long::class.java || parameterType == Long::class.javaObjectType -> arg.roundToLong()
            parameterType == Int::class.java || parameterType == Int::class.javaObjectType -> arg.roundToInt()
            parameterType == Short::class.java || parameterType == Short::class.javaObjectType -> arg.toInt().toShort()
            parameterType == Byte::class.java || parameterType == Byte::class.javaObjectType -> arg.toInt().toByte()
            else -> arg
        }
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
