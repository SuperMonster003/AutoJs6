package org.autojs.autojs.runtime.api.augment

import android.content.Context
import org.autojs.autojs.app.GlobalAppContext
import org.autojs.autojs.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.extension.AnyExtensions.jsBrief
import org.autojs.autojs.extension.FlexibleArray
import org.autojs.autojs.extension.ScriptableExtensions.defineProp
import org.autojs.autojs.extension.ScriptableExtensions.prop
import org.autojs.autojs.rhino.ProxyObject
import org.autojs.autojs.rhino.ProxyObject.Companion.PROXY_GETTER_KEY
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.augment.events.Events
import org.autojs.autojs.runtime.exception.ScriptException
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.autojs.runtime.exception.WrappedRuntimeException
import org.autojs.autojs.util.RhinoUtils
import org.autojs.autojs.util.RhinoUtils.DEFAULT_CALLER
import org.autojs.autojs.util.RhinoUtils.NOT_CONSTRUCTABLE
import org.autojs.autojs.util.RhinoUtils.coerceString
import org.autojs.autojs.util.RhinoUtils.newBaseFunction
import org.autojs.autojs.util.StringUtils.lowercaseFirstChar
import org.autojs.autojs6.R
import org.mozilla.javascript.NativeJavaClass
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.ScriptableObject.DONTENUM
import org.mozilla.javascript.ScriptableObject.PERMANENT
import org.mozilla.javascript.ScriptableObject.READONLY
import org.mozilla.javascript.ScriptableObject.UNINITIALIZED_CONST
import org.mozilla.javascript.WrappedException
import java.lang.reflect.InvocationTargetException
import java.util.function.Consumer
import java.util.function.Supplier
import kotlin.reflect.KClass
import org.mozilla.javascript.ScriptRuntime as RhinoScriptRuntime

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
abstract class Augmentable(private val scriptRuntime: ScriptRuntime? = null) : FlexibleArray() {

    private var mIsOriginalKeyName = false

    open val key: String
        get() = when {
            mIsOriginalKeyName -> javaClass.simpleName
            else -> lowercaseFirstChar(javaClass.simpleName)
        }

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
     */
    open val selfAssignmentGetters = listOf<Pair<String, Supplier<Any?>>>()

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

    fun originateKeyName() = also { mIsOriginalKeyName = true }

    fun augment(target: Scriptable, withDollarPrefix: Boolean = true, additionalAttributes: Int = 0): ScriptableObject {
        return augment(target, emptyList<Any>(), withDollarPrefix, additionalAttributes)
    }

    fun augment(target: Scriptable, proto: Any, withDollarPrefix: Boolean = true, additionalAttributes: Int = 0): ScriptableObject {
        val callFunc: (args: Array<out Any?>) -> Any? = { args ->
            try {
                (this as Invokable).invoke(*args)
            } catch (e: Exception) {
                val message = e.message?.let {
                    globalContext.getString(R.string.error_failed_to_call_method_with_cause, key, it)
                } ?: globalContext.getString(R.string.error_failed_to_call_method, key)
                throw WrappedRuntimeException(message, e)
            }
        }

        val constructFunc: (args: Array<out Any?>) -> Any? = { args ->
            try {
                (this as Constructable).construct(*args)
            } catch (e: Exception) {
                val message = e.message?.let {
                    globalContext.getString(R.string.error_failed_to_instantiate_with_cause, key, it)
                } ?: globalContext.getString(R.string.error_failed_to_instantiate, key)
                throw RuntimeException(message, e)
            }
        }

        val newObj = when (this) {
            is Versatile -> newBaseFunction(key, callFunc, constructFunc)
            is Invokable -> newBaseFunction(key, callFunc, NOT_CONSTRUCTABLE)
            is Constructable -> newBaseFunction(key, DEFAULT_CALLER, constructFunc)
            else -> RhinoUtils.newObject(target)
        }

        assign(newObj, proto)

        val keys = mutableListOf(key)
        if (withDollarPrefix) keys += "\$$key"

        keys.forEach {
            target.defineProp(it, newObj, PERMANENT or refineAttributes(additionalAttributes))
        }

        return newObj
    }

