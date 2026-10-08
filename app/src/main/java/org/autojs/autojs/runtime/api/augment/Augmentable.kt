package org.autojs.autojs.runtime.api.augment

import android.content.Context
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.rhino.extension.AnyExtensions.jsBrief
import org.autojs.autojs.rhino.ArgumentGuards
import org.autojs.autojs.rhino.ArgumentGuards.Companion.component1
import org.autojs.autojs.rhino.extension.ScriptableExtensions.defineProp
import org.autojs.autojs.rhino.extension.ScriptableExtensions.prop
import org.autojs.autojs.rhino.ProxyObject
import org.autojs.autojs.rhino.ProxyObject.Companion.AUGMENTED_CUSTOM_TO_STRING_KEY
import org.autojs.autojs.rhino.ProxyObject.Companion.PROXY_GETTER_KEY
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.events.Events
import org.autojs.autojs.runtime.exception.ScriptException
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException.Companion.getRefinedStackTrace
import org.autojs.autojs.runtime.exception.WrappedRuntimeException
import org.autojs.autojs.util.RhinoUtils
import org.autojs.autojs.util.RhinoUtils.DEFAULT_CALLER
import org.autojs.autojs.util.RhinoUtils.NOT_CONSTRUCTABLE
import org.autojs.autojs.util.RhinoUtils.coerceString
import org.autojs.autojs.util.RhinoUtils.newBaseFunction
import org.autojs.autojs6.R
import org.mozilla.javascript.JavaScriptException
import org.mozilla.javascript.NativeJavaClass
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.ScriptableObject.DONTENUM
import org.mozilla.javascript.ScriptableObject.PERMANENT
import org.mozilla.javascript.ScriptableObject.READONLY
import org.mozilla.javascript.ScriptableObject.UNINITIALIZED_CONST
import org.mozilla.javascript.VarScope
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap
import java.util.function.Consumer
import java.util.function.Supplier
import kotlin.reflect.KClass
import org.mozilla.javascript.ScriptRuntime as RhinoScriptRuntime

/**
 * Describes a name used to expose an [Augmentable] object on its target.
 *
 * When [withDollarPrefix] is enabled, both [name] and its dollar-prefixed alias
 * are bound to the same augmented object.
 *
 * zh-CN:
 *
 * 描述 [Augmentable] 对象在目标上的挂载名称.
 *
 * 当 [withDollarPrefix] 启用时, [name] 及其带美元前缀的别名将绑定到同一个扩充对象.
 */
data class AugmentableKey(
    val name: String,
    val withDollarPrefix: Boolean = true,
) {

    init {
        require(name.isNotBlank()) { "Augmentable key name must not be blank" }
    }

}

/**
 * Augmentable is an extensible base class (also an abstract class).
 * It's mainly used for extending the global objects of JavaScript scripts (app, device, images, etc.).
 * These objects originally existed in the "assets/modules" directory
 * named in the format __xxx__.js, and are loaded dynamically each time the script runs.
 * Since AutoJs6 v6.6.0, these __xxx__.js modules will be rewritten entirely in Kotlin syntax,
 * and reside under the package named org.autojs.autojs.runtime.api.augment.
 * The primary purpose is to boost the efficiency of script module initial loading,
 * and it is beneficial for controlling the standardization of value types.
 *
 * zh-CN:
 *
 * Augmentable 是一个扩充基类 (同时也是一个抽象类).
 * 它主要用于扩充 JavaScript 脚本的全局对象 (如 app, device, images 等).
 * 这些对象原本以 __xxx__.js 这样的命名方式存在于 "assets/modules" 目录下, 在每次运行脚本时动态加载.
 * 自 AutoJs6 v6.6.0 起, 这些 __xxx__.js 模块将全部由 kotlin 语法重写,
 * 并位于 org.autojs.autojs.runtime.api.augment 包名下.
 * 主要用于提升脚本初始加载模块的效率, 且有利于值类型的规范化控制.
 *
 * Created by SuperMonster003 on May 21, 2024.
 */
abstract class Augmentable(private val scriptRuntime: ScriptRuntime? = null) : ArgumentGuards() {

    /**
     * Names used to expose the augmented object. The first item is the primary
     * key used as the function name and in error messages. The declaration is
     * resolved once on first use and must remain stable for the lifetime of the
     * augmentable instance.
     *
     * zh-CN:
     *
     * 扩充对象的挂载名称. 首项为主键, 同时用于函数名称及错误消息.
     *
     * ```
     * override val keys = listOf(
     *     AugmentableKey("primary"),
     *     AugmentableKey("legacy", withDollarPrefix = false),
     * )
     * ```
     */
    open val keys: List<AugmentableKey>
        get() = listOf(AugmentableKey(javaClass.simpleName.replaceFirstChar { it.lowercaseChar() }))

