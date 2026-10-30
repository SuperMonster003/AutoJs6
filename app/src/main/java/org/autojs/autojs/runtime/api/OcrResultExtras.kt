package org.autojs.autojs.runtime.api

import android.os.Bundle
import android.util.Log
import org.autojs.autojs.util.RhinoUtils.newNativeArray
import org.autojs.autojs.util.RhinoUtils.newNativeObject
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.ScriptableObject
import org.autojs.plugin.paddle.ocr.api.OcrResult as PluginOcrResult

/**
 * Converts the extras [Bundle] of an engine result into the object scripts read as `OcrResult.extras`.
 *
 * Boolean and string values stay as they are, a char or char sequence becomes a string, every number becomes a JS
 * number (so a long above 2^53 loses precision), arrays and collections become arrays, nested bundles and maps with
 * string keys become objects, and null stays null. Any other value is dropped and logged, as an element or as a key.
 *
 * zh-CN: 把引擎结果的 extras Bundle 转为脚本读到的 OcrResult.extras 对象. 布尔值与字符串不变, 字符及字符序列转为字符串, 数字一律转为 JS 数字
 * (超过 2^53 的 long 损失精度), 数组与集合转为数组, 嵌套 Bundle 与字符串键的 Map 转为对象, null 保持 null. 其他类型的值 (键或数组元素) 丢弃并记录日志.
 */
internal object OcrResultExtras {

    private const val TAG = "OcrResultExtras"

    private const val KEY_QUAD = "quad"

    private object Unsupported

    // android.util.Log is a stub in JVM tests.
    // zh-CN: JVM 测试里 android.util.Log 是桩.
    internal var warn: (String) -> Unit = { Log.w(TAG, it) }

    fun toScriptObject(bundle: Bundle?): NativeObject = toScriptObject(fromBundle(bundle))

    fun toScriptObject(values: Map<String, Any?>): NativeObject = newNativeObject().also { target ->
        values.forEach { (key, value) -> ScriptableObject.putProperty(target, key, toScriptValue(value)) }
    }

    /**
     * The extras of a line merged from [parts]: the keys whose values are equal in every part, so per-segment values
     * such as `quad` leave and per-call values such as `executionProvider` stay.
     *
     * zh-CN: 由 [parts] 合并成一行时的 extras: 只保留各片段取值相同的键, 所以 quad 等片段各异的值不再保留, executionProvider 等每次调用相同的值保留.
     */
    fun mergeCommon(parts: List<NativeObject>): NativeObject {
        parts.singleOrNull()?.let { return it }
        val merged = newNativeObject()
        val first = parts.firstOrNull() ?: return merged
        first.entries.forEach { (key, value) ->
            if (parts.all { it.containsKey(key) && deepEquals(it[key], value) }) {
                ScriptableObject.putProperty(merged, key.toString(), value)
            }
        }
        return merged
    }

    /**
     * Moves `quad` (x1, y1, x2, y2, ...) by the region offset, the same way `OcrResult.bounds` moves.
     *
     * zh-CN: 把 quad (x1, y1, x2, y2, ...) 按区域偏移量平移, 与 OcrResult.bounds 的平移一致.
     */
    fun offsetQuad(extras: NativeObject, dx: Int, dy: Int) {
        val quad = extras[KEY_QUAD] as? NativeArray ?: return
        for (index in quad.indices) {
            val value = quad[index] as? Number ?: continue
            quad.put(index, quad, value.toDouble() + if (index % 2 == 0) dx else dy)
        }
    }

    internal fun fromBundle(bundle: Bundle?): Map<String, Any?> {
        bundle ?: return emptyMap()
        val keys = try {
            bundle.keySet().sorted()
        } catch (e: RuntimeException) {
            // A plugin's Parcelable class cannot be read here, which fails when the bundle is unparcelled.
            // zh-CN: 宿主读不了插件自有的 Parcelable 类时, Bundle 解包就会失败.
            warn("Dropped OCR result extras: ${e.message}")
            return emptyMap()
        }
        return normalizeEntries(keys) { key ->
            @Suppress("DEPRECATION")
            bundle.get(key)
        }
    }

    internal fun normalizeEntries(keys: Iterable<String>, valueOf: (String) -> Any?): Map<String, Any?> {
        val normalized = linkedMapOf<String, Any?>()
        keys.forEach { key ->
            val value = try {
                normalize(valueOf(key))
            } catch (e: RuntimeException) {
                warn("Dropped OCR result extra \"$key\": ${e.message}")
                Unsupported
            }
            if (value === Unsupported) {
                warn("Dropped OCR result extra \"$key\" of an unsupported type")
            } else {
                normalized[key] = value
            }
        }
        return normalized
    }

    private fun normalize(value: Any?): Any? = when (value) {
        null, is Boolean, is String -> value
        is Char, is CharSequence -> value.toString()
        is Byte, is Short, is Int -> (value as Number).toInt()
        is Number -> value.toDouble()
        is Bundle -> fromBundle(value)
        is BooleanArray -> normalizeList(value.asList())
        is ByteArray -> normalizeList(value.asList())
        is ShortArray -> normalizeList(value.asList())
        is CharArray -> normalizeList(value.asList())
        is IntArray -> normalizeList(value.asList())
        is LongArray -> normalizeList(value.asList())
        is FloatArray -> normalizeList(value.asList())
        is DoubleArray -> normalizeList(value.asList())
        is Array<*> -> normalizeList(value.asList())
        is Collection<*> -> normalizeList(value)
        is Map<*, *> -> when {
            value.keys.all { it is String } -> normalizeEntries(value.keys.map { it as String }) { value[it] }
            else -> Unsupported
        }
        else -> Unsupported
    }

    private fun normalizeList(values: Collection<*>): List<Any?> = values.map(::normalize).filterIndexed { index, value ->
        (value !== Unsupported).also { supported ->
            if (!supported) warn("Dropped an OCR result extras element at index $index")
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun toScriptValue(value: Any?): Any? = when (value) {
        is Map<*, *> -> toScriptObject(value as Map<String, Any?>)
        is List<*> -> newNativeArray(value.map(::toScriptValue).toTypedArray())
        else -> value
    }

    private fun deepEquals(a: Any?, b: Any?): Boolean = when {
        a is List<*> && b is List<*> -> a.size == b.size && a.indices.all { deepEquals(a[it], b[it]) }
        a is Map<*, *> && b is Map<*, *> -> a.size == b.size && a.keys.all { b.containsKey(it) && deepEquals(a[it], b[it]) }
        else -> a == b
    }

}

internal fun PluginOcrResult.toScriptResult() = OcrResult(text, confidence, bounds, OcrResultExtras.toScriptObject(extras))
