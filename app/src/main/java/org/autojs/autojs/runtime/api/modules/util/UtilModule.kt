package org.autojs.autojs.runtime.api.modules.util

import org.autojs.autojs.AutoJs
import org.autojs.autojs.core.console.ConsoleImpl
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.runtime.api.modules.ScriptArgs
import org.autojs.autojs.runtime.api.modules.ScriptAttributes
import org.autojs.autojs.runtime.api.modules.ScriptFunction
import org.autojs.autojs.runtime.api.modules.ScriptMessages
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.autojs.autojs.runtime.api.modules.ScriptPrototype
import org.autojs.autojs.runtime.api.modules.jsox.JsoxValues
import org.autojs.autojs.runtime.api.modules.util.UtilValues.isNullish
import org.autojs.autojs.runtime.api.modules.util.UtilValues.quote
import org.autojs.autojs.runtime.api.modules.util.UtilValues.rangeError
import org.autojs.autojs.runtime.api.modules.util.UtilValues.typeError
import org.autojs.autojs.runtime.api.modules.util.UtilValues.typeOf
import org.autojs.autojs.util.DisplayUtils
import org.autojs.autojs.util.RhinoUtils
import org.autojs.autojs.util.RhinoUtils.ObsoletedRhinoFunctionException
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Callable
import org.mozilla.javascript.Context
import org.mozilla.javascript.Function
import org.mozilla.javascript.LambdaFunction
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeError
import org.mozilla.javascript.NativeJSON
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.SerializableCallable
import org.mozilla.javascript.TopLevel
import org.mozilla.javascript.VarScope
import org.mozilla.javascript.Wrapper
import org.mozilla.javascript.regexp.NativeRegExp
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import org.autojs.autojs.runtime.api.Util as ApiUtil
import org.mozilla.javascript.ScriptRuntime as RhinoScriptRuntime

/**
 * The `util` module on the new script module layer (roadmap P2.3), with the sub-modules `util.java` ([UtilJavaModule]),
 * `util.version` ([UtilVersionModule]), `util.versionCodes` ([UtilVersionCodesModule]), `util.inspect`
 * ([UtilInspectModule]) and `util.morseCode` ([UtilMorseCodeModule]); the module object's prototype is the Java object
 * `org.autojs.autojs.runtime.api.Util` (`getClass`, `getClassName`, ...), as in the current module.
 *
 * Behavior follows the documentation, the d.ts and ROADMAP appendix E: `isString` / `isNumber` / ... are JS `typeof`
 * (a `java.lang.String` object is not a string), `isError` also accepts objects inheriting from `Error.prototype`,
 * `format('%d')` writes numbers in their JS form, `checkStringParam` / `checkStringArgument` take a RegExp (its source,
 * ignoring case) and match with JS regular expressions, `checkStringParam` also reads objects through their `toString`,
 * the string helpers accept concatenated strings, and `log` / `deprecate` / `debuglog` write to the script's console, as
 * the obsolete module did. Arguments follow the real signatures (T4), errors are TypeError / RangeError (D10).
 *
 * zh-CN: 新脚本模块层上的 util 模块 (P2.3) 及其子模块; 模块对象的原型为 Java 对象 runtime.api.Util, 与现有模块相同. 行为以文档, d.ts 与附录 E 为准:
 * 类型判断按 JS typeof, isError 也接受继承 Error.prototype 的对象, format('%d') 以 JS 形式写数字, checkStringParam / checkStringArgument 接受 RegExp 并按
 * JS 正则匹配, checkStringParam 也经 toString 读取对象, 字符串辅助函数接受拼接的字符串, log / deprecate / debuglog 写入脚本控制台 (同旧模块).
 * 参数按真实签名 (T4), 错误为 TypeError / RangeError (D10).
 */
@ScriptModule(name = "util")
class UtilModule(private val runtime: ScriptRuntime?) {

    @ScriptPrototype
    fun prototype(): Any = ApiUtil