    private val resolvedKeysSnapshot by lazy(::createResolvedKeys)

    val key: String
        get() = resolveKeys().primary

    /**
     * List element cases: <br>
     * - <1> - name to value
     * - <2> - name to value to flag
     */
    open val selfAssignmentProperties = listOf<Pair<Any, Any?>>()

    /**
     * List element cases: <br>
     * - <1> - name to value
     * - <2> - name to value to flag
     */
    open val globalAssignmentProperties = listOf<Pair<Any, Any?>>()

    /**
     * List element cases: <br>
     * - <1> - name
     * - <2> - name to alias
     * - <3> - name to aliasList
     * - <4> - name to flag
     * - <5> - name to alias to flag
     * - <6> - name to aliasList to flag
     */
    open val selfAssignmentFunctions = listOf<Any>()

    /**
     * List element cases: <br>
     * - <1> - name
     * - <2> - name to alias
     * - <3> - name to aliasList
     * - <4> - name to flag
     * - <5> - name to alias to flag
     * - <6> - name to aliasList to flag
     */
    open val globalAssignmentFunctions = listOf<Any>()

    /**
     * Names of the assigned functions whose thrown script errors (a [JavaScriptException], that is
     * an error object thrown by or into the script, such as the toolkit's `FlowError`) reach the
     * caller as they are instead of being wrapped into a "failed to call method" error; every other
     * exception is still wrapped.
     * zh-CN: 所赋函数中, 抛出的脚本错误 ([JavaScriptException], 即脚本抛出或抛入脚本的错误对象, 如工具集的 `FlowError`) 原样到达调用方而不被
     * 包装为 "方法调用失败" 错误的函数名; 其它异常仍被包装.
     */
    open val scriptErrorPassThroughFunctions: Set<String> = emptySet()

    /** Opt-in for callable modules that expose their own script Error subclass. */
    open val scriptErrorPassThroughInvoke: Boolean = false

    /**
     * List element cases: <br>
     * - <1> - name to class
     */
    open val selfAssignmentJavaClasses = listOf<Pair<String, KClass<*>>>()

    /**
     * List element cases: <br>
     * - <1> - name to class
     */
    open val globalAssignmentJavaClasses = listOf<Pair<String, KClass<*>>>()

    /**
     * List element cases: <br>
     * - <1> - name to getter
     * - <2> - name to getter to attributes
     */
    open val selfAssignmentGetters = listOf<Any>()

    /**
     * List element cases: <br>
     * - <1> - Triple<name, getter, setter>
     */
    open val selfAssignmentGettersAndSetters = listOf<Triple<String, Supplier<Any?>, Consumer<Any?>>>()

    /**
     * List element cases: <br>
     * - <1> - name to getter
     */
    open val globalAssignmentGetters = listOf<Pair<String, Supplier<Any?>>>()

    fun augmentWithRuntime(target: Scriptable, specifiedRuntime: ScriptRuntime, additionalAttributes: Int = 0): ScriptableObject {
        return augment(target, additionalAttributes, specifiedRuntime)
    }

    fun augmentWithRuntime(target: Scriptable, specifiedRuntime: ScriptRuntime, proto: Any, additionalAttributes: Int = 0): ScriptableObject {
        return augment(target, proto, additionalAttributes, specifiedRuntime)
    }

    @Deprecated(LEGACY_PREFIX_ARGUMENT_MESSAGE, level = DeprecationLevel.ERROR)
    fun augmentWithRuntime(
        target: Scriptable,
        specifiedRuntime: ScriptRuntime,
        withDollarPrefix: Boolean,
        additionalAttributes: Int = 0,
    ): ScriptableObject = legacyPrefixArgument()

    @Deprecated(LEGACY_PREFIX_ARGUMENT_MESSAGE, level = DeprecationLevel.ERROR)
    fun augmentWithRuntime(
        target: Scriptable,
        specifiedRuntime: ScriptRuntime,
        proto: Any,
        withDollarPrefix: Boolean,
        additionalAttributes: Int = 0,
    ): ScriptableObject = legacyPrefixArgument()

    fun augment(target: Scriptable, additionalAttributes: Int = 0, specifiedRuntime: ScriptRuntime? = null): ScriptableObject {
        return augment(target, emptyList<Any>(), additionalAttributes, specifiedRuntime)
    }

    fun augment(target: Scriptable, proto: Any, additionalAttributes: Int = 0, specifiedRuntime: ScriptRuntime? = null): ScriptableObject {
        val resolvedKeys = resolveKeys()
        val newObj = createAugmentedObject(target, proto, specifiedRuntime, resolvedKeys)
        mount(
            target,
            newObj,
            PERMANENT or refineAttributes(additionalAttributes),
            resolvedKeys,
        )
        return newObj
    }

