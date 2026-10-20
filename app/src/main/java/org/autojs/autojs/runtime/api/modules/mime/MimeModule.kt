package org.autojs.autojs.runtime.api.modules.mime

import eu.medsea.mimeutil.MimeType
import org.autojs.autojs.runtime.api.modules.ScriptArgs
import org.autojs.autojs.runtime.api.modules.ScriptInvoke
import org.autojs.autojs.runtime.api.modules.ScriptMessages
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.autojs.autojs.runtime.api.modules.ScriptPrototype
import org.mozilla.javascript.Context
import org.mozilla.javascript.LambdaConstructor
import org.mozilla.javascript.LambdaFunction
import org.mozilla.javascript.NativeJSON
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.SerializableCallable
import org.mozilla.javascript.SerializableConstructable
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.VarScope
import org.mozilla.javascript.Wrapper
import org.autojs.autojs.runtime.api.Mime as ApiMime
import org.mozilla.javascript.ScriptRuntime as RhinoScriptRuntime

/**
 * The `mime` module on the new script module layer (roadmap P2.2): `mime(mimeString)` parses a MIME type string; the
 * module object's prototype is the Java object `org.autojs.autojs.runtime.api.Mime` (`ScriptRuntime.mime`), which
 * provides `fromFile`, `getMediaType`, the MIME constants and the `MimeUtil2` methods, as with the current module.
 *
 * The result is a script object again (ROADMAP appendix E): the enumerable fields `raw`, `type`, `subtype`, `mimeType`,
 * `mimeTypeRefined` and `parameters` (a plain object of strings), read-only as documented, so `JSON.stringify` and
 * `Object.keys` see the fields (the current module returned a Java object). Its prototype has `constructor` (`JsMime`)
 * and `toString` as the obsolete module had, plus the documented `toStringReadable`; `toString` gives the obsolete
 * multi-line text with the parameters formatted as `util.format` did (`parameters: { charset: 'UTF-8' }`, without the
 * extra quotes of the current module). A non-string argument is a TypeError as in the obsolete module (the current module
 * parsed `mime(123)` as the type "123" with a wildcard subtype), surplus arguments a TypeError (appendix C Q7); an invalid MIME type string fails in
 * `MimeType` with its Java exception (D10).
 *
 * zh-CN: 新脚本模块层上的 mime 模块 (P2.2): mime(mimeString) 解析 MIME 类型字符串; 模块对象的原型是 Java 对象 org.autojs.autojs.runtime.api.Mime
 * (ScriptRuntime.mime), 提供 fromFile, getMediaType, MIME 常量与 MimeUtil2 的方法, 与现有模块相同. 解析结果恢复为脚本对象 (附录 E): 可枚举字段 raw, type,
 * subtype, mimeType, mimeTypeRefined 与 parameters (字符串值的普通对象), 按文档只读, JSON.stringify 与 Object.keys 都能看到字段 (现有模块返回 Java 对象).
 * 原型有旧 JS 的 constructor (JsMime) 与 toString, 另有文档写明的 toStringReadable; toString 输出旧 JS 的多行文本, parameters 按 util.format 的格式
 * (parameters: { charset: 'UTF-8' }, 没有现有模块多加的引号). 非字符串参数与旧 JS 一样为 TypeError (现有模块把 mime(123) 解析为类型 "123" 加通配子类型), 多余参数为 TypeError
 * (附录 C Q7); 无效的 MIME 类型字符串由 MimeType 以其 Java 异常失败 (D10).
 */
@ScriptModule(name = "mime")
class MimeModule {

    @ScriptPrototype
    val prototype: Any
        get() = ApiMime

    /** The `JsMime` prototype of this runtime's results, built on the first call. */
    @Volatile
    private var jsMimePrototype: ScriptableObject? = null

    @ScriptInvoke
    fun invoke(cx: Context, scope: VarScope, mimeString: Any?): Scriptable = parse(cx, scope, mimeString)

    private fun parse(cx: Context, scope: VarScope, mimeString: Any?): Scriptable {
        val text = when (val value = if (mimeString is Wrapper) mimeString.unwrap() else mimeString) {
            is CharSequence -> value.toString()
            else -> throw RhinoScriptRuntime.typeError(ScriptMessages.failedToCall(FUNCTION, ScriptMessages.argumentType("mimeString", "string", ScriptArgs.describe(mimeString))))
        }
        val mime = MimeType(text)
        val type: String = mime.type
        val subtype: String = mime.subtype
        val refined = "$type/$subtype"
        val parameters = cx.newObject(scope) as ScriptableObject
        val entries = text.split(PARAMETER_SEPARATOR).drop(1).filter { it.isNotEmpty() }
        entries.forEach { entry ->
            if ('=' in entry) {
                val (key, value) = entry.split(KEY_VALUE_SEPARATOR)
                val index = key.toIntOrNull()?.takeIf { it >= 0 && it.toString() == key }
                if (index != null) parameters.put(index, parameters, value) else parameters.put(key, parameters, value)
            }
        }
        val result = cx.newObject(scope) as ScriptableObject
        result.prototype = prototypeOf(cx, scope)
        val full = if (entries.isEmpty()) refined else "$refined; ${entries.joinToString("; ")}"
        listOf("raw" to text, "type" to type, "subtype" to subtype, "mimeType" to full, "mimeTypeRefined" to refined, "parameters" to parameters).forEach { (name, value) ->
            result.defineProperty(name, value, ScriptableObject.READONLY)
        }
        return result
    }

