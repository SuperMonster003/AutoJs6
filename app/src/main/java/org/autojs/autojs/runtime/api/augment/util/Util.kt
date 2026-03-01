package org.autojs.autojs.runtime.api.augment.util

import org.autojs.autojs.AutoJs
import org.autojs.autojs.annotation.RhinoFunctionBody
import org.autojs.autojs.annotation.RhinoSingletonFunctionInterface
import org.autojs.autojs.annotation.RhinoStandardFunctionInterface
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsArray
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsBigInt
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsBoolean
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsDate
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsError
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsFunction
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsNonNullObject
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsNumber
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsObject
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsRegExp
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsString
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsSymbol
import org.autojs.autojs.rhino.extension.AnyExtensions.isJsUndefined
import org.autojs.autojs.rhino.extension.AnyExtensions.jsBrief
import org.autojs.autojs.rhino.ArgumentGuards
import org.autojs.autojs.rhino.ArgumentGuards.Companion.component1
import org.autojs.autojs.rhino.ArgumentGuards.Companion.component2
import org.autojs.autojs.rhino.ArgumentGuards.Companion.component3
import org.autojs.autojs.rhino.extension.NumberExtensions.jsString
import org.autojs.autojs.rhino.extension.ScriptableExtensions.defineProp
import org.autojs.autojs.rhino.extension.ScriptableExtensions.prop
import org.autojs.autojs.runtime.api.augment.Augmentable
import org.autojs.autojs.runtime.api.augment.global.Species
import org.autojs.autojs.runtime.api.augment.util.Inspect.inspectRhino
import org.autojs.autojs.runtime.exception.WrappedIllegalArgumentException
import org.autojs.autojs.util.DisplayUtils
import org.autojs.autojs.util.RhinoUtils
import org.autojs.autojs.util.RhinoUtils.ObsoletedRhinoFunctionException
import org.autojs.autojs.util.RhinoUtils.callToStringFunction
import org.autojs.autojs.util.RhinoUtils.coerceFloatNumber
import org.autojs.autojs.util.RhinoUtils.coerceNumber
import org.autojs.autojs.util.RhinoUtils.coerceString
import org.autojs.autojs.util.RhinoUtils.js_function_bind
import org.autojs.autojs.util.RhinoUtils.js_json_stringify
import org.autojs.autojs.util.RhinoUtils.js_object_create
import org.autojs.autojs.util.RhinoUtils.js_object_setPrototypeOf
import org.autojs.autojs.util.RhinoUtils.js_typeof
import org.autojs.autojs.util.RhinoUtils.newNativeObject
import org.autojs.autojs.util.RhinoUtils.withRhinoContext
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Context
import org.mozilla.javascript.Function
import org.mozilla.javascript.ImporterTopLevel
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.ScriptableObject.DONTENUM
import org.mozilla.javascript.ScriptableObject.PERMANENT
import org.mozilla.javascript.ScriptableObject.READONLY
import java.time.LocalDateTime.now
import java.time.format.DateTimeFormatter.ofPattern
import java.util.*
import org.mozilla.javascript.ScriptRuntime as RhinoScriptRuntime

/**
 * Created by SuperMonster003 on Jun 3, 2024.
 */
@Suppress("unused", "FunctionName", "UNUSED_PARAMETER")
object Util : Augmentable() {

    override val selfAssignmentFunctions = listOf(
        ::__assignFunctions__.name to (READONLY or DONTENUM or PERMANENT),
        ::isArray.name,
        ::isBoolean.name,
        ::isNull.name,
        ::isNullOrUndefined.name,
        ::isNumber.name,
        ::isString.name,
        ::isSymbol.name,
        ::isUndefined.name,
        ::isRegExp.name,
        ::isObject.name,
        ::isDate.name,
        ::isError.name,
        ::isFunction.name,
        ::isBigInt.name,
        ::isJavaObject.name,
        ::isJavaArray.name,
        ::isInteger.name,
        ::isPrimitive.name,
        ::isReference.name,
        ::isEmptyObject.name,
        ::unwrapJavaObject.name,
        ::extend.name,
        ::format.name,
        ::deprecate.name,
        ::debuglog.name,
        ::log.name,
        ::checkStringArgument.name,
        ::assureStringStartsWith.name,
        ::assureStringEndsWith.name,
        ::assureStringSurroundsWith.name,
        ::ensureType.name,
        ::ensureStringType.name,
        ::ensureNumberType.name,
        ::ensureUndefinedType.name,
        ::ensureBooleanType.name,
        ::ensureSymbolType.name,
        ::ensureBigIntType.name,
        ::ensureObjectType.name,
        ::ensureFunctionType.name,
        ::ensureNonNullObjectType.name,
        ::ensureArrayType.name,
        ::toRegular.name,
        ::toRegularAndCall.name,
        ::toRegularAndApply.name,
        ::dpToPx.name,
        ::spToPx.name,
        ::pxToDp.name,
        ::pxToSp.name,
    )