    @Deprecated(LEGACY_PREFIX_ARGUMENT_MESSAGE, level = DeprecationLevel.ERROR)
    fun augment(
        target: Scriptable,
        withDollarPrefix: Boolean,
        additionalAttributes: Int = 0,
        specifiedRuntime: ScriptRuntime? = null,
    ): ScriptableObject = legacyPrefixArgument()

    @Deprecated(LEGACY_PREFIX_ARGUMENT_MESSAGE, level = DeprecationLevel.ERROR)
    fun augment(
        target: Scriptable,
        proto: Any,
        withDollarPrefix: Boolean,
        additionalAttributes: Int = 0,
        specifiedRuntime: ScriptRuntime? = null,
    ): ScriptableObject = legacyPrefixArgument()

    protected fun createAugmentedObject(
        target: Scriptable,
        proto: Any,
        specifiedRuntime: ScriptRuntime? = null,
        resolvedKeys: ResolvedKeys = resolveKeys(),
    ): ScriptableObject {
        val primaryKey = resolvedKeys.primary
        val creationScope = (specifiedRuntime ?: scriptRuntime)?.topLevelScope ?: RhinoUtils.getDeclarationScope(target)
        val callFunc: (args: Array<out Any?>) -> Any? = { args ->
            try {
                RhinoUtils.toJsValue(creationScope, (this as Invokable).invoke(*args))
            } catch (e: Exception) {
                if (scriptErrorPassThroughInvoke && e is JavaScriptException) throw e
                if (ScriptInterruptedException.causedByInterrupt(e)) {
                    throw e
                }
                val message = e.message?.let { msg ->
                    when {
                        msg.contains("\n") -> {
                            msg.split("\n").let { split ->
                                globalContext.getString(R.string.error_failed_to_call_method_with_cause, primaryKey, split.first()).let { result ->
                                    split.slice(1..split.lastIndex).joinToString("\n").takeUnless { it.isBlank() }?.let {
                                        "$result\n$it"
                                    } ?: result
                                }
                            }
                        }
                        else -> globalContext.getString(R.string.error_failed_to_call_method_with_cause, primaryKey, msg)
                    }
                } ?: globalContext.getString(R.string.error_failed_to_call_method, primaryKey)
                throw WrappedRuntimeException(message, e)
            }
        }

        val constructFunc: (args: Array<out Any?>) -> Any? = { args ->
            try {
                (this as Constructable).construct(*args)
            } catch (e: Exception) {
                if (ScriptInterruptedException.causedByInterrupt(e)) {
                    throw e
                }
                val message = e.message?.let {
                    globalContext.getString(R.string.error_failed_to_instantiate_with_cause, primaryKey, it)
                } ?: globalContext.getString(R.string.error_failed_to_instantiate, primaryKey)
                throw WrappedRuntimeException(message, e)
            }
        }

        val newObj = when (this) {
            is Versatile -> newBaseFunction(creationScope, primaryKey, callFunc, constructFunc)
            is Invokable -> newBaseFunction(creationScope, primaryKey, callFunc, NOT_CONSTRUCTABLE)
            is Constructable -> newBaseFunction(creationScope, primaryKey, DEFAULT_CALLER, constructFunc)
            else -> RhinoUtils.newObject(target)
        }

        assign(newObj, proto, specifiedRuntime)

        return newObj
    }

    protected fun mount(
        target: Scriptable,
        value: Any?,
        attributes: Int = ScriptableObject.EMPTY,
        resolvedKeys: ResolvedKeys = resolveKeys(),
    ) {
        resolvedKeys.names.forEach { target.defineProp(it, value, attributes) }
    }

    protected fun resolveKeys(): ResolvedKeys = resolvedKeysSnapshot

    private fun createResolvedKeys(): ResolvedKeys {
        val configuredKeys = keys
        require(configuredKeys.isNotEmpty()) { "Augmentable keys must not be empty" }

        val resolvedNames = configuredKeys.flatMap { configuredKey ->
            buildList {
                add(configuredKey.name)
                if (configuredKey.withDollarPrefix) add("\$${configuredKey.name}")
            }
        }
        val duplicateNames = resolvedNames.groupingBy { it }.eachCount()
            .filterValues { it > 1 }
            .keys
        require(duplicateNames.isEmpty()) {
            "Augmentable keys resolve to duplicate names: ${duplicateNames.joinToString()}"
        }

        return ResolvedKeys(configuredKeys.first().name, resolvedNames)
    }

    protected data class ResolvedKeys(
        val primary: String,
        val names: List<String>,
    )

