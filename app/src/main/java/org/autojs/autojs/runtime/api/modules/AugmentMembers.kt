package org.autojs.autojs.runtime.api.modules

import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.api.modules.util.UtilValues
import org.autojs.autojs.runtime.exception.ScriptException
import org.autojs.autojs.runtime.exception.ScriptInterruptedException
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException.Companion.getRefinedStackTrace
import org.autojs.autojs.runtime.exception.WrappedRuntimeException
import org.autojs.autojs6.R
import org.mozilla.javascript.JavaScriptException
import org.mozilla.javascript.ScriptableObject
import java.util.function.Supplier

/**
 * Members a module of roadmap P4.1 takes over from its current augment module unchanged, for what the annotations
 * cannot declare: the data properties the current module computes at run time (error constructors, objects of nested
 * functions, the forwarded client methods of `mail`, the catalog functions of `compose`) and its getters. Removed with
 * the `augment` package (P5.2), when the values move into the modules.
 *
 * zh-CN: P4.1 的模块从现有 augment 模块原样接过的成员, 即注解无法声明的部分: 现有模块在运行时计算的数据属性 (错误构造函数, 嵌套函数的对象, mail 转发的
 * 客户端方法, compose 的目录函数) 与 getter. 随 augment 包在 P5.2 删除 (届时这些值移入模块).
 */
internal object AugmentMembers {

    /**
     * Defines the data properties of [module] (`selfAssignmentProperties`, plain `name to value` pairs) on [target] in
     * their order, with the attributes of the current `Augmentable.assign` (PERMANENT; the values as they are).
     */
    @JvmStatic
    fun defineProperties(module: Augmentable, target: ScriptableObject) {
        module.selfAssignmentProperties.forEach { (name, value) ->
            target.defineProperty(name as String, value, ScriptableObject.PERMANENT)
        }
    }

    /**
     * Runs [parse], a parser of the current module that only rejects values (an unknown name or constant) with an
     * IllegalArgumentException, turning that into a RangeError of [function] with the same message (D10).
     */
    @JvmStatic
    fun <T> values(function: String, parse: () -> T): T = try {
        parse()
    } catch (e: IllegalArgumentException) {
        throw UtilValues.rangeError(function, e.message ?: function)
    }

    /**
     * Runs [call], the function [function] of the current [module] (null: its invoke) reached after the new layer's own
     * argument checks, and rethrows a failure as the current augment layer does (`Augmentable.installFunctions` and its
     * invoke wrapper): an interruption and a script error of a pass-through function unchanged, any other exception
     * printed and wrapped in a WrappedRuntimeException (InternalError) with the current message. The plugin modules of
     * P4.1 without error codes (ai, opencc, pinyin, pinyin4j, mediainfo) thus fail the same way in both states once their
     * arguments are accepted, for example when the plugin is not installed or disabled.
     */
    @JvmStatic
    fun <T> asCurrent(module: Augmentable, function: String?, call: () -> T): T = try {
        call()
    } catch (t: Throwable) {
        throw if (function == null) invokeError(module, t) else functionError(module, function, t)
    }

    private fun functionError(module: Augmentable, function: String, t: Throwable): Throwable {
        val e = (if (t is ScriptException) t.cause ?: t else t).also { it.printStackTrace() }
        if (ScriptInterruptedException.causedByInterrupt(e)) return ScriptInterruptedException(e)
        if (e is JavaScriptException && function in module.scriptErrorPassThroughFunctions) return e
        val message = Augmentable.globalContext.getString(R.string.error_failed_to_call_method, "${module.key}.$function")
        val niceMessage = when (val errMsg = e.message) {
            null -> e.takeUnless { it is WrappedIllegalArgumentException }?.stackTraceToString()?.let { getRefinedStackTrace(message, it) } ?: message
            else -> {
                val refined = errMsg.replaceFirst(CLASS_PREFIX, "")
                val trailingDot = if (refined.endsWith(".")) "" else "."
                if (e is WrappedIllegalArgumentException) "$message. $refined$trailingDot" else "$message. $refined$trailingDot\n$e"
            }
        }
        return WrappedRuntimeException(niceMessage)
    }

    private fun invokeError(module: Augmentable, t: Throwable): Throwable {
        if (t !is Exception) return t
        if (module.scriptErrorPassThroughInvoke && t is JavaScriptException) return t
        if (ScriptInterruptedException.causedByInterrupt(t)) return t
        val context = Augmentable.globalContext
        val message = t.message?.let { msg ->
            val lines = msg.split("\n")
            val first = context.getString(R.string.error_failed_to_call_method_with_cause, module.key, lines.first())
            lines.drop(1).joinToString("\n").takeUnless { it.isBlank() }?.let { "$first\n$it" } ?: first
        } ?: context.getString(R.string.error_failed_to_call_method, module.key)
        return WrappedRuntimeException(message, t)
    }

    /** The current layer's pattern of an exception class name before its message. */
    private val CLASS_PREFIX = Regex("^(Wrapped )?\\w*(\\.\\w+)*(Exception|Error): ")

    /** The current value of the getter [name] of [module] (`selfAssignmentGetters`, `name to Supplier`). */
    @JvmStatic
    fun getter(module: Augmentable, name: String): Any? {
        val supplier = module.selfAssignmentGetters.firstNotNullOf { item -> (item as Pair<*, *>).takeIf { it.first == name }?.second }
        @Suppress("UNCHECKED_CAST")
        return (supplier as Supplier<Any?>).get()
    }

}