    /**
     * When the subclass calls assign, the value of key will be ignored.
     * zh-CN: 子类调用 assign 时将忽略 key 的值.
     */
    fun assign(target: ScriptableObject, proto: Any? = null) {

        val global: ScriptableObject = scriptRuntime?.topLevelScope ?: ScriptableObject.getTopLevelScope(target) as ScriptableObject

        val protos = when (proto) {
            null -> emptyList()
            is List<*> -> proto
            else -> listOf(proto)
        }

        augmentPrototypes(target, global, protos)
        augmentProperties(target, global)
        augmentFunctions(target, global)
        augmentGettersAndSetters(target, global)
        augmentJavaClasses(target, global)
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

    private fun augmentPrototypes(newObj: ScriptableObject, global: ScriptableObject, protos: List<Any?>) {
        val scopeProtoList = mutableListOf<Scriptable>()
        val objProtoList = mutableListOf<Scriptable>()

        protos.forEach { protoObj ->
            objProtoList += when (protoObj) {
                is SimpleGetterProxy -> ProxyObject(global, runCatching {
                    fun(args: Array<out Any?>): Any? {
                        val (keyArg) = args
                        return protoObj::class.java.getDeclaredMethod(PROXY_GETTER_KEY, Scriptable::class.java, String::class.java)
                            .invoke(this, global, coerceString(keyArg))
                    }
                }.getOrNull(), null)
                is Scriptable -> protoObj
                else -> RhinoScriptRuntime.toObject(global, protoObj)
            }
        }

        if (this is AsEmitter) {
            require(scriptRuntime != null) { "Augmentable instance of AsEmitter must have a non-null scriptRuntime property" }
            objProtoList += Events.__asEmitter__(scriptRuntime, emptyArray())
        }

        objProtoList.forEachIndexed { index, prototypeObject -> augmentPrototypesAt(index, newObj, prototypeObject) }
        scopeProtoList.forEachIndexed { index, prototypeObject -> augmentPrototypesAt(index, global, prototypeObject) }
    }

    private fun augmentPrototypesAt(index: Int, o: ScriptableObject, proto: Scriptable) {
        when (index) {
            0 -> o.prototype = proto
            1 -> o.prototype.prototype = proto
            2 -> o.prototype.prototype.prototype = proto
            3 -> o.prototype.prototype.prototype.prototype = proto
            4 -> o.prototype.prototype.prototype.prototype.prototype = proto
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

    private fun augmentFunctions(target: ScriptableObject, global: ScriptableObject) {
        val targetFunctions = mutableListOf<Pair<Pair<String, String>, Int>>()
        val globalFunctions = mutableListOf<Pair<Pair<String, String>, Int>>()
        selfAssignmentFunctions.forEach { item ->
            when (item) {
                is String -> targetFunctions += item to item to PERMANENT
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
                            targetFunctions += name to alias to (PERMANENT or refineAttributes(flag))
                        }
                        if (flag and AS_GLOBAL != 0) {
                            globalFunctions += name to alias to (PERMANENT or refineAttributes(flag))
                        }
                    }
                }
                else -> throw WrappedIllegalArgumentException("Unknown function element ${item.jsBrief()} for selfAssignmentFunctions")
            }
        }
        globalAssignmentFunctions.forEach { item ->
            when (item) {
                is String -> globalFunctions += item to item to PERMANENT
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
                            globalFunctions += name to alias to (PERMANENT or refineAttributes(flag))
                        }
                        if (flag and AS_GLOBAL != 0) {
                            throw WrappedIllegalArgumentException("Flag \"AS_GLOBAL\" is unnecessary for global assignment")
                        }
                    }
                }
                else -> throw WrappedIllegalArgumentException("Unknown function element ${item.jsBrief()} for Augmentable#putFunctions")
            }
        }
        augmentFunctionsBy(target, targetFunctions)
        augmentFunctionsBy(global, globalFunctions)
    }

    internal fun augmentFunctionsBy(destination: ScriptableObject, functions: MutableCollection<Pair<Pair<String, String>, Int>>) {
        functions.forEach { pair ->
            val (funcNamePair, attributes) = pair
            val (funcName, funcNameAlias) = funcNamePair
            val f = newBaseFunction(funcName, { args ->
                try {
                    when (scriptRuntime) {
                        null -> javaClass
                            .getMethod(funcName, Array<Any>::class.java)
                            .invoke(this, args)
                        else -> javaClass
                            .getMethod(funcName, ScriptRuntime::class.java, Array<Any>::class.java)
                            .invoke(this, scriptRuntime, args)
                    }
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
                    }
                    if (ScriptInterruptedException.causedByInterrupt(e)) {
                        throw WrappedException(e)
                    }
                    val funcNameSuffix = if (funcName != funcNameAlias) " (${globalContext.getString(R.string.text_alias)}: $funcNameAlias)" else ""
                    val methodDescription = "$key.$funcName$funcNameSuffix"
                    val message = globalContext.getString(R.string.error_failed_to_invoke_method_with_description, methodDescription)
                    val niceMessage = when (val errMsg = e.message) {
                        null -> message
                        else -> "$message. ${errMsg.replaceFirst(Regex("^(Wrapped )?\\w*(\\.\\w+)*(Exception|Error): "), "")}"
                    }
                    when (e) {
                        is WrappedIllegalArgumentException -> {
                            // @Hint by SuperMonster003 on Oct 31, 2024.
                            //  ! Here we force WrappedIllegalArgumentException to be converted to RuntimeException
                            //  ! to prevent Rhino JavaScript code's try..catch blocks from catching this important exception.
                            //  ! Additionally, WrappedIllegalArgumentException itself inherits from WrappedException,
                            //  ! so the exception's code line number will be included when printing the error stack trace.
                            //  ! zh-CN:
                            //  ! 这里强制将 WrappedIllegalArgumentException 转换为 RuntimeException,
                            //  ! 是为了让 Rhino JavaScript 代码的 try..catch 块无法捕获这个重要异常.
                            //  ! 另外 WrappedIllegalArgumentException 本身继承了 WrappedException,
                            //  ! 因此在打印错误堆栈信息时会包含异常所在的 code line number (代码行号).
                            throw RuntimeException(niceMessage, e)
                        }
                        else -> throw WrappedRuntimeException(niceMessage, e)
                    }
                }
            }, NOT_CONSTRUCTABLE)
            destination.defineProperty(funcNameAlias, f, attributes)
        }
    }

    private fun augmentGettersAndSetters(target: ScriptableObject, global: ScriptableObject) {
        selfAssignmentGetters.forEach { pair ->
            val (name, getter) = pair
            target.defineProperty(name, getter, null, PERMANENT)
        }
        selfAssignmentGettersAndSetters.forEach { triple ->
            val (name, getter, setter) = triple
            target.defineProperty(name, getter, setter, PERMANENT)
        }
        globalAssignmentGetters.forEach { pair ->
            val (name, getter) = pair
            global.defineProperty(name, getter, null, PERMANENT)
        }
    }

    private fun augmentJavaClasses(target: ScriptableObject, global: ScriptableObject) {
        selfAssignmentJavaClasses.forEach { pair ->
            val (name, clazz) = pair
            target.defineProperty(name, NativeJavaClass(global, clazz.java), PERMANENT)
        }
        globalAssignmentJavaClasses.forEach { pair ->
            val (name, clazz) = pair
            global.defineProperty(name, NativeJavaClass(global, clazz.java), PERMANENT)
        }
    }

    private fun createNewThrowableInstance(e: Throwable, message: String): Throwable? = runCatching {
        e::class.constructors.firstOrNull {
            it.parameters.size == 1 && it.parameters[0].type.classifier == String::class
        }?.call(message)
    }.getOrNull()

    companion object : FlexibleArray() {

        const val AS_GLOBAL = 0x1000
        const val AS_IGNORED = 0x2000

        val globalContext: Context
            get() = GlobalAppContext.get()

        fun refineAttributes(attributes: Int) = attributes and (READONLY or DONTENUM or PERMANENT or UNINITIALIZED_CONST)

    }

}