    fun assignWithRuntime(target: ScriptableObject, specifiedRuntime: ScriptRuntime, proto: Any? = null) {
        assign(target, proto, specifiedRuntime)
    }

    /**
     * When the subclass calls assign, the value of key will be ignored.
     * zh-CN: 子类调用 assign 时将忽略 key 的值.
     */
    fun assign(target: ScriptableObject, proto: Any? = null, specifiedRuntime: ScriptRuntime? = null) {

        val globalScope = (specifiedRuntime ?: scriptRuntime)?.topLevelScope ?: RhinoUtils.getTopLevelScope(target)
        val global: ScriptableObject = globalScope.globalThis

        val protos = when (proto) {
            null -> emptyList()
            is List<*> -> proto
            else -> listOf(proto)
        }

        augmentPrototypes(target, globalScope, global, protos)
        augmentProperties(target, global)
        augmentFunctions(target, global, globalScope)
        augmentGettersAndSetters(target, global)
        augmentJavaClasses(target, globalScope, global)
    }

    fun put(o: Scriptable, name: String, value: Any?) {
        o.put(name, o, value)
    }

    fun put(o: ScriptableObject, item: Pair<String, Any>) {
        put(o, listOf(item))
    }

    fun put(o: ScriptableObject, items: List<Pair<String, Any?>>) {
        items.forEach { item -> put(o, item.first, item.second) }
    }

    fun putIfAbsent(o: ScriptableObject, item: Pair<String, Any>) {
        if (o.prop(item.first).isJsNullish()) put(o, item)
    }

    private fun augmentPrototypes(newObj: ScriptableObject, globalScope: VarScope, global: ScriptableObject, protos: List<Any?>) {
        val scopeProtoList = mutableListOf<Scriptable>()
        val objProtoList = mutableListOf<Scriptable>()

        protos.forEach { protoObj ->
            objProtoList += when (protoObj) {
                is SimpleGetterProxy -> ProxyObject(globalScope, fun(args: Array<out Any?>): Any? {
                    val (keyArg) = args
                    return protoObj.get(global, coerceString(keyArg))
                }, null)
                is Scriptable -> protoObj
                else -> RhinoScriptRuntime.toObject(globalScope, protoObj)
            }
        }

        if (this is AsEmitter) {
            requireNotNull(scriptRuntime) { "Augmentable instance of AsEmitter must have a non-null scriptRuntime property" }
            objProtoList += Events.__asEmitter__(scriptRuntime, emptyArray())
        }

        val newObjOriginalPrototype = newObj.prototype
        objProtoList.forEachIndexed { index, prototypeObject -> augmentPrototypesAt(index, newObj, prototypeObject, newObjOriginalPrototype) }
        val globalOriginalPrototype = global.prototype
        scopeProtoList.forEachIndexed { index, prototypeObject -> augmentPrototypesAt(index, global, prototypeObject, globalOriginalPrototype) }
    }

    private fun augmentPrototypesAt(index: Int, o: ScriptableObject, proto: Scriptable, originalProto: Scriptable?) {
        when (index) {
            0 -> o.prototype = proto.apply { prototype = originalProto }
            1 -> o.prototype.prototype = proto.apply { prototype = originalProto }
            2 -> o.prototype.prototype.prototype = proto.apply { prototype = originalProto }
            3 -> o.prototype.prototype.prototype.prototype = proto.apply { prototype = originalProto }
            4 -> o.prototype.prototype.prototype.prototype.prototype = proto.apply { prototype = originalProto }
            else -> throw RuntimeException("Augmentation can only be applied to no more than $index targets")
        }
    }

    private fun augmentProperties(target: Scriptable, global: ScriptableObject) {
        selfAssignmentProperties.forEach { pair ->
            val first = pair.first
            val second = pair.second
            val name: String
            val value: Any?
            var flag = 0
            when (first) {
                is String -> {
                    name = first
                    value = second
                }
                is Pair<*, *> -> {
                    name = coerceString(first.first)
                    value = first.second
                    flag = second as Int
                }
                else -> throw WrappedIllegalArgumentException("Unknown first element ${first.jsBrief()} for selfAssignmentProperties")
            }
            if (flag and AS_IGNORED == 0) {
                target.defineProp(name, value, PERMANENT or refineAttributes(flag))
            }
            if (flag and AS_GLOBAL != 0) {
                global.defineProp(name, value, PERMANENT or refineAttributes(flag))
            }
        }
        globalAssignmentProperties.forEach { pair ->
            val first = pair.first
            val second = pair.second
            val name: String
            val value: Any?
            var flag = 0
            when (first) {
                is String -> {
                    name = first
                    value = second
                }
                is Pair<*, *> -> {
                    name = coerceString(first.first)
                    value = first.second
                    flag = second as Int
                }
                else -> throw WrappedIllegalArgumentException("Unknown first element ${first.jsBrief()} for globalAssignmentProperties")
            }
            if (flag and AS_IGNORED == 0) {
                global.defineProp(name, value, PERMANENT or refineAttributes(flag))
            }
            if (flag and AS_GLOBAL != 0) {
                throw WrappedIllegalArgumentException("Flag \"AS_GLOBAL\" is unnecessary for global assignment")
            }
        }
    }