    private fun prototypeOf(cx: Context, scope: VarScope): ScriptableObject = jsMimePrototype ?: synchronized(this) {
        jsMimePrototype ?: (cx.newObject(scope) as ScriptableObject).also { proto ->
            val create = SerializableCallable { c, s, _, args -> parse(c, s, args.firstOrNull() ?: Undefined.instance) }
            val constructor = LambdaConstructor(scope, "JsMime", 1, create, SerializableConstructable { c, _, s, args -> parse(c, s, args.firstOrNull() ?: Undefined.instance) })
            ScriptableObject.putProperty(constructor, "prototype", proto)
            val toString = SerializableCallable { c, s, thisObj, _ -> describe(c, s, thisObj) }
            proto.defineProperty("constructor", constructor, ScriptableObject.EMPTY)
            proto.defineProperty("toString", LambdaFunction(scope, "toString", 0, toString, false), ScriptableObject.EMPTY)
            proto.defineProperty("toStringReadable", LambdaFunction(scope, "toStringReadable", 0, toString, false), ScriptableObject.DONTENUM)
            jsMimePrototype = proto
        }
    }

    /** The multi-line text of the obsolete `JsMime#toString`, from the fields of [thisObj] as they are now. */
    private fun describe(cx: Context, scope: VarScope, thisObj: Any?): String {
        fun field(name: String): Any? = (thisObj as? Scriptable)?.let { ScriptableObject.getProperty(it, name) }.let { if (it == null || it === Scriptable.NOT_FOUND) Undefined.instance else it }
        fun text(name: String): String = RhinoScriptRuntime.toString(field(name))
        return "JsMime {\n" +
                "  type: '${text("type")}',\n" +
                "  subtype: '${text("subtype")}',\n" +
                "  mimeType: '${text("mimeType")}',\n" +
                "  mimeTypeRefined: '${text("mimeTypeRefined")}',\n" +
                "  parameters: ${inspect(cx, scope, field("parameters"))},\n" +
                "  raw: '${text("raw")}',\n" +
                "}"
    }

    /**
     * The obsolete `util.format(parameters)` for a flat object: `{}`, `{ name: 'value', 'other-name': 'value' }`, or one
     * entry per line when the entries are longer than 60 characters (the rule of the obsolete util.inspect).
     */
    private fun inspect(cx: Context, scope: VarScope, value: Any?): String {
        if (value !is ScriptableObject || value is Wrapper) return primitive(cx, scope, value)
        val entries = value.ids.map { id ->
            val key = id.toString()
            val element = if (id is Int) value.get(id, value) else value.get(key, value)
            val quotedKey = json(cx, scope, key)
            val name = if (IDENTIFIER_KEY.matches(quotedKey)) quotedKey.substring(1, quotedKey.length - 1) else quotedKey.replace("'", "\\'").replace("\\\"", "\"").replace(QUOTE_AT_ENDS, "'")
            "$name: ${primitive(cx, scope, element)}"
        }
        if (entries.isEmpty()) return "{}"
        val length = entries.sumOf { it.length + 1 }
        return if (length > 60) "{ ${entries.joinToString(",\n  ")} }" else "{ ${entries.joinToString(", ")} }"
    }

    private fun primitive(cx: Context, scope: VarScope, value: Any?): String = when {
        value is CharSequence -> "'" + json(cx, scope, value.toString()).removeSurrounding("\"").replace("'", "\\'").replace("\\\"", "\"") + "'"
        value == null -> "null"
        value === Scriptable.NOT_FOUND -> "undefined"
        else -> RhinoScriptRuntime.toString(value)
    }

    private fun json(cx: Context, scope: VarScope, text: String): String = NativeJSON.stringify(cx, scope, text, null, null).toString()

    private companion object {
        const val FUNCTION = "mime"
        val PARAMETER_SEPARATOR = Regex("\\s*;\\s*")
        val KEY_VALUE_SEPARATOR = Regex("\\s*=\\s*")
        val IDENTIFIER_KEY = Regex("^\"([a-zA-Z_]\\w*)\"$")
        val QUOTE_AT_ENDS = Regex("(^\"|\"$)")
    }

}