    private val availableTypes = listOf(
        "object", "string", "undefined", "symbol",
        "bigint", "number", "function", "boolean",
    )

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun isArray(args: Array<out Any?>): Boolean = ensureArgumentsOnlyOne(args) {
        it.isJsArray()
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun isBoolean(args: Array<out Any?>): Boolean = ensureArgumentsOnlyOne(args) {
        it.isJsBoolean()
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun isNull(args: Array<out Any?>): Boolean = ensureArgumentsOnlyOne(args) {
        it == null
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun isNullOrUndefined(args: Array<out Any?>): Boolean = ensureArgumentsOnlyOne(args) {
        it.isJsNullish()
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun isNumber(args: Array<out Any?>): Boolean = ensureArgumentsOnlyOne(args) {
        it.isJsNumber()
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun isString(args: Array<out Any?>): Boolean = ensureArgumentsOnlyOne(args) {
        it.isJsString()
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun isSymbol(args: Array<out Any?>): Boolean = ensureArgumentsOnlyOne(args) {
        it.isJsSymbol()
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun isUndefined(args: Array<out Any?>): Boolean = ensureArgumentsOnlyOne(args) {
        it.isJsUndefined()
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun isRegExp(args: Array<out Any?>): Boolean = ensureArgumentsOnlyOne(args) {
        it.isJsRegExp()
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun isObject(args: Array<out Any?>): Boolean = ensureArgumentsOnlyOne(args) {
        it.isJsObject()
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun isDate(args: Array<out Any?>): Boolean = ensureArgumentsOnlyOne(args) {
        it.isJsDate()
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun isError(args: Array<out Any?>): Boolean = ensureArgumentsOnlyOne(args) {
        it.isJsError()
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun isFunction(args: Array<out Any?>): Boolean = ensureArgumentsOnlyOne(args) {
        it.isJsFunction()
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun isBigInt(args: Array<out Any?>): Boolean = ensureArgumentsOnlyOne(args) {
        it.isJsBigInt()
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun isJavaObject(args: Array<out Any?>): Boolean {
        return Species.isJavaObject(args)
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun isJavaArray(args: Array<out Any?>): Boolean = ensureArgumentsOnlyOne(args) {
        it is Scriptable && it.className == "JavaObject" && it.javaClass.isArray
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun isInteger(args: Array<out Any?>): Boolean = ensureArgumentsOnlyOne(args) {
        RhinoUtils.isInteger(it)
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun isPrimitive(args: Array<out Any?>): Boolean = ensureArgumentsOnlyOne(args) {
        !isReferenceRhino(it)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun isPrimitiveRhino(it: Any?) = !isReferenceRhino(it)

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun isReference(args: Array<out Any?>): Boolean = ensureArgumentsOnlyOne(args) {
        isReferenceRhino(it)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun isReferenceRhino(it: Any?) = it.isJsNonNullObject() || it.isJsFunction()

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun isEmptyObject(args: Array<out Any?>): Boolean = ensureArgumentsOnlyOne(args) {
        it.isJsObject() && it is ScriptableObject && it.isEmpty
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun unwrapJavaObject(args: Array<out Any?>) = ensureArgumentsOnlyOne(args) {
        unwrap(it)
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun extend(args: Array<out Any?>) = ensureArgumentsLength(args, 2) {
        val (d, b) = it
        extendRhino(d, b)
    }

    /**
     * @param d - Child "class"
     * @param b - Parent "class"
     */
    @JvmStatic
    @RhinoFunctionBody
    fun extendRhino(d: Any?, b: Any?) {
        require(d is ScriptableObject) { "Argument d ${d.jsBrief()} must be a ScriptableObject for util.extend" }

        val niceB: Scriptable? = when (b) {
            null -> null
            is Scriptable -> b
            else -> RhinoScriptRuntime.toObject(d, b) as Scriptable
        }
        js_object_setPrototypeOf(d, niceB)

        val bPrototype: Scriptable? = when {
            niceB == null -> js_object_create(null)
            else -> withRhinoContext { cx ->
                val tmp = object : BaseFunction() {
                    override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable?, args: Array<out Any?>) = newNativeObject().also {
                        it.defineProperty("constructor", d, READONLY or DONTENUM or PERMANENT)
                    }
                }
                tmp.construct(cx, ImporterTopLevel(cx), arrayOf()).also { instance ->
                    // FIXME by SuperMonster003 on Jul 13, 2024.
                    //  ! I'm not sure if there is a better way
                    //  ! to implement JavaScript snippet `tmp.prototype = b.prototype;`,
                    //  ! as prototype shouldn't be set after constructing an instance.
                    //  ! zh-CN:
                    //  ! 我不确定是否有更好的方法实现 JavaScript 片段 `tmp.prototype = b.prototype;`,
                    //  ! 因为原型设置不应该出现在实例构造之后.
                    instance.prototype = niceB.prop("prototype") as? Scriptable
                }
            }
        }
        val dPrototype = d.prop("prototype")
        when {
            dPrototype.isJsNullish() -> {
                d.defineProp("prototype", bPrototype)
            }
            dPrototype is Scriptable -> {
                dPrototype.prototype = bPrototype
            }
            else -> throw WrappedIllegalArgumentException("Invalid prototype: ${dPrototype.jsBrief()}")
        }
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun __assignFunctions__(args: Array<out Any?>) = ensureArgumentsLength(args, 3) {
        val (src, target, funcNames) = it
        __assignFunctions__Rhino(src, target, funcNames)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun __assignFunctions__Rhino(src: Any?, target: Any?, funcNames: Any?) {
        if (src !is ScriptableObject) throw WrappedIllegalArgumentException("Argument \"src\" ${src.jsBrief()} for util.__assignFunctions__ must be a ScriptableObject")
        if (target !is ScriptableObject) throw WrappedIllegalArgumentException("Argument \"target\" ${target.jsBrief()} for util.__assignFunctions__ must be a ScriptableObject")
        if (funcNames !is NativeArray) throw WrappedIllegalArgumentException("Argument \"funcNames\" ${funcNames.jsBrief()} for util.__assignFunctions__ must be a NativeArray")

        funcNames.forEach { funcName ->
            val name = Context.toString(funcName)
            val func = src.prop(name)
            if (func.isJsNullish()) {
                throw WrappedIllegalArgumentException("Function \"$name\" doesn't exist on source object $src")
            }
            if (func !is BaseFunction) {
                throw WrappedIllegalArgumentException("Name \"$name\" is not a function type on source object $src")
            }
            target.put(name, target, js_function_bind(null, func, src))
        }
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun format(args: Array<out Any?>): String {
        return formatRhino(*args)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun formatRhino(vararg args: Any?): String {
        if (args.isEmpty()) return ""

        val first = args[0]
        if (!first.isJsString()) {
            return args.joinToString(" ") { inspectRhino(it) }
        }

        val argsSize = args.size
        var index = 1
        val fmt = coerceString(first)
        val len = fmt.length
        // Estimate some margin to reduce expansion.
        // zh-CN: 预估一些余量, 减少扩容.
        val sb = StringBuilder(len + 16)

        var i = 0
        while (i < len) {
            val ch = fmt[i]
            if (ch != '%') {
                sb.append(ch)
                i++
                continue
            }

            // '%' at last char -> append '%'.
            if (i + 1 >= len) {
                sb.append('%')
                i++
                continue
            }

            when (val spec = fmt[i + 1]) {
                '%' -> {
                    sb.append('%')
                    i += 2
                }
                's' -> {
                    if (index >= argsSize) {
                        // Keep the placeholder as is when no corresponding parameter exists.
                        // zh-CN: 没有对应参数, 保持占位符原样.
                        sb.append('%').append('s')
                    } else {
                        sb.append(Context.toString(args[index++]))
                    }
                    i += 2
                }
                'd' -> {
                    if (index >= argsSize) {
                        sb.append('%').append('d')
                    } else {
                        sb.append(Context.toNumber(args[index++]).jsString)
                    }
                    i += 2
                }
                'j' -> {
                    if (index >= argsSize) {
                        sb.append('%').append('j')
                    } else {
                        try {
                            sb.append(Context.toString(js_json_stringify(args[index++])))
                        } catch (_: Throwable) {
                            sb.append("[Circular]")
                        }
                    }
                    i += 2
                }
                else -> {
                    // Keep unknown placeholders as is.
                    // zh-CN: 未知占位符, 按原样输出.
                    sb.append('%').append(spec)
                    i += 2
                }
            }
        }

        // Do NOT inspect the formatted string, otherwise control characters like '\n' will be escaped.
        // zh-CN: 不要对格式化结果做 inspect, 否则诸如 '\n' 的控制字符会被转义.
        val formatted = sb.toString()

        return when {
            index >= argsSize -> formatted
            else -> {
                // Append remaining parameters, separated by spaces.
                // zh-CN: 追加剩余参数, 以空格分隔.
                val out = StringBuilder(formatted.length + 1 + (argsSize - index) * 8)
                out.append(formatted)

                while (index < argsSize) {
                    val x = args[index++]
                    out.append(' ')
                    if (x == null || !x.isJsObject()) {
                        out.append(Context.toString(x))
                    } else {
                        out.append(inspectRhino(x))
                    }
                }

                out.toString()
            }
        }
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun deprecate(args: Array<out Any?>) {
        deprecateRhino()
    }

    /**
     * Mark that a method should not be used.
     * Returns a modified function which warns once by default.
     * If --no-deprecation is set, then it is a no-op.
     *
     * @Overwrite by SuperMonster003 on Apr 21, 2022.
     * This method is designed for Node.js and not suitable for AutoJs6.
     * zh-CN: 此方法仅适用于 Node.js 而不适用于 AutoJs6.
     *
     * @example Code before overwrite
     * function deprecate(fn, msg) {
     *     if (isUndefined(global.process)) {
     *         return function () {
     *             return me.deprecate(fn, msg).apply(this, arguments);
     *         };
     *     }
     *     /* ... */
     * }
     */
    @JvmStatic
    @RhinoFunctionBody
    fun deprecateRhino() {
        AutoJs.instance.globalConsole.warn("This method is not designed for AutoJs6.")
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun debuglog(args: Array<out Any?>) {
        debuglogRhino()
    }

    /**
     * @Overwrite by SuperMonster003 on Apr 21, 2022.
     * This method is designed for Node.js and not suitable for AutoJs6.
     * zh-CN: 此方法仅适用于 Node.js 而不适用于 AutoJs6.
     *
     * @example Code before overwrite
     * function debuglog(set) {
     *     if (isUndefined(debugEnviron))
     *         debugEnviron = process.env.NODE_DEBUG || '';
     *     set = set.toUpperCase();
     *     if (!debugs[set]) {
     *         if (new RegExp('\\b' + set + '\\b', 'i').test(debugEnviron)) {
     *             let pid = process.pid;
     *             debugs[set] = function () {
     *                 var msg = me.format.apply(me, arguments);
     *                 console.error('%s %d: %s', set, pid, msg);
     *             };
     *         } else {
     *             debugs[set] = function () {
     *             };
     *         }
     *     }
     *     return debugs[set];
     * }
     */
    @JvmStatic
    @RhinoFunctionBody
    fun debuglogRhino() {
        AutoJs.instance.globalConsole.warn("This method is not designed for AutoJs6.")
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun log(args: Array<out Any?>) {
        logRhino(*args)
    }

    /**
     * Method log is just a thin wrapper to console.log that prepends a timestamp
     */
    @JvmStatic
    @RhinoFunctionBody
    fun logRhino(vararg arguments: Any?) {
        val timestamp = now().format(ofPattern("dd MMM HH:mm:ss", Locale.US))
        AutoJs.instance.globalConsole.log("%s - %s", timestamp, formatRhino(*arguments))
    }

    @JvmStatic
    @Deprecated("Deprecated in Java", ReplaceWith("checkStringArgument(args)"))
    @RhinoSingletonFunctionInterface
    fun checkStringParam(args: Array<out Any?>): Boolean = checkStringArgument(args)

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun checkStringArgument(args: Array<out Any?>): Boolean = ensureArgumentsLength(args, 2) {
        val (src, pattern) = it
        checkStringArgumentRhino(src, pattern)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun checkStringArgumentRhino(src: Any?, pattern: Any?): Boolean {
        val nicePattern = when (pattern) {
            is String -> assureStringSurroundsWithRhino(pattern, "^", "$")
            is Regex -> pattern.pattern
            else -> throw Error("Unknown pattern $pattern for util.checkStringArgument")
        }
        val niceSrc = when {
            src.isJsNullish() -> throw WrappedIllegalArgumentException("Argument \"src\" ${src.jsBrief()} for util.checkStringArgument must be non-nullish")
            isPrimitiveRhino(src) -> Context.toString(src)
            else -> throw Error("Param src must be non-nullish")
        }
        return nicePattern.toRegex(RegexOption.IGNORE_CASE).matches(niceSrc.trim())
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun assureStringStartsWith(args: Array<out Any?>): String = ensureArgumentsLength(args, 2) {
        val (s, start) = it
        assureStringStartsWithRhino(s, start)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun assureStringStartsWithRhino(s: Any?, start: Any?): String {
        if (s !is String) throw WrappedIllegalArgumentException("Argument \"s\" ${s.jsBrief()} for util.assureStringStartsWith must be a string")
        if (start !is String) throw WrappedIllegalArgumentException("Argument \"start\" ${start.jsBrief()} for util.assureStringStartsWith must be a string")
        return if (s.startsWith(start)) s else start + s
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun assureStringEndsWith(args: Array<out Any?>): String = ensureArgumentsLength(args, 2) {
        val (s, end) = it
        assureStringEndsWithRhino(s, end)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun assureStringEndsWithRhino(s: Any?, end: Any?): String {
        if (s !is String) throw WrappedIllegalArgumentException("Argument \"s\" ${s.jsBrief()} for util.assureStringEndsWith must be a string")
        if (end !is String) throw WrappedIllegalArgumentException("Argument \"end\" ${end.jsBrief()} for util.assureStringEndsWith must be a string")
        return if (s.endsWith(end)) s else s + end
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun assureStringSurroundsWith(args: Array<out Any?>) = ensureArgumentsLengthInRange(args, 2..3) {
        val (s, start, end) = it
        assureStringSurroundsWithRhino(s, start, end)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun assureStringSurroundsWithRhino(s: Any?, start: Any?, end: Any?): String {
        return assureStringEndsWithRhino(assureStringStartsWithRhino(s, start), if (end.isJsNullish()) start else end)
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun ensureType(args: Array<out Any?>) = ensureArgumentsLength(args, 2) {
        val (o, type) = it
        ensureTypeRhino(o, type)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun ensureTypeRhino(o: Any?, type: Any?) {
        if (type !is String) throw WrappedIllegalArgumentException("Argument \"type\" must be a string")
        val niceType = type.lowercase()
        if (niceType !in availableTypes) throw WrappedIllegalArgumentException(
            "Argument \"type\" must be one of these types: ${
                availableTypes.joinToString(", ") { "\"$it\"" }
            }")
        if (js_typeof(o) != niceType) {
            throw WrappedIllegalArgumentException("Argument must be type of $type instead of ${o.jsBrief()}")
        }
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun ensureStringType(args: Array<out Any?>) {
        ensureStringTypeRhino(*args)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun ensureStringTypeRhino(vararg args: Any?) {
        args.forEach { ensureTypeRhino(it, "string") }
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun ensureNumberType(args: Array<out Any?>) {
        ensureNumberTypeRhino(*args)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun ensureNumberTypeRhino(vararg args: Any?) {
        args.forEach { ensureTypeRhino(it, "number") }
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun ensureUndefinedType(args: Array<out Any?>) {
        ensureUndefinedTypeRhino(*args)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun ensureUndefinedTypeRhino(vararg args: Any?) {
        args.forEach { ensureTypeRhino(it, "undefined") }
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun ensureBooleanType(args: Array<out Any?>) {
        ensureBooleanTypeRhino(*args)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun ensureBooleanTypeRhino(vararg args: Any?) {
        args.forEach { ensureTypeRhino(it, "boolean") }
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun ensureSymbolType(args: Array<out Any?>) {
        ensureSymbolTypeRhino(*args)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun ensureSymbolTypeRhino(vararg args: Any?) {
        args.forEach { ensureTypeRhino(it, "symbol") }
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun ensureBigIntType(args: Array<out Any?>) {
        ensureBigIntTypeRhino(*args)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun ensureBigIntTypeRhino(vararg args: Any?) {
        args.forEach { ensureTypeRhino(it, "bigint") }
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun ensureObjectType(args: Array<out Any?>) {
        ensureObjectTypeRhino(*args)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun ensureObjectTypeRhino(vararg args: Any?) {
        args.forEach { ensureTypeRhino(it, "object") }
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun ensureFunctionType(args: Array<out Any?>) {
        ensureFunctionTypeRhino(*args)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun ensureFunctionTypeRhino(vararg args: Any?) {
        args.forEach { ensureTypeRhino(it, "function") }
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun ensureNonNullObjectType(args: Array<out Any?>) {
        ensureNonNullObjectTypeRhino(*args)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun ensureNonNullObjectTypeRhino(vararg args: Any?) {
        args.forEach { ensureTypeRhino(it ?: throw WrappedIllegalArgumentException("Argument must be non-null"), "object") }
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun ensureArrayType(args: Array<out Any?>) {
        ensureArrayTypeRhino(*args)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun ensureArrayTypeRhino(vararg args: Any?) {
        args.forEach {
            if (it == null || !it.isJsArray()) {
                throw WrappedIllegalArgumentException("Argument must be a JavaScript Array instead of ${it.jsBrief()}")
            }
        }
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun toRegular(args: Array<out Any?>) = ensureArgumentsOnlyOne(args) {
        toRegularRhino(it)
    }

    @JvmStatic
    @RhinoFunctionBody
    fun toRegularRhino(f: Any?): BaseFunction {
        val func = f.also { ensureFunctionTypeRhino(it) } as BaseFunction
        return when {
            func.prop("prototype").isJsObject() -> {
                func
            }
            else -> RegularFunction(func).also {
                it.defineFunctionProperties(
                    arrayOf("toString"),
                    RegularFunction::class.java,
                    READONLY or DONTENUM or PERMANENT,
                )
            }
        }
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun toRegularAndCall(args: Array<out Any?>) {
        throw ObsoletedRhinoFunctionException(::toRegularAndCall.name)
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun toRegularAndApply(args: Array<out Any?>) {
        throw ObsoletedRhinoFunctionException(::toRegularAndCall.name)
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun dpToPx(args: Array<out Any?>): Double = ensureArgumentsOnlyOne(args) {
        coerceNumber(DisplayUtils.dpToPx(coerceFloatNumber(it)))
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun spToPx(args: Array<out Any?>): Double = ensureArgumentsOnlyOne(args) {
        coerceNumber(DisplayUtils.spToPx(coerceFloatNumber(it)))
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun pxToDp(args: Array<out Any?>): Double = ensureArgumentsOnlyOne(args) {
        coerceNumber(DisplayUtils.pxToDp(coerceFloatNumber(it)))
    }

    @JvmStatic
    @RhinoSingletonFunctionInterface
    fun pxToSp(args: Array<out Any?>): Double = ensureArgumentsOnlyOne(args) {
        coerceNumber(DisplayUtils.pxToSp(coerceFloatNumber(it)))
    }

    internal class RegularFunction(private val func: BaseFunction) : BaseFunction() {

        override fun call(cx: Context, scope: Scriptable, thisObj: Scriptable?, args: Array<out Any?>): Any {
            return func.call(cx, scope, null, args)
        }

        companion object : ArgumentGuards() {
            @JvmStatic
            @RhinoStandardFunctionInterface
            fun toString(cx: Context, thisObj: Scriptable, args: Array<Any?>, funObj: Function): String = ensureArgumentsIsEmpty(args) {
                when (thisObj) {
                    is RegularFunction -> callToStringFunction(thisObj.func)
                    else -> callToStringFunction(thisObj)
                }
            }
        }
    }

}