    private fun augmentFunctions(target: ScriptableObject, global: ScriptableObject, scope: VarScope) {
        val assignments = getFunctionAssignments()
        installFunctions(target, assignments.target, scope)
        installFunctions(global, assignments.global, scope)
    }

    private fun getFunctionAssignments(): FunctionAssignments {
        val selfDeclarations = selfAssignmentFunctions
        val globalDeclarations = globalAssignmentFunctions
        FUNCTION_ASSIGNMENT_CACHE[javaClass]?.let { cached ->
            if (cached.matches(selfDeclarations, globalDeclarations)) {
                return cached.assignments
            }
        }

        val assignments = normalizeFunctionAssignments(javaClass, selfDeclarations, globalDeclarations)
        val selfSnapshot = snapshotDeclarations(selfDeclarations)
        val globalSnapshot = snapshotDeclarations(globalDeclarations)
        if (selfSnapshot != null && globalSnapshot != null) {
            FUNCTION_ASSIGNMENT_CACHE[javaClass] = FunctionAssignmentCacheEntry(selfSnapshot, globalSnapshot, assignments)
        }
        return assignments
    }

    private fun normalizeFunctionAssignments(
        owner: Class<*>,
        selfDeclarations: List<Any>,
        globalDeclarations: List<Any>,
    ): FunctionAssignments {
        val targetFunctions = mutableListOf<FunctionAssignment>()
        val globalFunctions = mutableListOf<FunctionAssignment>()
        fun MutableCollection<FunctionAssignment>.addAssignment(funcName: String, alias: String, attributes: Int) {
            add(FunctionAssignment(funcName, alias, attributes, getCachedFunctionMethod(owner, funcName)))
        }

        selfDeclarations.forEach { item ->
            when (item) {
                is String -> targetFunctions.addAssignment(item, item, PERMANENT)
                is Pair<*, *> -> {
                    val first = item.first
                    val second = item.second
                    val name: String
                    val aliasList = mutableListOf<String>()
                    var flag = 0
                    when (first) {
                        is String -> {
                            name = first
                        }
                        is Pair<*, *> -> {
                            name = coerceString(first.first)
                            when (val alias = first.second) {
                                is String -> aliasList += alias
                                is List<*> -> aliasList += alias.map { coerceString(it) }
                                else -> throw WrappedIllegalArgumentException("Unknown first element ${first.jsBrief()} for selfAssignmentFunctions")
                            }
                        }
                        else -> throw WrappedIllegalArgumentException("Unknown name info ${first.jsBrief()} for selfAssignmentFunctions")
                    }
                    when (second) {
                        is String -> aliasList += second
                        is List<*> -> aliasList += second.map { coerceString(it) }
                        is Int -> flag = second
                        else -> throw WrappedIllegalArgumentException("Unknown second element ${second.jsBrief()} for selfAssignmentFunctions")
                    }
                    if (aliasList.isEmpty()) aliasList += name
                    aliasList.forEach { alias ->
                        if (flag and AS_IGNORED == 0) {
                            targetFunctions.addAssignment(name, alias, PERMANENT or refineAttributes(flag))
                        }
                        if (flag and AS_GLOBAL != 0) {
                            globalFunctions.addAssignment(name, alias, PERMANENT or refineAttributes(flag))
                        }
                        if (flag and AS_FUNCTIONAL_TO_STRING != 0) {
                            targetFunctions.addAssignment(
                                AUGMENTED_CUSTOM_TO_STRING_KEY,
                                AUGMENTED_CUSTOM_TO_STRING_KEY,
                                DONTENUM or PERMANENT or refineAttributes(flag),
                            )
                        }
                    }
                }
                else -> throw WrappedIllegalArgumentException("Unknown function element ${item.jsBrief()} for selfAssignmentFunctions")
            }
        }
        globalDeclarations.forEach { item ->
            when (item) {
                is String -> globalFunctions.addAssignment(item, item, PERMANENT)
                is Pair<*, *> -> {
                    val (first, second) = item
                    val name: String
                    val aliasList = mutableListOf<String>()
                    var flag = 0
                    when (first) {
                        is String -> {
                            name = first
                        }
                        is Pair<*, *> -> {
                            name = coerceString(first.first)
                            when (val alias = first.second) {
                                is String -> aliasList += alias
                                is List<*> -> aliasList += alias.map { coerceString(it) }
                                else -> throw WrappedIllegalArgumentException("Unknown first element ${first.jsBrief()} for globalAssignmentFunctions")
                            }
                        }
                        else -> throw WrappedIllegalArgumentException("Unknown name info ${first.jsBrief()} for globalAssignmentFunctions")
                    }
                    when (second) {
                        is String -> aliasList += second
                        is List<*> -> aliasList += second.map { coerceString(it) }
                        is Int -> flag = second
                        else -> throw WrappedIllegalArgumentException("Unknown second element ${second.jsBrief()} for globalAssignmentFunctions")
                    }
                    if (aliasList.isEmpty()) aliasList += name
                    aliasList.forEach { alias ->
                        if (flag and AS_IGNORED == 0) {
                            globalFunctions.addAssignment(name, alias, PERMANENT or refineAttributes(flag))
                        }
                        if (flag and AS_GLOBAL != 0) {
                            throw WrappedIllegalArgumentException("Flag \"AS_GLOBAL\" is unnecessary for global assignment")
                        }
                    }
                }
                else -> throw WrappedIllegalArgumentException("Unknown function element ${item.jsBrief()} for Augmentable#putFunctions")
            }
        }
        return FunctionAssignments(targetFunctions, globalFunctions)
    }

