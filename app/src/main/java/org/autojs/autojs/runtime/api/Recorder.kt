package org.autojs.autojs.runtime.api

import org.autojs.autojs.extension.AnyExtensions.isJsNullish
import org.autojs.autojs.extension.AnyExtensions.jsBrief
import org.autojs.autojs.runtime.ScriptRuntime
import org.autojs.autojs.util.RhinoUtils.callFunction
import org.autojs.autojs.util.RhinoUtils.coerceLongNumber
import org.autojs.autojs.util.RhinoUtils.coerceString
import org.mozilla.javascript.BaseFunction
import org.mozilla.javascript.Callable
import org.mozilla.javascript.Context
import org.mozilla.javascript.Scriptable
import java.lang.System.currentTimeMillis

class Recorder(private val scriptRuntime: ScriptRuntime) {

    private var mKeys: MutableMap<String, Double> = mutableMapOf()
    private var mAnonymity: MutableList<Double> = mutableListOf()

    @JvmOverloads
    fun save(key: String? = null, ts: Any? = null): Double {
        return addValue(key, ts)
    }

    @JvmOverloads
    fun load(key: String? = null, ts: Any? = null): Double {
        return toTimestamp(ts) - getValue(key)
    }

    fun isGreaterThan(key: String?, compare: Any?): Boolean {
        return load(key, toTimestamp()) > coerceLongNumber(compare)
    }

    fun isLessThan(key: String?, compare: Any?): Boolean {
        return load(key, toTimestamp()) < coerceLongNumber(compare)
    }

    fun has(key: String?) = when {
        key.isJsNullish() -> false
        else -> mKeys.containsKey(Context.toString(key))
    }

    fun remove(key: String?) = when {
        key.isJsNullish() -> false
        else -> mKeys.remove(key) != null
    }

    fun clear() {
        mKeys.clear()
        mAnonymity.clear()
    }

    @JvmSynthetic
    fun shortcut(key: Any? = null, ts: Any? = null): Double = when {
        key.isJsNullish() -> when {
            mAnonymity.isNotEmpty() -> load()
            else -> save()
        }
        key is BaseFunction -> {
            val tmpKeyPrefix = key.functionName.takeUnless { it.isEmpty() } ?: "(anonymous)"
            val tmpKey = "$tmpKeyPrefix@${currentTimeMillis()}".also { save(it) }
            val thisObj = when {
                ts is Scriptable && ts !is Callable -> ts
                ts.isJsNullish() -> null
                else -> listOf(
                    "Argument[1] for recorder.shortcut",
                    "must be a JavaScript Object",
                    "instead of ${ts.jsBrief()} if provided"
                ).joinToString(" ").let { throw IllegalArgumentException(it) }
            }
            callFunction(scriptRuntime, key, null, thisObj, arrayOf())
            load(tmpKey).also { remove(tmpKey) }
        }
        else -> coerceString(key).let { k ->
            when {
                has(k) -> load(k, toTimestamp())
                else -> save(k, toTimestamp())
            }
        }

    }

    private fun addValue(key: String? = null, ts: Any? = null) = toTimestamp(ts).also {
        when {
            key.isJsNullish() -> mAnonymity.add(it)
            else -> mKeys[key!!] = it
        }
    }

    private fun getValue(key: String?): Double = when {
        key.isJsNullish() -> mAnonymity.removeLastOrNull()
        else -> mKeys[Context.toString(key)]
    } ?: Double.NaN

    private fun toTimestamp(ts: Any? = null): Double = when {
        ts.isJsNullish() -> currentTimeMillis().toDouble()
        else -> coerceLongNumber(ts).toDouble()
    }

}
