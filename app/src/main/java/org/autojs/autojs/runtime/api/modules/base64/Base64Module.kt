package org.autojs.autojs.runtime.api.modules.base64

import org.autojs.autojs.runtime.api.modules.ScriptFunction
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.mozilla.javascript.ConsString
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.Undefined
import org.mozilla.javascript.Wrapper
import java.nio.charset.Charset
import android.util.Base64 as AndroidBase64
import org.mozilla.javascript.ScriptRuntime as RhinoScriptRuntime

/**
 * The `base64` module on the new script module layer (roadmap P1.1 example; it is not mounted by `ScriptRuntime` yet).
 *
 * Behavior follows the documentation and the d.ts: charset names ignore case and punctuation and an unknown charset is
 * an error; besides strings and byte arrays, any other value is encoded through its script `toString`, which fixes the
 * two deviations of the current module recorded in ROADMAP appendix E (`encode(123)` gave "123.0", objects with a
 * custom `toString` were encoded as "[object Object]").
 *
 * zh-CN: 新脚本模块层上的 base64 模块 (P1.1 示例, 尚未由 ScriptRuntime 挂载). 行为以文档与 d.ts 为准; 字符串与字节数组以外的值按脚本的 toString 编码,
 * 修正了附录 E 记录的现有模块的两处偏差.
 */
@ScriptModule(name = "base64")
object Base64Module {

    @ScriptFunction
    fun encode(o: Any?, encoding: String? = null): String {
        val charset = charsetOf(encoding, "base64.encode")
        return AndroidBase64.encodeToString(bytesOf(o, charset, "base64.encode"), AndroidBase64.NO_WRAP)
    }

    /** String input is Base64 text, read as US-ASCII; [encoding] is the charset of the decoded string. */
    @ScriptFunction
    fun decode(o: Any?, encoding: String? = null): String {
        val charset = charsetOf(encoding, "base64.decode")
        return String(AndroidBase64.decode(bytesOf(o, Charsets.US_ASCII, "base64.decode"), AndroidBase64.NO_WRAP), charset)
    }

    private fun bytesOf(o: Any?, charset: Charset, function: String): ByteArray {
        val value = if (o is Wrapper) o.unwrap() else o
        return when {
            value == null || Undefined.isUndefined(value) -> throw RhinoScriptRuntime.typeError("$function: argument \"o\" must not be ${if (value == null) "null" else "undefined"}")
            value is String -> value.toByteArray(charset)
            value is ConsString -> value.toString().toByteArray(charset)
            value is ByteArray -> value
            value is NativeArray -> ByteArray(value.length.toInt()) { i -> (value.get(i, value) as? Number)?.toByte() ?: 0 }
            else -> RhinoScriptRuntime.toString(value).toByteArray(charset)
        }
    }

    private fun charsetOf(encoding: String?, function: String): Charset {
        if (encoding == null) return Charset.defaultCharset()
        val wanted = normalize(encoding)
        return Charset.availableCharsets().values.firstOrNull { charset ->
            normalize(charset.name()) == wanted || charset.aliases().any { normalize(it) == wanted }
        } ?: throw RhinoScriptRuntime.rangeError("$function: unknown charset \"$encoding\"")
    }

    private fun normalize(name: String) = name.lowercase().filter { it in 'a'..'z' || it in '0'..'9' }

}