    internal fun augmentFunctionsBy(destination: ScriptableObject, functions: MutableCollection<Pair<Pair<String, String>, Int>>) {
        installFunctions(destination, functions.map { (funcNamePair, attributes) ->
            val (funcName, funcNameAlias) = funcNamePair
            FunctionAssignment(funcName, funcNameAlias, attributes, getCachedFunctionMethod(javaClass, funcName))
        }, RhinoUtils.getDeclarationScope(destination))
    }

    private fun installFunctions(destination: ScriptableObject, functions: Collection<FunctionAssignment>, scope: VarScope) {
        functions.forEach { assignment ->
            val (funcName, funcNameAlias, attributes, method) = assignment
            val f = newBaseFunction(scope, funcName, { args ->
                try {
                    RhinoUtils.toJsValue(scope, method.invoke(this, scriptRuntime, args))
                } catch (t: Throwable) {

                    // @Hint by SuperMonster003 on Sep 27, 2024.
                    //  ! It is required by Rhino that thrown object here
                    //  ! must be an instance of RuntimeException or Error.
                    //  ! zh-CN: Rhino 要求此处抛出的对象需为 RuntimeException 或 Error 实例.

                    val e = when (t) {
                        is InvocationTargetException -> when (val targetEx = t.targetException) {
                            is ScriptException -> targetEx.cause ?: targetEx
                            else -> targetEx
                        }
                        else -> t
                    }.also { it.printStackTrace() }

                    if (ScriptInterruptedException.causedByInterrupt(e)) {
                        throw ScriptInterruptedException(e)
                    }
                    if (e is JavaScriptException && funcName in scriptErrorPassThroughFunctions) {
                        throw e
                    }
                    val funcNameSuffix = if (funcName != funcNameAlias) " (${globalContext.getString(R.string.text_alias)}: $funcNameAlias)" else ""
                    val methodDescription = "$key.$funcName$funcNameSuffix"
                    val message = globalContext.getString(R.string.error_failed_to_call_method, methodDescription)
                    val niceMessage = when (val errMsg = e.message) {
                        null -> e.takeUnless { it is WrappedIllegalArgumentException }?.stackTraceToString()?.let {
                            getRefinedStackTrace(message, it)
                        } ?: message
                        else -> {
                            val refined = errMsg.replaceFirst(Regex("^(Wrapped )?\\w*(\\.\\w+)*(Exception|Error): "), "")
                            val trailingDot = if (refined.endsWith(".")) "" else "."
                            when (e is WrappedIllegalArgumentException) {
                                true -> "$message. $refined$trailingDot"
                                else -> "$message. $refined$trailingDot\n$e"
                            }
                        }
                    }
                    throw WrappedRuntimeException(niceMessage)
                }
            }, NOT_CONSTRUCTABLE)
            destination.defineWith(funcNameAlias, f, attributes)
        }
    }

