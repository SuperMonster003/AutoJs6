package org.autojs.autojs.runtime.api.modules.sysprops

import org.autojs.autojs.runtime.api.augment.sysprops.SystemProperties
import org.autojs.autojs.runtime.api.modules.ScriptFunction
import org.autojs.autojs.runtime.api.modules.ScriptInvoke
import org.autojs.autojs.runtime.api.modules.ScriptModule
import org.autojs.autojs.runtime.api.modules.global.GlobalValues
import org.autojs.autojs.runtime.api.modules.global.GlobalValues.isNullish
import org.autojs.autojs.util.RhinoUtils
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.ScriptableObject
import org.mozilla.javascript.VarScope
import org.mozilla.javascript.regexp.NativeRegExp
import org.mozilla.javascript.ScriptRuntime as RhinoScriptRuntime

/**
 * The `sysprops` module on the new script module layer (roadmap P3.4): callable as `get`, with `get`, `getInt`,
 * `getBoolean` and `getAll` over the current reader of system properties (`augment/sysprops/SystemProperties`; to be moved
 * out before P5.2). A property name is a string (other values by their string form, as before); the default of `getInt` is
 * an integer and that of `getBoolean` a boolean as documented (T4 / D10; the current module truncated, took the
 * truthiness, or threw IllegalArgumentException).
 *
 * `getAll` filters by names and values (a string is a case-sensitive substring, a RegExp is tested, nullish matches all);
 * an object as the only argument gives the filters as `key` / `keys` and `value` / `values`, as documented (the current
 * module never fell back to `keys` / `values`: a missing `key` filtered with the text "NOT_FOUND" and matched nothing).
 *
 * zh-CN: 新脚本模块层上的 sysprops 模块 (P3.4): 可调用 (同 get), 另有 get, getInt, getBoolean 与 getAll, 由现有的系统属性读取实现完成
 * (augment/sysprops/SystemProperties; P5.2 前迁出). 属性名为字符串 (其他值与以前一样按字符串形式); getInt 的默认值为整数, getBoolean 的默认值为布尔值, 均按
 * 文档 (T4 / D10; 现有模块截断, 取真值或抛 IllegalArgumentException). getAll 按名称与值过滤 (字符串为区分大小写的包含, RegExp 调用 test, null / undefined
 * 不限制); 只有一个对象参数时按文档以 key / keys 与 value / values 给出过滤器 (现有模块从不回退到 keys / values: 缺少 key 时以文本 "NOT_FOUND" 过滤, 结果为空).
 */
@ScriptModule(name = "sysprops")
class SysPropsModule {

    @ScriptInvoke
    fun invoke(propName: String, defaultValue: String? = null): String? = SystemProperties.get(propName, defaultValue)

    @ScriptFunction
    fun get(propName: String, defaultValue: String? = null): String? = SystemProperties.get(propName, defaultValue)

    @ScriptFunction
    fun getInt(propName: String, defaultValue: Any? = null): Int? =
        SystemProperties.getInt(propName, if (isNullish(defaultValue)) null else GlobalValues.int("sysprops.getInt", "defaultValue", defaultValue))

    @ScriptFunction
    fun getBoolean(propName: String, defaultValue: Boolean? = null): Boolean? = SystemProperties.getBoolean(propName, defaultValue)

    @ScriptFunction
    fun getAll(cx: Context, scope: VarScope, keyFilter: Any? = null, valueFilter: Any? = ABSENT): NativeObject {
        val options = keyFilter as? NativeObject
        val (keys, values) = when {
            options != null && valueFilter === ABSENT -> either(options, "key", "keys") to either(options, "value", "values")
            else -> keyFilter to valueFilter.takeUnless { it === ABSENT }
        }
        val result = RhinoUtils.newNativeObject(scope)
        SystemProperties.getAll().forEach { (key, value) ->
            if (matches(cx, key, keys) && matches(cx, value, values)) result.put(key, result, value)
        }
        return result
    }

    private fun either(options: NativeObject, name: String, alias: String): Any? =
        ScriptableObject.getProperty(options, name).takeUnless { isNullish(it) } ?: ScriptableObject.getProperty(options, alias).takeUnless { isNullish(it) }

    private fun matches(cx: Context, input: String, filter: Any?): Boolean = when {
        isNullish(filter) -> true
        filter is NativeRegExp -> ScriptableObject.callMethod(cx, filter, "test", arrayOf(input)) == true
        else -> input.contains(RhinoScriptRuntime.toString(filter))
    }

    private companion object {
        val ABSENT = Any()
    }

}
