package org.autojs.autojs.runtime.api.modules.global

import org.autojs.autojs.runtime.api.modules.ScriptFunction
import org.autojs.autojs.runtime.api.modules.ScriptGlobal
import org.autojs.autojs.runtime.api.modules.ScriptInvoke
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.Symbol
import org.mozilla.javascript.Undefined
import java.math.BigInteger

/**
 * The `species` module on the new script module layer (roadmap P2.2): `species(o)` is the species name of a script value
 * ("Null", "Undefined", "Boolean", "String", "Number", "BigInt", or the class name of a script object such as "Array",
 * "Object", "JavaObject", "JavaClass"), and `species.isXxx(o)` compares it; `isJavaClass`, `isJavaPackage` and `isObject`
 * are also bound on the global object, as with the current module (`isObject` replaces the legacy global function of the
 * same name and keeps its position). Each function takes exactly one argument (surplus arguments are a TypeError,
 * appendix C Q7). The value is classified as Rhino passes it, without entering a Context (T6; the current module
 * converted it with `Context.javaToJS` in a fallback Context); a string built at run time (ConsString) is a "String" and
 * a symbol a "Symbol", as the documented `Object.prototype.toString` rule gives (the current module wrapped both as Java
 * objects, "JavaObject").
 *
 * zh-CN: 新脚本模块层上的 species 模块 (P2.2): species(o) 返回脚本值的种类名 (Null, Undefined, Boolean, String, Number, BigInt, 或脚本对象的类名,
 * 如 Array, Object, JavaObject, JavaClass), species.isXxx(o) 与之比较; isJavaClass, isJavaPackage 与 isObject 同时挂到全局对象上, 与现有模块相同
 * (isObject 替换 legacy 中的同名全局函数并保留其位置). 每个函数恰好接受一个参数 (多余参数为 TypeError, 附录 C Q7). 按 Rhino 传入的值直接分类, 不进入 Context
 * (T6; 现有模块在兜底 Context 中经 Context.javaToJS 转换); 运行时拼接的字符串 (ConsString) 为 String, symbol 为 Symbol, 与文档写明的
 * Object.prototype.toString 规则一致 (现有模块把两者都包装为 Java 对象, 得到 JavaObject).
 */
@ScriptModule(name = "species")
object SpeciesModule {

    @ScriptInvoke
    fun invoke(o: Any?): String = speciesOf(o)

    @ScriptFunction
    fun isArray(o: Any?): Boolean = speciesOf(o) == "Array"

    @ScriptFunction
    fun isArrayBuffer(o: Any?): Boolean = speciesOf(o) == "ArrayBuffer"

    @ScriptFunction
    fun isBigInt(o: Any?): Boolean = speciesOf(o) == "BigInt"

    @ScriptFunction
    fun isBoolean(o: Any?): Boolean = speciesOf(o) == "Boolean"

    @ScriptFunction
    fun isContinuation(o: Any?): Boolean = speciesOf(o) == "Continuation"

    @ScriptFunction
    fun isDataView(o: Any?): Boolean = speciesOf(o) == "DataView"

    @ScriptFunction
    fun isDate(o: Any?): Boolean = speciesOf(o) == "Date"

    @ScriptFunction
    fun isError(o: Any?): Boolean = speciesOf(o) == "Error"

    @ScriptFunction
    fun isFloat32Array(o: Any?): Boolean = speciesOf(o) == "Float32Array"

    @ScriptFunction
    fun isFloat64Array(o: Any?): Boolean = speciesOf(o) == "Float64Array"

    @ScriptFunction
    fun isFunction(o: Any?): Boolean = speciesOf(o) == "Function"

    @ScriptFunction
    fun isHTMLDocument(o: Any?): Boolean = speciesOf(o) == "HTMLDocument"

    @ScriptFunction
    fun isInt16Array(o: Any?): Boolean = speciesOf(o) == "Int16Array"

    @ScriptFunction
    fun isInt32Array(o: Any?): Boolean = speciesOf(o) == "Int32Array"

    @ScriptFunction
    fun isInt8Array(o: Any?): Boolean = speciesOf(o) == "Int8Array"

    @ScriptFunction
    fun isJavaObject(o: Any?): Boolean = speciesOf(o) == "JavaObject"

    @ScriptFunction(global = ScriptGlobal.ALSO)
    fun isJavaClass(o: Any?): Boolean = speciesOf(o) == "JavaClass"

    @ScriptFunction(global = ScriptGlobal.ALSO)
    fun isJavaPackage(o: Any?): Boolean = speciesOf(o) == "JavaPackage"

    @ScriptFunction
    fun isMap(o: Any?): Boolean = speciesOf(o) == "Map"

    @ScriptFunction
    fun isNamespace(o: Any?): Boolean = speciesOf(o) == "Namespace"

    @ScriptFunction
    fun isNull(o: Any?): Boolean = speciesOf(o) == "Null"

    @ScriptFunction
    fun isNumber(o: Any?): Boolean = speciesOf(o) == "Number"

    @ScriptFunction(global = ScriptGlobal.ALSO)
    fun isObject(o: Any?): Boolean = speciesOf(o) == "Object"

    @ScriptFunction
    fun isQName(o: Any?): Boolean = speciesOf(o) == "QName"

    @ScriptFunction
    fun isRegExp(o: Any?): Boolean = speciesOf(o) == "RegExp"

    @ScriptFunction
    fun isSet(o: Any?): Boolean = speciesOf(o) == "Set"

    @ScriptFunction
    fun isString(o: Any?): Boolean = speciesOf(o) == "String"

    @ScriptFunction
    fun isUint16Array(o: Any?): Boolean = speciesOf(o) == "Uint16Array"

    @ScriptFunction
    fun isUint32Array(o: Any?): Boolean = speciesOf(o) == "Uint32Array"

    @ScriptFunction
    fun isUint8Array(o: Any?): Boolean = speciesOf(o) == "Uint8Array"

    @ScriptFunction
    fun isUint8ClampedArray(o: Any?): Boolean = speciesOf(o) == "Uint8ClampedArray"

    @ScriptFunction
    fun isUndefined(o: Any?): Boolean = speciesOf(o) == "Undefined"

    @ScriptFunction
    fun isWeakMap(o: Any?): Boolean = speciesOf(o) == "WeakMap"

    @ScriptFunction
    fun isWeakSet(o: Any?): Boolean = speciesOf(o) == "WeakSet"

    @ScriptFunction
    fun isWindow(o: Any?): Boolean = speciesOf(o) == "Window"

    @ScriptFunction
    fun isXML(o: Any?): Boolean = speciesOf(o) == "XML"

    @ScriptFunction
    fun isXMLList(o: Any?): Boolean = speciesOf(o) == "XMLList"

    /** The species name of [o]. */
    @JvmStatic
    fun speciesOf(o: Any?): String = when (o) {
        null -> "Null"
        Undefined.instance, Undefined.SCRIPTABLE_UNDEFINED -> "Undefined"
        is Boolean -> "Boolean"
        is CharSequence -> "String"
        is BigInteger -> "BigInt"
        is Number -> "Number"
        Scriptable.NOT_FOUND -> "Undefined"
        is Symbol -> "Symbol"
        is Scriptable -> o.className
        else -> "JavaObject"
    }

}