    /** Binds the functions [funcNames] of [src] to [src] and puts them on [target] (host initialization helper). */
    @ScriptFunction(attributes = ScriptAttributes.READONLY or ScriptAttributes.DONTENUM or ScriptAttributes.PERMANENT)
    fun __assignFunctions__(cx: Context, scope: VarScope, src: Any?, target: Any?, funcNames: Any?) {
        val function = "util.__assignFunctions__"
        if (src !is ScriptableObject) throw typeError(function, "src", "object", src)
        if (target !is ScriptableObject) throw typeError(function, "target", "object", target)
        if (funcNames !is NativeArray) throw typeError(function, "funcNames", "Array", funcNames)
        val bind = ScriptableObject.getProperty(ScriptableObject.getFunctionPrototype(scope), "bind") as Callable
        for (i in 0 until funcNames.length.toInt()) {
            val name = RhinoScriptRuntime.toString(funcNames.get(i, funcNames))
            val func = ScriptableObject.getProperty(src, name)
            if (func !is Callable) throw typeError(function, "src.$name", "function", func.takeUnless { it === Scriptable.NOT_FOUND })
            target.put(name, target, bind.call(cx, scope, func, arrayOf(src)))
        }
    }

    @ScriptFunction
    fun isArray(o: Any?): Boolean = isJsArray(o)

    @ScriptFunction
    fun isBoolean(o: Any?): Boolean = typeOf(o) == "boolean"

    @ScriptFunction
    fun isNull(o: Any?): Boolean = o == null

    @ScriptFunction
    fun isNullOrUndefined(o: Any?): Boolean = isNullish(o)

    @ScriptFunction
    fun isNumber(o: Any?): Boolean = typeOf(o) == "number"

    @ScriptFunction
    fun isString(o: Any?): Boolean = typeOf(o) == "string"

    @ScriptFunction
    fun isSymbol(o: Any?): Boolean = typeOf(o) == "symbol"

    @ScriptFunction
    fun isUndefined(o: Any?): Boolean = typeOf(o) == "undefined"

    @ScriptFunction
    fun isRegExp(o: Any?): Boolean = isObject(o) && (o as Scriptable).className == "RegExp"

    @ScriptFunction
    fun isObject(o: Any?): Boolean = o != null && typeOf(o) == "object"

    @ScriptFunction
    fun isDate(o: Any?): Boolean = isObject(o) && (o as Scriptable).className == "Date"

    /** An error object, or any object inheriting from `Error.prototype` (`e instanceof Error`). */
    @ScriptFunction
    fun isError(scope: VarScope, o: Any?): Boolean {
        if (!isObject(o) || o !is Scriptable) return false
        if (o is NativeError || o.className == "Error") return true
        val errorPrototype = TopLevel.getBuiltinPrototype(ScriptableObject.getTopLevelScope(scope), TopLevel.Builtins.Error)
        return errorPrototype != null && RhinoScriptRuntime.jsDelegatesTo(o, errorPrototype)
    }

    @ScriptFunction
    fun isFunction(o: Any?): Boolean = typeOf(o) == "function"

    @ScriptFunction
    fun isBigInt(o: Any?): Boolean = typeOf(o) == "bigint"

    /** A Java object as Rhino wraps it (class JavaObject; Java classes, arrays and JS values are not). */
    @ScriptFunction
    fun isJavaObject(scope: VarScope, o: Any?): Boolean = !isNullish(o) && (Context.javaToJS(o, scope) as? Scriptable)?.className == "JavaObject"

    @ScriptFunction
    fun isJavaArray(o: Any?): Boolean = ((o as? Wrapper)?.unwrap() ?: o)?.javaClass?.isArray == true

    @ScriptFunction
    fun isInteger(o: Any?): Boolean = o is Number && o.toDouble().let { d -> !d.isInfinite() && !d.isNaN() && Math.floor(d) == d }

    @ScriptFunction
    fun isPrimitive(o: Any?): Boolean = !isReference(o)

    @ScriptFunction
    fun isReference(o: Any?): Boolean = isObject(o) || typeOf(o) == "function"

    /** A JS object without own properties. */
    @ScriptFunction
    fun isEmptyObject(o: Any?): Boolean = isObject(o) && o is ScriptableObject && o.isEmpty

    @ScriptFunction
    fun unwrapJavaObject(o: Any?): Any? = RhinoUtils.unwrap(o)

