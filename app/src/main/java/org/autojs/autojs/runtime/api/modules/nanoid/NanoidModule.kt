package org.autojs.autojs.runtime.api.modules.nanoid

import org.autojs.autojs.runtime.api.modules.ScriptInvoke
import org.autojs.autojs.runtime.api.modules.ScriptMessages
import org.autojs.autojs.runtime.api.modules.ScriptModule
import java.security.SecureRandom
import org.mozilla.javascript.ScriptRuntime as RhinoScriptRuntime

/**
 * The `nanoid` module on the new script module layer (roadmap P2.2): `nanoid(size = 21)` is a random ID of URL-safe
 * characters from `SecureRandom`. As documented, `size` is converted to a 32-bit integer (truncated toward zero) and must
 * not be negative; a negative size is a RangeError (the current module failed with NegativeArraySizeException), a value
 * without a number a TypeError, NaN / Infinity / out of range a RangeError (D10). One `SecureRandom` serves every call
 * (the current module created one per call).
 *
 * zh-CN: 新脚本模块层上的 nanoid 模块 (P2.2): nanoid(size = 21) 返回由 SecureRandom 生成的 URL 安全字符随机 ID. 按文档, size 转换为 32 位整数 (向零截断)
 * 且不能为负数; 负数为 RangeError (现有模块以 NegativeArraySizeException 失败), 不是数值为 TypeError, NaN / Infinity / 越界为 RangeError (D10).
 * 所有调用共用一个 SecureRandom (现有模块每次调用新建一个).
 */
@ScriptModule(name = "nanoid")
object NanoidModule {

    private const val FUNCTION = "nanoid"
    private const val DEFAULT_SIZE = 21

    @Suppress("SpellCheckingInspection")
    private const val URL_ALPHABET = "useandom-26T198340PX75pxJACKVERYMINDBUSHWOLF_GQZbfghjklqvwyzrict"

    private val random by lazy { SecureRandom() }

    @ScriptInvoke
    fun invoke(size: Double? = null): String {
        val n = sizeOf(size)
        val bytes = ByteArray(n).also { random.nextBytes(it) }
        return buildString(n) { bytes.forEach { append(URL_ALPHABET[it.toInt() and 63]) } }
    }

    private fun sizeOf(size: Double?): Int {
        if (size == null) return DEFAULT_SIZE
        if (size.isNaN() || size.isInfinite() || size <= Int.MIN_VALUE - 1.0 || size >= Int.MAX_VALUE + 1.0) {
            throw RhinoScriptRuntime.rangeError(ScriptMessages.failedToCall(FUNCTION, ScriptMessages.argumentInteger("size", 32, RhinoScriptRuntime.numberToString(size, 10))))
        }
        val n = size.toInt()
        if (n < 0) throw RhinoScriptRuntime.rangeError(ScriptMessages.failedToCall(FUNCTION, ScriptMessages.argumentCannotBe("size", n.toString())))
        return n
    }

}