    @Suppress("UnnecessaryVariable", "UNCHECKED_CAST")
    private fun augmentGettersAndSetters(target: ScriptableObject, global: ScriptableObject) {
        selfAssignmentGetters.forEach { item ->
            when (item) {
                is Pair<*, *> -> {
                    val (first, second) = item
                    when (first) {
                        is String -> {
                            val name = first
                            when (val getter = second) {
                                is Supplier<*> -> {
                                    target.defineWith(name, getter as Supplier<Any?>, null, PERMANENT)
                                }
                                else -> throw WrappedIllegalArgumentException("Unknown second element ${second.jsBrief()} for selfAssignmentGetters")
                            }
                        }
                        is Pair<*, *> -> {
                            val (name, getter) = first
                            require(name is String) {
                                throw WrappedIllegalArgumentException("First element of pair must be a String for selfAssignmentGetters")
                            }
                            when (val attributes = second) {
                                is Int -> {
                                    target.defineWith(name, getter as Supplier<Any?>, null, refineAttributes(attributes))
                                }
                                else -> throw WrappedIllegalArgumentException("Unknown second element ${second.jsBrief()} for selfAssignmentGetters")
                            }
                        }
                        else -> throw WrappedIllegalArgumentException("Unknown item ${item.jsBrief()} for selfAssignmentGetters")
                    }
                }
                else -> throw WrappedIllegalArgumentException("Unknown item ${item.jsBrief()} for selfAssignmentGetters")
            }
        }
        selfAssignmentGettersAndSetters.forEach { triple ->
            val (name, getter, setter) = triple
            target.defineWith(name, getter, setter, PERMANENT)
        }
        globalAssignmentGetters.forEach { pair ->
            val (name, getter) = pair
            global.defineWith(name, getter, null, PERMANENT)
        }
    }

    private fun augmentJavaClasses(target: ScriptableObject, globalScope: VarScope, global: ScriptableObject) {
        selfAssignmentJavaClasses.forEach { pair ->
            val (name, clazz) = pair
            target.defineWith(name, NativeJavaClass(globalScope, clazz.java), PERMANENT)
        }
        globalAssignmentJavaClasses.forEach { pair ->
            val (name, clazz) = pair
            global.defineWith(name, NativeJavaClass(globalScope, clazz.java), PERMANENT)
        }
    }