    /** Makes [d] (a constructor) inherit from [b] (a constructor, an object or null): statics and instances alike. */
    @ScriptFunction
    fun extend(cx: Context, scope: VarScope, d: Any?, b: Any?) {
        val function = "util.extend"
        if (d !is ScriptableObject) throw typeError(function, "d", "function", d)
        val base: Scriptable? = when (b) {
            null -> null
            is Scriptable -> b
            else -> RhinoScriptRuntime.toObject(cx, scope, b)
        }
        val objectCtor = TopLevel.getBuiltinCtor(cx, scope, TopLevel.Builtins.Object)
        ScriptableObject.callMethod(cx, objectCtor, "setPrototypeOf", arrayOf(d, base))
        val prototype: Scriptable? = when (base) {
            null -> ScriptableObject.callMethod(cx, objectCtor, "create", arrayOf(null)) as Scriptable
            else -> cx.newObject(scope).also { instance ->
                (instance as ScriptableObject).defineProperty("constructor", d, ScriptableObject.READONLY or ScriptableObject.DONTENUM or ScriptableObject.PERMANENT)
                instance.prototype = ScriptableObject.getProperty(base, "prototype") as? Scriptable
            }
        }
        when (val current = ScriptableObject.getProperty(d, "prototype")) {
            Scriptable.NOT_FOUND, null -> d.defineProperty("prototype", prototype, ScriptableObject.EMPTY)
            is Scriptable -> current.prototype = prototype
            else -> throw typeError(function, "d.prototype", "object", current)
        }
    }

    /** `%s`, `%d`, `%j` and `%%` placeholders; arguments left over are appended, objects through `util.inspect`. */
    @ScriptFunction
    fun format(cx: Context, scope: VarScope, vararg args: Any?): String = formatArgs(cx, scope, args)

    /** Not designed for AutoJs6 (a Node.js entry): only writes a warning to the console. */
    @ScriptFunction
    fun deprecate(vararg args: Any?) {
        warn()
    }

    /** Not designed for AutoJs6 (a Node.js entry): only writes a warning to the console. */
    @ScriptFunction
    fun debuglog(vararg args: Any?) {
        warn()
    }

