package org.autojs.autojs.runtime.api.modules.flow

import org.autojs.autojs.core.automator.flow.Stabilizer
import org.autojs.autojs.util.RhinoUtils
import org.mozilla.javascript.NativeArray
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Scriptable
import org.mozilla.javascript.Undefined
import java.util.IdentityHashMap

/** Copies script arrays and plain objects before the next sampling callback can mutate them.
 * zh-CN: 在下次采样回调可能修改对象之前复制脚本数组与普通对象. */
internal object FlowSnapshot {

    fun capture(value: Any?): Any? {
        val visiting = IdentityHashMap<Any, Boolean>()
        fun copy(raw: Any?): Any? {
            val v = RhinoUtils.unwrap(raw)
            if (v == null || v is Undefined || v === Scriptable.NOT_FOUND) return null
            if (v is CharSequence) return v.toString()
            if (v is Boolean || v is Number) return v
            require(visiting.put(v, true) == null) { "A stability snapshot cannot contain cycles" }
            return try {
                when (v) {
                    is NativeArray -> v.map { copy(it) }
                    is NativeObject -> v.ids.associate { key ->
                        key.toString() to copy(if (key is Number) v.get(key.toInt(), v) else v.get(key.toString(), v))
                    }
                    is Map<*, *> -> v.entries.associate { copy(it.key) to copy(it.value) }
                    is Iterable<*> -> v.map { copy(it) }
                    is Array<*> -> v.map { copy(it) }
                    else -> Stabilizer.snapshot(v)
                }
            } finally {
                visiting.remove(v)
            }
        }
        return copy(value)
    }
}