    companion object : ArgumentGuards() {

        private const val LEGACY_PREFIX_ARGUMENT_MESSAGE =
            "Declare dollar-prefix policies with Augmentable.keys and remove the withDollarPrefix argument"

        private fun legacyPrefixArgument(): Nothing = error(LEGACY_PREFIX_ARGUMENT_MESSAGE)

        private data class FunctionAssignment(
            val funcName: String,
            val alias: String,
            val attributes: Int,
            val method: CachedFunctionMethod,
        )

        private data class FunctionAssignments(
            val target: List<FunctionAssignment>,
            val global: List<FunctionAssignment>,
        )

        private data class FunctionAssignmentCacheEntry(
            val selfDeclarations: List<DeclarationSnapshot>,
            val globalDeclarations: List<DeclarationSnapshot>,
            val assignments: FunctionAssignments,
        ) {
            fun matches(self: List<Any>, global: List<Any>): Boolean {
                return selfDeclarations.matches(self) && globalDeclarations.matches(global)
            }
        }

        private sealed class DeclarationSnapshot {
            abstract fun matches(value: Any?): Boolean

            object NullValue : DeclarationSnapshot() {
                override fun matches(value: Any?) = value == null
            }

            data class StringValue(val value: String) : DeclarationSnapshot() {
                override fun matches(value: Any?) = value is String && this.value == value
            }

            data class IntValue(val value: Int) : DeclarationSnapshot() {
                override fun matches(value: Any?) = value is Int && this.value == value
            }

            data class PairValue(val first: DeclarationSnapshot, val second: DeclarationSnapshot) : DeclarationSnapshot() {
                override fun matches(value: Any?): Boolean {
                    return value is Pair<*, *> && first.matches(value.first) && second.matches(value.second)
                }
            }

            data class ListValue(val values: List<DeclarationSnapshot>) : DeclarationSnapshot() {
                override fun matches(value: Any?): Boolean {
                    return value is List<*> && values.matches(value)
                }
            }
        }

        private class CachedFunctionMethod(private val owner: Class<*>, private val name: String) {

            @Volatile
            private var methodWithoutRuntime: Method? = null

            @Volatile
            private var methodWithRuntime: Method? = null

            fun invoke(receiver: Augmentable, runtime: ScriptRuntime?, args: Array<out Any?>): Any? {
                val method = resolve(withRuntime = runtime != null)
                val result = when (runtime) {
                    null -> method.invoke(receiver, args)
                    else -> method.invoke(receiver, runtime, args)
                }
                return if (method.returnType == Void.TYPE) RhinoUtils.UNDEFINED else result
            }

            private fun resolve(withRuntime: Boolean): Method {
                val cached = when (withRuntime) {
                    true -> methodWithRuntime
                    false -> methodWithoutRuntime
                }
                if (cached != null) return cached

                return synchronized(this) {
                    val resolved = when (withRuntime) {
                        true -> methodWithRuntime ?: owner.getMethod(name, ScriptRuntime::class.java, Array<Any>::class.java)
                        false -> methodWithoutRuntime ?: owner.getMethod(name, Array<Any>::class.java)
                    }
                    when (withRuntime) {
                        true -> methodWithRuntime = resolved
                        false -> methodWithoutRuntime = resolved
                    }
                    resolved
                }
            }
        }

        private val FUNCTION_ASSIGNMENT_CACHE = ConcurrentHashMap<Class<*>, FunctionAssignmentCacheEntry>()
        private val FUNCTION_METHOD_CACHE = ConcurrentHashMap<Pair<Class<*>, String>, CachedFunctionMethod>()

        private fun getCachedFunctionMethod(owner: Class<*>, name: String): CachedFunctionMethod {
            val key = owner to name
            return FUNCTION_METHOD_CACHE[key] ?: CachedFunctionMethod(owner, name).let { created ->
                FUNCTION_METHOD_CACHE.putIfAbsent(key, created) ?: created
            }
        }

        private fun snapshotDeclarations(declarations: List<Any>): List<DeclarationSnapshot>? {
            return buildList {
                declarations.forEach { declaration ->
                    add(snapshotDeclaration(declaration) ?: return null)
                }
            }
        }

        private fun snapshotDeclaration(value: Any?): DeclarationSnapshot? {
            return when (value) {
                null -> DeclarationSnapshot.NullValue
                is String -> DeclarationSnapshot.StringValue(value)
                is Int -> DeclarationSnapshot.IntValue(value)
                is Pair<*, *> -> {
                    val first = snapshotDeclaration(value.first) ?: return null
                    val second = snapshotDeclaration(value.second) ?: return null
                    DeclarationSnapshot.PairValue(first, second)
                }
                is List<*> -> {
                    val values = buildList {
                        value.forEach { item -> add(snapshotDeclaration(item) ?: return null) }
                    }
                    DeclarationSnapshot.ListValue(values)
                }
                else -> null
            }
        }

        private fun List<DeclarationSnapshot>.matches(values: List<*>): Boolean {
            return size == values.size && indices.all { index -> this[index].matches(values[index]) }
        }

        // @Caution by SuperMonster003 on Oct 5, 2025.
        //  ! These constants are "bit flags", each value is a power of 2, with only one bit being 1 in binary.
        //  ! They can be combined using `bitwise OR (|)` and checked using `bitwise AND (&)` without interference.
        //  ! Do not modify them to the form 0x0101/0x0102/0x0103/0x0104/...,
        //  ! to avoid flag overlap causing combination and detection logic to fail,
        //  ! e.g. `flags & AS_GLOBAL` may give false results.
        //  ! zh-CN:
        //  ! 这些常量是 "位标志" (bit flags), 每个值都是二的幂, 二进制中仅有一位为 1.
        //  ! 可用 `按位或 (|)` 进行组合, `按位与 (&)` 进行检测, 互不干扰.
        //  ! 不可将其修改为 0x0101/0x0102/0x0103/0x0104/... 的形式,
        //  ! 以避免标志重叠造成组合与检测逻辑失效, 如 `flags & AS_GLOBAL` 可能出现误判.
        //  !
        const val AS_GLOBAL = 0x0100
        const val AS_IGNORED = 0x0200
        const val AS_LITERAL_TO_STRING = 0x0400
        const val AS_FUNCTIONAL_TO_STRING = 0x0800

        val globalContext: Context
            get() = GlobalAppContext.get()

        fun refineAttributes(attributes: Int) = attributes and (READONLY or DONTENUM or PERMANENT or UNINITIALIZED_CONST)

        private fun isInternalName(value: String) = value.startsWith("__") && value.endsWith("__")

        private fun ScriptableObject.defineWith(name: String, getter: Supplier<Any?>, setter: Consumer<Any?>?, attributes: Int) {
            val normalizedGetter = Supplier {
                RhinoUtils.toJsValue(RhinoUtils.getDeclarationScope(this), getter.get())
            }
            when (isInternalName(name)) {
                true -> this.defineProperty(name, normalizedGetter, setter, DONTENUM or attributes)
                else -> this.defineProperty(name, normalizedGetter, setter, attributes)
            }
        }

        private fun ScriptableObject.defineWith(name: String, value: Any?, attributes: Int) {
            val normalizedValue = RhinoUtils.toJsValue(RhinoUtils.getDeclarationScope(this), value)
            when (isInternalName(name)) {
                true -> this.defineProperty(name, normalizedValue, DONTENUM or attributes)
                else -> this.defineProperty(name, normalizedValue, attributes)
            }
        }

    }

}
