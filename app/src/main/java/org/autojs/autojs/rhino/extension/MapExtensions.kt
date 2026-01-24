package org.autojs.autojs.rhino.extension

import org.autojs.autojs.rhino.extension.ArrayExtensions.toNativeArray
import org.autojs.autojs.rhino.extension.IterableExtensions.toNativeArray
import org.autojs.autojs.util.RhinoUtils.UNDEFINED
import org.autojs.autojs.util.RhinoUtils.newNativeObject
import org.mozilla.javascript.Context
import org.mozilla.javascript.NativeObject
import org.mozilla.javascript.Wrapper

object MapExtensions {

    fun <K, V> Map<K, V>.toNativeObject(): NativeObject = newNativeObject().also { o ->
        forEach { entry: Map.Entry<K, V> ->
            val key = Context.toString(entry.key)
            when (val value = entry.value) {
                is String -> o.put(key, o, value)
                is Number -> o.put(key, o, Context.toNumber(value))
                is Boolean -> o.put(key, o, Context.toBoolean(value))
                is Wrapper -> o.put(key, o, value.unwrap())
                is Unit -> o.put(key, o, UNDEFINED)
                is List<*> -> o.put(key, o, value.toNativeArray())
                is Array<*> -> o.put(key, o, value.toNativeArray())
                is Map<*, *> -> o.put(key, o, value.toNativeObject())
                is Pair<*, *> -> o.put(key, o, newNativeObject().also { newObj ->
                    newObj.put(Context.toString(value.first), o, value.second)
                })
                else -> o.put(key, o, Context.javaToJS(value, o))
            }
        }
    }


}