    /** Writes `util.format(...args)` to the console, after a timestamp of `dd MMM HH:mm:ss`. */
    @ScriptFunction
    fun log(cx: Context, scope: VarScope, vararg args: Any?) {
        val timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("dd MMM HH:mm:ss", Locale.US))
        val text = formatArgs(cx, scope, args)
        console()?.log("%s - %s", timestamp, text) ?: AutoJs.instance.globalConsole.log("%s - %s", timestamp, text)
    }

    /** Compatibility alias of [checkStringArgument] that also reads objects through their `toString`. */
    @ScriptFunction
    fun checkStringParam(cx: Context, scope: VarScope, src: Any?, pattern: Any?): Boolean = checkString(cx, scope, "util.checkStringParam", src, pattern, objects = true)

    /**
     * Whether [src] (a primitive, trimmed) matches [pattern] ignoring case: a string pattern is anchored with `^` and `$`,
     * a RegExp is used by its source.
     */
    @ScriptFunction
    fun checkStringArgument(cx: Context, scope: VarScope, src: Any?, pattern: Any?): Boolean = checkString(cx, scope, "util.checkStringArgument", src, pattern, objects = false)

    @ScriptFunction
    fun assureStringStartsWith(s: Any?, start: Any?): String {
        val text = string("util.assureStringStartsWith", "s", s)
        val prefix = string("util.assureStringStartsWith", "start", start)
        return if (text.startsWith(prefix)) text else prefix + text
    }

    @ScriptFunction
    fun assureStringEndsWith(s: Any?, end: Any?): String {
        val text = string("util.assureStringEndsWith", "s", s)
        val suffix = string("util.assureStringEndsWith", "end", end)
        return if (text.endsWith(suffix)) text else text + suffix
    }

    /** [end] defaults to [start]. */
    @ScriptFunction
    fun assureStringSurroundsWith(s: Any?, start: Any?, end: Any? = null): String {
        val text = string("util.assureStringSurroundsWith", "s", s)
        val prefix = string("util.assureStringSurroundsWith", "start", start)
        val suffix = if (isNullish(end)) prefix else string("util.assureStringSurroundsWith", "end", end)
        return surround(text, prefix, suffix)
    }

    /** Fails with a TypeError unless `typeof o` is [type] (ignoring case). */
    @ScriptFunction
    fun ensureType(o: Any?, type: Any?) {
        val function = "util.ensureType"
        val name = string(function, "type", type).lowercase()
        if (name !in TYPES) throw rangeError(function, ScriptMessages.argumentType("type", TYPE_UNION, quote(type)))
        if (typeOf(o) != name) throw typeError(function, "o", name, o)
    }

    @ScriptFunction
    fun ensureStringType(vararg values: Any?) = ensureEach("util.ensureStringType", "string", values)

    @ScriptFunction
    fun ensureNumberType(vararg values: Any?) = ensureEach("util.ensureNumberType", "number", values)

    @ScriptFunction
    fun ensureUndefinedType(vararg values: Any?) = ensureEach("util.ensureUndefinedType", "undefined", values)

    @ScriptFunction
    fun ensureBooleanType(vararg values: Any?) = ensureEach("util.ensureBooleanType", "boolean", values)

    @ScriptFunction
    fun ensureSymbolType(vararg values: Any?) = ensureEach("util.ensureSymbolType", "symbol", values)

    @ScriptFunction(aliases = ["ensureBigintType"])
    fun ensureBigIntType(vararg values: Any?) = ensureEach("util.ensureBigIntType", "bigint", values)

    @ScriptFunction
    fun ensureObjectType(vararg values: Any?) = ensureEach("util.ensureObjectType", "object", values)

    @ScriptFunction
    fun ensureFunctionType(vararg values: Any?) = ensureEach("util.ensureFunctionType", "function", values)

    @ScriptFunction
    fun ensureNonNullObjectType(vararg values: Any?) {
        values.forEachIndexed { i, v ->
            if (v == null) throw typeError("util.ensureNonNullObjectType", ScriptMessages.argumentCannotBe("values[$i]", "null"))
            if (typeOf(v) != "object") throw typeError("util.ensureNonNullObjectType", "values[$i]", "object", v)
        }
    }

    @ScriptFunction
    fun ensureArrayType(vararg values: Any?) {
        values.forEachIndexed { i, v -> if (!isJsArray(v)) throw typeError("util.ensureArrayType", "values[$i]", "Array", v) }
    }

    /** [f] itself when it has an object prototype, otherwise a regular function calling it (arrow functions and the like). */
    @ScriptFunction
    fun toRegular(cx: Context, scope: VarScope, f: Any?): Any {
        if (typeOf(f) != "function" || f !is Callable) throw typeError("util.toRegular", "f", "function", f)
        if (f is Scriptable) {
            val prototype = ScriptableObject.getProperty(f, "prototype")
            if (prototype != null && prototype !== Scriptable.NOT_FOUND && typeOf(prototype) == "object") return f
        }
        return RegularFunction(f).also { regular ->
            RhinoScriptRuntime.setFunctionProtoAndParent(regular, cx, scope)
            regular.defineProperty("toString", LambdaFunction(scope, "toString", 0, SerializableCallable { c, s, thisObj, _ ->
                when (thisObj) {
                    is RegularFunction -> (thisObj.target as? Scriptable)?.let { ScriptableObject.callMethod(c, it, "toString", emptyArray()) } ?: RhinoScriptRuntime.toString(thisObj.target)
                    is Scriptable -> ScriptableObject.callMethod(c, thisObj, "toString", emptyArray())
                    else -> RhinoScriptRuntime.toString(thisObj)
                }.let { RhinoScriptRuntime.toString(it) }
            }, false), ScriptableObject.READONLY or ScriptableObject.DONTENUM or ScriptableObject.PERMANENT)
        }
    }

    /** Abandoned: always throws. */
    @ScriptFunction
    fun toRegularAndCall(vararg args: Any?) {
        throw ObsoletedRhinoFunctionException("toRegularAndCall")
    }

    /** Abandoned: always throws. */
    @ScriptFunction
    fun toRegularAndApply(vararg args: Any?) {
        throw ObsoletedRhinoFunctionException("toRegularAndApply")
    }

    @ScriptFunction
    fun dpToPx(dp: Double): Double = DisplayUtils.dpToPx(dp.toFloat()).toDouble()

    @ScriptFunction
    fun spToPx(sp: Double): Double = DisplayUtils.spToPx(sp.toFloat()).toDouble()

    @ScriptFunction
    fun pxToDp(px: Double): Double = DisplayUtils.pxToDp(px.toFloat()).toDouble()

    @ScriptFunction
    fun pxToSp(px: Double): Double = DisplayUtils.pxToSp(px.toFloat()).toDouble()

    /** A function calling its target with `this` set to null. */
    internal class RegularFunction(val target: Callable) : BaseFunction() {
        override fun call(cx: Context, nt: Any?, scope: VarScope, thisObj: Any?, args: Array<out Any?>): Any? = target.call(cx, scope, null, args)
    }

    private fun console(): ConsoleImpl? = runtime?.console as ConsoleImpl?

    private fun warn() {
        console()?.warn(NOT_FOR_AUTOJS6) ?: AutoJs.instance.globalConsole.warn(NOT_FOR_AUTOJS6)
    }

    private fun ensureEach(function: String, type: String, values: Array<out Any?>) {
        values.forEachIndexed { i, v -> if (typeOf(v) != type) throw typeError(function, "values[$i]", type, v) }
    }

    /** A JS string (concatenated strings included); other values are a TypeError. */
    private fun string(function: String, param: String, value: Any?): String {
        if (typeOf(value) != "string") throw typeError(function, param, "string", value)
        return value.toString()
    }

    private fun checkString(cx: Context, scope: VarScope, function: String, src: Any?, pattern: Any?, objects: Boolean): Boolean {
        val source = when {
            typeOf(pattern) == "string" -> surround(pattern.toString(), "^", "$")
            pattern is NativeRegExp -> RhinoScriptRuntime.toString(ScriptableObject.getProperty(pattern, "source"))
            else -> throw typeError(function, "pattern", "string | RegExp", pattern)
        }
        val text = when {
            isNullish(src) -> throw typeError(function, ScriptMessages.argumentCannotBe("src", ScriptArgs.describe(src)))
            !isReference(src) -> RhinoScriptRuntime.toString(src)
            objects -> {
                val obj = src as? Scriptable ?: RhinoScriptRuntime.toObject(cx, scope, src)
                val toString = ScriptableObject.getProperty(obj, "toString")
                if (toString !is Callable) throw typeError(function, "src.toString", "function", toString.takeUnless { it === Scriptable.NOT_FOUND })
                RhinoScriptRuntime.toString(toString.call(cx, scope, obj, emptyArray()))
            }
            else -> throw typeError(function, "src", "string | number | boolean | bigint", src)
        }
        val regExp = (TopLevel.getBuiltinCtor(cx, scope, TopLevel.Builtins.RegExp) as Function).construct(cx, scope, arrayOf(source, "i"))
        return RhinoScriptRuntime.toBoolean(ScriptableObject.callMethod(cx, regExp, "test", arrayOf(text.trim { JsoxValues.isStrWhiteSpace(it) })))
    }

    private companion object {

        const val NOT_FOR_AUTOJS6 = "This method is not designed for AutoJs6."

        val TYPES = listOf("object", "string", "undefined", "symbol", "bigint", "number", "function", "boolean")

        /** [TYPES] for the RangeError message of [ensureType], joined once instead of on each failing call (roadmap P2.4). */
        val TYPE_UNION = TYPES.joinToString(" | ") { "'$it'" }

        fun isJsArray(o: Any?) = o is Scriptable && o !== Scriptable.NOT_FOUND && o.className == "Array"

        fun surround(text: String, start: String, end: String): String {
            val started = if (text.startsWith(start)) text else start + text
            return if (started.endsWith(end)) started else started + end
        }

        fun formatArgs(cx: Context, scope: VarScope, args: Array<out Any?>): String {
            if (args.isEmpty()) return ""
            val first = args[0]
            if (typeOf(first) != "string") return args.joinToString(" ") { Inspector.of(cx, scope).format(it) }
            val fmt = first.toString()
            var index = 1
            val sb = StringBuilder(fmt.length + 16)
            var i = 0
            while (i < fmt.length) {
                val ch = fmt[i]
                if (ch != '%' || i + 1 >= fmt.length) {
                    sb.append(ch)
                    i++
                    continue
                }
                when (val spec = fmt[i + 1]) {
                    '%' -> sb.append('%')
                    's', 'd', 'j' -> if (index >= args.size) {
                        sb.append('%').append(spec)
                    } else {
                        val arg = args[index++]
                        sb.append(
                            when (spec) {
                                's' -> RhinoScriptRuntime.toString(arg)
                                'd' -> RhinoScriptRuntime.numberToString(RhinoScriptRuntime.toNumber(arg), 10)
                                else -> try {
                                    RhinoScriptRuntime.toString(NativeJSON.stringify(cx, scope, arg, null, null))
                                } catch (_: RuntimeException) {
                                    "[Circular]"
                                }
                            },
                        )
                    }
                    else -> sb.append('%').append(spec)
                }
                i += 2
            }
            while (index < args.size) {
                val x = args[index++]
                sb.append(' ')
                sb.append(if (x == null || typeOf(x) != "object") RhinoScriptRuntime.toString(x) else Inspector.of(cx, scope).format(x))
            }
            return sb.toString()
        }

    }

}
