package org.autojs.autojs.runtime.api.augment.selector

import org.autojs.autojs.annotation.RhinoRuntimeFunctionInterface
import org.autojs.autojs.core.accessibility.UiSelector
import org.autojs.autojs.core.automator.UiObject
import org.autojs.autojs.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.extension.FlexibleArray
import org.autojs.autojs.extension.ScriptableExtensions.hasProp
import org.autojs.autojs.extension.ScriptableExtensions.defineProp
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.api.augment.Invokable
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.autojs.runtime.exception.ShouldNeverHappenException
import org.autojs.autojs.util.RhinoUtils.NOT_CONSTRUCTABLE
import org.autojs.autojs.util.RhinoUtils.coerceBoolean
import org.autojs.autojs.util.RhinoUtils.coerceString
import org.autojs.autojs.util.RhinoUtils.newBaseFunction
import org.autojs.autojs.util.RhinoUtils.withRhinoContext
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Context
import org.mozilla.javascript.Scriptable
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
            withRhinoContext { context ->
                global.defineProp(methodName, newBaseFunction(null, { argList ->
                    val methodKey = coerceString(argList[0])
                    newBaseFunction(methodKey, { arguments ->
                        runCatching {
                            var result: Any? = null
                            val availableMethods = selectorObj.javaClass.methods.filter { method -> method.name == methodName }
                            require(availableMethods.isNotEmpty()) { "Method $methodKey doesn't exist on global selector" }
                            val validArgSizes = mutableListOf<Int>()
                            for ((index, m) in availableMethods.withIndex()) {
                                try {
                                    // @Caution by SuperMonster003 on Oct 28, 2024.
                                    //  ! Do NOT use `selectorObj` as a new global selector
                                    //  ! will APPEND on the old one (e.g., text('a') -> text('a').desc('b')).
                                    //  ! zh-CN:
                                    //  ! 不要使用 `selectorObj`, 因为新的全局选择器将附加到旧选择器之上
                                    //  ! (例如 text('a') -> text('a').desc('b')).
                                    result = m.invoke(scriptRuntime.selector(), *convertArgumentsIfNeeded(m, arguments))
                                    break
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    if (index == availableMethods.lastIndex) throw e
                                    validArgSizes.add(m.parameterCount)
                                }
                            }
                            if (availableMethods.size != validArgSizes.size) {
                                return@runCatching result
                            }
                            listOf(
                                "Invalid arguments length (${arguments.size})",
                                "for method UiSelector$$methodKey,",
                                "acceptable length is ${
                                    when (validArgSizes.size) {
                                        1 -> "${validArgSizes[0]}"
                                        else -> "[ ${validArgSizes.joinToString(", ")} ]"
                                    }
                                }",
                            ).joinToString(" ").let { s -> throw WrappedIllegalArgumentException(s) }
                        }.getOrElse { e ->
                            scriptRuntime.console.warn("${e.message}\n${e.stackTraceToString()}")
                            scriptRuntime.console.warn("method: ${methodKey}, arguments: [${arguments.joinToString(",") { coerceString(it) }}]")
                            throw e
                        }
                    }, NOT_CONSTRUCTABLE)
                }, NOT_CONSTRUCTABLE).call(context, global, global, arrayOf(methodName)))
            }
        }
    }

    override fun invoke(vararg args: Any?): Scriptable = ensureArgumentsIsEmpty(args) {
        Context.javaToJS(scriptRuntime.selector(), scriptRuntime.topLevelScope) as Scriptable
    }

    private fun convertArgumentsIfNeeded(method: Method, args: Array<out Any?>): Array<Any?> {
        return method.parameterTypes.mapIndexed { index, paramType ->
            val arg = args[index]
            when {
                arg !is Double -> arg
                paramType == Double::class.java || paramType == java.lang.Double.TYPE -> arg
                paramType == Float::class.java || paramType == java.lang.Float.TYPE -> arg.toDouble()
                paramType == Long::class.java || paramType == java.lang.Long.TYPE -> arg.roundToLong()
                paramType == Int::class.java || paramType == Integer.TYPE -> arg.roundToInt()
                paramType == Short::class.java || paramType == java.lang.Short.TYPE -> arg.toInt().toShort()
                paramType == Byte::class.java || paramType == java.lang.Byte.TYPE -> arg.toInt().toByte()
                else -> arg
            }
        }.toTypedArray()
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
    companion object : FlexibleArray() {

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
